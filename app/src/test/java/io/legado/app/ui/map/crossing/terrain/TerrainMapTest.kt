package io.legado.app.ui.map.crossing.terrain

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.RiverDef
import io.legado.app.ui.map.crossing.model.TerrainFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地形分析层单测（纯 JVM）。
 * 覆盖：空栅格兜底、河流栅格化、非渡口水面不可越、通视性、高程口径 0..255 ↔ 0..7000m。
 */
class TerrainMapTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)

    private fun buildMap(raster: HeightRaster = HeightRaster.synthetic(1L, 32, true)): TerrainMap =
        TerrainMap(raster, GeoProjection(focus, 32), emptyList())

    @Test
    fun `空尺寸空数据栅格走合成兜底且标记降级`() {
        val fallback = HeightRaster.create(0, 0, null, degradedHint = false)
        assertTrue(fallback.degraded)
        assertTrue(fallback.width > 0 && fallback.height > 0)
        assertEquals(fallback.size, fallback.width * fallback.height)
    }

    @Test
    fun `高程像素 0 与 255 对齐 0 到 7000 米口径`() {
        val data = IntArray(32 * 32) { 128 }
        val raster = HeightRaster.create(32, 32, data, degradedHint = false)
        assertEquals(0.0, HeightRaster.metersFor(0), 1e-6)
        assertEquals(7000.0, HeightRaster.metersFor(255), 1e-6)
        assertTrue(raster.metersAt(0, 0) in 0.0..7000.0)
    }

    @Test
    fun `河流折线被栅格化为河面单元`() {
        val rivers = listOf(
            RiverDef(listOf(Geo(lat = 0.1, lng = 0.1), Geo(lat = 0.9, lng = 0.9))),
        )
        val map = TerrainMap(HeightRaster.synthetic(2L, 32, true), GeoProjection(focus, 32), rivers)
        assertTrue("河流应栅格化出河面单元", map.riverCells().isNotEmpty())
    }

    @Test
    fun `非渡口水面任何兵种都不可通行`() {
        val data = IntArray(32 * 32) { i -> if (i / 32 in 14..17) 0 else 128 }
        val map = buildMap(HeightRaster.create(32, 32, data, degradedHint = false))
        val water = Cell(5, 15)
        assertTrue(map.isWater(water))
        assertFalse(map.isFord(water))
        assertFalse(map.passable(water, ArmyType.REGULAR))
        assertFalse(map.passable(water, ArmyType.CAVALRY))
        assertFalse(map.passable(water, ArmyType.MILITIA))
    }

    @Test
    fun `通视性同格为真且不抛异常`() {
        val map = buildMap()
        val a = Cell(8, 8)
        assertTrue(map.visible(a, a))
        // 任意两点通视查询不得抛异常（边界 / 遮挡兜底）。
        assertTrue(map.visible(Cell(0, 0), Cell(31, 31)).let { true })
    }

    @Test
    fun `越界单元按不可通行处理`() {
        val map = buildMap()
        assertFalse(map.passable(Cell(-1, 0), ArmyType.REGULAR))
        assertFalse(map.passable(Cell(32, 0), ArmyType.REGULAR))
    }
}
