package io.legado.app.help

import android.content.Context
import io.legado.app.utils.printOnDebug

/**
 * 古地名：place.txt 为 地名 \t 释义 \t 今地
 * 只收先秦两汉常见地名与列国都邑，够古籍正文用。
 */
object PlaceDict {

    data class Entry(
        val name: String,
        val note: String,
        val modern: String,
        val lat: Double? = null,
        val lng: Double? = null,
        val world: Boolean = false,
        val en: String? = null,
    )

    @Volatile
    private var table: Map<String, Entry>? = null

    @Volatile
    private var byFirst: Map<Char, List<String>>? = null

    @Volatile
    private var enTable: HashMap<String, Entry> = HashMap()

    fun ensureLoaded(context: Context) {
        if (table == null) load(context)
    }

    fun find(context: Context, name: String): Entry? = load(context)[name]

    /** 从文本 pos 处做最长地名匹配，2–5 字 */
    fun matchAt(context: Context, text: String, pos: Int): Entry? {
        if (pos !in text.indices) return null
        val map = load(context)
        val idx = byFirst(context)
        for (len in 5 downTo 2) {
            val s0 = maxOf(0, pos - len + 1)
            val s1 = minOf(pos, text.length - len)
            if (s1 < s0) continue
            for (s in s0..s1) {
                val cand = text.substring(s, s + len)
                val bucket = idx[cand[0]] ?: continue
                if (bucket.any { it == cand }) {
                    map[cand]?.let { return it }
                }
            }
        }
        return null
    }

    /**
     * 英文地名匹配：读外文原著时点 Babylon / Troy 这类词也要能出图。
     * 按单词边界取 token，整词查表（忽略大小写）。
     */
    fun matchEnglish(context: Context, text: String, pos: Int): Entry? {
        if (pos !in text.indices) return null
        fun isWord(c: Char) = c.isLetter() || c == '\'' || c == '-'
        if (!text[pos].isLetter()) return null
        load(context)
        val map: Map<String, Entry> = enTable
        if (map.isEmpty()) return null
        var s = pos
        while (s > 0 && isWord(text[s - 1])) s--
        var e = pos
        while (e < text.length - 1 && isWord(text[e + 1])) e++
        val raw = text.substring(s, e + 1).trim('\'', '-')
        if (raw.length < 2) return null
        map[raw.lowercase()]?.let { return it }
        // 处理 Battle of Troy 这类复合写法：逐词回退
        val parts = raw.split(' ', '-')
        if (parts.size > 1) {
            for (i in parts.indices.reversed()) {
                map[parts[i].lowercase()]?.let { return it }
            }
        }
        return null
    }

    private fun load(context: Context): Map<String, Entry> {
        table?.let { return it }
        synchronized(this) {
            table?.let { return it }
            val loaded = HashMap<String, Entry>(300)
            runCatching {
                context.applicationContext.assets.open("place.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 2) return@forEach
                            loaded[p[0]] = Entry(p[0], p[1], p.getOrElse(2) { "" })
                        }
                    }
                context.applicationContext.assets.open("world_place.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 3) return@forEach
                            val lat = p[1].toDoubleOrNull() ?: return@forEach
                            val lng = p[2].toDoubleOrNull() ?: return@forEach
                            loaded[p[0]] = Entry(
                                name = p[0],
                                note = p.getOrElse(3) { "" },
                                modern = p.getOrElse(4) { "" },
                                lat = lat, lng = lng, world = true
                            )
                        }
                    }
                context.applicationContext.assets.open("world_place_en.txt")
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 2) return@forEach
                            val cn = p[1]
                            val old = loaded[cn] ?: return@forEach
                            loaded[cn] = old.copy(en = p[0])
                            enTable[p[0].lowercase()] = old.copy(en = p[0])
                        }
                    }
            }.onFailure { it.printOnDebug() }
            table = loaded
            return loaded
        }
    }

    private fun byFirst(context: Context): Map<Char, List<String>> {
        byFirst?.let { return it }
        synchronized(this) {
            byFirst?.let { return it }
            val src = load(context)
            val buckets = HashMap<Char, ArrayList<String>>(200)
            for (k in src.keys) {
                if (k.length < 2) continue
                buckets.getOrPut(k[0]) { ArrayList(12) }.add(k)
            }
            buckets.values.forEach { it.sortByDescending { w -> w.length } }
            val out = HashMap<Char, List<String>>(buckets.size)
            buckets.forEach { (k, v) -> out[k] = v }
            byFirst = out
            return out
        }
    }
}
