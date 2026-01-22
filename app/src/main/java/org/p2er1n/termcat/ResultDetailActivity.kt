package org.p2er1n.termcat

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.ComponentActivity
import io.noties.markwon.Markwon

class ResultDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result_detail)

        val body = intent.getStringExtra(EXTRA_RESULT_TEXT).orEmpty()
        val textView = findViewById<TextView>(R.id.result_detail_body)
        textView.movementMethod = LinkMovementMethod.getInstance()
        Markwon.create(this).setMarkdown(textView, body)
    }

    companion object {
        const val EXTRA_RESULT_TEXT = "extra_result_text"
    }
}
