package io.legado.app.ui.map.replay

import io.legado.app.ui.map.sandbox.DemTerrainElevation
import io.legado.app.ui.map.sandbox.TerrainElevation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U11 回放贴地高程单测（纯 JVM，fixture 手算网格）。
 *
 * 口径与沙盘 DEM 一致：0..255 灰度 ↔ 0..7000m，米制 × 0.009 得世界单位
 * （7000m ↔ 63 单位）；回放只透传该高度，不二次换算、不二次解码。
 * fixture 2×2：row0 北缘 [0, 255]，row1 南缘 [0, 255]；行 0 = 北缘。
 */
class ReplayTerrainTest {

    private val bbox = DemTerrainElevation.DEFAULT_BBOX

    /** 2×2 fixture：西北 / 西南 = 0，东北 / 东南 = 255。 */
    private fun twoByTwo(): DemTerrainElevation =
        DemTerrainElevation.create(2, 2, intArrayOf(0, 255, 0, 255), bbox)

    private fun campaign(armies: List<ArmyPiece>): Campaign = Campaign(
        id = "terrain-test", title = "贴地测试", era = "", context = "", rounds = 10,
        sides = listOf(
            BattleSide("a", "甲", SideRole.OWN),
            BattleSide("b", "乙", SideRole.ENEMY),
        ),
        armies = armies,
        phases = emptyList(),
        events = emptyList(),
        dailyLi = 50,
    )

    private fun piece(id: String, side: String, lat: Double, lng: Double) =
        ArmyPiece(id, id, side, lat, lng, 10, "elite", Morale.HIGH, Intent.ADVANCE)

    // ── 棋子基座贴地 ──

    @Test
    fun `棋子基座高度按 DEM 采样与高程一致`() {
        val dem = twoByTwo()
        // 东南角（24°N, 122°E）灰度 255 → 63.0 世界单位。
        val director = ReplayDirector(
            campaign(listOf(piece("p1", "a", 24.0, 122.0))),
            elevation = dem,
        )
        val visual = director.visuals().first()
        assertEquals(63.0, visual.groundHeight, 1e-9)
        assertEquals(dem.heightAt(24.0, 122.0), visual.groundHeight, 1e-9)
    }

    @Test
    fun `箭身起终节点贴地高度逐点对齐高程`() {
        val dem = twoByTwo()
        val arrowPiece = ArmyPiece(
            "ar1", "甲一", "a", 42.0, 104.0, 10, "elite",
            Morale.HIGH, Intent.ADVANCE, 24.0, 122.0,
        )
        val director = ReplayDirector(campaign(listOf(arrowPiece)), elevation = dem)
        val arrow = director.arrows().first()
        assertEquals(2, arrow.ground.size)
        // 起点西北角灰度 0；终点东南角灰度 255。
        assertEquals(0.0, arrow.ground[0].height, 1e-9)
        assertEquals(63.0, arrow.ground[1].height, 1e-9)
        assertEquals(dem.heightAt(42.0, 104.0), arrow.ground[0].height, 1e-9)
        assertEquals(dem.heightAt(24.0, 122.0), arrow.ground[1].height, 1e-9)
    }

    // ── 世界单位换算 ──

    @Test
    fun `高程沿用沙盘 0_009 换算回放不二次缩放`() {
        val dem = twoByTwo()
        val director = ReplayDirector(
            campaign(listOf(piece("p1", "a", 24.0, 122.0))),
            elevation = dem,
        )
        val height = director.visuals().first().groundHeight
        val expected = DemTerrainElevation.DEM_MAX_METERS *
            DemTerrainElevation.VERTICAL_EXAGGERATION
        // 255 灰度 → 7000m → ×0.009 = 63.0；回放透传，不乘第二套夸张系数。
        assertEquals(7000.0, DemTerrainElevation.DEM_MAX_METERS, 1e-9)
        assertEquals(expected, height, 1e-9)
        assertEquals(63.0, height, 1e-9)
    }

    // ── Flat 兜底 ──

    @Test
    fun `Flat 兜底全部高度 0_0 且降级标志可见`() {
        val director = ReplayDirector(campaign(listOf(piece("p1", "a", 35.0, 112.0))))
        assertTrue(director.terrainDegraded)
        director.visuals().forEach {
            assertEquals(0.0, it.groundHeight, 1e-9)
            assertTrue(it.groundHeight.isFinite())
        }
        director.arrows().forEach { arrow ->
            arrow.ground.forEach { assertEquals(0.0, it.height, 1e-9) }
        }
    }

    @Test
    fun `注入 Flat 实例与默认构造均判为降级`() {
        val flat = ReplayDirector(campaign(listOf(piece("p1", "a", 35.0, 112.0))))
        assertTrue(flat.terrainDegraded)
        val explicit = ReplayDirector(
            campaign(listOf(piece("p2", "a", 35.0, 112.0))),
            elevation = TerrainElevation.Flat,
        )
        assertTrue(explicit.terrainDegraded)
    }

    // ── 越界钳制 / 非法坐标 ──

    @Test
    fun `越界经纬度钳到边缘像素不抛异常`() {
        val dem = twoByTwo()
        // 北缘以西 → 钳西北角灰度 0；南缘以东 → 钳东南角灰度 255。
        val director = ReplayDirector(
            campaign(listOf(
                piece("nw", "a", 50.0, 100.0),
                piece("se", "b", 10.0, 130.0),
            )),
            elevation = dem,
        )
        val visuals = director.visuals().associate { it.piece.id to it.groundHeight }
        assertEquals(0.0, visuals["nw"]!!, 1e-9)
        assertEquals(63.0, visuals["se"]!!, 1e-9)
        assertTrue(visuals.values.all { it.isFinite() })
    }

    @Test
    fun `NaN 坐标兜底 0 不产出 NaN`() {
        val dem = twoByTwo()
        val nanPiece = ArmyPiece(
            "nan", "溃兵", "a", Double.NaN, Double.NaN, 5, "militia",
            Morale.STEADY, Intent.ADVANCE,
        )
        val director = ReplayDirector(campaign(listOf(nanPiece)), elevation = dem)
        val height = director.visuals().first().groundHeight
        assertEquals(0.0, height, 1e-9)
        assertFalse(height.isNaN())
    }

    // ── 势力 / 战线多边形 ──

    @Test
    fun `多边形逐顶点贴地采样三档不透明度对齐 E 项`() {
        val dem = twoByTwo()
        val pts = listOf(LatLng(24.0, 122.0), LatLng(42.0, 104.0), LatLng(33.0, 113.0))
        val own = ReplayFrontSector("f-own", "秦", SideRole.OWN, pts)
        val enemy = ReplayFrontSector("f-enemy", "赵", SideRole.ENEMY, pts)
        val ally = ReplayFrontSector("f-ally", "齐", SideRole.ALLY, pts)

        assertEquals(ReplayConfig.OWN_FILL_ALPHA, own.fillAlpha(), 1e-6f)
        assertEquals(ReplayConfig.ENEMY_FILL_ALPHA, enemy.fillAlpha(), 1e-6f)
        assertEquals(ReplayConfig.ALLY_FILL_ALPHA, ally.fillAlpha(), 1e-6f)
        assertEquals(0.12f, own.fillAlpha(), 1e-6f)
        assertEquals(0.10f, enemy.fillAlpha(), 1e-6f)
        assertEquals(0.08f, ally.fillAlpha(), 1e-6f)

        val surface = own.sampleSurface(dem)
        assertEquals(3, surface.size)
        assertEquals(63.0, surface[0].height, 1e-9)
        assertEquals(0.0, surface[1].height, 1e-9)
        assertEquals(dem.heightAt(33.0, 113.0), surface[2].height, 1e-9)
    }

    @Test
    fun `多边形顶点不足 3 个采样返回空序列`() {
        val sector = ReplayFrontSector("f2", "残部", SideRole.OWN,
            listOf(LatLng(35.0, 112.0), LatLng(35.1, 112.1)))
        assertTrue(sector.sampleSurface(twoByTwo()).isEmpty())
    }

    @Test
    fun `导演层多边形采样结果与注入高程一致`() {
        val dem = twoByTwo()
        val sector = ReplayFrontSector(
            "f3", "秦", SideRole.OWN,
            listOf(LatLng(24.0, 122.0), LatLng(42.0, 104.0), LatLng(33.0, 113.0)),
        )
        val withSectors = campaign(emptyList()).copy(sectors = listOf(sector))
        val director = ReplayDirector(withSectors, elevation = dem)
        val (_, surface) = director.sectorSurface().first()
        assertEquals(63.0, surface.first().height, 1e-9)
        assertEquals(0.0, surface[1].height, 1e-9)
    }

    // ── 冲突特效落点 ──

    @Test
    fun `交战中点与遭遇光环落点贴地采样`() {
        val dem = twoByTwo()
        val armies = listOf(
            ArmyPiece("a1", "甲一", "a", 35.000, 112.000, 10, "elite",
                Morale.HIGH, Intent.ADVANCE, 35.01, 112.01),
            ArmyPiece("b1", "乙一", "b", 35.005, 112.005, 8, "regular",
                Morale.HIGH, Intent.ADVANCE, 35.00, 112.00),
        )
        val director = ReplayDirector(campaign(armies), elevation = dem)
        director.update(0)

        val clash = director.clashes.first()
        val mid = LatLng((35.000 + 35.005) / 2, (112.000 + 112.005) / 2)
        assertEquals(dem.heightAt(mid.lat, mid.lng), clash.groundHeight, 1e-9)
        assertTrue(clash.groundHeight.isFinite())
    }

    @Test
    fun `退化 DEM 实例高度恒 0 且标记降级`() {
        // 显式标注 null 类型：create 同时有 IntArray? / ByteArray? 两个重载，裸 null 会触发重载歧义。
        val emptyData: IntArray? = null
        val degraded = DemTerrainElevation.create(0, 0, emptyData, bbox)
        val director = ReplayDirector(
            campaign(listOf(piece("p1", "a", 35.0, 112.0))),
            elevation = degraded,
        )
        assertTrue(director.terrainDegraded)
        assertEquals(0.0, director.visuals().first().groundHeight, 1e-9)
    }
}
