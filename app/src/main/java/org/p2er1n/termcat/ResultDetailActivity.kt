package org.p2er1n.termcat

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class ResultDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result_detail)

        val body = intent.getStringExtra(EXTRA_RESULT_TEXT).orEmpty()
        findViewById<TextView>(R.id.result_detail_body).text = body
    }

    companion object {
        const val EXTRA_RESULT_TEXT = "extra_result_text"
    }
}
