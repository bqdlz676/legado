package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 寻路器单测（纯 JVM）。
 * 覆盖：路径非空且起点正确、路径全程可通行、深水带绕行兜底、侧翼路径（避敌军格 / 避视野半径 /
 * 退化标志 / 纯函数判定）、不可通行落点钳制。
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
    fun `侧翼路径绕开敌军视线且非空，不踏上敌军所在格`() {
        val enemy = Cell(15, 15)
        val finder = Pathfinder(plainMap(), "clear")
        val flank = finder.flankPath(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR, listOf(enemy))
        assertTrue(flank.isNotEmpty())
        assertTrue("侧翼路径不得踏上敌军所在格", enemy !in flank)
    }

    @Test
    fun `侧翼路径全程不进入敌军视野半径且未退化`() {
        val enemy = Cell(15, 15)
        val finder = Pathfinder(plainMap(), "clear")
        val radius = Pathfinder.radiusFor(ArmyType.REGULAR)
        val route = finder.flankRoute(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR, listOf(enemy), radius)
        assertTrue(route.path.isNotEmpty())
        assertTrue("视野可绕开时不应退化：${route.reason}", !route.degraded)
        assertTrue(enemy !in route.path)
        route.path.forEach { cell ->
            assertTrue(
                "路径格 $cell 落入敌军视野半径（格距 ${finder.cellDist(cell, enemy)} ≤ $radius）",
                finder.cellDist(cell, enemy) > radius,
            )
        }
    }

    @Test
    fun `watchedBy 半径内通视为真、半径外为假`() {
        val finder = Pathfinder(plainMap(), "clear")
        val enemy = Cell(15, 15)
        assertTrue(finder.watchedBy(enemy, Cell(16, 16), 4))
        assertFalse(finder.watchedBy(enemy, Cell(0, 0), 4))
    }

    @Test
    fun `全图视野封锁时退化为代价最小路径并置降级标志`() {
        val enemy = Cell(15, 15)
        val finder = Pathfinder(plainMap(), "clear")
        val route = finder.flankRoute(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR, listOf(enemy), visionRadiusCells = 30)
        assertTrue("退化路径也必须非空", route.path.isNotEmpty())
        assertTrue("严格无路时应置降级标志", route.degraded)
        assertTrue(enemy !in route.path)
    }

    @Test
    fun `无敌军时侧翼路径不退化`() {
        val finder = Pathfinder(plainMap(), "clear")
        val route = finder.flankRoute(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR, emptyList())
        assertTrue(route.path.isNotEmpty())
        assertFalse(route.degraded)
    }

    @Test
    fun `敌军在终点时不崩溃且不踏上敌军格`() {
        val enemy = Cell(28, 26)
        val finder = Pathfinder(plainMap(), "clear")
        val route = finder.flankRoute(Cell(2, 5), enemy, ArmyType.REGULAR, listOf(enemy))
        assertTrue(route.path.isNotEmpty())
        assertTrue(enemy !in route.path)
    }

    @Test
    fun `敌军在起点时不崩溃且路径非空`() {
        val enemy = Cell(2, 5)
        val finder = Pathfinder(plainMap(), "clear")
        val route = finder.flankRoute(enemy, Cell(28, 26), ArmyType.REGULAR, listOf(enemy))
        assertTrue(route.path.isNotEmpty())
    }

    @Test
    fun `深水封锁目标时侧翼兜底路径非空`() {
        val enemy = Cell(15, 15)
        val finder = Pathfinder(blockedBandMap(), "clear")
        val route = finder.flankRoute(Cell(2, 5), Cell(16, 16), ArmyType.REGULAR, listOf(enemy))
        assertTrue(route.path.isNotEmpty())
    }

    @Test
    fun `起终点在栅格边缘时侧翼路径非空`() {
        val enemy = Cell(15, 15)
        val finder = Pathfinder(plainMap(), "clear")
        val route = finder.flankRoute(Cell(0, 0), Cell(31, 31), ArmyType.REGULAR, listOf(enemy))
        assertTrue(route.path.isNotEmpty())
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
