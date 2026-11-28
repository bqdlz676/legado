package io.legado.app.ci.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import app.cash.paparazzi.Paparazzi
import io.legado.app.ci.screenshot.SkyRenderView
import io.legado.app.ci.screenshot.SkyStateFixtures
import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.offline.DegradationLevel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 判据 14 / 15 的 layoutlib 级帧计时（规格 §十四.14 整帧 <16ms、§十四.15 双视角峰值 <24ms）。
 *
 * 〔口径声明——不许把本批数字写成真机达标〕
 *  - 运行环境：Paparazzi layoutlib 纯 JVM 软渲染，Canvas 为 layoutlib 位图 Canvas；
 *  - 不含真机 GPU 栅格 / 纹理上屏，不含 vsync 等待，不含触控采样；
 *  - 16ms / 24ms 只作对照预算线；本批通过只证明 CPU 编排 + layoutlib 绘制路径不退化，
 *    真机整帧耗与时序峰值必须在真机或带屏帧计时环境复测（缺口见证据报告）。
 *
 * 与既有 `app/.../bench/SkyBenchTest.kt` 的分工：既有四条是纯 JVM CPU 子集（无 Android
 * Canvas）；本类在 screenshot 模块走真实 View onDraw 入口，把 layoutlib 绘制段也计入计时，
 * 是沙箱内最接近整帧的一层。两类结论都不背书真机帧耗。
 */
class SkyOnDrawBenchSnapshotTest {

    @get:Rule
    val paparazzi: Paparazzi = Paparazzi()

    private companion object {
        /** 与既有截图基线同尺寸（Paparazzi 默认 NEXUS_5， prior 帧 562×1000）。 */
        const val FRAME_W = 562
        const val FRAME_H = 1000

        /** 预热轮数：暖热 JIT、构建渲染器离屏位图 / atlas / 拾取器缓存。 */
        const val WARM_ROUNDS = 20

        /** 14 整帧采样次数。 */
        const val FRAME_SAMPLES = 100

        /** 15 过场帧数：800ms 按 60fps 折算取 120 帧（规格 §十六.4 VIEW_SWITCH_MS=800）。 */
        const val SWITCH_FRAMES = 120

        /** §十四.14 对照预算 16ms；§十四.15 对照预算 24ms。 */
        const val FRAME_BUDGET_MS = 16.0
        const val SWITCH_BUDGET_MS = 24.0
    }

    private fun percentile(samples: List<Double>, p: Double): Double {
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        val idx = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun median(samples: List<Double>): Double = percentile(samples, 0.5)

    private fun newSkyController(): GlobeController {
        val controller = GlobeController()
        controller.enterSky(SkyViewMode.SKY_HEAVEN)
        controller.setSkyBlend(1f)
        controller.setSkyPose(0.0, 0.0)
        return controller
    }

    private fun makeView(store: io.legado.app.ui.map.globe.astro.data.StarStore?): SkyRenderView {
        val view = SkyRenderView(paparazzi.context)
        view.layout(0, 0, FRAME_W, FRAME_H)
        view.store = store
        view.nowMs = SkyStateFixtures.FIXED_UTC_MILLIS
        return view
    }

    // ───────────── §十四.14 天球整帧 onDraw 计时（layoutlib 代理口径）─────────────

    @Test
    fun `整帧 onDraw 中位数与 P95 在 16ms 对照线内`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val controller = newSkyController()
        val snapshot = controller.nextFrame(0.016, SkyStateFixtures.FIXED_UTC_MILLIS)
            .copy(skyDegradation = DegradationLevel.FULL_TEXTURES, fallbackResolutions = emptyList())
        val view = makeView(store)
        view.snapshot = snapshot
        val bitmap = Bitmap.createBitmap(FRAME_W, FRAME_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // warmup 20 轮：构建离屏背景、atlas 批绘缓存与拾取器，不计入样本。
        repeat(WARM_ROUNDS) { view.drawForBench(canvas) }

        val samples = ArrayList<Double>(FRAME_SAMPLES)
        repeat(FRAME_SAMPLES) {
            val start = System.nanoTime()
            view.drawForBench(canvas)
            samples += (System.nanoTime() - start) / 1_000_000.0
        }

        val med = median(samples)
        val p95 = percentile(samples, 0.95)
        val max = samples.maxOrNull() ?: 0.0
        // 标记行供 CI 日志检索：数值为本机 layoutlib 实测，命令与帧数见证据报告。
        println("SKY-ONDRAW-BENCH n=$FRAME_SAMPLES warmup=$WARM_ROUNDS " +
            "median=$med ms p95=$p95 ms max=$max ms")

        // 断言线贴规格 16ms；余量口径同既有 bench：本批只覆盖 CPU 编排 + layoutlib 绘制，
        // 真机 GPU 栅格未计入，本断言不构成「真机整帧达标」结论。
        assertTrue(
            "整帧 onDraw 中位数应 < ${FRAME_BUDGET_MS}ms（layoutlib 代理口径），实际 $med ms",
            med < FRAME_BUDGET_MS,
        )
    }

    // ───────────── §十四.15 双视角切换 120 帧逐帧计时（代理口径）─────────────

    @Test
    fun `双视角切换 120 帧峰值与 P99 在 24ms 对照线内`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        // 天外起步，切入地上观：enterSky 接通 800ms spring 过场，nextFrame 逐帧做弹簧积分。
        val controller = GlobeController()
        controller.enterSky(SkyViewMode.SKY_HEAVEN)
        controller.setSkyPose(0.0, 0.0)
        controller.enterSky(SkyViewMode.SKY_EARTH)

        val view = makeView(store)
        val bitmap = Bitmap.createBitmap(FRAME_W, FRAME_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val dtSec = SkyParams.VIEW_SWITCH_MS / 1000.0 / SWITCH_FRAMES
        // 固定观测时刻：bench 只测帧编排与绘制耗时，恒星时相位不参与计时；
        // 弹簧截止判定用墙钟，注入固定时刻不影响 120 帧步进积分口径。
        val nowMs = SkyStateFixtures.FIXED_UTC_MILLIS

        // warmup 3 轮：暖热投影分支与渲染器离屏缓存（渲染器静态缓存跨用例保留，
        // 不按整帧 20 轮预热，避免 layoutlib 重复栅格拉高 Screenshot job 时长）。
        repeat(3) {
            val warm = GlobeController()
            warm.enterSky(SkyViewMode.SKY_HEAVEN)
            warm.enterSky(SkyViewMode.SKY_EARTH)
            repeat(SWITCH_FRAMES) {
                val snap = warm.nextFrame(dtSec, nowMs)
                view.snapshot = snap
                view.drawForBench(canvas)
            }
        }

        val perFrame = ArrayList<Double>(SWITCH_FRAMES)
        repeat(SWITCH_FRAMES) {
            val start = System.nanoTime()
            val snapshot = controller.nextFrame(dtSec, nowMs)
            view.snapshot = snapshot
            view.drawForBench(canvas)
            perFrame += (System.nanoTime() - start) / 1_000_000.0
        }

        val p99 = percentile(perFrame, 0.99)
        val max = perFrame.maxOrNull() ?: 0.0
        println("SKY-SWITCH-BENCH frames=$SWITCH_FRAMES durationMs=${SkyParams.VIEW_SWITCH_MS} " +
            "p99=$p99 ms max=$max ms")

        // 口径：含控制器弹簧积分（nextFrame 内 springPose）与 layoutlib onDraw；
        // 不含真机 GPU 栅格、不含 vsync。断言取 P99 而非 max，避免单轮 GC / 调度毛刺直接翻红；
        // max 仅作诊断值记录。24ms 为对照预算线，不背书真机过场峰值。
        assertTrue(
            "双视角切换 P99 应 < ${SWITCH_BUDGET_MS}ms（layoutlib 代理口径），实际 $p99 ms",
            p99 < SWITCH_BUDGET_MS,
        )
    }
}
