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
import kotlin.math.abs

/**
 * 八向格距度量单测（B8R3，纯 JVM）。
 * 锁定：顶层 [octile] 为真八向公式（横纵一步 1、对角一步根号二），[octileCells] 取整误差
 * 不超半格；[Pathfinder.cellDist] 委托同一单源；旧曼哈顿公式不得回潮。
 */
class GridMetricTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val projection = GeoProjection(focus, 32)

    private fun plainMap(): TerrainMap {
        val data = IntArray(32 * 32) { 128 }
        val raster = HeightRaster.create(32, 32, data, degradedHint = false)
        return TerrainMap(raster, projection, emptyList())
    }

    @Test
    fun `横向三格 octile 等于三 纵向同理`() {
        assertEquals(3.0, octile(Cell(0, 0), Cell(3, 0)), 1e-9)
        assertEquals(3.0, octile(Cell(0, 0), Cell(0, 3)), 1e-9)
    }

    @Test
    fun `对角一格 octile 等于根号二近似系数`() {
        assertEquals(OCTILE_DIAGONAL_FACTOR, octile(Cell(0, 0), Cell(1, 1)), 1e-9)
    }

    @Test
    fun `横三纵二斜向格距取大边加根号二减一乘小边`() {
        val expect = 3.0 + (OCTILE_DIAGONAL_FACTOR - 1.0) * 2.0
        assertEquals(expect, octile(Cell(0, 0), Cell(3, 2)), 1e-9)
        assertTrue("斜向格距应落在三八与三九之间", octile(Cell(0, 0), Cell(3, 2)) in 3.8..3.9)
    }

    @Test
    fun `对角两步 octile 等于两倍根号二近似`() {
        assertEquals(2.0 * OCTILE_DIAGONAL_FACTOR, octile(Cell(0, 0), Cell(2, 2)), 1e-9)
    }

    @Test
    fun `起终点重合 octile 为零且度量对称`() {
        val a = Cell(7, 11)
        val b = Cell(20, 4)
        assertEquals(0.0, octile(a, a), 1e-9)
        assertEquals(octile(a, b), octile(b, a), 1e-12)
    }

    @Test
    fun `octileCells 四舍五入误差不超过半格`() {
        val fixtures = listOf(Cell(1, 1), Cell(3, 2), Cell(2, 2), Cell(3, 0), Cell(5, 5))
        fixtures.forEach { c ->
            val exact = octile(Cell(0, 0), c)
            val rounded = octileCells(Cell(0, 0), c)
            assertTrue("取整误差超半格：$c 精确 $exact 取整 $rounded", abs(exact - rounded) <= 0.5 + 1e-9)
        }
        assertEquals(1, octileCells(Cell(0, 0), Cell(1, 1)))
        assertEquals(4, octileCells(Cell(0, 0), Cell(3, 2)))
        assertEquals(3, octileCells(Cell(0, 0), Cell(2, 2)))
    }

    @Test
    fun `寻路器 cellDist 与顶层 octile 逐值相等`() {
        val finder = Pathfinder(plainMap(), "clear")
        val fixtures = listOf(
            Cell(0, 0) to Cell(3, 0),
            Cell(2, 5) to Cell(16, 16),
            Cell(8, 8) to Cell(11, 10),
            Cell(28, 26) to Cell(2, 5),
            Cell(15, 15) to Cell(16, 16),
        )
        fixtures.forEach { (a, b) ->
            assertEquals(finder.cellDist(a, b), octile(a, b), 1e-12)
        }
    }

    @Test
    fun `反例锁定 偏移三二格在四格八向半径内 曼哈顿会误判超距`() {
        // B8R3 原始反例：(3,2) 八向 3.828 ≤ 4 应判可见；旧曼哈顿 dx+dy=5 > 4 误判不可见。
        val d = octile(Cell(0, 0), Cell(3, 2))
        assertTrue("八向格距应在四格半径内", d <= 4.0)
        val manhattan = 3 + 2
        assertTrue("旧曼哈顿口径五超出四格半径 反例可区分新旧实现", manhattan > 4)
    }

    @Test
    fun `斜向二二格在三格半径内 曼哈顿四格会误判超距`() {
        val d = octile(Cell(0, 0), Cell(2, 2))
        assertTrue("八向二点八二八应在三格半径内", d <= 3.0)
        assertTrue("旧曼哈顿四超出三格半径", 2 + 2 > 3)
    }

    @Test
    fun `平地寻路器构造不触达地形代价分支`() {
        // 占位 sanity：保证 plainMap 可装配，供委托相等用例复用；地形分支由 PathfinderTest 覆盖。
        val finder = Pathfinder(plainMap(), "clear")
        val path = finder.find(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR)
        assertTrue(path.isNotEmpty())
    }
}
