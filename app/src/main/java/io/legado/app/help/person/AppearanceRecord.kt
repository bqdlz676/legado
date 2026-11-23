package io.legado.app.help.person

/**
 * 整本出场记录（设计规格 §2.3 person_chapter_event 逐章出场表）。
 *
 * 一条记录 = 某人物在某书某章的一次出场：章节序号用于「首次出场 → 关键事迹 → 末次出场」
 * 迷你时间轴；[line] 为出场段落摘要（≤46 字，旧扁平表口径保留）；[eventTime] 为
 * 事件时间戳（如「前722」），无载为 null，不编。
 *
 * 该表是阅读副产物，随时可重建：内存表 + 行式文本落盘，不引 Room。
 * 行格式（持久化用，Tab 分隔）：
 * `personId \t bookId \t chapterId \t order \t chapterTitle \t line \t eventTime`
 */
data class AppearanceRecord(
    val personId: String,
    val bookId: String,
    val chapterId: String,
    val order: Int,
    val chapterTitle: String,
    val line: String,
    val eventTime: String? = null,
) {
    companion object {
        private const val SEP = "\t"

        /** 序列化为落盘行；字段内 Tab / 换行先剥离，保证一行一条记录。 */
        fun encode(r: AppearanceRecord): String = listOf(
            r.personId.sanitize(),
            r.bookId.sanitize(),
            r.chapterId.sanitize(),
            r.order.toString(),
            r.chapterTitle.sanitize(),
            r.line.sanitize(),
            r.eventTime?.sanitize().orEmpty(),
        ).joinToString(SEP)

        /** 反序列化一行；列数不足或 personId 为空返回 null，不抛异常（容错口径同字典表）。 */
        fun decode(line: String): AppearanceRecord? {
            val p = line.split(SEP)
            if (p.size < 6) return null
            val personId = p[0]
            if (personId.isBlank()) return null
            return AppearanceRecord(
                personId = personId,
                bookId = p[1],
                chapterId = p[2],
                order = p[3].trim().toIntOrNull() ?: 0,
                chapterTitle = p[4],
                line = p[5],
                eventTime = p.getOrNull(6)?.takeIf { it.isNotBlank() },
            )
        }

        private fun String.sanitize(): String =
            replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim()
    }
}
