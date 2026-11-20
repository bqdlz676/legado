package io.legado.app.ui.main

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.ChineseDict

/**
 * 字典详情页：大字、全部读音、部首笔画、完整义项
 */
class CharDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_char_detail)

        val ch = intent.getStringExtra("char").orEmpty()
        val info = if (ch.isEmpty()) null else ChineseDict.getChar(this, ch)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_char).text = ch
        findViewById<TextView>(R.id.tv_pinyin).text = info?.pinyin.orEmpty()

        val meta = buildString {
            if (!info?.radical.isNullOrBlank()) append("部首 ").append(info?.radical)
            if (!info?.strokes.isNullOrBlank()) {
                if (isNotEmpty()) append("　·　")
                append("笔画 ").append(info?.strokes)
            }
            if (isEmpty()) append("未收录字头")
        }
        findViewById<TextView>(R.id.tv_meta).text = meta
        findViewById<TextView>(R.id.tv_meaning).text =
            info?.meaning?.takeIf { it.isNotBlank() } ?: "字库暂未收录该字的释义。"
    }
}
