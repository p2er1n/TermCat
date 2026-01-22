package org.p2er1n.termcat

import android.app.Service
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.util.Log
import java.io.File

class PaddleOcrService : Service() {
    private val ocrEngine by lazy { PaddleOcrEngine(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val imagePath = intent?.getStringExtra(EXTRA_IMAGE_PATH)
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RESULT_RECEIVER)
        if (imagePath.isNullOrBlank() || receiver == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Thread {
            val result = runOcr(imagePath)
            val data = Bundle().apply {
                putString(EXTRA_RESULT_TEXT, result)
            }
            receiver.send(Activity.RESULT_OK, data)
            stopSelf(startId)
        }.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runOcr(imagePath: String): String {
        val file = File(imagePath)
        if (!file.exists()) return ""
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return ""
        return try {
            ocrEngine.run(bitmap)
        } catch (exception: Exception) {
            Log.e(TAG, "Paddle OCR failed", exception)
            ""
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_RESULT_RECEIVER = "extra_result_receiver"
        const val EXTRA_RESULT_TEXT = "extra_result_text"
        private const val TAG = "PaddleOcrService"
    }
}
