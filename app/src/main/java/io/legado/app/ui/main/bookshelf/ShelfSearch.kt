package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.main.bookshelf.search.HitSegment
import io.legado.app.ui.main.bookshelf.search.ShelfItemLite
import io.legado.app.ui.main.bookshelf.search.searchBooks

/**
 * D25 · 书架页下拉搜索浮层状态机（纯 JVM，不依赖 Compose / Android 框架，便于单测）。
 *
 * 口径（F-13）：搜索只做书架页下拉浮层，不再起独立搜索页；
 * 浮层输入即查本地书架，书源联网检索仍归 D1~D7，本状态机不碰网络。
 */
object ShelfSearch {

    enum class Stage { CLOSED, OPENED }

    data class State(
        val stage: Stage,
        val query: String,
    ) {
        val isOpen: Boolean get() = stage == Stage.OPENED
    }

    /**
     * 浮层检索结果：[results] 交既有结果列表渲染；[hint] 非空时作为空态文案透传画面层；
     * [segments] 与 [results] 按下标对齐，承载纯函数产出的命中片段区间，渲染高亮直接切此区间，
     * 不再在画面层做第二套 `indexOf` 匹配；空词 / 无命中时为空列表。
     */
    data class SearchOutcome(
        val results: List<ShelfBookUi>,
        val hint: String,
        val segments: List<List<HitSegment>> = emptyList(),
    )

    /** 初始态：收起、无关键词。 */
    val closed = State(Stage.CLOSED, "")

    /** 展开浮层；保留上次关键词，便于接续输入。 */
    fun open(state: State): State = state.copy(stage = Stage.OPENED)

    /** 收起浮层：清空关键词，下次打开是一次新搜索，不留陈旧查询。 */
    fun close(state: State): State = State(Stage.CLOSED, "")

    /** 输入即查：关键词原样落状态，结果由 [search] / [filter] 派生。 */
    fun updateQuery(state: State, query: String): State = state.copy(query = query)

    /**
     * 本地书架过滤：书名 / 作者包含关键词即命中，忽略大小写与首尾空白。
     * 关键词为空时原样返回全量（浮层引导态）；列表为空或无命中时返回空列表，由画面层给占位。
     */
    fun filter(books: List<ShelfBookUi>, query: String): List<ShelfBookUi> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return books
        return books.filter {
            it.title.lowercase().contains(key) || it.author.lowercase().contains(key)
        }
    }

    /**
     * S-13 接线：输入词变化时的浮层检索。
     * 既有 [filter] 本地过滤（书名 / 作者包含）保留不删：空词引导态兜底与既有单测仍走它。
     * 非空词时把全量书架投影成 [ShelfItemLite] 交 [searchBooks]，由契约纯函数的三路命中
     * （书名 / 作者 / 正文）与排序覆盖最终结果。为什么不拿本地过滤做前置裁剪：仅正文命中的书
     * 会在进纯函数前被书名 / 作者口径漏掉，违背切片-13「搜书名、作者、或正文中的任意一句」；
     * 统一口径到 S-13 纯函数并保留正文命中路，最终命中路、排序以纯函数为准。
     */
    fun search(books: List<ShelfBookUi>, query: String): SearchOutcome {
        if (query.isBlank()) return SearchOutcome(filter(books, query), "", emptyList())
        val lites = books.map { it.toLite() }
        val result = searchBooks(query, lites)
        // 回映按 Lite 排队取原件：两本书投影出完全相同 Lite 时，旧 associateBy 会折叠成一键，
        // 第二条命中被 mapNotNull 漏掉；这里每个 Lite 挂一个原件队列，逐条命中从队首取，顺序不丢条目。
        val originQueue = HashMap<ShelfItemLite, MutableList<ShelfBookUi>>()
        lites.forEachIndexed { index, lite ->
            originQueue.getOrPut(lite) { mutableListOf() }.add(books[index])
        }
        val entries = result.hits.mapNotNull { hit ->
            val queue = originQueue[hit.item]
            val origin = if (!queue.isNullOrEmpty()) queue.removeAt(0) else return@mapNotNull null
            origin to hit.segments
        }
        return SearchOutcome(
            results = entries.map { it.first },
            hint = result.hint,
            segments = entries.map { it.second },
        )
    }

    /**
     * 最小换算：规格未给正文来源，先取可用最短字段，后续接线单替换。
     * ShelfBookUi 无正文片段字段：正文片段先取章节文案 [ShelfBookUi.chapterText]；
     * 章节文案为空时回落 书名+作者 拼接，保证三路匹配不因空串漏命中。
     */
    private fun ShelfBookUi.toLite(): ShelfItemLite {
        val body = chapterText.ifBlank { "$title $author" }
        return ShelfItemLite(title = title, author = author, bodySnippet = body)
    }
}
