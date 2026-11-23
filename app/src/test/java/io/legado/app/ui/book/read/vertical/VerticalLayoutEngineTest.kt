package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 落文 · 竖排排版引擎单测。
 *
 * 覆盖冻结 API：
 * - [VerticalLayoutEngine.layout] 分页/列序/字序/偏移/段落映射/进度；
 * - [VerticalLayoutEngine.hitTest] 精确命中、首字/末字角点、列间空白与注音栏脱靶、最近字回退；
 * - [VerticalLayoutEngine.pageTurnAt] 左 1/5 上翻、右 1/5 下翻、中间不翻；
 * - [VerticalLayoutEngine.invalidateCache] 清状态；字位缓存重排复用（×3 路径）。
 *
 * 本测试为 JVM 单测：字号以 px 直接注入，不依赖 Android 尺寸换算与 TextPaint。
 */
class VerticalLayoutEngineTest {

    private companion object {
        const val W = 1080
        const val H = 1920
        const val BODY_PX = 36f
        const val TITLE_PX = 40f
    }

    private fun newEngine() = VerticalLayoutEngine(
        bodySizePx = BODY_PX,
        titleSizePx = TITLE_PX,
        metrics = DefaultTypefaceMetrics(),
    )

    private fun flatCells(page: VerticalPage): List<CharCell> =
        page.columns.flatMap { it.cells }

    // ---------------------------------------------------------------- layout

    @Test
    fun `layout short text produces one page with sequential offsets`() {
        val engine = newEngine()
        val page = engine.layout("城濮之战晋师败楚", W, H)

        val cells = flatCells(page)
        assertEquals(8, cells.size)
        assertEquals(0, page.pageIndex)
        cells.forEachIndexed { i, cell -> assertEquals(i, cell.offset) }
        // 末页进度收敛到 1。
        assertEquals(1f, page.progress, 1e-6f)
    }

    @Test
    fun `columns flow from right to left and cells top to bottom`() {
        val text = "城濮之战晋师败楚子玉怒从晋师退舍三晨食毕而后陈".repeat(2)
        val engine = newEngine()
        val page = engine.layout(text, W, H)

        assertTrue("应至少两列", page.columns.size >= 2)
        // 列号 0 在最右：左缘 x 应大于其左侧一列。
        assertTrue(page.columns[0].left > page.columns[1].left)

        val firstColumn = page.columns.first().cells
        for (i in 1 until firstColumn.size) {
            assertTrue("列内 y 应自上而下递增", firstColumn[i].y >= firstColumn[i - 1].y)
        }
    }

    @Test
    fun `paragraph mapping returns paragraph text and in-paragraph position`() {
        val engine = newEngine()
        val page = engine.layout("第一段甲乙丙。\n第二段丁戊己。", W, H)

        // 第一段 7 字；第二段首字 offset = 7。
        val secondParaFirst = flatCells(page).first { it.offset == 7 }
        val hit = engine.hitTest(
            secondParaFirst.x + secondParaFirst.width / 2f,
            secondParaFirst.y + secondParaFirst.height / 2f,
        )

        assertNotNull(hit)
        assertEquals('第', hit!!.char)
        assertEquals(7, hit.offset)
        assertEquals("第二段丁戊己。", hit.paragraphText)
        assertEquals(0, hit.pos)
    }

    @Test
    fun `title cells render larger than body cells`() {
        val engine = newEngine()
        val page = engine.layout("正文甲乙丙丁", W, H, title = "卷一")

        val firstColumn = page.columns.first().cells
        assertTrue(firstColumn.isNotEmpty())
        assertEquals(TITLE_PX, firstColumn.first().height, 1e-6f)
        assertTrue(firstColumn.first().height > BODY_PX)
    }

    @Test
    fun `long text paginates into multiple pages and navigation advances`() {
        val engine = newEngine()
        val text = "城濮之战晋师败楚".repeat(80)
        val first = engine.layout(text, W, H)

        assertTrue("长文应翻页", engine.canNext)
        assertEquals(0, first.pageIndex)

        var page = first
        var steps = 0
        while (engine.canNext && steps < 50) {
            page = engine.next() ?: break
            steps++
        }
        assertTrue("至少前进一页", steps > 0)
        assertEquals(steps, page.pageIndex)
        assertFalse(engine.canNext)
        // 末页进度收敛到 1。
        assertEquals(1f, page.progress, 1e-6f)

        // 上翻可回退。
        val prev = engine.prev()
        assertNotNull(prev)
        assertEquals(steps - 1, prev!!.pageIndex)
    }

    @Test
    fun `empty text yields empty page without crash`() {
        val engine = newEngine()
        val page = engine.layout("", W, H)

        assertTrue(page.columns.isEmpty())
        assertEquals(0f, page.progress, 1e-6f)
        assertNull(engine.hitTest(540f, 960f))
        // 空白页下翻页分区仍然可用。
        assertEquals(PageTurn.PREV, engine.pageTurnAt(0f))
        assertEquals(PageTurn.NEXT, engine.pageTurnAt((W - 1).toFloat()))
    }

    // ---------------------------------------------------------------- hitTest

    @Test
    fun `hitTest exact box returns matching char`() {
        val engine = newEngine()
        val page = engine.layout("城濮之战", W, H)
        val cell = flatCells(page).first()

        val hit = engine.hitTest(cell.x + cell.width / 2f, cell.y + cell.height / 2f)

        assertNotNull(hit)
        assertEquals(cell.char, hit!!.char)
        assertEquals(cell.offset, hit.offset)
        assertEquals("城濮之战", hit.paragraphText)
        assertEquals(0, hit.pos)
    }

    @Test
    fun `hitTest first char corner top-left hits first char`() {
        val engine = newEngine()
        val page = engine.layout("城濮之战晋师", W, H)
        val first = flatCells(page).first()

        // 首字方框左上角闭区间边界。
        val hit = engine.hitTest(first.x, first.y)

        assertNotNull(hit)
        assertEquals(first.char, hit!!.char)
        assertEquals(first.offset, hit.offset)
    }

    @Test
    fun `hitTest last char corner bottom-right hits last char`() {
        val engine = newEngine()
        val page = engine.layout("城濮之战晋师", W, H)
        val last = flatCells(page).last()

        // 末字方框右下角闭区间边界。
        val hit = engine.hitTest(last.x + last.width, last.y + last.height)

        assertNotNull(hit)
        assertEquals(last.char, hit!!.char)
        assertEquals(last.offset, hit.offset)
    }

    @Test
    fun `hitTest inter-column gap returns null`() {
        val text = "城濮之战晋师败楚子玉怒从晋师退舍三晨食毕而后陈".repeat(2)
        val engine = newEngine()
        val page = engine.layout(text, W, H)
        assertTrue(page.columns.size >= 2)

        val rightCol = page.columns[0]
        val leftCol = page.columns[1]
        // 右列左缘与左列右缘之间的列间距空白。
        val gapX = (leftCol.left + leftCol.cells.first().width + rightCol.left) / 2f
        val midY = (rightCol.cells.first().y + rightCol.cells.last().y) / 2f

        assertNull(engine.hitTest(gapX, midY))
    }

    @Test
    fun `hitTest misses annotation band and empty area returns null`() {
        val engine = newEngine()
        engine.layout("城濮之战", W, H)

        // 左侧 18% 注音/批注栏内无正文列。
        assertNull(engine.hitTest(50f, 960f))
        // 版心外远处。
        assertNull(engine.hitTest(540f, 10f))
    }

    @Test
    fun `hitTest falls back to nearest char within half pitch`() {
        val engine = newEngine()
        val page = engine.layout("城濮之战晋师", W, H)
        val column = page.columns.first()
        assertTrue(column.cells.size >= 2)

        val first = column.cells[0]
        val second = column.cells[1]
        // 两行字框之间的中点纵向位置，横向落在列厚内。
        val midY = (first.y + first.height + second.y) / 2f
        val midX = first.x + first.width / 2f

        val hit = engine.hitTest(midX, midY)
        assertNotNull(hit)
        val nearestOffset = if (abs(midY - (first.y + first.height / 2f)) <=
            abs(midY - (second.y + second.height / 2f))
        ) first.offset else second.offset
        assertEquals(nearestOffset, hit!!.offset)
    }

    // ------------------------------------------------------------- page turn

    @Test
    fun `pageTurnAt left fifth goes prev right fifth goes next center null`() {
        val engine = newEngine()
        engine.layout("城濮之战", W, H)

        val edge = W / 5f
        assertEquals(PageTurn.PREV, engine.pageTurnAt(0f))
        assertEquals(PageTurn.PREV, engine.pageTurnAt(edge - 1f))
        assertEquals(PageTurn.NEXT, engine.pageTurnAt((W - 1).toFloat()))
        assertEquals(PageTurn.NEXT, engine.pageTurnAt(W - edge + 1f))
        assertNull(engine.pageTurnAt(W / 2f))
    }

    // ----------------------------------------------------------------- cache

    @Test
    fun `second layout with same content reuses rect cache then invalidate clears`() {
        val engine = newEngine()
        val text = "城濮之战晋师败楚".repeat(10)

        engine.layout(text, W, H)
        assertFalse("首次排版应为冷排版", engine.lastLayoutReusedCache)

        val second = engine.layout(text, W, H)
        assertTrue("同尺寸重排应复用字位缓存", engine.lastLayoutReusedCache)
        assertTrue(second.columns.isNotEmpty())

        engine.invalidateCache()
        assertNull(engine.currentPage)
        assertFalse(engine.lastLayoutReusedCache)

        val third = engine.layout(text, W, H)
        assertTrue(third.columns.isNotEmpty())
        assertFalse("清缓存后再次排版应为冷排版", engine.lastLayoutReusedCache)
    }

    @Test
    fun `invalidateCache then recompute reproduces same offsets and geometry`() {
        val engine = newEngine()
        val text = "城濮之战晋师败楚子玉".repeat(6)

        val first = engine.layout(text, W, H)
        val before = flatCells(first).map { it.offset to (it.x to it.y) }

        engine.invalidateCache()
        val third = engine.layout(text, W, H)
        val after = flatCells(third).map { it.offset to (it.x to it.y) }

        assertEquals(before.size, after.size)
        before.forEachIndexed { i, (offset, xy) ->
            assertEquals(offset, after[i].first)
            assertEquals(xy.first, after[i].second.first, 1e-4f)
            assertEquals(xy.second, after[i].second.second, 1e-4f)
        }
    }

    // ------------------------------------------------------------- classifier

    @Test
    fun `classifier marks latin digit and cjk correctly`() {
        assertEquals(CharKind.LATIN, VerticalCharClassifier.classify('A'))
        assertEquals(CharKind.LATIN, VerticalCharClassifier.classify('z'))
        assertEquals(CharKind.DIGIT, VerticalCharClassifier.classify('5'))
        assertEquals(CharKind.CJK, VerticalCharClassifier.classify('汉'))
        assertEquals(CharKind.PUNCT, VerticalCharClassifier.classify('，'))
        assertEquals(CharKind.SPACE, VerticalCharClassifier.classify(' '))

        assertTrue(VerticalCharClassifier.isInlineScript(CharKind.LATIN))
        assertTrue(VerticalCharClassifier.isInlineScript(CharKind.DIGIT))
        assertFalse(VerticalCharClassifier.isInlineScript(CharKind.CJK))
    }

    @Test
    fun `latin and digit chars are emitted and marked rotated`() {
        val engine = newEngine()
        engine.layout("AB1945年", W, H)
        val cells = engine.debugCurrentCells()

        val byChar = cells.associateBy { it.char }
        assertTrue(byChar.containsKey('A'))
        assertTrue(byChar.containsKey('B'))
        assertTrue(byChar.containsKey('1'))
        assertTrue(byChar.containsKey('5'))
        assertTrue(byChar.containsKey('年'))

        // 拉丁/数字/年份 90° 侧排；CJK 正立。
        assertTrue(byChar.getValue('A').rotated)
        assertTrue(byChar.getValue('B').rotated)
        assertTrue(byChar.getValue('1').rotated)
        assertTrue(byChar.getValue('9').rotated)
        assertFalse(byChar.getValue('年').rotated)
    }
}
