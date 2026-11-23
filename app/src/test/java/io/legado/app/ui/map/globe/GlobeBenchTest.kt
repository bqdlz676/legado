package io.legado.app.ui.map.globe

import io.legado.app.ui.map.globe.astro.StarField
import io.legado.app.ui.map.globe.data.SeedPowers
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.mesh.LodLevel
import io.legado.app.ui.map.globe.render.DrawBudget
import io.legado.app.ui.map.globe.render.FrameSnapshot
import io.legado.app.ui.map.globe.replay.FactionPalette
import io.legado.app.ui.map.globe.replay.power.PowerLayer
import io.legado.app.ui.map.globe.replay.route.GlobeRoute
import io.legado.app.ui.map.globe.replay.route.RouteWaypoint
import io.legado.app.ui.map.globe.scene.GlobeScene
import io.legado.app.ui.map.globe.terrain.TerrainSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地球仪纯逻辑层性能 bench（规格切片 19 / 20 性能红线）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * - 本 bench 在宿主 JVM 上跑，只验证纯逻辑路径不超时；荣耀 WIN 真机耗时需复测，
 *   未在本机 / 真机验证的部分不计入通过结论（编译通过 ≠ 功能可用）。
 * - 预算取宽松上限：单帧内几何 / 采样 / 快照必须留得出绘制余量。
 * - 真机目标：手机 55fps、首开 2K 缓存 ≤800ms、draw call ≤15（切片 19 / 20）。
 */
class GlobeBenchTest {

    private companion object {
        const val WARM_ROUNDS = 200
        const val MEASURE_ROUNDS = 2_000
        const val BATCH_ROUNDS = 10_000

        /** 单点几何换算热路径均值预算。 */
        const val GEOM_AVG_BUDGET_MS = 1.0
        /** 单次路线采样预算。 */
        const val ROUTE_SAMPLE_BUDGET_MS = 1.0
        /** 单顶点地形分类预算。 */
        const val TERRAIN_AVG_BUDGET_MS = 1.0
        /** 单帧快照装配预算（组件模型预算 4ms 口径，对齐 ComponentPalette.MODEL_BUDGET_MS）。 */
        const val SNAPSHOT_BUDGET_MS = 4.0

        /** 128×128 全网格离屏构建预算：势力色块按 LOD 缓存、非每帧重算，放宽到一次性构建口径。 */
        const val FULL_GRID_BUDGET_MS = 16.0
    }

    private val route = GlobeRoute(
        id = "bench-route",
        factionId = "macedon",
        colorHex = FactionPalette.hexOf("macedon"),
        waypoints = listOf(
            RouteWaypoint("佩拉", 40.75, 22.52),
            RouteWaypoint("巴比伦", 32.54, 44.42),
            RouteWaypoint("撒马尔罕", 39.65, 66.96),
            RouteWaypoint("印度河", 31.40, 73.10),
        ),
        dailyLi = GlobeConfig.DAILY_LI_MARCH,
    )

    private val terrain = TerrainSource()
    private val powerLayer = PowerLayer(SeedPowers.all)

    @Test
    fun `geo 经纬度往返热路径均值在预算内`() {
        repeat(WARM_ROUNDS) { Geo.direction(39.9, 116.4) }

        val samples = ArrayList<Double>(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) { i ->
            val start = System.nanoTime()
            val dir = Geo.direction((i % 90) - 45.0, (i % 180) - 90.0)
            Geo.latLng(dir)
            samples += (System.nanoTime() - start) / 1_000_000.0
        }
        val avg = samples.average()
        assertTrue("geo 往返均值应 ≤ ${GEOM_AVG_BUDGET_MS}ms，实际 $avg ms", avg < GEOM_AVG_BUDGET_MS)
    }

    @Test
    fun `路线大圆采样单点在预算内`() {
        repeat(WARM_ROUNDS) { route.sample(0.5, GlobeConfig.RADIUS.toDouble()) }

        val start = System.nanoTime()
        repeat(MEASURE_ROUNDS) { i ->
            route.sample((i % 100) / 100.0, GlobeConfig.RADIUS.toDouble())
            route.sampleTangent((i % 100) / 100.0)
        }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / MEASURE_ROUNDS
        assertTrue("路线采样单次应 ≤ ${ROUTE_SAMPLE_BUDGET_MS}ms，实际 $perCall ms", perCall < ROUTE_SAMPLE_BUDGET_MS)
    }

    @Test
    fun `地形分类批量顶点在预算内`() {
        repeat(WARM_ROUNDS) { terrain.sample(30.0, 120.0) }

        val start = System.nanoTime()
        repeat(BATCH_ROUNDS) { i ->
            terrain.sample((i % 180) - 90.0, (i % 360) - 180.0)
        }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / BATCH_ROUNDS
        assertTrue("地形分类单点应 ≤ ${TERRAIN_AVG_BUDGET_MS}ms，实际 $perCall ms", perCall < TERRAIN_AVG_BUDGET_MS)
    }

    @Test
    fun `势力网格采样 32 步在单帧预算内`() {
        // 预热
        powerLayer.sampleGrid(16, 32)

        val start = System.nanoTime()
        repeat(50) { powerLayer.sampleGrid(32, 64) }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / 50
        assertTrue("势力 32×64 采样应 ≤ ${SNAPSHOT_BUDGET_MS}ms，实际 $perCall ms", perCall < SNAPSHOT_BUDGET_MS)
    }

    @Test
    fun `势力全网格 128×128 离屏构建在预算内`() {
        val steps = GlobeConfig.POWER_GRID_STEPS
        // 预热一次，构建缓存与热点。
        powerLayer.sampleGrid(steps, steps)

        val rounds = 10
        val start = System.nanoTime()
        repeat(rounds) { powerLayer.sampleGrid(steps, steps) }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / rounds
        assertTrue("势力 ${steps}×${steps} 全网格采样应 ≤ ${FULL_GRID_BUDGET_MS}ms，实际 $perCall ms",
            perCall < FULL_GRID_BUDGET_MS)
    }

    @Test
    fun `帧快照装配在模型预算内`() {
        val scene = GlobeScene()

        // 预热
        repeat(20) { FrameSnapshot.from(scene) }

        val rounds = MEASURE_ROUNDS / 10
        val start = System.nanoTime()
        repeat(rounds) { FrameSnapshot.from(scene) }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / rounds
        assertTrue("快照装配应 ≤ ${SNAPSHOT_BUDGET_MS}ms，实际 $perCall ms", perCall < SNAPSHOT_BUDGET_MS)
    }

    @Test
    fun `星空程序化构建一次完成且星数达标`() {
        val start = System.nanoTime()
        val field = StarField()
        val buildMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("星数应等于配置 ${GlobeConfig.STAR_COUNT}，实际 ${field.stars.size}",
            field.stars.size == GlobeConfig.STAR_COUNT)
        assertTrue("星空构建应 ≤ ${SNAPSHOT_BUDGET_MS}ms，实际 $buildMs ms", buildMs < SNAPSHOT_BUDGET_MS)
    }

    @Test
    fun `draw call 预算按 LOD 上限核算`() {
        val budget = DrawBudget(LodLevel.LOD0)
        repeat(10) { budget.add() }
        assertTrue("LOD0 应在 10 call 内，当前 ${budget.current()}", !budget.exceeded())
        budget.add()
        assertTrue("第 11 个 call 应超 LOD0 预算", budget.exceeded())
    }
}
