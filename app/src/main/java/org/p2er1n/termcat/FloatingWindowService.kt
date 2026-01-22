package org.p2er1n.termcat

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.IntentFilter
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import android.widget.TextView
import android.text.method.LinkMovementMethod
import io.noties.markwon.Markwon
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.provider.Settings
import android.content.ComponentName
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class FloatingWindowService : Service() {
    private enum class ProgressKind {
        NONE,
        PREPARING,
        OCR,
        LLM,
        CUSTOM
    }

    private enum class BodyKind {
        PROCESSING,
        EMPTY,
        RESULT
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var resultView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var resultParams: WindowManager.LayoutParams
    private var receiverRegistered = false
    private var lastResultFull = ""
    private var progressKind = ProgressKind.NONE
    private var progressDone = 0
    private var progressTotal = 0
    private var bodyKind = BodyKind.RESULT
    private var customStatusText: String? = null
    private val markwon by lazy { Markwon.create(this) }

    override fun onCreate() {
        super.onCreate()
        if (!isScrollServiceEnabled()) {
            Toast.makeText(
                this,
                getString(R.string.accessibility_scroll_required),
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sendOverlayStatusBroadcast(true)
        AppPrefs.setOverlayRunning(this, true)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        resultParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            x = 0
            y = 0
        }

        val themedContext = createLocalizedThemedContext()
        overlayView = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_floating_window, null, false)
        overlayView.findViewById<View>(R.id.overlay_card).setOnClickListener {
            handleCaptureClick()
        }

        resultView = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_result_sheet, null, false)
        resultView.visibility = View.GONE
        resultView.findViewById<TextView>(R.id.result_body).movementMethod = LinkMovementMethod.getInstance()
        val moreOverlay = resultView.findViewById<View>(R.id.result_more_overlay)
        val stopButton = resultView.findViewById<MaterialButton>(R.id.result_close)
        stopButton.setOnClickListener {
            stopCaptureAndHide()
        }
        val copyButton = resultView.findViewById<MaterialButton>(R.id.result_copy)
        copyButton.text = getLocalizedString(R.string.result_copy)
        copyButton.setOnClickListener {
            copyResult()
        }
        moreOverlay.setOnClickListener { openFullResult() }

        attachDragHandler(overlayView)
        windowManager.addView(overlayView, layoutParams)
        windowManager.addView(resultView, resultParams)
        registerResultReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        if (::resultView.isInitialized) {
            windowManager.removeView(resultView)
        }
        unregisterResultReceiver()
        AppPrefs.setOverlayRunning(this, false)
        sendOverlayStatusBroadcast(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createLocalizedContext(): Context {
        val locale = AppCompatDelegate.getApplicationLocales()[0]
        if (locale == null) {
            return this
        }
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        return createConfigurationContext(config)
    }

    private fun createLocalizedThemedContext(): Context {
        return ContextThemeWrapper(createLocalizedContext(), R.style.Theme_TermCat)
    }

    private fun getLocalizedString(resId: Int, vararg args: Any): String {
        val localized = createLocalizedContext()
        return if (args.isNotEmpty()) {
            localized.getString(resId, *args)
        } else {
            localized.getString(resId)
        }
    }

    private fun getStringForLocale(locale: Locale, resId: Int): String {
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        val localized = createConfigurationContext(config)
        return localized.getString(resId)
    }

    private fun normalizeLlmStatus(status: String): Pair<ProgressKind, String> {
        val en = getStringForLocale(Locale.ENGLISH, R.string.result_progress_llm)
        val zh = getStringForLocale(Locale.SIMPLIFIED_CHINESE, R.string.result_progress_llm)
        return if (status == en || status == zh) {
            ProgressKind.LLM to getLocalizedString(R.string.result_progress_llm)
        } else {
            ProgressKind.CUSTOM to status
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(target: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var downTime = 0L
        var moved = false

        target.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downTime = event.downTime
                    moved = false
                    target.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) {
                        moved = true
                    }
                    layoutParams.x = startX + dx
                    layoutParams.y = startY + dy
                    windowManager.updateViewLayout(overlayView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    target.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (!moved && event.eventTime - downTime < 250) {
                        target.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    target.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    true
                }
                else -> false
            }
        }
    }

    private fun handleCaptureClick() {
        hideOverlaysForCapture()
        val intent = Intent(this, CapturePermissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
    }

    private fun showProcessing() {
        lastResultFull = ""
        progressKind = ProgressKind.PREPARING
        bodyKind = BodyKind.PROCESSING
        resultView.findViewById<TextView>(R.id.result_title).text =
            getLocalizedString(R.string.result_title)
        resultView.findViewById<TextView>(R.id.result_body).text =
            getLocalizedString(R.string.result_processing)
        resultView.findViewById<TextView>(R.id.result_progress).text =
            getLocalizedString(R.string.result_progress_preparing)
        resultView.findViewById<MaterialButton>(R.id.result_copy).isEnabled = false
        resultView.findViewById<View>(R.id.result_more_overlay).isEnabled = false
        resultView.findViewById<View>(R.id.result_more_overlay).visibility = View.GONE
        updateStopButton()
        resultView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
    }

    private fun updateOcrProgress(done: Int, total: Int) {
        progressKind = ProgressKind.OCR
        progressDone = done
        progressTotal = total
        resultView.findViewById<TextView>(R.id.result_progress).text =
            getLocalizedString(R.string.result_progress_ocr, done, total)
        resultView.findViewById<View>(R.id.result_more_overlay).visibility = View.GONE
        updateStopButton()
        resultView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
    }

    private fun updateLlmStatus(status: String) {
        val (kind, display) = normalizeLlmStatus(status)
        progressKind = kind
        customStatusText = if (kind == ProgressKind.CUSTOM) display else null
        resultView.findViewById<TextView>(R.id.result_progress).text = display
        resultView.findViewById<View>(R.id.result_more_overlay).visibility = View.GONE
        updateStopButton()
        resultView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
    }

    private fun showResult(text: String) {
        bodyKind = if (text.isBlank()) BodyKind.EMPTY else BodyKind.RESULT
        val body = text.ifBlank { getLocalizedString(R.string.result_empty) }
        progressKind = ProgressKind.NONE
        resultView.findViewById<TextView>(R.id.result_title).text =
            getLocalizedString(R.string.result_title)
        markwon.setMarkdown(resultView.findViewById(R.id.result_body), body)
        resultView.findViewById<TextView>(R.id.result_progress).text = ""
        resultView.findViewById<MaterialButton>(R.id.result_copy).isEnabled = text.isNotBlank()
        resultView.findViewById<View>(R.id.result_more_overlay).isEnabled = text.isNotBlank()
        resultView.findViewById<View>(R.id.result_more_overlay).visibility =
            if (text.isNotBlank()) View.VISIBLE else View.GONE
        updateStopButton()
        resultView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
    }

    private fun hideResult() {
        resultView.visibility = View.GONE
    }

    private fun updateStopButton() {
        val button = resultView.findViewById<MaterialButton>(R.id.result_close)
        val progressView = resultView.findViewById<TextView>(R.id.result_progress)
        val progressText = progressView.text.toString()
        val isProcessing = progressText.isNotBlank()
        progressView.visibility = if (isProcessing) View.VISIBLE else View.GONE
        if (isProcessing) {
            button.text = getLocalizedString(R.string.result_stop)
            button.setOnClickListener { stopCaptureAndHide() }
        } else {
            button.text = getLocalizedString(R.string.result_close)
            button.setOnClickListener { hideResult() }
        }
    }

    private fun refreshLocalizedTexts() {
        if (!::resultView.isInitialized) return
        val titleView = resultView.findViewById<TextView>(R.id.result_title)
        titleView.text = getLocalizedString(R.string.result_title)
        val copyButton = resultView.findViewById<MaterialButton>(R.id.result_copy)
        copyButton.text = getLocalizedString(R.string.result_copy)
        val bodyView = resultView.findViewById<TextView>(R.id.result_body)
        when (bodyKind) {
            BodyKind.PROCESSING -> bodyView.text = getLocalizedString(R.string.result_processing)
            BodyKind.EMPTY -> bodyView.text = getLocalizedString(R.string.result_empty)
            BodyKind.RESULT -> Unit
        }
        val progressView = resultView.findViewById<TextView>(R.id.result_progress)
        when (progressKind) {
            ProgressKind.PREPARING -> {
                progressView.text = getLocalizedString(R.string.result_progress_preparing)
            }
            ProgressKind.OCR -> {
                progressView.text = getLocalizedString(
                    R.string.result_progress_ocr,
                    progressDone,
                    progressTotal
                )
            }
            ProgressKind.LLM -> {
                progressView.text = getLocalizedString(R.string.result_progress_llm)
            }
            ProgressKind.CUSTOM -> {
                progressView.text = customStatusText.orEmpty()
            }
            else -> Unit
        }
        updateStopButton()
    }

    private fun stopCaptureAndHide() {
        sendStopCapture()
        lastResultFull = ""
        hideResult()
        restoreOverlay()
    }

    private fun hideOverlaysForCapture() {
        overlayView.visibility = View.GONE
        resultView.visibility = View.GONE
    }

    private fun restoreOverlay() {
        overlayView.visibility = View.VISIBLE
    }

    private fun copyResult() {
        val text = resultView.findViewById<TextView>(R.id.result_body).text.toString()
        if (text.isBlank() || text == getLocalizedString(R.string.result_processing)) {
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TermCat OCR", text))
        Toast.makeText(this, getLocalizedString(R.string.result_copied), Toast.LENGTH_SHORT).show()
    }

    private fun openFullResult() {
        if (lastResultFull.isBlank()) {
            return
        }
        val intent = Intent(this, ResultDetailActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ResultDetailActivity.EXTRA_RESULT_TEXT, lastResultFull)
        startActivity(intent)
        hideResult()
    }

    private fun registerResultReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_CAPTURE_CANCELLED)
            addAction(ACTION_CAPTURE_DONE)
            addAction(ACTION_OCR_PROGRESS)
            addAction(ACTION_LLM_RESULT)
            addAction(ACTION_LLM_STATUS)
            addAction(ACTION_LLM_ERROR)
            addAction(ACTION_APP_LOCALE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(resultReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterResultReceiver() {
        if (!receiverRegistered) return
        unregisterReceiver(resultReceiver)
        receiverRegistered = false
    }

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CAPTURE_DONE -> {
                    val total = intent.getIntExtra(EXTRA_OCR_TOTAL, 0)
                    showProcessing()
                    if (total > 0) {
                        updateOcrProgress(0, total)
                    }
                }
                ACTION_OCR_PROGRESS -> {
                    val done = intent.getIntExtra(EXTRA_OCR_DONE, 0)
                    val total = intent.getIntExtra(EXTRA_OCR_TOTAL, 0)
                    updateOcrProgress(done, total)
                }
                ACTION_LLM_STATUS -> {
                    val status = intent.getStringExtra(EXTRA_LLM_STATUS)
                        ?: getLocalizedString(R.string.result_progress_llm)
                    updateLlmStatus(status)
                }
                ACTION_LLM_RESULT -> {
                    val rawText = intent.getStringExtra(EXTRA_LLM_TEXT).orEmpty()
                    lastResultFull = rawText
                    val trimmed = rawText.take(MAX_RESULT_CHARS)
                    showResult(trimmed)
                }
                ACTION_LLM_ERROR -> {
                    val error = intent.getStringExtra(EXTRA_LLM_ERROR).orEmpty()
                    val ocr = intent.getStringExtra(EXTRA_OCR_TEXT).orEmpty()
                    val combined = buildString {
                        append(error)
                        if (ocr.isNotBlank()) {
                            append("\n\n")
                            append(ocr)
                        }
                    }
                    lastResultFull = combined
                    showResult(combined.take(MAX_RESULT_CHARS))
                }
                ACTION_CAPTURE_CANCELLED -> {
                    restoreOverlay()
                }
                ACTION_APP_LOCALE_CHANGED -> {
                    refreshLocalizedTexts()
                }
            }
        }
    }

    private fun sendOverlayStatusBroadcast(running: Boolean) {
        val intent = Intent(ACTION_OVERLAY_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_OVERLAY_RUNNING, running)
        }
        sendBroadcast(intent)
    }

    private fun sendStopCapture() {
        val intent = Intent(this, ScreenshotService::class.java).apply {
            action = ScreenshotService.ACTION_STOP_CAPTURE
        }
        startService(intent)
    }

    private fun isScrollServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
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


    companion object {
        const val ACTION_CAPTURE_CANCELLED = "org.p2er1n.termcat.CAPTURE_CANCELLED"
        const val ACTION_CAPTURE_DONE = "org.p2er1n.termcat.CAPTURE_DONE"
        const val ACTION_OCR_PROGRESS = "org.p2er1n.termcat.OCR_PROGRESS"
        const val ACTION_LLM_STATUS = "org.p2er1n.termcat.LLM_STATUS"
        const val ACTION_LLM_RESULT = "org.p2er1n.termcat.LLM_RESULT"
        const val ACTION_LLM_ERROR = "org.p2er1n.termcat.LLM_ERROR"
        const val ACTION_OVERLAY_STATUS = "org.p2er1n.termcat.OVERLAY_STATUS"
        const val ACTION_APP_LOCALE_CHANGED = "org.p2er1n.termcat.APP_LOCALE_CHANGED"
        const val EXTRA_OCR_DONE = "extra_ocr_done"
        const val EXTRA_OCR_TOTAL = "extra_ocr_total"
        const val EXTRA_LLM_STATUS = "extra_llm_status"
        const val EXTRA_OCR_TEXT = "extra_ocr_text"
        const val EXTRA_LLM_TEXT = "extra_llm_text"
        const val EXTRA_LLM_ERROR = "extra_llm_error"
        const val EXTRA_OVERLAY_RUNNING = "extra_overlay_running"
        private const val MAX_RESULT_CHARS = 1200
    }
}
