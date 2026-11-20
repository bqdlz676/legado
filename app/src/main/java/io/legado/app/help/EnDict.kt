package io.legado.app.help

import android.content.Context
import io.legado.app.utils.printOnDebug

/**
 * 离线英汉词典
 * 数据源 assets/en_dict.txt，每行：词 \t 音标 \t 释义
 * 阅读英文时点中单词即查，查不到时按常见词形变化还原再查
 */
object EnDict {

    data class Entry(
        val word: String,
        val phonetic: String,
        val translation: String,
    )

    @Volatile
    private var map: Map<String, Entry>? = null

    fun preload(context: Context) {
        ensure(context)
    }

    private fun ensure(context: Context): Map<String, Entry> {
        map?.let { return it }
        synchronized(this) {
            map?.let { return it }
            val loaded = HashMap<String, Entry>(52000)
            runCatching {
                context.applicationContext.assets.open("en_dict.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty()) return@forEach
                            val a = line.indexOf('\t')
                            if (a <= 0) return@forEach
                            val b = line.indexOf('\t', a + 1)
                            if (b < 0) return@forEach
                            val w = line.substring(0, a)
                            loaded[w] = Entry(
                                w,
                                line.substring(a + 1, b),
                                line.substring(b + 1)
                            )
                        }
                    }
            }.onFailure {
                it.printOnDebug()
            }
            map = loaded
            return loaded
        }
    }

    /** 查词，查不到则尝试还原词形 */
    fun lookup(context: Context, raw: String): Entry? {
        val w = raw.trim().lowercase()
        if (w.isEmpty()) return null
        val dict = ensure(context)
        dict[w]?.let { return it }
        for (cand in candidates(w)) {
            dict[cand]?.let { e ->
                return Entry(w, e.phonetic, e.translation)
            }
        }
        return null
    }

    private fun candidates(w: String): List<String> {
        val list = ArrayList<String>(8)
        if (w.length < 3) return list
        // 复数 / 第三人称单数
        if (w.endsWith("ies") && w.length > 4) list.add(w.dropLast(3) + "y")
        if (w.endsWith("es") && w.length > 3) list.add(w.dropLast(2))
        if (w.endsWith("s") && w.length > 2 && !w.endsWith("ss")) list.add(w.dropLast(1))
        // 过去式 / 过去分词
        if (w.endsWith("ied") && w.length > 4) list.add(w.dropLast(3) + "y")
        if (w.endsWith("ed") && w.length > 3) {
            val d = w.dropLast(2)
            list.add(d)
            list.add(w.dropLast(1))
            if (d.length > 3 && d.last() == d[d.length - 2]) list.add(d.dropLast(1))
        }
        // 现在分词
        if (w.endsWith("ing") && w.length > 4) {
            val d = w.dropLast(3)
            list.add(d)
            list.add(d + "e")
            if (d.length > 3 && d.last() == d[d.length - 2]) list.add(d.dropLast(1))
        }
        // 比较级 / 最高级
        if (w.endsWith("er") && w.length > 3) {
            list.add(w.dropLast(2))
            list.add(w.dropLast(1))
        }
        if (w.endsWith("est") && w.length > 4) {
            list.add(w.dropLast(3))
            list.add(w.dropLast(2))
        }
        // 副词
        if (w.endsWith("ly") && w.length > 4) {
            list.add(w.dropLast(2))
            list.add(w.dropLast(2) + "e")
        }
        return list
    }
}
