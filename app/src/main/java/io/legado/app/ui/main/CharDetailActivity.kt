package io.legado.app.ui.main

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.ChineseDict
import io.legado.app.help.HanConv

/**
 * 字典详情页：大字、全部读音、部首笔画、完整义项
 */
class CharDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_char_detail)

        HanConv.ensureLoaded(this)
        val ch = intent.getStringExtra("char").orEmpty()
        val info = if (ch.isEmpty()) null else ChineseDict.getChar(this, ch)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_char).text = ch
        val simp = HanConv.toSimp(ch)
        val pyLine = buildString {
            val py = info?.pinyin.orEmpty()
            if (py.isNotBlank()) append(py)
            if (simp != ch && simp.isNotEmpty()) {
                if (isNotEmpty()) append("　·　")
                append("简 ").append(simp)
            }
        }
        findViewById<TextView>(R.id.tv_pinyin).text = pyLine
        findViewById<TextView>(R.id.tv_pinyin).visibility =
            if (pyLine.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

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
