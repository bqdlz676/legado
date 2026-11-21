package io.legado.app.help

import android.content.Context
import android.util.LruCache
import io.legado.app.utils.printOnDebug

/**
 * 典故／成语词表
 * 数据源 assets/allusion.txt，每行：词 \t 拼音 \t 释义 \t 出处
 * 阅读时按行扫描正文，命中即在原字下方画一道细线，点中弹气泡显示释义与出处
 *
 * 索引从「首字」升到「前两字」：三万字里「一／不／之／为」这类首字桶
 * 能有几百条，逐条 regionMatches 在每行渲染时都跑；两字桶把体积压掉一个量级，
 * 且两字桶天然要求原文对齐，错配更少。
 * 另外按「整行文本」缓存扫描结果，点一次是 O(1)，不再重扫全行。
 */
object AllusionDict {

    data class Entry(
        val word: String,
        val pinyin: String,
        val meaning: String,
        val source: String,
    )

    class Hit(val start: Int, val end: Int, val entry: Entry)

    @Volatile
    private var index: Map<Int, List<Entry>>? = null

    private val lineCache = object : LruCache<String, List<Hit>>(180) {}

    fun find(context: Context, word: String): Entry? {
        if (word.length < 2) return null
        val key = key2(word[0], word[1])
        return ensureLoaded(context)[key]?.firstOrNull { it.word == word }
    }

    /** 提前加载，避免首帧卡顿 */
    fun preload(context: Context) {
        ensureLoaded(context)
    }

    private fun ensureLoaded(context: Context): Map<Int, List<Entry>> {
        index?.let { return it }
        synchronized(this) {
            index?.let { return it }
            val map = HashMap<Int, MutableList<Entry>>(16384)
            runCatching {
                context.applicationContext.assets.open("allusion.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty()) return@forEach
                            val p1 = line.indexOf('\t')
                            if (p1 <= 0) return@forEach
                            val p2 = line.indexOf('\t', p1 + 1)
                            if (p2 < 0) return@forEach
                            val p3 = line.indexOf('\t', p2 + 1)
                            if (p3 < 0) return@forEach
                            val word = line.substring(0, p1)
                            if (word.length < 2) return@forEach
                            // 全文两字即词，统一取前两字做桶
                            map.getOrPut(key2(word[0], word[1])) { ArrayList() }.add(
                                Entry(
                                    word,
                                    line.substring(p1 + 1, p2),
                                    line.substring(p2 + 1, p3),
                                    line.substring(p3 + 1)
                                )
                            )
                        }
                    }
            }.onFailure {
                it.printOnDebug()
            }
            // 同一桶内按长度降序，取最长命中
            map.values.forEach { list ->
                list.sortByDescending { it.word.length }
            }
            index = map
            return map
        }
    }

    /** 前两字打成 Int 桶键，避免每步 substring 建对象 */
    private fun key2(a: Char, b: Char): Int = (a.code shl 16) or b.code

    /** 扫描一行文本，返回不重叠的最长命中 */
    fun match(context: Context, text: String): List<Hit> {
        if (text.length < 2) return emptyList()
        val cached = lineCache.get(text)
        if (cached != null) return cached
        val idx = ensureLoaded(context)
        val hits = ArrayList<Hit>(4)
        var i = 0
        while (i < text.length - 1) {
            val list = idx[key2(text[i], text[i + 1])]
            if (list == null) {
                i++
                continue
            }
            var chosen: Entry? = null
            for (e in list) {
                val n = e.word.length
                if (i + n > text.length) continue
                if (text.regionMatches(i, e.word, 0, n)) {
                    chosen = e
                    break
                }
            }
            if (chosen != null) {
                hits.add(Hit(i, i + chosen.word.length, chosen))
                i += chosen.word.length
            } else {
                i++
            }
        }
        lineCache.put(text, hits)
        return hits
    }

    /** 判断某一列位置落在哪个典故里 */
    fun hitAt(context: Context, text: String, pos: Int): Hit? {
        if (pos < 0 || pos >= text.length) return null
        for (h in match(context, text)) {
            if (pos >= h.start && pos < h.end) return h
        }
        return null
    }
}
