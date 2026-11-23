package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 竖排分页器直测（交接清单 #4：分页 / 文字渲染纯 JVM 路径）。
 *
 * 直接构造 internal [VerticalPaginator] 覆盖：空文本兜底、段落偏移表、空白不占字形、
 * 标题列居右、拉丁/数字侧排标记、按列分页不跨列截断、字位缓存重建（×3 路径底层）。
 * 引擎门面层的翻页状态机见 [VerticalPageStateTest]。
 */
class VerticalPaginatorTest {

    private companion object {
        const val W = 1080f
        const val H = 1920f
        const val BODY = 36f
        const val TITLE = 40f
    }

    private fun newCache() = VerticalMeasureCache()

    private fun newPaginator(cache: VerticalMeasureCache = newCache()) = VerticalPaginator(
        spec = VerticalLayoutSpec.create(
            viewWidthPx = W,
            viewHeightPx = H,
            bodySizePx = BODY,
            titleSizePx = TITLE,
            titleGapPx = 24f,
        ),
        metrics = DefaultTypefaceMetrics(),
        cache = cache,
    )

    private fun allCells(result: VerticalPaginator.PaginationResult) =
        result.pages.flatMap { it.columns }.flatMap { it.cells }

    // ------------------------------------------------------------- 空态兜底

    @Test
    fun `empty text and title yield one empty page without crash`() {
        val result = newPaginator().layout("", null, reuseCache = false)

        assertEquals(1, result.pages.size)
        assertTrue(result.pages.first().columns.isEmpty())
        assertEquals(0, result.totalCells)
        assertTrue(result.paragraphs.isEmpty())
        assertFalse(result.reusedFromCache)
    }

    @Test
    fun `whitespace only text emits no cells`() {
        val result = newPaginator().layout("  \n\t　\r\n ", null, reuseCache = false)

        assertEquals(0, result.totalCells)
        assertTrue(allCells(result).isEmpty())
    }

    // ------------------------------------------------------------- 段落与偏移

    @Test
    fun `offsets are sequential over non-whitespace chars`() {
        val result = newPaginator().layout("甲乙丙。\n丁戊己。", null, reuseCache = false)
        val cells = allCells(result)

        assertEquals(8, cells.size)
        cells.forEachIndexed { i, cell -> assertEquals(i, cell.offset) }
    }

    @Test
    fun `paragraph ranges carry trimmed text and start offsets`() {
        val result = newPaginator().layout("甲乙丙。\n丁戊己。", null, reuseCache = false)

        assertEquals(2, result.paragraphs.size)
        assertEquals(ParagraphRange(0, "甲乙丙。"), result.paragraphs[0])
        assertEquals(ParagraphRange(4, "丁戊己。"), result.paragraphs[1])
    }

    @Test
    fun `internal whitespace and newlines do not consume offsets`() {
        val result = newPaginator().layout("甲 乙\n丙", null, reuseCache = false)
        val cells = allCells(result)

        assertEquals(3, cells.size)
        assertEquals(listOf(0, 1, 2), cells.map { it.offset })
        assertEquals(listOf('甲', '乙', '丙'), cells.map { it.char })
    }

    @Test
    fun `punctuation is emitted as a cell`() {
        val result = newPaginator().layout("城濮之战，", null, reuseCache = false)
        val cells = allCells(result)

        assertEquals(5, cells.size) // "城濮之战，" 共 5 字符 5 格；切片-09 未规定全角标点拆两格，第 69 行同口径
        val comma = cells.first { it.char == '，' }
        assertEquals(CharKind.PUNCT, comma.kind)
        assertFalse(comma.rotated)
    }

    // ------------------------------------------------------------- 标题列

    @Test
    fun `title cells precede body and carry title kind`() {
        val result = newPaginator().layout("甲乙", "卷一", reuseCache = false)
        val cells = allCells(result)

        assertEquals(4, cells.size)
        assertEquals(CharKind.TITLE, cells[0].kind)
        assertEquals(CharKind.TITLE, cells[1].kind)
        assertEquals('卷', cells[0].char)
        assertEquals('一', cells[1].char)
        assertEquals(CharKind.CJK, cells[2].kind)
    }

    @Test
    fun `title column sits right of body column`() {
        val result = newPaginator().layout("甲乙丙丁戊己庚辛", "卷一", reuseCache = false)
        val columns = result.pages.flatMap { it.columns }

        assertTrue(columns.size >= 2)
        // 列号 0 在最右：标题列左缘大于其左侧正文列。
        assertTrue(columns[0].left > columns[1].left)
    }

    // ------------------------------------------------------------- 侧排标记

    @Test
    fun `latin and digit rotated while cjk and punct upright`() {
        val result = newPaginator().layout("AB19，汉", null, reuseCache = false)
        val byChar = allCells(result).associateBy { it.char }

        assertTrue(byChar.getValue('A').rotated)
        assertTrue(byChar.getValue('B').rotated)
        assertTrue(byChar.getValue('1').rotated)
        assertTrue(byChar.getValue('9').rotated)
        assertFalse(byChar.getValue('，').rotated)
        assertFalse(byChar.getValue('汉').rotated)
    }

    // ------------------------------------------------------------- 分页切列

    @Test
    fun `long text paginates by columns without splitting a column across pages`() {
        val spec = VerticalLayoutSpec.create(W, H, BODY, TITLE, titleGapPx = 24f)
        val perPage = spec.columnsPerPage
        val result = newPaginator().layout("城濮之战晋师败楚".repeat(40), null, reuseCache = false)

        assertTrue("长文应翻页", result.pages.size > 1)
        result.pages.forEach { page ->
            assertTrue("每页列数不超过容量", page.columns.size <= perPage)
        }

        // 同一列的字必须同页：按列号归组后不出现跨页重复列号。
        val columnOwners = HashMap<Int, Int>()
        result.pages.forEachIndexed { pageIndex, page ->
            page.columns.forEach { col ->
                col.cells.forEach { cell ->
                    val previous = columnOwners.put(cell.columnIndex, pageIndex)
                    assertTrue("列 ${cell.columnIndex} 不得跨页", previous == null || previous == pageIndex)
                }
            }
        }
    }

    // ------------------------------------------------------------- 缓存重建

    @Test
    fun `cache rebuild reproduces same offsets and geometry when fully covered`() {
        val cache = newCache()
        val paginator = newPaginator(cache)
        val text = "城濮之战晋师败楚子玉".repeat(6)

        val cold = paginator.layout(text, null, reuseCache = false)
        assertTrue(cache.coversRects(cold.totalCells))

        val rebuilt = paginator.layout(text, null, reuseCache = true)
        assertTrue(rebuilt.reusedFromCache)

        val coldCells = allCells(cold).map { it.offset to (it.x to it.y) }
        val rebuiltCells = allCells(rebuilt).map { it.offset to (it.x to it.y) }
        assertEquals(coldCells.size, rebuiltCells.size)
        coldCells.forEachIndexed { i, (offset, xy) ->
            assertEquals(offset, rebuiltCells[i].first)
            assertEquals(xy.first, rebuiltCells[i].second.first, 1e-4f)
            assertEquals(xy.second, rebuiltCells[i].second.second, 1e-4f)
        }
    }

    @Test
    fun `partial cache coverage falls back to cold layout`() {
        val cache = newCache()
        val paginator = newPaginator(cache)
        val text = "城濮之战晋师败楚".repeat(6)

        val cold = paginator.layout(text, null, reuseCache = false)
        // 人为挖掉一个字位，缓存不再齐全，必须走冷排版。
        cache.clearRects()
        repeat(cold.totalCells - 1) { cache.putRect(allCells(cold)[it]) }

        val rebuilt = paginator.layout(text, null, reuseCache = true)
        assertFalse(rebuilt.reusedFromCache)
        assertEquals(cold.totalCells, rebuilt.totalCells)
    }
}
