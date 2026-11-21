package io.legado.app.ui.main

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.R
import io.legado.app.help.ChineseDict
import io.legado.app.help.HanConv

/**
 * 字典详情页：字头、读音、部首笔画反切、说文、释义、书证、词组。
 * 没有内容的区块整块隐藏，不留空标签。
 */
class CharDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_char_detail)

        HanConv.ensureLoaded(this)
        val ch = intent.getStringExtra("char").orEmpty()
        val info = if (ch.isEmpty()) null else ChineseDict.getChar(this, ch)
        val sw = if (ch.isEmpty()) null else ChineseDict.getShuowen(this, ch)
        val usage = if (ch.isEmpty()) null else ChineseDict.getUsage(this, ch)
        val words = if (ch.isEmpty()) emptyList() else ChineseDict.wordsStartingWith(this, ch)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_char).text = ch

        val simp = HanConv.toSimp(ch)
        val pyLine = buildString {
            val py = info?.pinyin.orEmpty()
            if (py.isNotBlank()) append(py)
            if (sw != null && sw.fanqie.isNotBlank()) {
                if (isNotEmpty()) append("　·　")
                append("反切 ").append(sw.fanqie)
            }
            if (simp != ch && simp.isNotEmpty()) {
                if (isNotEmpty()) append("　·　")
                append("简 ").append(simp)
            }
        }
        findViewById<TextView>(R.id.tv_pinyin).text = pyLine
        findViewById<TextView>(R.id.tv_pinyin).visibility =
            if (pyLine.isBlank()) View.GONE else View.VISIBLE

        val meta = buildString {
            if (!info?.radical.isNullOrBlank()) append("部首 ").append(info?.radical)
            if (!info?.strokes.isNullOrBlank()) {
                if (isNotEmpty()) append("　·　")
                append("笔画 ").append(info?.strokes)
            }
            if (isEmpty()) append("未收录字头")
        }
        findViewById<TextView>(R.id.tv_meta).text = meta

        // 说文
        val swView = findViewById<TextView>(R.id.tv_shuowen)
        val swLabel = findViewById<TextView>(R.id.tv_label_shuowen)
        if (sw != null && sw.text.isNotBlank()) {
            swView.text = "《说文解字》：${sw.text}"
        } else {
            swView.visibility = View.GONE
            swLabel.visibility = View.GONE
        }

        // 释义
        findViewById<TextView>(R.id.tv_meaning).text =
            info?.meaning?.takeIf { it.isNotBlank() } ?: "字库暂未收录该字的释义。"

        // 书证
        val usView = findViewById<TextView>(R.id.tv_usage)
        val usSrc = findViewById<TextView>(R.id.tv_usage_src)
        val usLabel = findViewById<TextView>(R.id.tv_label_usage)
        if (usage != null && usage.sentence.isNotBlank()) {
            usSrc.text = "《${usage.source}》"
            usView.text = usage.sentence
        } else {
            usView.visibility = View.GONE
            usSrc.visibility = View.GONE
            usLabel.visibility = View.GONE
        }

        // 词组
        val wView = findViewById<TextView>(R.id.tv_words)
        val wLabel = findViewById<TextView>(R.id.tv_label_words)
        if (words.isNotEmpty()) {
            wView.text = words.joinToString("　　")
        } else {
            wView.visibility = View.GONE
            wLabel.visibility = View.GONE
        }
    }
}
