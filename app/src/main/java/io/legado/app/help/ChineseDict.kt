package io.legado.app.help

import android.content.Context
import io.legado.app.utils.printOnDebug
import kotlin.math.max
import kotlin.math.min

/**
 * 中文字词查询
 * char_dict.txt   ：字 \t 拼音 \t 部首 \t 笔画 \t 释义
 * char_shuowen.txt：字 \t 说文释文 \t 反切 \t 部首
 * char_usage.txt  ：字 \t 出处 \t 例句
 * zh_ci.txt       ：词 \t 释义
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

    data class Shuowen(
        val text: String,
        val fanqie: String,
        val radical: String,
    )

    data class Usage(
        val source: String,
        val sentence: String,
    )

    @Volatile
    private var charMap: Map<String, CharInfo>? = null

    @Volatile
    private var wordMap: Map<String, String>? = null

    @Volatile
    private var shuowenMap: Map<String, Shuowen>? = null

    @Volatile
    private var usageMap: Map<String, Usage>? = null

    @Volatile
    private var prefixIndex: Map<Char, List<String>>? = null

    fun preload(context: Context) {
        ensureChar(context)
        ensureWord(context)
        ensureShuowen(context)
        ensureUsage(context)
    }

    fun getChar(context: Context, ch: String): CharInfo? = ensureChar(context)[ch]

    @Volatile
    private var pinyinTable: Map<String, List<String>>? = null

    /** 一字的全部读音，来自 Unihan 汉语大字典、现代汉语词典、通用规范字典 */
    fun getPinyin(context: Context, ch: String): List<String> {
        val t = pinyinTable ?: synchronized(this) {
            val m = HashMap<String, List<String>>(20000)
            runCatching {
                context.applicationContext.assets.open("char_pinyin.txt")
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val tab = line.indexOf('\t')
                            if (tab > 0) {
                                val body = line.substring(tab + 1)
                                m[line.substring(0, tab)] = body.split(',')
                                    .map { it.trim() }.filter { it.isNotEmpty() }
                            }
                        }
                    }
            }
            pinyinTable = m
            m
        }
        return t[ch] ?: emptyList()
    }

    fun getShuowen(context: Context, ch: String): Shuowen? = ensureShuowen(context)[ch]

    fun getUsage(context: Context, ch: String): Usage? = ensureUsage(context)[ch]

    /** 以该字开头的常见词，短词优先 */
    fun wordsStartingWith(context: Context, ch: Char, limit: Int = 14): List<String> {
        val idx = ensurePrefix(context)
        return idx[ch]?.take(limit) ?: emptyList()
    }

    /**
     * 词的拼音：逐字取首音，用于词气泡的注音行。
     * 多音字按单字表走，人名另有 PersonPinyin 兜。
     */
    fun pinyinOfWord(context: Context, word: String): String {
        val sb = StringBuilder()
        for (c in word) {
            val info = getChar(context, c.toString()) ?: continue
            val py = info.pinyin.split(' ').firstOrNull()?.trim().orEmpty()
            if (py.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(py)
        }
        return sb.toString()
    }

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

    private fun ensureShuowen(context: Context): Map<String, Shuowen> {
        shuowenMap?.let { return it }
        synchronized(this) {
            shuowenMap?.let { return it }
            val loaded = HashMap<String, Shuowen>(10000)
            runCatching {
                context.applicationContext.assets.open("char_shuowen.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty()) return@forEach
                            val p = line.split('\t')
                            if (p.size < 2) return@forEach
                            loaded[p[0]] = Shuowen(
                                p[1],
                                p.getOrElse(2) { "" },
                                p.getOrElse(3) { "" }
                            )
                        }
                    }
            }.onFailure { it.printOnDebug() }
            shuowenMap = loaded
            return loaded
        }
    }

    private fun ensureUsage(context: Context): Map<String, Usage> {
        usageMap?.let { return it }
        synchronized(this) {
            usageMap?.let { return it }
            val loaded = HashMap<String, Usage>(8000)
            runCatching {
                context.applicationContext.assets.open("char_usage.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty()) return@forEach
                            val p = line.split('\t')
                            if (p.size < 3) return@forEach
                            loaded[p[0]] = Usage(p[1], p[2])
                        }
                    }
            }.onFailure { it.printOnDebug() }
            usageMap = loaded
            return loaded
        }
    }

    /** 首字索引：只收 2–3 字的词，每桶留 40 条，控内存 */
    private fun ensurePrefix(context: Context): Map<Char, List<String>> {
        prefixIndex?.let { return it }
        synchronized(this) {
            prefixIndex?.let { return it }
            val src = ensureWord(context)
            val buckets = HashMap<Char, ArrayList<String>>(4200)
            for (k in src.keys) {
                if (k.length < 2 || k.length > 3) continue
                buckets.getOrPut(k[0]) { ArrayList(48) }.add(k)
            }
            val out = HashMap<Char, List<String>>(buckets.size)
            buckets.forEach { (ch, list) ->
                list.sortBy { it.length }
                out[ch] = list.take(40)
            }
            prefixIndex = out
            return out
        }
    }
}
