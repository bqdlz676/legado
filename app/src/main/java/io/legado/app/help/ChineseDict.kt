package io.legado.app.help

import android.content.Context
import io.legado.app.utils.printOnDebug
import kotlin.math.max
import kotlin.math.min

/**
 * 中文字词查询
 * char_dict.txt：字 \t 拼音 \t 部首 \t 笔画 \t 释义
 * zh_ci.txt   ：词 \t 释义
 */
object ChineseDict {

    data class CharInfo(
        val char: String,
        val pinyin: String,
        val radical: String,
        val strokes: String,
        val meaning: String,
    )

    data class WordInfo(
        val word: String,
        val meaning: String,
    )

    @Volatile
    private var charMap: Map<String, CharInfo>? = null

    @Volatile
    private var wordMap: Map<String, String>? = null

    fun preload(context: Context) {
        ensureChar(context)
        ensureWord(context)
    }

    fun getChar(context: Context, ch: String): CharInfo? = ensureChar(context)[ch]

    /** 从点击位置取词，优先覆盖点击位置的最长词 */
    fun wordAt(context: Context, text: String, pos: Int): WordInfo? {
        if (pos !in text.indices) return null
        val map = ensureWord(context)
        for (len in 5 downTo 2) {
            val s0 = max(0, pos - len + 1)
            val s1 = min(pos, text.length - len)
            if (s1 < s0) continue
            for (s in s0..s1) {
                val cand = text.substring(s, s + len)
                val m = map[cand] ?: continue
                return WordInfo(cand, m)
            }
        }
        return null
    }

    private fun ensureChar(context: Context): Map<String, CharInfo> {
        charMap?.let { return it }
        synchronized(this) {
            charMap?.let { return it }
            val loaded = HashMap<String, CharInfo>(18000)
            runCatching {
                context.applicationContext.assets.open("char_dict.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty()) return@forEach
                            val p = line.split('\t')
                            if (p.size < 5) return@forEach
                            loaded[p[0]] = CharInfo(p[0], p[1], p[2], p[3], p[4].replace('\u0001', '\n'))
                        }
                    }
            }.onFailure { it.printOnDebug() }
            charMap = loaded
            return loaded
        }
    }

    private fun ensureWord(context: Context): Map<String, String> {
        wordMap?.let { return it }
        synchronized(this) {
            wordMap?.let { return it }
            val loaded = HashMap<String, String>(280000)
            runCatching {
                context.applicationContext.assets.open("zh_ci.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty()) return@forEach
                            val a = line.indexOf('\t')
                            if (a <= 0) return@forEach
                            loaded[line.substring(0, a)] = line.substring(a + 1)
                        }
                    }
            }.onFailure { it.printOnDebug() }
            wordMap = loaded
            return loaded
        }
    }
}
