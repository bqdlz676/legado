package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.visionRadiusCells
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
        val radius = ArmyType.REGULAR.visionRadiusCells
        val route = finder.flankRoute(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR, listOf(enemy), radius)
        assertTrue(route.path.isNotEmpty())
        assertTrue("视野可绕开时不应退化：${route.reason}", !route.degraded)
        assertFalse("严格避视野通路不含视线穿越事实", route.crossedSight)
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
        assertTrue("全图视野封锁的加价路径实际穿越视野环", route.crossedSight)
        assertTrue(enemy !in route.path)
    }

    @Test
    fun `无敌军时侧翼路径不退化`() {
        val finder = Pathfinder(plainMap(), "clear")
        // 显式标注 List<Cell>：与逐敌条目重载 List<EnemySight> 区分，避免空列表重载歧义（B8R2）。
        val route = finder.flankRoute(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR, emptyList<Cell>())
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

    @Test
    fun `混编兵种逐敌闸门骑兵六格环内不入路且民兵三格外可通行`() {
        // B8R2：高视野骑兵（半径 6）与低视野民兵（半径 3）混编，闸门必须各按自身半径，
        // 不能统一套本方兵种半径给骑兵留暴露环、也不能按骑兵半径对民兵过度绕行。
        val cavalry = Cell(9, 23)
        val militia = Cell(23, 9)
        val finder = Pathfinder(plainMap(), "clear")
        val route = finder.flankRoute(
            Cell(2, 5),
            Cell(28, 26),
            ArmyType.REGULAR,
            listOf(EnemySight(cavalry, 6), EnemySight(militia, 3)),
        )
        assertTrue("混编侧翼路径必须非空", route.path.isNotEmpty())
        assertTrue("路径不得踏上骑兵所在格", cavalry !in route.path)
        assertTrue("路径不得踏上民兵所在格", militia !in route.path)
        route.path.forEach { cell ->
            // 骑兵 6 格视野环：路径一格都不得进入。
            assertTrue(
                "路径格 $cell 闯入骑兵视野环（格距 ${finder.cellDist(cell, cavalry)} ≤ 6）",
                finder.cellDist(cell, cavalry) > 6,
            )
            assertFalse(finder.watchedBy(cavalry, cell, 6))
            // 民兵 3 格视野环：逐敌按其自身半径 3 闸门。
            assertTrue(
                "路径格 $cell 闯入民兵视野环（格距 ${finder.cellDist(cell, militia)} ≤ 3）",
                finder.cellDist(cell, militia) > 3,
            )
            assertFalse(finder.watchedBy(militia, cell, 3))
        }
        // 民兵半径小：路径可走到其 3 格环之外的开阔地，证明没有被按骑兵 6 格过度挤离走廊。
        assertTrue("路径应行经民兵 3 格环之外的格", route.path.any { finder.cellDist(it, militia) > 3 })
    }

    @Test
    fun `混编全图封锁时退化路径仍返回非空且不抛异常`() {
        // B8R2 退化兜底：骑兵半径 30 覆盖全图 + 民兵半径 3，严格无路后走加价 / 折线兜底；
        // 三级退化链条对逐敌条目同样成立，不得抛异常、不得返回空路径。
        val cavalry = Cell(15, 15)
        val militia = Cell(20, 20)
        val finder = Pathfinder(plainMap(), "clear")
        val route = finder.flankRoute(
            Cell(2, 5),
            Cell(28, 26),
            ArmyType.REGULAR,
            listOf(EnemySight(cavalry, 30), EnemySight(militia, 3)),
        )
        assertTrue("退化路径也必须非空", route.path.isNotEmpty())
        assertTrue("全图封锁应置降级标志", route.degraded)
        assertTrue("骑兵半径 30 覆盖全图，兜底路径仍穿越视野环", route.crossedSight)
        assertTrue(cavalry !in route.path)
        assertTrue(militia !in route.path)
    }

    @Test
    fun `目标格身处敌军视野环内 严格模式仍可落点不被剪枝`() {
        // B8R5：目标格与起点同享视野剪枝豁免。敌军贴邻目标格驻守（半径 1），目标格落在其
        // 视野环内；严格 A* 仍应能落到目标格，不得把终点本身剪掉而误判降级绕行。
        val enemy = Cell(28, 25)
        val finder = Pathfinder(plainMap(), "clear")
        val route = finder.flankRoute(
            Cell(2, 5),
            Cell(28, 26),
            ArmyType.REGULAR,
            listOf(EnemySight(enemy, 1)),
        )
        assertTrue("豁免目标格后侧翼路径必须非空", route.path.isNotEmpty())
        assertEquals("终点必须落在目标格", Cell(28, 26), route.path.last())
        assertFalse("目标可达时不应误置降级标志：${route.reason}", route.degraded)
        assertTrue("路径不得踏上敌军所在格", enemy !in route.path)
    }

    @Test
    fun `地形双重封锁时第三级直线折线兜底出线且理由标明折线`() {
        // B8R5：厚水带横断全图且无渡口，严格 A* 与加价 A* 均无路，落到第三级 straightPath
        // 折线兜底；专门锁住该兜底路径真的能出线（首格为出发格、逐格可通行、理由标明折线），
        // 而不是只断言非空。
        val map = blockedBandMap()
        val finder = Pathfinder(map, "clear")
        val route = finder.flankRoute(
            Cell(2, 5),
            Cell(2, 26),
            ArmyType.REGULAR,
            emptyList<EnemySight>(),
        )
        assertTrue("第三级折线兜底也必须非空", route.path.isNotEmpty())
        assertTrue("双封锁应置降级标志", route.degraded)
        assertFalse("无敌军参与的纯地形折线兜底不构成视线穿越事实", route.crossedSight)
        assertTrue("降级理由应标明直线折线兜底", route.reason.contains("直线折线"))
        assertEquals("折线兜底首格为出发格", Cell(2, 5), route.path.first())
        route.path.forEach { cell ->
            assertTrue("折线路径格应可通行：$cell", map.passable(cell, ArmyType.REGULAR))
            assertTrue(
                "折线路径格不得是非渡口水面：$cell",
                !(map.isWater(cell) && !map.isFord(cell)),
            )
        }
    }
}
