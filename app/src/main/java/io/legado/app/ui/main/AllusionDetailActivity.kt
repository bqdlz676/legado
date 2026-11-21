package io.legado.app.ui.main

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.AllusionDict

/**
 * 典故详情页：词头、读音、释义、出处。
 * 阅读页气泡里的「詳情」落这里。
 */
class AllusionDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allusion_detail)

        val word = intent.getStringExtra("word").orEmpty()
        val entry = if (word.isEmpty()) null else AllusionDict.find(this, word)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tv_word).text =
            entry?.word?.takeIf { it.isNotBlank() } ?: word.ifBlank { "典故" }

        val py = entry?.pinyin.orEmpty()
        findViewById<TextView>(R.id.tv_pinyin).apply {
            text = py
            visibility = if (py.isBlank()) View.GONE else View.VISIBLE
        }

        findViewById<TextView>(R.id.tv_meaning).text =
            entry?.meaning?.takeIf { it.isNotBlank() } ?: "词库暂未收录该典故。"

        val src = entry?.source.orEmpty()
        findViewById<TextView>(R.id.tv_source).apply {
            text = src.ifBlank { "—" }
        }
        findViewById<TextView>(R.id.tv_label_src).visibility =
            if (src.isBlank()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tv_source).visibility =
            if (src.isBlank()) View.GONE else View.VISIBLE
    }
}
