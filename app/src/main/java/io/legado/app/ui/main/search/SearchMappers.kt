package io.legado.app.ui.main.search

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook

/**
 * 落文 · 搜索结果映射与合并（实体 → UI 模型）
 *
 * 切片-13：纯文本列表，不配图；这里只做字段搬运与本地 / 远程去重合并，
 * 不碰网络、不做高亮（高亮归 [SearchHighlight]）。任何字段为空都安全兜底，不抛异常。
 */

/** 远程书源结果 → 搜索条目；标题 / id 为空时互兜底，禁止空 id 穿透。 */
fun SearchBook.toSearchItem(): SearchResultItem = SearchResultItem(
    id = bookUrl.ifBlank { "$name-$author" },
    title = name.ifBlank { bookUrl },
    author = author,
    snippet = intro?.trim().orEmpty(),
    field = SearchField.BOOK_TITLE,
)

/** 本地书架结果 → 搜索条目。 */
fun Book.toSearchItem(): SearchResultItem = SearchResultItem(
    id = bookUrl.ifBlank { "$name-$author" },
    title = name,
    author = author,
    snippet = intro?.trim().orEmpty(),
    field = SearchField.BOOK_TITLE,
)

/**
 * 本地书架 + 远程书源合并去重：
 * - 同 id（或同名同作者）视为同一条，先出现者保留，本地书架条目前置；
 * - 标题为空的脏数据不参与展示；
 * - 入参为空安全回落空列表。纯 JVM 可单测。
 */
object SearchMerger {

    fun merge(
        local: List<SearchResultItem>,
        remote: List<SearchResultItem>,
    ): List<SearchResultItem> {
        val seen = HashSet<String>()
        val out = ArrayList<SearchResultItem>()
        (local + remote).forEach { item ->
            val key = dedupKey(item)
            if (seen.add(key) && item.title.isNotBlank()) {
                out.add(item)
            }
        }
        return out
    }

    private fun dedupKey(item: SearchResultItem): String =
        item.id.ifBlank { "${item.title}::${item.author}" }.trim().lowercase()
}
