package io.legado.app.help

import android.content.Context

/**
 * 离线英汉词典
 * 数据源 assets/en_dict.bin（构建期由 en_dict.txt 转成 CompactDict 紧凑格式），
 * 每行：词 \t 音标 \t 释义；40 万条不入 HashMap，只留一块字节数组做二分。
 * 阅读英文时点中单词即查，查不到时按常见词形变化还原再查
 */
object EnDict {

    data class Entry(
        val word: String,
        val phonetic: String,
        val translation: String,
    )

    @Volatile
    private var table: CompactDict.Table? = null

    fun preload(context: Context) {
        ensure(context)
    }

    private fun ensure(context: Context): CompactDict.Table? {
        table?.let { return it }
        synchronized(this) {
            table?.let { return it }
            val t = CompactDict.open(context.applicationContext, "en_dict.bin")
            table = t
            return t
        }
    }

    /** 查词，查不到则尝试还原词形 */
    fun lookup(context: Context, raw: String): Entry? {
        val w = raw.trim()
        if (w.isEmpty()) return null
        val dict = ensure(context) ?: return null
        val lower = w.lowercase()
        decode(w, dict.find(w))?.let { return it }
        if (lower != w) decode(w, dict.find(lower))?.let { return it }
        for (cand in candidates(lower)) {
            decode(w, dict.find(cand))?.let { return it }
        }
        return null
    }

    private fun decode(showWord: String, packed: String?): Entry? {
        if (packed == null) return null
        val t = packed.indexOf('\t')
        return if (t < 0) Entry(showWord, "", packed)
        else Entry(showWord, packed.substring(0, t), packed.substring(t + 1))
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
