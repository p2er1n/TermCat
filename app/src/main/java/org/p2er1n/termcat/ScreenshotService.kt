package org.p2er1n.termcat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import android.content.ComponentName
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatCompletion
import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatCompletionMessageParam
import com.openai.models.ChatCompletionSystemMessageParam
import com.openai.models.ChatCompletionUserMessageParam
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import android.util.Log
import android.os.ResultReceiver
import androidx.appcompat.app.AppCompatDelegate
import androidx.annotation.RawRes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class ScreenshotService : Service() {
    private val handlerThread = HandlerThread("ScreenCapture")
    private lateinit var backgroundHandler: Handler
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile
    private var cancelled = false
    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val paddleOcrEngine: PaddleOcrEngine by lazy {
        PaddleOcrEngine(this)
    }
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@ScreenshotService, "Screen capture stopped.", Toast.LENGTH_SHORT).show()
            }
            stopSelf()
        }
    }
    private var captureImageFile: File? = null
    private var captureTextFile: File? = null

    override fun onCreate() {
        super.onCreate()
        handlerThread.start()
        backgroundHandler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CAPTURE) {
            cancelled = true
            stopService(Intent(this, PaddleOcrService::class.java))
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        cancelled = false
        startForegroundCompat()
        backgroundHandler.post {
            startCapture(resultCode, resultData)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
        handlerThread.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection?.registerCallback(projectionCallback, backgroundHandler)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TermCatCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )

        backgroundHandler.postDelayed({ captureAndProcess() }, 250)
    }

    private fun captureAndProcess() {
        try {
            if (cancelled) {
                return
            }
            val bitmaps = captureBitmaps()
            if (cancelled) {
                return
            }
            stopProjectionSession()
            sendCaptureDone(bitmaps.size)
            val ocrText = runOcrBatch(bitmaps)
            if (cancelled) {
                return
            }
            if (ocrText.isNotEmpty()) {
                val textFile = File(cacheDir, "capture_${System.currentTimeMillis()}.txt")
                textFile.writeText(ocrText)
                captureTextFile = textFile
            }
            if (cancelled) {
                return
            }
            runLlm(ocrText)

            Handler(Looper.getMainLooper()).post {
                val messageRes = if (ocrText.isNotEmpty()) R.string.ocr_done else R.string.ocr_failed
                Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
            }
        } finally {
            cleanupCaptureFiles()
            stopSelf()
        }
    }

    private fun captureBitmaps(): List<Bitmap> {
        val canAutoScroll = isScrollServiceEnabled()
        if (!canAutoScroll) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    getString(R.string.accessibility_scroll_required),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val bitmaps = mutableListOf<Bitmap>()
        var savedImage = false
        val maxPages = if (canAutoScroll) {
            AppPrefs.getMaxCapturePages(this)
        } else {
            1
        }
        var endHits = 0
        var stuckHits = 0
        var noChangeHits = 0
        var lastSignature: Long? = null
        var hasScrolled = false

        if (canAutoScroll) {
            AccessibilityScrollService.resetScrollState()
        }

        for (index in 0 until maxPages) {
            if (cancelled) {
                break
            }
            if (canAutoScroll && hasScrolled && AccessibilityScrollService.isAtScrollEnd()) {
                endHits += 1
                if (endHits >= 2) {
                    break
                }
            }
            val bitmap = captureBitmapWithRetry() ?: break
            if (!savedImage) {
                val outputFile = File(cacheDir, "capture_${System.currentTimeMillis()}.png")
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                captureImageFile = outputFile
                savedImage = true
            }

            val signature = computeBitmapSignature(bitmap)
            if (lastSignature != null && signature == lastSignature) {
                noChangeHits += 1
                bitmap.recycle()
                if (noChangeHits >= NO_CHANGE_HITS_TO_STOP) {
                    break
                }
            } else {
                noChangeHits = 0
                lastSignature = signature
                bitmaps.add(bitmap)
            }

            if (canAutoScroll && index < maxPages - 1) {
                if (hasScrolled && AccessibilityScrollService.isAtScrollEnd()) {
                    endHits += 1
                    if (endHits >= 2) {
                        break
                    }
                } else if (!hasScrolled) {
                    endHits = 0
                }
                val beforeEventTime = AccessibilityScrollService.getLastScrollEventTime()
                val scrolled = AccessibilityScrollService.performScrollDown()
                if (!scrolled) {
                    break
                }
                hasScrolled = true
                AccessibilityScrollService.waitForUiToSettle()
                val scrollState = AccessibilityScrollService.waitForScrollState(
                    beforeEventTime,
                    SCROLL_EVENT_TIMEOUT_MS
                )
                if (scrollState != null) {
                    if (scrollState.maxScrollY > 0 && scrollState.scrollY >= scrollState.maxScrollY) {
                        endHits += 1
                        if (endHits >= 2) {
                            break
                        }
                    } else {
                        endHits = 0
                    }
                    if (scrollState.deltaY <= MIN_SCROLL_DELTA_PX) {
                        stuckHits += 1
                        if (stuckHits >= 2) {
                            break
                        }
                    } else {
                        stuckHits = 0
                    }
                }
                if (AccessibilityScrollService.isAtScrollEnd()) {
                    endHits += 1
                    if (endHits >= 2) {
                        break
                    }
                } else {
                    endHits = 0
                }
            }
        }

        return bitmaps
    }

    private fun captureBitmapWithRetry(): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        for (attempt in 0 until 10) {
            image = reader.acquireLatestImage()
            if (image != null) break
            SystemClock.sleep(80)
        }
        val bitmap = image?.toBitmap()
        image?.close()
        return bitmap
    }

    private fun runOcrBatch(bitmaps: List<Bitmap>): String {
        if (bitmaps.isEmpty()) return ""
        val texts = mutableListOf<String>()
        val total = bitmaps.size
        sendOcrProgress(0, total)
        for ((index, bitmap) in bitmaps.withIndex()) {
            if (cancelled) {
                bitmap.recycle()
                break
            }
            val ocrText = runOcr(bitmap)
            if (ocrText.isNotBlank()) {
                texts.add(ocrText)
            }
            sendOcrProgress(index + 1, total)
            bitmap.recycle()
        }
        return mergeOcrTexts(texts)
    }

    private fun mergeOcrTexts(texts: List<String>): String {
        if (texts.isEmpty()) return ""
        val seen = LinkedHashSet<String>()
        texts.forEach { block ->
            block.split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { line -> seen.add(line) }
        }
        return seen.joinToString(separator = "\n")
    }

    private fun computeBitmapSignature(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIGNATURE_SIZE, SIGNATURE_SIZE, true)
        val pixels = IntArray(SIGNATURE_SIZE * SIGNATURE_SIZE)
        scaled.getPixels(pixels, 0, SIGNATURE_SIZE, 0, 0, SIGNATURE_SIZE, SIGNATURE_SIZE)
        if (scaled != bitmap) {
            scaled.recycle()
        }
        var sum = 0L
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val lum = (r * 30 + g * 59 + b * 11) / 100
            sum += lum.toLong()
        }
        val avg = sum / pixels.size
        var hash = 0L
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val lum = (r * 30 + g * 59 + b * 11) / 100
            if (lum >= avg) {
                hash = hash or (1L shl i)
            }
        }
        return hash
    }

    private fun isScrollServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val id = ComponentName(this, AccessibilityScrollService::class.java).flattenToString()
        if (enabled.any { it.id == id }) {
            return true
        }
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.split(':').any { it.equals(id, ignoreCase = true) }
    }

    private fun cleanupCaptureFiles() {
        captureImageFile?.delete()
        captureImageFile = null
        captureTextFile?.delete()
        captureTextFile = null
    }

    private fun releaseResources() {
        stopProjectionSession()
    }

    private fun stopProjectionSession() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundCompat() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun runOcr(bitmap: Bitmap): String {
        return if (AppPrefs.getOcrEngine(this) == AppPrefs.OCR_ENGINE_PADDLE) {
            runPaddleOcrWithRetry(bitmap)
        } else {
            runMlKitOcr(bitmap)
        }
    }

    private fun runMlKitOcr(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val latinText = Tasks.await(
                latinRecognizer.process(image),
                OCR_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            ).text
            val chineseText = Tasks.await(
                chineseRecognizer.process(image),
                OCR_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            ).text
            val combined = listOf(latinText, chineseText)
                .filter { it.isNotBlank() }
                .distinct()
            combined.joinToString(separator = "\n")
        } catch (exception: Exception) {
            ""
        }
    }

    private fun runPaddleOcrWithRetry(bitmap: Bitmap): String {
        if (cancelled) return ""
        val imagePath = writeBitmapForOcr(bitmap) ?: return ""
        try {
            repeat(PADDLE_RETRY_COUNT) { attempt ->
                if (cancelled) return ""
                val result = runPaddleOcrOnce(imagePath)
                if (result.isNotBlank()) {
                    return result
                }
                Log.w(TAG, "Paddle OCR returned empty, attempt=${attempt + 1}")
            }
            return ""
        } finally {
            File(imagePath).delete()
        }
    }

    private fun runPaddleOcrOnce(imagePath: String): String {
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference("")
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val text = resultData?.getString(PaddleOcrService.EXTRA_RESULT_TEXT).orEmpty()
                resultRef.set(text)
                latch.countDown()
            }
        }

        val intent = Intent(this, PaddleOcrService::class.java).apply {
            putExtra(PaddleOcrService.EXTRA_IMAGE_PATH, imagePath)
            putExtra(PaddleOcrService.EXTRA_RESULT_RECEIVER, receiver)
        }
        startService(intent)

        val ok = latch.await(PADDLE_PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!ok) {
            Log.w(TAG, "Paddle OCR timed out after ${PADDLE_PROCESS_TIMEOUT_MS}ms")
        }
        return resultRef.get()
    }

    private fun writeBitmapForOcr(bitmap: Bitmap): String? {
        val outputFile = File(cacheDir, "paddle_ocr_${System.currentTimeMillis()}.png")
        return try {
            FileOutputStream(outputFile).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return null
                }
            }
            outputFile.absolutePath
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to write bitmap for OCR", exception)
            null
        }
    }

    private fun runLlm(ocrText: String) {
        val endpoint = AppPrefs.getLlmEndpoint(this)
        val apiKey = AppPrefs.getLlmApiKey(this)
        val model = AppPrefs.getLlmModel(this)

        if (endpoint.isBlank() || apiKey.isBlank() || model.isBlank()) {
            sendLlmError(getString(R.string.llm_error_config), ocrText)
            return
        }
        if (ocrText.isBlank()) {
            sendLlmError(getString(R.string.llm_error_response), "")
            return
        }

        try {
            sendLlmStatus(getString(R.string.result_progress_llm))
            val client = createOpenAiClient(apiKey, endpoint)
            val params = buildLlmParams(model, ocrText)
            val response = client.chat().completions().create(params)
            val content = extractContent(response)
            if (content.isBlank()) {
                sendLlmError(getString(R.string.llm_error_response), ocrText)
                return
            }
            sendLlmResult(content)
        } catch (exception: Exception) {
            sendLlmError(getString(R.string.llm_error_generic), ocrText)
        }
    }

    private fun createOpenAiClient(apiKey: String, endpoint: String): OpenAIClient {
        return OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .baseUrl(normalizeBaseUrl(endpoint))
            .build()
    }

    private fun buildLlmParams(model: String, ocrText: String): ChatCompletionCreateParams {
        val systemPrompt = loadSystemPrompt()
        val systemMessage = ChatCompletionSystemMessageParam.builder()
            .content(ChatCompletionSystemMessageParam.Content.ofText(systemPrompt))
            .build()
        val userMessage = ChatCompletionUserMessageParam.builder()
            .content(
                ChatCompletionUserMessageParam.Content.ofText(
                    loadUserPrompt(ocrText)
                )
            )
            .build()

        val messages = listOf(
            ChatCompletionMessageParam.ofSystem(systemMessage),
            ChatCompletionMessageParam.ofUser(userMessage)
        )

        return ChatCompletionCreateParams.builder()
            .model(model)
            .messages(messages)
            .temperature(0.2)
            .build()
    }

    private fun extractContent(response: ChatCompletion): String {
        val first = response.choices().firstOrNull() ?: return ""
        val content = first.message().content()
        return content.orElse("")
    }

    private fun normalizeBaseUrl(endpoint: String): String {
        val trimmed = endpoint.trim().removeSuffix("/")
        val v1Index = trimmed.indexOf("/v1")
        return if (v1Index >= 0) {
            trimmed.substring(0, v1Index + 3)
        } else {
            trimmed
        }
    }

    private fun loadSystemPrompt(): String {
        val resId = if (isAppLanguageChinese()) {
            R.raw.llm_system_prompt_zh
        } else {
            R.raw.llm_system_prompt_en
        }
        return readRawText(resId).trimEnd()
    }

    private fun loadUserPrompt(ocrText: String): String {
        val resId = if (isAppLanguageChinese()) {
            R.raw.llm_user_prompt_zh
        } else {
            R.raw.llm_user_prompt_en
        }
        val template = readRawText(resId)
        return template.replace("{{TERMS}}", ocrText)
    }

    private fun readRawText(@RawRes resId: Int): String {
        val input = resources.openRawResource(resId)
        return input.bufferedReader().use { it.readText() }
    }

    private fun isAppLanguageChinese(): Boolean {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = appLocales[0] ?: resources.configuration.locales[0]
        return locale?.language?.startsWith("zh") == true
    }

    private fun sendLlmResult(text: String) {
        if (cancelled) return
        val intent = Intent(FloatingWindowService.ACTION_LLM_RESULT).apply {
            setPackage(packageName)
            putExtra(FloatingWindowService.EXTRA_LLM_TEXT, text)
        }
        sendBroadcast(intent)
    }

    private fun sendLlmError(message: String, ocrText: String) {
        if (cancelled) return
        val intent = Intent(FloatingWindowService.ACTION_LLM_ERROR).apply {
            setPackage(packageName)
            putExtra(FloatingWindowService.EXTRA_LLM_ERROR, message)
            putExtra(FloatingWindowService.EXTRA_OCR_TEXT, ocrText)
        }
        sendBroadcast(intent)
    }

    private fun sendCaptureDone(totalPages: Int) {
        if (cancelled) return
        val intent = Intent(FloatingWindowService.ACTION_CAPTURE_DONE).apply {
            setPackage(packageName)
            putExtra(FloatingWindowService.EXTRA_OCR_TOTAL, totalPages)
        }
        sendBroadcast(intent)
    }

    private fun sendOcrProgress(done: Int, total: Int) {
        if (cancelled) return
        val intent = Intent(FloatingWindowService.ACTION_OCR_PROGRESS).apply {
            setPackage(packageName)
            putExtra(FloatingWindowService.EXTRA_OCR_DONE, done)
            putExtra(FloatingWindowService.EXTRA_OCR_TOTAL, total)
        }
        sendBroadcast(intent)
    }

    private fun sendLlmStatus(status: String) {
        if (cancelled) return
        val intent = Intent(FloatingWindowService.ACTION_LLM_STATUS).apply {
            setPackage(packageName)
            putExtra(FloatingWindowService.EXTRA_LLM_STATUS, status)
        }
        sendBroadcast(intent)
    }

    private fun createNotification(): Notification {
        val channelId = "termcat_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                getString(R.string.capture_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val ACTION_STOP_CAPTURE = "org.p2er1n.termcat.STOP_CAPTURE"
        private const val NOTIFICATION_ID = 201
        private const val OCR_TIMEOUT_SECONDS = 8L
        private const val LLM_TIMEOUT_SECONDS = 20L
        private const val SCROLL_EVENT_TIMEOUT_MS = 900L
        private const val MIN_SCROLL_DELTA_PX = 4
        private const val SIGNATURE_SIZE = 8
        private const val NO_CHANGE_HITS_TO_STOP = 2
        private const val TAG = "ScreenshotService"
        private const val PADDLE_PROCESS_TIMEOUT_MS = 12000L
        private const val PADDLE_RETRY_COUNT = 2
    }
}
