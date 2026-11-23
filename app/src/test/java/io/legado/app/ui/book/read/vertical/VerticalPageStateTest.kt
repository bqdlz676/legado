package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 竖排引擎翻页状态机单测（交接清单 #4）。
 *
 * 覆盖 [VerticalLayoutEngine] 的 prev/next/jumpTo 边界收敛、同文重排保页、
 * 换文归零、缓存失效复位、中间页/末页进度口径；命中与分区已有 [VerticalLayoutEngineTest] 覆盖。
 */
class VerticalPageStateTest {

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

    private fun longText() = "城濮之战晋师败楚".repeat(80)

    // ------------------------------------------------------------- 初始空态

    @Test
    fun `before layout engine is empty and safe to query`() {
        val engine = newEngine()

        assertNull(engine.currentPage)
        assertFalse(engine.canPrev)
        assertFalse(engine.canNext)
        assertNull(engine.hitTest(540f, 960f))
        // 未排版时规格未建立，翻页分区返回 null，不抛异常。
        assertNull(engine.pageTurnAt(0f))
        assertNull(engine.jumpTo(3))
    }

    // ------------------------------------------------------------- 前后翻页边界

    @Test
    fun `next at last page returns null and prev at first returns null`() {
        val engine = newEngine()
        engine.layout(longText(), W, H)
        assertTrue(engine.canNext)
        assertFalse(engine.canPrev)

        var page = engine.currentPage!!
        var steps = 0
        while (engine.canNext) {
            page = engine.next() ?: break
            steps++
        }
        assertTrue(steps > 1)
        assertFalse(engine.canNext)
        assertNull(engine.next())
        assertEquals(steps, page.pageIndex)

        // 回翻到首页。
        while (engine.canPrev) {
            engine.prev()
        }
        assertFalse(engine.canPrev)
        assertNull(engine.prev())
        assertEquals(0, engine.currentPage!!.pageIndex)
    }

    @Test
    fun `prev and next return page with matching index`() {
        val engine = newEngine()
        engine.layout(longText(), W, H)

        val next = engine.next()
        assertNotNull(next)
        assertEquals(1, next!!.pageIndex)

        val prev = engine.prev()
        assertNotNull(prev)
        assertEquals(0, prev!!.pageIndex)
    }

    // ------------------------------------------------------------- 跳转收敛

    @Test
    fun `jumpTo clamps out of range indices`() {
        val engine = newEngine()
        engine.layout(longText(), W, H)
        val lastIndex = engine.currentPage!!.let {
            var i = it.pageIndex
            while (engine.canNext) i = engine.next()!!.pageIndex
            i
        }

        val below = engine.jumpTo(-10)
        assertNotNull(below)
        assertEquals(0, below!!.pageIndex)

        val above = engine.jumpTo(9999)
        assertNotNull(above)
        assertEquals(lastIndex, above!!.pageIndex)

        val middle = engine.jumpTo(lastIndex / 2)
        assertNotNull(middle)
        assertEquals(lastIndex / 2, middle!!.pageIndex)
    }

    // ------------------------------------------------------------- 重排保页 / 换文归零

    @Test
    fun `same content relayout keeps current page index`() {
        val engine = newEngine()
        engine.layout(longText(), W, H)
        engine.next()
        engine.next()
        assertEquals(2, engine.currentPage!!.pageIndex)

        val relaid = engine.layout(longText(), W, H)
        assertEquals(2, relaid.pageIndex)
    }

    @Test
    fun `different content layout resets to first page`() {
        val engine = newEngine()
        engine.layout(longText(), W, H)
        engine.next()
        engine.next()
        assertEquals(2, engine.currentPage!!.pageIndex)

        val fresh = engine.layout("短文甲乙丙丁", W, H)
        assertEquals(0, fresh.pageIndex)
        assertFalse(engine.canNext)
    }

    // ------------------------------------------------------------- 缓存失效复位

    @Test
    fun `invalidateCache clears navigation state and cold lays out again`() {
        val engine = newEngine()
        engine.layout(longText(), W, H)
        engine.next()

        engine.invalidateCache()
        assertNull(engine.currentPage)
        assertFalse(engine.canPrev)
        assertFalse(engine.canNext)
        assertFalse(engine.lastLayoutReusedCache)

        val relaid = engine.layout(longText(), W, H)
        assertTrue(relaid.columns.isNotEmpty())
        assertFalse("清缓存后应为冷排版", engine.lastLayoutReusedCache)
        assertEquals(0, relaid.pageIndex)
    }

    // ------------------------------------------------------------- 进度口径

    @Test
    fun `middle page progress between zero and one last page converges to one`() {
        val engine = newEngine()
        val first = engine.layout(longText(), W, H)

        if (engine.canNext) {
            assertTrue("中间页进度应在 (0,1)", first.progress in 0f..1f)
        }

        var last = first
        while (engine.canNext) {
            last = engine.next()!!
        }
        assertEquals(1f, last.progress, 1e-6f)
    }
}
