package io.legado.app.ui.main.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 高亮切分单测：覆盖命中 / 未命中 / 空文本 / 空查询 / 多命中 / 长查询 / 大小写 / CJK / 合并相邻。
 */
class SearchHighlightTest {

    @Test
    fun `empty text yields empty segments`() {
        assertTrue(SearchHighlight.split("", "左传").isEmpty())
    }

    @Test
    fun `blank query keeps whole text normal`() {
        val seg = SearchHighlight.split("春秋左传", "")
        assertEquals(1, seg.size)
        assertFalse(seg.single().highlighted)
        assertEquals("春秋左传", seg.single().text)
    }

    @Test
    fun `whitespace only query keeps whole text normal`() {
        val seg = SearchHighlight.split("史记", "   ")
        assertEquals("史记", seg.single().text)
        assertFalse(seg.single().highlighted)
    }

    @Test
    fun `no match keeps whole text normal`() {
        val seg = SearchHighlight.split("春秋左传", "史记")
        assertEquals(1, seg.size)
        assertFalse(seg.single().highlighted)
    }

    @Test
    fun `single match splits normal and highlighted`() {
        val seg = SearchHighlight.split("春秋左传", "左传")
        assertEquals(
            listOf(
                MatchSegment("春秋", false),
                MatchSegment("左传", true),
            ),
            seg,
        )
    }

    @Test
    fun `multiple matches each highlighted`() {
        val seg = SearchHighlight.split("左传·庄公十年 左传", "左传")
        assertEquals(
            listOf(
                MatchSegment("左传", true),
                MatchSegment("·庄公十年 ", false),
                MatchSegment("左传", true),
            ),
            seg,
        )
    }

    @Test
    fun `case insensitive latin match`() {
        val seg = SearchHighlight.split("Spring and Autumn", "spring")
        assertTrue(seg.first().highlighted)
        assertEquals("Spring", seg.first().text)
    }

    @Test
    fun `query longer than text falls back to normal`() {
        val seg = SearchHighlight.split("春秋", "春秋左传")
        assertEquals(1, seg.size)
        assertFalse(seg.single().highlighted)
    }

    @Test
    fun `adjacent same color segments merge`() {
        // "aa" 命中两次相邻 → 合并为一段高亮 "aaaa"
        val seg = SearchHighlight.split("aaaa", "aa")
        assertEquals(1, seg.size)
        assertTrue(seg.single().highlighted)
        assertEquals("aaaa", seg.single().text)
    }

    @Test
    fun `cjk substring match at head and tail`() {
        val seg = SearchHighlight.split("史记·廉颇蔺相如列传", "史记")
        assertEquals("史记", seg.first().text)
        assertTrue(seg.first().highlighted)
    }
}
