package io.legado.app.ui.main.search

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索结果映射与合并单测：实体 → UI 模型字段兜底；本地 / 远程去重、本地前置、
 * 脏标题剔除；空入参安全回落。纯 JVM，不碰网络与数据库。
 */
class SearchMappersTest {

    @Test
    fun `remote book maps fields with blank fallbacks`() {
        val remote = SearchBook(
            bookUrl = "https://src/1",
            name = "春秋左传",
            author = "左丘明",
            intro = "  春秋时期编年体史书  ",
        )
        val item = remote.toSearchItem()
        assertEquals("https://src/1", item.id)
        assertEquals("春秋左传", item.title)
        assertEquals("左丘明", item.author)
        assertEquals("春秋时期编年体史书", item.snippet)
    }

    @Test
    fun `remote blank bookUrl falls back to name-author key`() {
        val remote = SearchBook(bookUrl = "", name = "史记", author = "司马迁")
        val item = remote.toSearchItem()
        assertEquals("史记-司马迁", item.id)
        assertEquals("史记", item.title)
    }

    @Test
    fun `remote blank name falls back to bookUrl`() {
        val remote = SearchBook(bookUrl = "https://src/2", name = "", author = "佚名")
        val item = remote.toSearchItem()
        assertEquals("https://src/2", item.title)
    }

    @Test
    fun `local book maps to search item`() {
        val local = Book(bookUrl = "local://3", name = "汉书", author = "班固", intro = "东汉")
        val item = local.toSearchItem()
        assertEquals("local://3", item.id)
        assertEquals("汉书", item.title)
        assertEquals("班固", item.author)
        assertEquals("东汉", item.snippet)
    }

    @Test
    fun `merge keeps local first and dedupes by id`() {
        val local = listOf(SearchResultItem(id = "a", title = "史记", author = "司马迁"))
        val remote = listOf(
            SearchResultItem(id = "a", title = "史记（远程副本）", author = "司马迁"),
            SearchResultItem(id = "b", title = "汉书", author = "班固"),
        )
        val merged = SearchMerger.merge(local, remote)
        assertEquals(2, merged.size)
        assertEquals("a", merged[0].id)
        assertEquals("史记", merged[0].title)
        assertEquals("b", merged[1].id)
    }

    @Test
    fun `merge dedupes by name-author when id is blank`() {
        val local = listOf(SearchResultItem(id = "", title = "左传", author = "左丘明"))
        val remote = listOf(SearchResultItem(id = "", title = "左传", author = "左丘明"))
        val merged = SearchMerger.merge(local, remote)
        assertEquals(1, merged.size)
    }

    @Test
    fun `merge drops blank-title dirty rows`() {
        val local = listOf(SearchResultItem(id = "x", title = "  "))
        val remote = listOf(SearchResultItem(id = "y", title = "正常书名"))
        val merged = SearchMerger.merge(local, remote)
        assertEquals(1, merged.size)
        assertEquals("y", merged[0].id)
    }

    @Test
    fun `merge empty inputs return empty list without throwing`() {
        assertTrue(SearchMerger.merge(emptyList(), emptyList()).isEmpty())
        assertTrue(
            SearchMerger.merge(
                listOf(SearchResultItem(id = "z", title = "册")),
                emptyList(),
            ).size == 1,
        )
    }
}
