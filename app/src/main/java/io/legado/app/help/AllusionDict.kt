package io.legado.app.help

import android.content.Context
import io.legado.app.utils.printOnDebug

/**
 * 典故／成语词表
 * 数据源 assets/allusion.txt，每行：词 \t 拼音 \t 释义 \t 出处
 * 阅读时按行扫描正文，命中即在原字下方画一道细线，点中弹气泡显示释义与出处
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
    private var index: Map<Char, List<Entry>>? = null

    /** 提前加载，避免首帧卡顿 */
    fun preload(context: Context) {
        ensureLoaded(context)
    }

    private fun ensureLoaded(context: Context): Map<Char, List<Entry>> {
        index?.let { return it }
        synchronized(this) {
            index?.let { return it }
            val map = HashMap<Char, MutableList<Entry>>(4096)
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
                            map.getOrPut(word[0]) { ArrayList() }.add(
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
            // 同一首字下按长度降序，取最长命中
            map.values.forEach { list ->
                list.sortByDescending { it.word.length }
            }
            index = map
            return map
        }
    }

    /** 扫描一行文本，返回不重叠的最长命中 */
    fun match(context: Context, text: String): List<Hit> {
        if (text.length < 2) return emptyList()
        val idx = ensureLoaded(context)
        val hits = ArrayList<Hit>(4)
        var i = 0
        while (i < text.length - 1) {
            val list = idx[text[i]]
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
