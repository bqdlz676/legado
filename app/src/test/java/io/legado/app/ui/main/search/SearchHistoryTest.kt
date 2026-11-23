package io.legado.app.ui.main.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 历史 / 热门整理单测：归一化、去重、置顶、截断、单条删除、清空、空输入兜底。
 */
class SearchHistoryTest {

    @Test
    fun `blank keyword normalizes to null and is not stored`() {
        assertNull(SearchHistory.normalizeKey(null))
        assertNull(SearchHistory.normalizeKey(""))
        assertNull(SearchHistory.normalizeKey("   "))
    }

    @Test
    fun `clean drops blanks dedupes and caps`() {
        val raw = listOf("左传", " 左传 ", "", "史记", "   ", "左传")
        assertEquals(listOf("左传", "史记"), SearchHistory.clean(raw))
    }

    @Test
    fun `clean honors limit`() {
        val raw = List(20) { "词$it" }
        assertEquals(12, SearchHistory.clean(raw).size)
        assertEquals(5, SearchHistory.clean(raw, limit = 5).size)
    }

    @Test
    fun `add prepends new keyword`() {
        val next = SearchHistory.add(listOf("史记", "春秋"), "左传")
        assertEquals(listOf("左传", "史记", "春秋"), next)
    }

    @Test
    fun `existing keyword moves to front on re-add`() {
        val next = SearchHistory.add(listOf("史记", "春秋", "左传"), "春秋")
        assertEquals(listOf("春秋", "史记", "左传"), next)
    }

    @Test
    fun `add trims and collapses whitespace`() {
        val next = SearchHistory.add(emptyList(), "  春秋   左传 ")
        assertEquals(listOf("春秋 左传"), next)
    }

    @Test
    fun `blank add leaves history unchanged`() {
        val history = listOf("史记")
        assertEquals(history, SearchHistory.add(history, "   "))
        assertEquals(history, SearchHistory.add(history, null))
    }

    @Test
    fun `add caps at limit`() {
        val history = List(12) { "旧$it" }
        val next = SearchHistory.add(history, "新词", limit = 12)
        assertEquals(12, next.size)
        assertEquals("新词", next.first())
    }

    @Test
    fun `remove single keyword by normalized match`() {
        val history = listOf("左传", "史记", "春秋")
        assertEquals(listOf("左传", "春秋"), SearchHistory.remove(history, " 史记 "))
    }

    @Test
    fun `remove unknown keyword leaves list unchanged`() {
        val history = listOf("左传", "史记")
        assertEquals(history, SearchHistory.remove(history, "春秋"))
    }

    @Test
    fun `clear returns empty list`() {
        assertEquals(emptyList<String>(), SearchHistory.clear())
    }

    @Test
    fun `hot dedupes trims and caps`() {
        val hot = listOf("左传", "", " 史记 ", "左传", "春秋")
        assertEquals(listOf("左传", "史记", "春秋"), SearchHistory.hot(hot))
    }

    @Test
    fun `hot handles null and empty input safely`() {
        assertEquals(emptyList<String>(), SearchHistory.hot(null))
        assertEquals(emptyList<String>(), SearchHistory.hot(emptyList()))
    }
}
