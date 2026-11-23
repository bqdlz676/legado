package io.legado.app.ui.main.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 匹配 / 排序单测：归一化、四级命中分值、大小写不敏感、排序稳定性、截断与空态兜底。
 */
class SearchMatcherTest {

    private fun item(
        title: String = "",
        author: String = "",
        snippet: String = "",
        id: String = title + author + snippet,
    ) = SearchResultItem(id = id, title = title, author = author, snippet = snippet)

    @Test
    fun `normalize trims and collapses internal whitespace`() {
        assertEquals("春秋 左传", SearchMatcher.normalize("  春秋   左传  "))
        assertEquals("", SearchMatcher.normalize("   "))
        assertEquals("", SearchMatcher.normalize(""))
    }

    @Test
    fun `blank query matches nothing`() {
        val item = item(title = "春秋左传")
        assertFalse(SearchMatcher.matches(item, "   "))
        assertEquals(-1, SearchMatcher.score(item, ""))
        assertTrue(SearchMatcher.rank(listOf(item), "").isEmpty())
    }

    @Test
    fun `empty candidates rank to empty`() {
        assertTrue(SearchMatcher.rank(emptyList(), "左传").isEmpty())
    }

    @Test
    fun `all empty fields do not match`() {
        assertFalse(SearchMatcher.matches(item(), "左传"))
    }

    @Test
    fun `title prefix outranks title contains which outranks author and snippet`() {
        val prefix = item(title = "左传注")
        val contains = item(title = "春秋左传纪事")
        val author = item(title = "unknown", author = "左传编者")
        val snippet = item(title = "unknown", snippet = "详见左传桓公二年")

        assertEquals(0, SearchMatcher.score(prefix, "左传"))
        assertEquals(1, SearchMatcher.score(contains, "左传"))
        assertEquals(2, SearchMatcher.score(author, "左传"))
        assertEquals(3, SearchMatcher.score(snippet, "左传"))

        val ranked = SearchMatcher.rank(listOf(snippet, author, contains, prefix), "左传")
        assertEquals(listOf(prefix, contains, author, snippet), ranked)
    }

    @Test
    fun `match is case insensitive`() {
        val item = item(title = "Spring")
        assertTrue(SearchMatcher.matches(item, "spring"))
        assertTrue(SearchMatcher.matches(item, "SPRING"))
    }

    @Test
    fun `rank keeps original order within same score`() {
        val a = item(title = "春秋", id = "a")
        val b = item(title = "春秋", id = "b")
        val ranked = SearchMatcher.rank(listOf(b, a), "春秋")
        assertEquals(listOf(b, a), ranked)
    }

    @Test
    fun `rank truncates to limit`() {
        val items = List(5) { item(title = "春秋${it}", id = "${it}") }
        assertEquals(3, SearchMatcher.rank(items, "春秋", limit = 3).size)
    }

    @Test
    fun `non positive limit falls back to page limit`() {
        val items = List(SearchTokens.RESULT_PAGE_LIMIT + 5) {
            item(title = "春秋", id = "${it}")
        }
        val ranked = SearchMatcher.rank(items, "春秋", limit = -1)
        assertEquals(SearchTokens.RESULT_PAGE_LIMIT, ranked.size)
    }

    @Test
    fun `hit field maps back to matched field`() {
        assertEquals(
            SearchField.BOOK_TITLE,
            SearchMatcher.hitField(item(title = "左传"), "左传"),
        )
        assertEquals(
            SearchField.AUTHOR,
            SearchMatcher.hitField(item(title = "x", author = "左传编者"), "左传"),
        )
        assertEquals(
            SearchField.CONTENT,
            SearchMatcher.hitField(item(title = "x", snippet = "左传"), "左传"),
        )
    }

    @Test
    fun `no match falls back to item declared field`() {
        val item = item(title = "x", field = SearchField.CONTENT)
        assertEquals(SearchField.CONTENT, SearchMatcher.hitField(item, "史记"))
    }
}
