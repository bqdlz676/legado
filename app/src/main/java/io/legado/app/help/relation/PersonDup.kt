package io.legado.app.help.relation

import android.content.Context
import io.legado.app.utils.printOnDebug

/**
 * 同名分组：CBDB 里「丁佑」有两个（唐 749–801 / 明 1450–），
 * 不分组就会把两个人生平混成一条，读者一查就发现是脏数据。
 *
 * 数据是离线生成的 person_dup.txt：同名 ≥2 且至少一人有朝代/生卒的组。
 * 置信：有生卒 L2，只有单条孤证 L3。
 */
object PersonDup {

    data class Row(
        val name: String,
        val birth: String,
        val death: String,
        val era: String,
        val identity: String,
        val confidence: String,
        val alias: String,
    ) {
        /** 区分标签：时代 + 身份，正是检索时要显示的那一列 */
        fun tag(): String = buildString {
            if (era.isNotBlank()) append(era)
            if (identity.isNotBlank()) {
                if (isNotEmpty()) append("·")
                append(identity)
            }
            val by = birth.trim().toIntOrNull()
            val dy = death.trim().toIntOrNull()
            if (by != null || dy != null) {
                if (isNotEmpty()) append(" ")
                append(if (by != null) fmt(by) else "?")
                append("–")
                append(if (dy != null) fmt(dy) else "?")
            }
            if (isBlank()) append("生卒无载")
        }

        private fun fmt(y: Int) = if (y <= 0) "前${1 - y}" else "$y"
    }

    @Volatile
    private var table: Map<String, List<Row>>? = null

    fun candidates(context: Context, name: String): List<Row> = load(context)[name] ?: emptyList()

    fun confidenceOf(context: Context, name: String): String? {
        val rows = candidates(context, name)
        if (rows.size < 2) return null
        return if (rows.count { it.confidence == "L2" } >= 2) "同名 ${rows.size} 人，均已考出生卒" 
        else "同名 ${rows.size} 人，其中 ${rows.count { it.confidence == "L3" }} 条孤证"
    }

    private fun load(context: Context): Map<String, List<Row>> {
        table?.let { return it }
        synchronized(this) {
            table?.let { return it }
            val m = HashMap<String, MutableList<Row>>(9000)
            runCatching {
                context.applicationContext.assets.open("person_dup.txt")
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 6) return@forEach
                            m.getOrPut(p[0]) { ArrayList(4) }.add(
                                Row(p[0], p[1], p[2], p[3], p[4], p[5], if (p.size > 6) p[6] else "")
                            )
                        }
                    }
            }.onFailure { it.printOnDebug() }
            val out = HashMap<String, List<Row>>(m.size)
            m.forEach { (k, v) ->
                // 有生卒的排前面，孤证沉底，免得一上来抛脏数据
                out[k] = v.sortedWith(compareByDescending<Row> { it.confidence == "L2" }
                    .thenByDescending { it.birth })
            }
            table = out
            return out
        }
    }
}
