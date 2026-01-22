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

class FloatingWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var resultView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var resultParams: WindowManager.LayoutParams
    private var receiverRegistered = false
    private var lastResultFull = ""

    override fun onCreate() {
        super.onCreate()
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

        val themedContext = ContextThemeWrapper(this, R.style.Theme_TermCat)
        overlayView = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_floating_window, null, false)
        overlayView.findViewById<MaterialCardView>(R.id.overlay_card).setOnClickListener {
            handleCaptureClick()
        }
        overlayView.findViewById<MaterialButton>(R.id.overlay_capture).setOnClickListener {
            handleCaptureClick()
        }

        resultView = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_result_sheet, null, false)
        resultView.visibility = View.GONE
        val moreOverlay = resultView.findViewById<View>(R.id.result_more_overlay)
        resultView.findViewById<MaterialButton>(R.id.result_close).setOnClickListener {
            stopCaptureAndHide()
        }
        resultView.findViewById<MaterialButton>(R.id.result_copy).setOnClickListener {
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

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(target: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        target.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = startX + (event.rawX - touchX).toInt()
                    layoutParams.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(overlayView, layoutParams)
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
        resultView.findViewById<TextView>(R.id.result_title).text =
            getString(R.string.result_title)
        resultView.findViewById<TextView>(R.id.result_body).text =
            getString(R.string.result_processing)
        resultView.findViewById<TextView>(R.id.result_progress).text =
            getString(R.string.result_progress_preparing)
        resultView.findViewById<MaterialButton>(R.id.result_copy).isEnabled = false
        resultView.findViewById<View>(R.id.result_more_overlay).isEnabled = false
        resultView.findViewById<View>(R.id.result_more_overlay).visibility = View.GONE
        resultView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
    }

    private fun updateOcrProgress(done: Int, total: Int) {
        resultView.findViewById<TextView>(R.id.result_progress).text =
            getString(R.string.result_progress_ocr, done, total)
        resultView.findViewById<View>(R.id.result_more_overlay).visibility = View.GONE
        resultView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
    }

    private fun updateLlmStatus(status: String) {
        resultView.findViewById<TextView>(R.id.result_progress).text = status
        resultView.findViewById<View>(R.id.result_more_overlay).visibility = View.GONE
        resultView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
    }

    private fun showResult(text: String) {
        val body = text.ifBlank { getString(R.string.result_empty) }
        resultView.findViewById<TextView>(R.id.result_title).text =
            getString(R.string.result_title)
        resultView.findViewById<TextView>(R.id.result_body).text = body
        resultView.findViewById<TextView>(R.id.result_progress).text = ""
        resultView.findViewById<MaterialButton>(R.id.result_copy).isEnabled = text.isNotBlank()
        resultView.findViewById<View>(R.id.result_more_overlay).isEnabled = text.isNotBlank()
        resultView.findViewById<View>(R.id.result_more_overlay).visibility =
            if (text.isNotBlank()) View.VISIBLE else View.GONE
        resultView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
    }

    private fun hideResult() {
        resultView.visibility = View.GONE
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
        if (text.isBlank() || text == getString(R.string.result_processing)) {
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TermCat OCR", text))
        Toast.makeText(this, getString(R.string.result_copied), Toast.LENGTH_SHORT).show()
    }

    private fun openFullResult() {
        if (lastResultFull.isBlank()) {
            return
        }
        val intent = Intent(this, ResultDetailActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ResultDetailActivity.EXTRA_RESULT_TEXT, lastResultFull)
        startActivity(intent)
    }

    private fun registerResultReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_CAPTURE_CANCELLED)
            addAction(ACTION_CAPTURE_DONE)
            addAction(ACTION_OCR_PROGRESS)
            addAction(ACTION_LLM_RESULT)
            addAction(ACTION_LLM_STATUS)
            addAction(ACTION_LLM_ERROR)
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
                        ?: getString(R.string.result_progress_llm)
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


    companion object {
        const val ACTION_CAPTURE_CANCELLED = "org.p2er1n.termcat.CAPTURE_CANCELLED"
        const val ACTION_CAPTURE_DONE = "org.p2er1n.termcat.CAPTURE_DONE"
        const val ACTION_OCR_PROGRESS = "org.p2er1n.termcat.OCR_PROGRESS"
        const val ACTION_LLM_STATUS = "org.p2er1n.termcat.LLM_STATUS"
        const val ACTION_LLM_RESULT = "org.p2er1n.termcat.LLM_RESULT"
        const val ACTION_LLM_ERROR = "org.p2er1n.termcat.LLM_ERROR"
        const val ACTION_OVERLAY_STATUS = "org.p2er1n.termcat.OVERLAY_STATUS"
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
