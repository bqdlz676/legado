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
 *
 * 口径（B2 锁定，手算依据逐条写在用例注释里）：
 *  - 行军速度 = 基础速度 × 天候折减(≤1) × 路况速度系数(=1/路况代价系数) × 疲劳惩罚；
 *  - 天候折减：晴朗 / 缺省 1.00，雾 0.90，风 0.85，雨 / 泥 0.75，雪 0.60（坏天气只拖慢、绝不加速）；
 *  - 寻路代价侧取天候倒数：坏天气路径更贵。
 */
class TerrainCostTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val projection = GeoProjection(focus, 32)

    @Test
    fun `路况系数 平地 小于 丘陵 小于 山地 小于 非涉渡水面`() {
        // 手算（寻路代价系数，越高越难走；与速度侧互为倒数口径）：
        // PLAIN 1.00，HILL 1.40，MOUNTAIN 2.00，SNOW 2.50，WATER 涉渡 3.00 / 非涉渡 99.00。
        assertEquals(1.0, TerrainCost.terrainFactor(TerrainKind.PLAIN, false), 1e-9)
        assertEquals(1.4, TerrainCost.terrainFactor(TerrainKind.HILL, false), 1e-9)
        assertEquals(2.0, TerrainCost.terrainFactor(TerrainKind.MOUNTAIN, false), 1e-9)
        assertEquals(2.5, TerrainCost.terrainFactor(TerrainKind.SNOW, false), 1e-9)
        assertEquals(3.0, TerrainCost.terrainFactor(TerrainKind.WATER, true), 1e-9)
        assertEquals(99.0, TerrainCost.terrainFactor(TerrainKind.WATER, false), 1e-9)

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
        // 手算：slopePenalty(s) = (1 + s/45).coerceIn(1, 2)。
        // 10° → 1 + 10/45 ≈ 1.2222222；40° → 1 + 40/45 ≈ 1.8888889；80° 超顶 → 2.000。
        val gentle = TerrainCost.slopePenalty(10.0)
        val steep = TerrainCost.slopePenalty(40.0)
        val capped = TerrainCost.slopePenalty(80.0)
        assertEquals(1.0 + 10.0 / TerrainCost.SLOPE_FULL_DEG, gentle, 1e-9)
        assertEquals(1.0 + 40.0 / TerrainCost.SLOPE_FULL_DEG, steep, 1e-9)
        assertTrue(gentle < steep)
        assertEquals(2.0, capped, 1e-6)
    }

    @Test
    fun `疲劳越高行军速度越低，骑兵基础速度快于民兵`() {
        // 手算：疲劳惩罚 = 1 / (1 + fatigue × 0.12)；fatigue 0 → 1.000，5 → 1 / 1.6 = 0.625。
        // 骑兵基础 1.40、民兵基础 0.80；平地、晴天、路况系数 1.0。
        // restedCavalry = 1.40 × 1.00 × 1.00 × 1.000 = 1.400
        // tiredCavalry  = 1.40 × 1.00 × 1.00 × 0.625 = 0.875
        // militia       = 0.80 × 1.00 × 1.00 × 1.000 = 0.800
        val restedCavalry = TerrainCost.marchSpeed(ArmyType.CAVALRY, 0.0, "clear", TerrainKind.PLAIN)
        val tiredCavalry = TerrainCost.marchSpeed(ArmyType.CAVALRY, 5.0, "clear", TerrainKind.PLAIN)
        val militia = TerrainCost.marchSpeed(ArmyType.MILITIA, 0.0, "clear", TerrainKind.PLAIN)
        assertEquals(1.40, restedCavalry, 1e-9)
        assertEquals(0.875, tiredCavalry, 1e-9)
        assertEquals(0.80, militia, 1e-9)
        assertTrue(restedCavalry > tiredCavalry)
        assertTrue(restedCavalry > militia)
    }

    @Test
    fun `雨天候拖慢行军`() {
        // 手算（REGULAR 基础 1.00，平地路况系数 1.00，疲劳 0 惩罚 1.00）：
        // clear：1.00 × 1.00(晴) × 1.00 × 1.00 = 1.000
        // rain ：1.00 × 0.75(雨) × 1.00 × 1.00 = 0.750
        // 坏天气折减 ≤1，故雨天速度严格小于晴天；方向不得写反（旧实现 rain=1.30 反而提速，已回归）。
        val clear = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "clear", TerrainKind.PLAIN)
        val rain = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "rain", TerrainKind.PLAIN)
        assertEquals(1.00, clear, 1e-9)
        assertEquals(0.75, rain, 1e-9)
        assertTrue(clear > rain)
    }

    @Test
    fun `F1 路况进入行军速度预算：山地慢于平地，雪地更慢`() {
        // 手算：路况速度系数 = 1 / 路况代价系数（晴天、REGULAR、疲劳 0）。
        // PLAIN    ：1.00 × 1.00 × 1/1.0 = 1.000
        // HILL     ：1.00 × 1.00 × 1/1.4 ≈ 0.7142857
        // MOUNTAIN ：1.00 × 1.00 × 1/2.0 = 0.500
        // SNOW     ：1.00 × 1.00 × 1/2.5 = 0.400
        // 路况系数必须进速度预算；若漏乘，四类地形速度会全部等于基础值，本用例即红。
        val plain = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "clear", TerrainKind.PLAIN)
        val hill = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "clear", TerrainKind.HILL)
        val mountain = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "clear", TerrainKind.MOUNTAIN)
        val snow = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "clear", TerrainKind.SNOW)
        assertEquals(1.000, plain, 1e-9)
        assertEquals(1.0 / 1.4, hill, 1e-9)
        assertEquals(0.500, mountain, 1e-9)
        assertEquals(0.400, snow, 1e-9)
        assertTrue(plain > hill)
        assertTrue(hill > mountain)
        assertTrue(mountain > snow)
    }

    @Test
    fun `F1 坏天气速度折减不超过 1，雪慢于雨慢于晴`() {
        // 手算（REGULAR 平地、疲劳 0）：
        // clear 1.000，fog 0.900，wind 0.850，rain 0.750，snow 0.600。
        // 单调口径：clear ≥ fog ≥ wind ≥ rain > snow；任何天气折减都 ≤1，不允许 >1 加速。
        val clear = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "clear", TerrainKind.PLAIN)
        val fog = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "fog", TerrainKind.PLAIN)
        val wind = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "wind", TerrainKind.PLAIN)
        val rain = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "rain", TerrainKind.PLAIN)
        val snow = TerrainCost.marchSpeed(ArmyType.REGULAR, 0.0, "snow", TerrainKind.PLAIN)
        assertEquals(1.00, clear, 1e-9)
        assertEquals(0.90, fog, 1e-9)
        assertEquals(0.85, wind, 1e-9)
        assertEquals(0.75, rain, 1e-9)
        assertEquals(0.60, snow, 1e-9)
        assertTrue(clear >= fog)
        assertTrue(fog >= wind)
        assertTrue(wind >= rain)
        assertTrue(rain > snow)
        assertTrue(rain <= clear)
        assertTrue(TerrainCost.weatherModifier("clear") <= 1.0)
        assertTrue(TerrainCost.weatherModifier("fog") <= 1.0)
        assertTrue(TerrainCost.weatherModifier("wind") <= 1.0)
        assertTrue(TerrainCost.weatherModifier("rain") <= 1.0)
        assertTrue(TerrainCost.weatherModifier("snow") <= 1.0)
    }

    @Test
    fun `天候别名与缺省口径：mud mist windy snowy 同值，null 与未知归基准`() {
        // 手算：别名归一到同一折减；缺省 / 未知天气按基准 1.00，不惩罚也不加成。
        assertEquals(0.75, TerrainCost.weatherModifier("rain"), 1e-9)
        assertEquals(0.75, TerrainCost.weatherModifier("rainy"), 1e-9)
        assertEquals(0.75, TerrainCost.weatherModifier("mud"), 1e-9)
        assertEquals(0.90, TerrainCost.weatherModifier("fog"), 1e-9)
        assertEquals(0.90, TerrainCost.weatherModifier("foggy"), 1e-9)
        assertEquals(0.90, TerrainCost.weatherModifier("mist"), 1e-9)
        assertEquals(0.85, TerrainCost.weatherModifier("wind"), 1e-9)
        assertEquals(0.85, TerrainCost.weatherModifier("windy"), 1e-9)
        assertEquals(0.60, TerrainCost.weatherModifier("snow"), 1e-9)
        assertEquals(0.60, TerrainCost.weatherModifier("snowy"), 1e-9)
        assertEquals(1.0, TerrainCost.weatherModifier(null), 1e-9)
        assertEquals(1.0, TerrainCost.weatherModifier("clear"), 1e-9)
        assertEquals(1.0, TerrainCost.weatherModifier("  OverCast "), 1e-9)
    }

    @Test
    fun `寻路代价侧坏天气更贵：晴 小于 雨 小于 雪（速度折减取倒数）`() {
        // 手算：enterCost = 路况 × 坡度 / 天候折减。同一格 base 与坡度相同，代价 ∝ 1/折减。
        // clear：base × 1.0 / 1.00 = base；rain：base / 0.75 ≈ base × 1.3333；snow：base / 0.60 = base × 1.6667。
        // 用比值断言可把 base / 坡度约掉，不依赖该格具体地形类别。
        val data = IntArray(32 * 32) { 128 }
        val flatRaster = HeightRaster.create(32, 32, data, degradedHint = false)
        val flatMap = TerrainMap(flatRaster, projection, emptyList())
        val cell = Cell(5, 5)
        val clearCost = TerrainCost.enterCost(cell, ArmyType.REGULAR, flatMap, "clear")
        val rainCost = TerrainCost.enterCost(cell, ArmyType.REGULAR, flatMap, "rain")
        val snowCost = TerrainCost.enterCost(cell, ArmyType.REGULAR, flatMap, "snow")
        assertTrue("前置：该平地格应可通行", flatMap.passable(cell, ArmyType.REGULAR))
        assertTrue(clearCost < rainCost)
        assertTrue(rainCost < snowCost)
        assertEquals(1.0 / 0.75, rainCost / clearCost, 1e-9)
        assertEquals(1.0 / 0.60, snowCost / clearCost, 1e-9)
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
