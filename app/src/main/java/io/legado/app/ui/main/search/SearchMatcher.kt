package io.legado.app.ui.main.search

/**
 * 落文 · 搜索匹配与排序（纯 JVM，可单测）
 *
 * 切片-13：搜书名、作者、或正文中的任意一句。本件负责查询归一化、字段命中判定与
 * 命中优先级排序；不碰网络 / 数据库，空查询、空候选、空字段全部兜底为空列表，不崩溃。
 *
 * 命中优先级（小者靠前）：书名前缀 > 书名包含 > 作者包含 > 正文片段包含。
 * 同优先级保持候选原顺序（稳定排序）。
 */
object SearchMatcher {

    /** 查询归一化：去首尾空白、压缩内部连续空白；不做繁简转换（交由数据层兜底）。 */
    fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    /** 命中分值；-1 表示未命中。 */
    fun score(item: SearchResultItem, query: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return -1
        val title = item.title.trim()
        val author = item.author.trim()
        val snippet = item.snippet.trim()
        if (title.isEmpty() && author.isEmpty() && snippet.isEmpty()) return -1

        val hayTitle = title.lowercase()
        val hayAuthor = author.lowercase()
        val haySnippet = snippet.lowercase()
        val needle = q.lowercase()

        return when {
            hayTitle.startsWith(needle) -> 0
            hayTitle.contains(needle) -> 1
            hayAuthor.contains(needle) -> 2
            haySnippet.contains(needle) -> 3
            else -> -1
        }
    }

    /** 是否命中任一字段。 */
    fun matches(item: SearchResultItem, query: String): Boolean =
        score(item, query) >= 0

    /** 命中主字段（用于条目副文提示）；未命中回落 [SearchField.BOOK_TITLE]。 */
    fun hitField(item: SearchResultItem, query: String): SearchField =
        when (score(item, query)) {
            0, 1 -> SearchField.BOOK_TITLE
            2 -> SearchField.AUTHOR
            3 -> SearchField.CONTENT
            else -> item.field
        }

    /**
     * 候选集匹配 + 排序 + 截断。
     *
     * 兜底：查询归一化为空 → 空列表；候选为空 → 空列表；单条字段全空不参与匹配。
     * [limit] ≤ 0 时回落到 [SearchTokens.RESULT_PAGE_LIMIT]，不允许负截断。
     */
    fun rank(
        candidates: List<SearchResultItem>,
        query: String,
        limit: Int = SearchTokens.RESULT_PAGE_LIMIT,
    ): List<SearchResultItem> {
        if (candidates.isEmpty()) return emptyList()
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val cap = if (limit > 0) limit else SearchTokens.RESULT_PAGE_LIMIT

        return candidates
            .mapIndexed { index, item -> index to score(item, q) }
            .filter { it.second >= 0 }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(cap)
            .map { (index, _) -> candidates[index] }
    }
}
