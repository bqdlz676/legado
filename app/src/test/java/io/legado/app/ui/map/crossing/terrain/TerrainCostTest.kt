package io.legado.app.ui.map.crossing.terrain

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.model.TerrainKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行军代价表单测（纯 JVM）。
 * 覆盖：路况系数次序、坡度惩罚封顶、天候 / 疲劳折减、非涉渡水面堵死。
 */
class TerrainCostTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val raster = HeightRaster.synthetic(seed = 1L, gridN = 32, degraded = true)
    private val projection = GeoProjection(focus, 32)
    private val map = TerrainMap(raster, projection, emptyList())

    @Test
    fun `路况系数 平地 小于 丘陵 小于 山地 小于 非涉渡水面`() {
        assertTrue(TerrainCost.terrainFactor(TerrainKind.PLAIN, false) <
            TerrainCost.terrainFactor(TerrainKind.HILL, false))
        assertTrue(TerrainCost.terrainFactor(TerrainKind.HILL, false) <
            TerrainCost.terrainFactor(TerrainKind.MOUNTAIN, false))
        assertTrue(TerrainCost.terrainFactor(TerrainKind.MOUNTAIN, false) <
            TerrainCost.terrainFactor(TerrainKind.WATER, false))
        // 涉渡水面代价高于平地，但不堵死。
        assertTrue(TerrainCost.terrainFactor(TerrainKind.WATER, true) <
            TerrainCost.terrainFactor(TerrainKind.WATER, false))
    }

    @Test
    fun `坡度惩罚随坡度上升并在 45 度封顶两倍`() {
        val gentle = TerrainCost.slopePenalty(10.0)
        val steep = TerrainCost.slopePenalty(40.0)
        val capped = TerrainCost.slopePenalty(80.0)
        assertTrue(gentle < steep)
        assertEquals(2.0, capped, 1e-6)
    }

    @Test
    fun `疲劳越高行军速度越低，骑兵基础速度快于民兵`() {
        val restedCavalry = TerrainCost.marchSpeed(ArmyType.CAVALRY, 0.0, "clear")
        val tiredCavalry = TerrainCost.marchSpeed(ArmyType.CAVALRY, 5.0, "clear")
        val militia = TerrainCost.marchSpeed(ArmyType.MILITIA, 0.0, "clear")
        assertTrue(restedCavalry > tiredCavalry)
        assertTrue(restedCavalry > militia)
    }

    @Test
    fun `雨天候拖慢行军`() {
        val clear = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "clear")
        val rain = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "rain")
        assertTrue(clear > rain)
    }

    @Test
    fun `非涉渡水面进入代价为堵死上限`() {
        // 构造一条横贯水带（零高程），取水带内部格；边缘格可能判为渡口，内部格不可越。
        val data = IntArray(32 * 32) { i ->
            val row = i / 32
            if (row in 14..17) 0 else 128
        }
        val waterRaster = HeightRaster.create(32, 32, data, degradedHint = false)
        val waterMap = TerrainMap(waterRaster, projection, emptyList())
        val interior = Cell(5, 15)
        assertTrue("前置：该格应是水面", waterMap.isWater(interior))
        assertTrue("前置：内部水带格不应判为渡口", !waterMap.isFord(interior))
        val cost = TerrainCost.enterCost(interior, ArmyType.REGULAR, waterMap, "clear")
        assertTrue("非涉渡水面代价应堵死，实际 $cost", cost >= TerrainCost.BLOCKED_COST)
    }
}
