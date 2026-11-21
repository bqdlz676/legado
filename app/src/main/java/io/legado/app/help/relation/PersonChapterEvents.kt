package io.legado.app.help.relation

import android.content.Context
import io.legado.app.utils.printOnDebug
import java.io.File

/**
 * person_chapter_event：逐章出场表（设计规格 §2.3 第三张表）
 *
 * 只做两件事：
 *   1) 灌粗稿 —— 章节正文里该人物出现的段落 + 同段共现人物，RelationExtractor 已能算
 *   2) 出摘要 —— 首次出场 → 关键事迹 → 末次出场，给半屏 Sheet 的「本书」段
 *
 * 存成行式文本（filesDir/pce.txt），不引 Room：这张表是阅读副产物，随时可重建。
 * 行：name \t bookId \t chapterId \t order \t chapterTitle \t line
 */
object PersonChapterEvents {

    private const val FILE = "pce.txt"
    private const val SEP = "\t"

    private data class Rec(
        val bookId: String,
        val chapterId: String,
        val order: Int,
        val title: String,
        val line: String,
    )

    @Volatile private var map: MutableMap<String, MutableList<Rec>>? = null

    fun has(ctx: Context, name: String): Boolean = recs(ctx, name).isNotEmpty()

    /** 首次 → 关键事迹 → 末次，一句话给 Sheet 用 */
    fun summary(ctx: Context, name: String): String {
        val list = recs(ctx, name).sortedWith(compareBy({ it.bookId }, { it.order }))
        if (list.isEmpty()) return "未收录"
        val first = list.first()
        val last = list.last()
        val sb = StringBuilder()
        sb.append("首次　").append(chapterLabel(first)).append("　").append(trim(first.line))
        if (list.size > 2) {
            val mid = list[list.size / 2]
            sb.append("\n关键　").append(chapterLabel(mid)).append("　").append(trim(mid.line))
        }
        sb.append("\n末次　").append(chapterLabel(last)).append("　").append(trim(last.line))
        val books = list.map { it.bookId }.distinct().size
        val chapters = list.map { "${it.bookId}/${it.chapterId}" }.distinct().size
        sb.append("\n共 ").append(books).append(" 书 · ").append(chapters).append(" 章 · ")
            .append(list.size).append(" 段")
        return sb.toString()
    }

    /** 章节正文灌粗稿：返回写入条数。同章重复调用会覆盖该章旧记录。 */
    fun harvest(
        ctx: Context,
        bookId: String,
        chapterId: String,
        chapterTitle: String,
        order: Int,
        text: String,
    ): Int {
        if (text.isBlank()) return 0
        val m = ensure(ctx)
        // 直接扫已知人物本名/别名是否在正文出现
        val hits = PersonTimeInfo.allNames(ctx)
        var n = 0
        for (name in hits) {
            if (name.length < 2 || !text.contains(name)) continue
            val line = runCatching {
                RelationExtractor.sentencesAbout(ctx, text, name, null)
            }.getOrNull()?.firstOrNull().orEmpty()
            if (line.isBlank()) continue
            val rec = Rec(bookId, chapterId, order, chapterTitle, line)
            val list = m.getOrPut(name) { ArrayList(4) }
            list.removeAll { it.bookId == bookId && it.chapterId == chapterId }
            list.add(rec)
            n++
        }
        if (n > 0) save(ctx)
        return n
    }

    /** 手动补录（JSON/CSV 手录格式之外的兜底入口） */
    fun record(
        ctx: Context,
        name: String,
        bookId: String,
        chapterId: String,
        chapterTitle: String,
        order: Int,
        line: String,
    ) {
        val m = ensure(ctx)
        val list = m.getOrPut(name) { ArrayList(4) }
        list.removeAll { it.bookId == bookId && it.chapterId == chapterId }
        list.add(Rec(bookId, chapterId, order, chapterTitle, line))
        save(ctx)
    }

    private fun chapterLabel(r: Rec): String =
        "${r.title.ifBlank { "第${r.order + 1}章" }}（${r.bookId}）"

    private fun trim(s: String): String {
        val one = s.replace('\n', ' ')
        return if (one.length <= 46) one else one.take(45) + "…"
    }

    private fun recs(ctx: Context, name: String): List<Rec> = ensure(ctx)[name] ?: emptyList()

    private fun ensure(ctx: Context): MutableMap<String, MutableList<Rec>> {
        map?.let { return it }
        synchronized(this) {
            map?.let { return it }
            val m = HashMap<String, MutableList<Rec>>(512)
            val f = File(ctx.applicationContext.filesDir, FILE)
            if (f.exists()) {
                runCatching {
                    f.forEachLine { line ->
                        if (line.isEmpty()) return@forEachLine
                        val p = line.split(SEP)
                        if (p.size < 6) return@forEachLine
                        val name = p[0]
                        val rec = Rec(p[1], p[2], p[3].toIntOrNull() ?: 0, p[4], p[5])
                        m.getOrPut(name) { ArrayList(4) }.add(rec)
                    }
                }.onFailure { it.printOnDebug() }
            }
            map = m
            return m
        }
    }

    private fun save(ctx: Context) {
        val m = map ?: return
        runCatching {
            val f = File(ctx.applicationContext.filesDir, FILE)
            val tmp = File(f.parentFile, "$FILE.tmp")
            tmp.bufferedWriter().use { w ->
                m.forEach { (name, list) ->
                    list.forEach { r ->
                        w.write(
                            listOf(name, r.bookId, r.chapterId, r.order.toString(), r.title, r.line)
                                .joinToString(SEP)
                        )
                        w.write("\n")
                    }
                }
            }
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        }.onFailure { it.printOnDebug() }
    }
}
