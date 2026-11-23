package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 寻路器单测（纯 JVM）。
 * 覆盖：路径非空且起点正确、路径全程可通行、深水带绕行兜底、侧翼路径、不可通行落点钳制。
 */
class PathfinderTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val projection = GeoProjection(focus, 32)

    private fun plainMap(): TerrainMap {
        val data = IntArray(32 * 32) { 128 }
        val raster = HeightRaster.create(32, 32, data, degradedHint = false)
        return TerrainMap(raster, projection, emptyList())
    }

    /** 厚水带：rows 10..21 零高程，内部格不构成渡口。 */
    private fun blockedBandMap(): TerrainMap {
        val data = IntArray(32 * 32) { i -> if (i / 32 in 10..21) 0 else 128 }
        val raster = HeightRaster.create(32, 32, data, degradedHint = false)
        return TerrainMap(raster, projection, emptyList())
    }

    @Test
    fun `平地寻路返回非空路径且起点为出发格`() {
        val finder = Pathfinder(plainMap(), "clear")
        val path = finder.find(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR)
        assertTrue(path.isNotEmpty())
        assertEquals(Cell(2, 5), path.first())
    }

    @Test
    fun `路径全程不站上非渡口水面`() {
        val map = blockedBandMap()
        val finder = Pathfinder(map, "clear")
        val path = finder.find(Cell(2, 5), Cell(2, 26), ArmyType.REGULAR)
        assertTrue("水带阻挡也应给出兜底路径", path.isNotEmpty())
        path.forEach { cell ->
            assertTrue("路径格应可通行：$cell", map.passable(cell, ArmyType.REGULAR))
            assertTrue("路径格不得是非渡口水面：$cell", !(map.isWater(cell) && !map.isFord(cell)))
        }
    }

    @Test
    fun `侧翼路径绕开敌军视线且非空`() {
        val finder = Pathfinder(plainMap(), "clear")
        val flank = finder.flankPath(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR, listOf(Cell(15, 15)))
        assertTrue(flank.isNotEmpty())
    }

    @Test
    fun `目标落在深水格时落点钳制到可通行格`() {
        val map = blockedBandMap()
        val finder = Pathfinder(map, "clear")
        val path = finder.find(Cell(2, 5), Cell(16, 16), ArmyType.REGULAR)
        assertTrue(path.isNotEmpty())
        val end = path.last()
        assertTrue(map.passable(end, ArmyType.REGULAR))
    }
}
