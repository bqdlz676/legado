package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 竖排字位缓存单测：覆盖字宽命中、字位回填/读取、齐全性判定与整体失效。
 */
class VerticalMeasureCacheTest {

    private fun cell(offset: Int, column: Int = 0) = LayoutCell(
        char = '汉',
        offset = offset,
        kind = CharKind.CJK,
        columnIndex = column,
        x = 100f + column * 10f,
        y = 36f + offset * 64.8f,
        size = 36f,
        rotated = false,
    )

    @Test
    fun `advance computes once then hits cache`() {
        val cache = VerticalMeasureCache()
        var computes = 0
        val first = cache.advance('A'.code) { computes++; 36f }
        val second = cache.advance('A'.code) { computes++; 36f }

        assertEquals(36f, first, 1e-6f)
        assertEquals(36f, second, 1e-6f)
        assertEquals(1, computes)
        assertEquals(1, cache.measureCalls)
        assertEquals(1, cache.advanceHits)
    }

    @Test
    fun `rect put and roundtrip by offset`() {
        val cache = VerticalMeasureCache()
        assertNull(cache.rect(0))

        cache.putRect(cell(offset = 0))
        val hit = cache.rect(0)

        assertNotNull(hit)
        assertEquals(0, hit!!.offset)
        assertEquals(1, cache.cachedRectCount)
        assertEquals(1, cache.rectHits)
    }

    @Test
    fun `coversRects reports missing offset`() {
        val cache = VerticalMeasureCache()
        cache.putRect(cell(0))
        cache.putRect(cell(1))

        assertTrue(cache.coversRects(2))
        assertFalse(cache.coversRects(3))
    }

    @Test
    fun `clearAll wipes entries and resets counters`() {
        val cache = VerticalMeasureCache(advanceCapacity = 8)
        cache.advance('A'.code) { 36f }
        cache.putRect(cell(0))
        cache.rect(0)

        cache.clearAll()

        assertEquals(0, cache.cachedRectCount)
        assertEquals(0, cache.cachedAdvanceCount)
        assertEquals(0, cache.measureCalls)
        assertEquals(0, cache.advanceHits)
        assertEquals(0, cache.rectHits)
        assertNull(cache.rect(0))
    }

    @Test
    fun `advance table evicts wholesale when over capacity`() {
        val cache = VerticalMeasureCache(advanceCapacity = 2)
        cache.advance('A'.code) { 1f }
        cache.advance('B'.code) { 2f }
        // 第三个码位触顶，整表失效后重算。
        cache.advance('C'.code) { 3f }

        assertEquals(3, cache.measureCalls)
        assertEquals(1, cache.cachedAdvanceCount)
    }
}
