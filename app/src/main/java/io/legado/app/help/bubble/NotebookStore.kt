package io.legado.app.help.bubble

import android.content.Context

/**
 * 生字本最小本地存储（兜底入口「加入生字本」落库）。
 *
 * 边界口径：
 * - 不接 Room / 不动数据层迁移；单表条目以 SharedPreferences 内的转义行格式持久化，
 *   条目字段 = 词条 / 书名 / 章节标题 / 时间戳。
 * - 同书同词条去重；空词条不落库；任何持久化异常都兜住返回 false，不抛到 UI 层。
 * - [encode] / [decode] 为纯函数，宿主 JVM 单测可直接验收往返一致性。
 */
object NotebookStore {

    /** 一条生字本记录。 */
    data class Entry(
        val token: String,
        val bookName: String,
        val chapterTitle: String,
        val timestamp: Long,
    )

    private const val PREFS_NAME = "luowen_notebook"
    private const val KEY_ENTRIES = "entries"

    /** 追加一条记录；同书同词条已存在时返回 false；落库成功返回 true。 */
    fun add(
        context: Context,
        token: String,
        bookName: String,
        chapterTitle: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val clean = token.trim()
        if (clean.isEmpty()) return false
        val entries = list(context).toMutableList()
        if (entries.any { it.token == clean && it.bookName == bookName }) return false
        entries.add(Entry(clean, bookName.trim(), chapterTitle.trim(), now))
        return persist(context, entries)
    }

    /** 读出全部记录（按落库先后）；无记录返回空列表。 */
    fun list(context: Context): List<Entry> = decode(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null)
            .orEmpty(),
    )

    private fun persist(context: Context, entries: List<Entry>): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, encode(entries))
            .apply()
        true
    }.getOrDefault(false)

    /** 纯函数：条目列表 → 转义行文本（每行一条，字段以制表符分隔）。 */
    internal fun encode(entries: List<Entry>): String = entries.joinToString("\n") { e ->
        listOf(e.token, e.bookName, e.chapterTitle, e.timestamp.toString())
            .joinToString("\t") { escape(it) }
    }

    /** 纯函数：转义行文本 → 条目列表；坏行 / 字段数不符 / 词条为空 / 时间戳非法一律跳过，不抛异常。 */
    internal fun decode(raw: String): List<Entry> = raw.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("\t").map(::unescape)
            if (parts.size != 4) return@mapNotNull null
            val token = parts[0]
            val timestamp = parts[3].toLongOrNull() ?: return@mapNotNull null
            if (token.isBlank()) return@mapNotNull null
            Entry(
                token = token,
                bookName = parts[1],
                chapterTitle = parts[2],
                timestamp = timestamp,
            )
        }
        .toList()

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[++i]
                when (n) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    else -> sb.append(n)
                }
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
