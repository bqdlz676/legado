package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.mesh.LodLevel
import io.legado.app.ui.map.globe.render.DrawBudget
import io.legado.app.ui.map.globe.geo.Geo
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地球仪宿主层性能 bench（规格切片 19 / 20 性能红线 + 组件模型预算）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * - 本 bench 在宿主 JVM 上跑，只验证门面调用 + 投影装配路径不超时；荣耀 WIN 真机
 *   耗时需复测，未在本机 / 真机验证的部分不计入通过结论（编译通过 ≠ 功能可用）。
 * - 真机目标：手机 55fps、首开 2K 缓存 ≤800ms、draw call ≤15（切片 19 / 20）。
 * - 单帧模型 / 装配预算对齐 ComponentPalette.MODEL_BUDGET_MS = 4ms，给绘制留余量。
 */
class GlobeHostBenchTest {

    private companion object {
        const val WARM_ROUNDS = 100
        const val MEASURE_ROUNDS = 1_000

        /** 单帧门面快照 + 全实体投影装配预算（模型预算 4ms 口径）。 */
        const val FRAME_ASSEMBLY_BUDGET_MS = 4.0

        /** 单条路线采样 + 粒子预算。 */
        const val ROUTE_BUDGET_MS = 1.0

        /** 势力多边形逐角投影预算。 */
        const val POWER_PROJECT_BUDGET_MS = 4.0
    }

    private val host = GlobeHostController().apply { setOnline(false) }
    private val projection = GlobeProjection(1000f, 1000f, 200f)

    @Test
    fun `单帧快照加全实体投影装配在模型预算内`() {
        // 预热：触发懒加载与热点。
        repeat(WARM_ROUNDS) {
            val snapshot = host.frame(0.016, System.currentTimeMillis())
            projectAll(snapshot)
        }

        val start = System.nanoTime()
        repeat(MEASURE_ROUNDS) {
            val snapshot = host.frame(0.016, System.currentTimeMillis())
            projectAll(snapshot)
        }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / MEASURE_ROUNDS
        assertTrue(
            "单帧快照 + 投影装配应 ≤ ${FRAME_ASSEMBLY_BUDGET_MS}ms，实际 $perCall ms",
            perCall < FRAME_ASSEMBLY_BUDGET_MS,
        )
    }

    @Test
    fun `路线大圆采样与粒子在帧预算内`() {
        val snapshot = host.frame(0.016, System.currentTimeMillis())
        val route = snapshot.route!!
        val radius = GlobeConfig.RADIUS.toDouble()

        repeat(WARM_ROUNDS) {
            route.sampledPoints(radius)
            route.particles(radius)
        }

        val start = System.nanoTime()
        repeat(MEASURE_ROUNDS) {
            route.sampledPoints(radius)
            route.particles(radius)
        }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / MEASURE_ROUNDS
        assertTrue(
            "路线采样 + 粒子单次应 ≤ ${ROUTE_BUDGET_MS}ms，实际 $perCall ms",
            perCall < ROUTE_BUDGET_MS,
        )
    }

    @Test
    fun `势力多边形逐角投影在预算内`() {
        val snapshot = host.frame(0.016, System.currentTimeMillis())
        val powers = snapshot.powerLayer.powers

        repeat(WARM_ROUNDS) {
            powers.forEach { power ->
                power.pts.forEach { (lat, lng) -> projection.project(Geo.direction(lat, lng)) }
            }
        }

        val start = System.nanoTime()
        repeat(200) {
            powers.forEach { power ->
                power.pts.forEach { (lat, lng) -> projection.project(Geo.direction(lat, lng)) }
            }
        }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / 200
        assertTrue(
            "势力投影单帧应 ≤ ${POWER_PROJECT_BUDGET_MS}ms，实际 $perCall ms",
            perCall < POWER_PROJECT_BUDGET_MS,
        )
    }

    @Test
    fun `宿主 pass 数在 LOD0 与手机 draw call 预算内`() {
        // 与 GlobeCanvas 的 pass 口径一一对应：大气辉光 / 海洋球壳 / 势力色块 /
        // 路线 / 锋芒 / 粒子 / 光针 / 标签 / 云层壳 / 太阳方位。
        val budget = DrawBudget(LodLevel.LOD0)
        val hostPasses = listOf(
            "atmosphere", "ocean", "powers", "route", "arrowHead",
            "particles", "markers", "labels", "clouds", "sun",
        )
        hostPasses.forEach { budget.add() }

        assertTrue(
            "宿主 ${hostPasses.size} 个 pass 应在 LOD0 预算 ${budget.limit()} 内，当前 ${budget.current()}",
            !budget.exceeded(),
        )
        assertTrue(
            "pass 数不得超过手机端上限 ${GlobeConfig.MAX_DRAW_CALLS}",
            budget.current() <= GlobeConfig.MAX_DRAW_CALLS,
        )
    }

    /** 模拟宿主画布一帧内的全部投影工作。 */
    private fun projectAll(snapshot: io.legado.app.ui.map.globe.render.FrameSnapshot) {
        snapshot.markers.forEach { marker ->
            projection.project(marker.direction())
        }
        snapshot.visibleLabels.forEach { (place, _) ->
            projection.project(Geo.direction(place.lat, place.lng))
        }
        snapshot.route?.let { route ->
            route.sampledPoints(GlobeConfig.RADIUS.toDouble()).forEach { projection.project(it) }
            route.particles(GlobeConfig.RADIUS.toDouble()).forEach { projection.project(it) }
        }
        snapshot.powerLayer.powers.forEach { power ->
            power.pts.forEach { (lat, lng) -> projection.project(Geo.direction(lat, lng)) }
        }
        projection.project(snapshot.sunDir)
    }
}
