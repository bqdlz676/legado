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
        val words = if (ch.isEmpty()) emptyList() else ChineseDict.wordsStartingWith(this, ch[0])

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_char).text = ch

        val simp = HanConv.toSimp(ch)
        val pyLine = buildString {
            val reads = ChineseDict.getPinyin(this@CharDetailActivity, ch)
            val py = if (reads.isNotEmpty()) reads.joinToString("　") else info?.pinyin.orEmpty()
            if (py.isNotBlank()) append(py)
            if (reads.size > 1) append("（").append(reads.size).append(" 读）")
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

        // 释义：按 1. 2. 3. 切成编号义项，一条一行
        val meaningView = findViewById<TextView>(R.id.tv_meaning)
        val raw = info?.meaning?.takeIf { it.isNotBlank() }
        if (raw == null) {
            meaningView.text = "字库暂未收录该字的释义。"
        } else {
            meaningView.text = splitSenses(raw).joinToString("\n") { it }
        }

        // 辨析口诀：从部首与字形说明拼，不做无据推断
        val mnView = findViewById<TextView>(R.id.tv_mnemonic)
        val mnLabel = findViewById<TextView>(R.id.tv_label_mnemonic)
        val mnemonic = buildMnemonic(info, sw, ch)
        if (mnemonic.isBlank()) {
            mnView.visibility = View.GONE
            mnLabel.visibility = View.GONE
        } else {
            mnView.text = mnemonic
        }

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

    /** 「1.义项 2.义项」→ 编号条目；没有编号的整段保留 */
    private fun splitSenses(raw: String): List<String> {
        val parts = raw
        val marks = Regex("(\\d+)\\.").findAll(parts).toList()
        if (marks.size < 2) return listOf("① " + raw.trim())
        val out = ArrayList<String>(marks.size)
        for ((i, m) in marks.withIndex()) {
            val start = m.range.first
            val end = if (i + 1 < marks.size) marks[i + 1].range.first else parts.length
            val body = parts.substring(start, end).trim().removePrefix(m.value)
            if (body.isNotBlank()) out.add("${i + 1}. " + body)
        }
        return if (out.isEmpty()) listOf("① " + raw.trim()) else out
    }

    /** 口诀只陈述字形与本义，不臆断用法 */
    private fun buildMnemonic(info: ChineseDict.CharInfo?, sw: ChineseDict.Shuowen?, ch: String): String {
        val sb = StringBuilder()
        val radical = info?.radical?.takeIf { it.isNotBlank() && it != "-" }
        if (radical != null) {
            sb.append("部首「").append(radical).append("」")
        }
        val swText = sw?.text.orEmpty()
        val shape = when {
            swText.contains("从") && (swText.contains("声") || swText.contains("从")) -> "形声"
            swText.contains("象") -> "象形"
            swText.contains("会意") -> "会意"
            swText.contains("指事") -> "指事"
            else -> null
        }
        if (shape != null) {
            if (sb.isNotEmpty()) sb.append("，")
            sb.append(shape)
        }
        if (sb.isNotEmpty()) sb.append("。")
        if (swText.isNotBlank()) {
            sb.append("本义：").append(swText.take(28))
            if (swText.length > 28) sb.append("…")
            sb.append("。")
        }
        val senses = info?.meaning?.let { splitSenses(it) } ?: emptyList()
        if (senses.size > 1) {
            sb.append("今列 ").append(senses.size).append(" 义，首条为常见义。")
        }
        return if (swText.isBlank() && radical == null) "" else sb.toString()
    }
}
