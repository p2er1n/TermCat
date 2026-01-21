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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
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

class ScreenshotService : Service() {
    private val handlerThread = HandlerThread("ScreenCapture")
    private lateinit var backgroundHandler: Handler
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this@ScreenshotService, "Screen capture stopped.", Toast.LENGTH_SHORT).show()
            }
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        handlerThread.start()
        backgroundHandler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
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

        backgroundHandler.postDelayed({ captureImage() }, 250)
    }

    private fun captureImage() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage()
        if (image == null) {
            backgroundHandler.postDelayed({ captureImage() }, 100)
            return
        }

        val bitmap = image.toBitmap()
        image.close()

        val outputFile = File(cacheDir, "capture_${System.currentTimeMillis()}.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        sendCaptureDone()
        val ocrText = runOcr(bitmap)
        if (ocrText.isNotEmpty()) {
            val textFile = File(cacheDir, "capture_${System.currentTimeMillis()}.txt")
            textFile.writeText(ocrText)
        }
        runLlm(ocrText)

        Handler(Looper.getMainLooper()).post {
            val messageRes = if (ocrText.isNotEmpty()) R.string.ocr_done else R.string.ocr_failed
            Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
        }

        stopSelf()
    }

    private fun releaseResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun runOcr(bitmap: Bitmap): String {
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
        val systemPrompt = """
            You are a legal risk assistant. Analyze the text and highlight risky clauses.
            Return concise bullet points. If no risks, say "No obvious risks found."
        """.trimIndent()
        val systemMessage = ChatCompletionSystemMessageParam.builder()
            .content(ChatCompletionSystemMessageParam.Content.ofText(systemPrompt))
            .build()
        val userMessage = ChatCompletionUserMessageParam.builder()
            .content(ChatCompletionUserMessageParam.Content.ofText("Text:\n$ocrText"))
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

    private fun sendLlmResult(text: String) {
        val intent = Intent(FloatingWindowService.ACTION_LLM_RESULT).apply {
            setPackage(packageName)
            putExtra(FloatingWindowService.EXTRA_LLM_TEXT, text)
        }
        sendBroadcast(intent)
    }

    private fun sendLlmError(message: String, ocrText: String) {
        val intent = Intent(FloatingWindowService.ACTION_LLM_ERROR).apply {
            setPackage(packageName)
            putExtra(FloatingWindowService.EXTRA_LLM_ERROR, message)
            putExtra(FloatingWindowService.EXTRA_OCR_TEXT, ocrText)
        }
        sendBroadcast(intent)
    }

    private fun sendCaptureDone() {
        val intent = Intent(FloatingWindowService.ACTION_CAPTURE_DONE).apply {
            setPackage(packageName)
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
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
        private const val NOTIFICATION_ID = 201
        private const val OCR_TIMEOUT_SECONDS = 8L
        private const val LLM_TIMEOUT_SECONDS = 20L
    }
}
