package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.mesh.LodLevel
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染核心性能 bench（规格切片 19 / 20 性能红线）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * - 本 bench 在宿主 JVM 上跑，只验证纯几何 / 剔除 / 投影 / 软渲染路径不超时；
 *   荣耀 WIN 真机耗时需复测，未在本机 / 真机验证的部分不计入通过结论
 *   （编译通过 ≠ 功能可用）。
 * - 真机红线：手机 55fps、桌面 120fps、draw call ≤15、LOD 切换 2–3 帧、首开 ≤800ms。
 * - 单帧模型 / 装配预算对齐既有 bench 的 4ms 口径，给真机绘制留余量；网格离屏构建
 *   属一次性开销，放宽到 16ms。
 */
class RenderBenchTest {

    private companion object {
        const val WARM_ROUNDS = 100
        const val MEASURE_ROUNDS = 1_000

        /** 单点投影热路径均值预算。 */
        const val PROJECTION_AVG_BUDGET_MS = 1.0

        /** 单帧剔除 + 投影装配预算（模型预算 4ms 口径）。 */
        const val FRAME_ASSEMBLY_BUDGET_MS = 4.0

        /** 软渲染整帧（含光栅化）预算：对齐手机单帧 55fps 帧长，留余量取 16ms。 */
        const val RASTER_FRAME_BUDGET_MS = 16.0

        /** 高精网格离屏构建一次性预算。 */
        const val MESH_BUILD_BUDGET_MS = 16.0
    }

    private val camera = GlobeCamera(256f, 256f, 80f)

    @Test
    fun `单点投影热路径均值在预算内`() {
        repeat(WARM_ROUNDS) { camera.project(io.legado.app.ui.map.globe.math.Vec3.UNIT_Z) }

        val samples = ArrayList<Double>(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) { i ->
            val dir = io.legado.app.ui.map.globe.geo.Geo.direction(
                (i % 180) - 90.0,
                (i % 360) - 180.0,
            )
            val start = System.nanoTime()
            camera.project(dir)
            samples += (System.nanoTime() - start) / 1_000_000.0
        }
        val avg = samples.average()
        assertTrue("投影单点均值应 ≤ ${PROJECTION_AVG_BUDGET_MS}ms，实际 $avg ms",
            avg < PROJECTION_AVG_BUDGET_MS)
    }

    @Test
    fun `LOD0 单帧剔除加投影装配在模型预算内`() {
        val mesh = RenderLod.meshFor(LodLevel.LOD0, GlobeConfig.RADIUS)
        val world = Array(mesh.vertexCount) { camera.transform(mesh.normalOf(it)) }

        repeat(WARM_ROUNDS) {
            for (i in 0 until mesh.triangleCount) {
                val t = mesh.triangle(i)
                BackFaceCuller.shouldCull(world[t.a], world[t.b], world[t.c])
            }
        }

        val start = System.nanoTime()
        repeat(MEASURE_ROUNDS) {
            for (i in 0 until mesh.triangleCount) {
                val t = mesh.triangle(i)
                val decision = BackFaceCuller.shouldCull(world[t.a], world[t.b], world[t.c])
                if (!decision.culled) {
                    camera.projectWorld(world[t.a])
                    camera.projectWorld(world[t.b])
                    camera.projectWorld(world[t.c])
                }
            }
        }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / MEASURE_ROUNDS
        assertTrue("LOD0 剔除 + 投影单帧应 ≤ ${FRAME_ASSEMBLY_BUDGET_MS}ms，实际 $perCall ms",
            perCall < FRAME_ASSEMBLY_BUDGET_MS)
    }

    @Test
    fun `LOD0 整帧软渲染在手机帧预算内`() {
        val smallCamera = GlobeCamera(256f, 256f, 80f)
        val fb = Framebuffer(256, 256)
        val pipeline = RenderPipeline(smallCamera, fb, DrawBudget(LodLevel.LOD0))

        repeat(20) { pipeline.renderLod(LodLevel.LOD0, GlobeConfig.RADIUS) }

        val rounds = 50
        val start = System.nanoTime()
        repeat(rounds) { pipeline.renderLod(LodLevel.LOD0, GlobeConfig.RADIUS) }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / rounds
        assertTrue("整帧软渲染应 ≤ ${RASTER_FRAME_BUDGET_MS}ms，实际 $perCall ms",
            perCall < RASTER_FRAME_BUDGET_MS)
    }

    @Test
    fun `LOD2 高精网格离屏构建在一次性预算内`() {
        // 预热类加载与热点。
        RenderLod.meshFor(LodLevel.LOD2, GlobeConfig.RADIUS)

        val rounds = 10
        val start = System.nanoTime()
        repeat(rounds) { RenderLod.meshFor(LodLevel.LOD2, GlobeConfig.RADIUS) }
        val perCall = (System.nanoTime() - start) / 1_000_000.0 / rounds
        assertTrue("LOD2 网格构建应 ≤ ${MESH_BUILD_BUDGET_MS}ms，实际 $perCall ms",
            perCall < MESH_BUILD_BUDGET_MS)
    }

    @Test
    fun `draw call 红线：15 个 pass 为手机端上限`() {
        val budget = DrawBudget(LodLevel.LOD2) // LOD0 上限10会误判15红线；LOD2 上限18，min(18,15)=15 才是手机端红线
        repeat(GlobeConfig.MAX_DRAW_CALLS) { budget.add() }
        assertTrue("15 个 pass 不应超手机端上限", !budget.exceeded())
        budget.add()
        assertTrue("第 16 个 pass 应超手机端上限", budget.exceeded())
    }

    @Test
    fun `帧率红线常量与规格一致`() {
        assertTrue("手机目标帧率应为 55", RenderLod.mobileFps == GlobeConfig.TARGET_FPS_MOBILE)
        assertTrue("桌面目标帧率应为 120", RenderLod.desktopFps == GlobeConfig.TARGET_FPS_DESKTOP)
        assertTrue("morph 帧数应在 2..3", RenderLod.morphFrames() in 2..3)
        assertTrue("首开预算应 ≤ 800ms", GlobeConfig.FIRST_LOAD_MS <= 800)
    }
}
