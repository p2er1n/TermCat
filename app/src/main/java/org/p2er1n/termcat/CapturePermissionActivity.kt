package org.p2er1n.termcat

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class CapturePermissionActivity : ComponentActivity() {
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                    putExtra(ScreenshotService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenshotService.EXTRA_RESULT_DATA, result.data)
                }
                startForegroundService(serviceIntent)
            } else {
                sendCaptureCancelled()
            }
            moveTaskToBack(true)
            finish()
            overridePendingTransition(0, 0)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        overridePendingTransition(0, 0)
    }

    private fun sendCaptureCancelled() {
        val intent = Intent(FloatingWindowService.ACTION_CAPTURE_CANCELLED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
