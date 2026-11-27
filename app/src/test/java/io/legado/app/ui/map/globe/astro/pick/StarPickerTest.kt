package io.legado.app.ui.map.globe.astro.pick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StarPicker 网格分桶拾取纯逻辑验收（§九.3）：24dp 网格、20dp 候选半径、重叠取最亮、
 * 并列取索引靠前；空表返回 -1。密度 1f 时格宽 / 半径均以 px 口径断言。
 */
class StarPickerTest {

    private fun picker(x: FloatArray, y: FloatArray, mag: FloatArray, id: LongArray, n: Int): StarPicker {
        val p = StarPicker(1f)
        p.rebuild(x, y, mag, id, n)
        return p
    }

    @Test
    fun `empty table returns minus one`() {
        val p = picker(FloatArray(0), FloatArray(0), FloatArray(0), LongArray(0), 0)
        assertEquals(0, p.size())
        assertEquals(-1, p.pick(100f, 100f))
        assertEquals(-1L, p.pickId(100f, 100f))
    }

    @Test
    fun `overlapping candidates pick brightest`() {
        val x = floatArrayOf(100f, 105f)
        val y = floatArrayOf(100f, 100f)
        val mag = floatArrayOf(3.0f, 1.0f) // 后者更亮（星等更小）
        val id = longArrayOf(11L, 22L)
        val p = picker(x, y, mag, id, 2)
        assertEquals(2, p.size())
        assertEquals(22L, p.pickId(102f, 100f))
    }

    @Test
    fun `radius boundary inclusive at twenty dp`() {
        val x = floatArrayOf(100f)
        val y = floatArrayOf(100f)
        val mag = floatArrayOf(2.0f)
        val id = longArrayOf(7L)
        val p = picker(x, y, mag, id, 1)
        assertEquals(7L, p.pickId(120f, 100f)) // 距离恰 20px：边界内
        assertEquals(-1L, p.pickId(121f, 100f)) // 超界：无命中
    }

    @Test
    fun `equal magnitude tie breaks by earlier index`() {
        val x = floatArrayOf(100f, 103f)
        val y = floatArrayOf(100f, 100f)
        val mag = floatArrayOf(2.0f, 2.0f)
        val id = longArrayOf(31L, 42L)
        val p = picker(x, y, mag, id, 2)
        assertEquals(0, p.pick(101f, 100f)) // 并列取索引靠前者
        assertTrue(p.pick(101f, 100f) >= 0)
    }
}
