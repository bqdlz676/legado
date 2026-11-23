package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 竖排排版引擎 bench（切片-09 五：重排速度 ×3；契约 §1.5：命中 ≤80ms）。
 *
 * 说明：本文件为 JVM 基准断言，沙箱内不执行；由云端 CI 跑 `testDebugUnitTest` 取真实耗时。
 * 断言写死指标，不允许在源码里放宽阈值；若 CI 未跑过，交付说明必须标注“bench 未验证”。
 *
 * 测量开销建模：真机 [TextPaintVerticalMetrics] 每字都要走 Paint 测量，是冷路径主要成本；
 * 这里用 [HeavyMeasureMetrics] 模拟同等量级的逐字计算，使冷/热路径差异贴近真机，
 * 而不是用“返回常量”的伪测量把冷路径成本压没、导致 ×3 失真。
 */
class VerticalLayoutEngineBench {

    private companion object {
        const val W = 1080
        const val H = 1920
        const val BODY_PX = 36f
        const val TITLE_PX = 40f

        /** 命中时延预算：80ms（契约 §1.5），单位 ns。 */
        const val HIT_BUDGET_NS = 80_000_000L

        /** 重排加速比写死：热路径平均耗时 ≤ 冷路径 1/3（切片-09 五，×3）。 */
        const val SPEEDUP = 3
    }

    /** 模拟真机逐字 Paint 测量成本的伪 metrics：advance 内做一段可复现的算术。 */
    private class HeavyMeasureMetrics : VerticalTypefaceMetrics {
        override fun measureAdvance(text: String, sizePx: Float, bold: Boolean): Float {
            var acc = 0f
            for (ch in text) acc += ch.code.toFloat()
            // 模拟测量的固定折算工作量；结果仍落在 1 em 量级，不影响版面几何。
            repeat(8) { acc = acc * 0.999f + sizePx * 0.001f }
            return if (bold) sizePx * 1.02f else sizePx + acc * 1e-6f
        }

        override fun classify(ch: Char): CharKind = VerticalCharClassifier.classify(ch)
    }

    private fun newEngine() = VerticalLayoutEngine(
        bodySizePx = BODY_PX,
        titleSizePx = TITLE_PX,
        metrics = HeavyMeasureMetrics(),
    )

    private fun benchmarkText(): String = "城濮之战晋师败楚".repeat(375)

    @Test
    fun `cached re-layout is at least 3x faster than cold layout`() {
        val engine = newEngine()
        val text = benchmarkText()

        // 预热：让 JIT 与类装载稳定，避免首轮冷启动污染均值。
        repeat(3) {
            engine.invalidateCache()
            engine.layout(text, W, H)
        }
        engine.layout(text, W, H)

        val iterations = 20

        var coldNanos = 0L
        repeat(iterations) {
            engine.invalidateCache()
            val t0 = System.nanoTime()
            engine.layout(text, W, H)
            coldNanos += System.nanoTime() - t0
        }
        val coldAvg = coldNanos / iterations

        val measureBeforeHot = engine.debugMeasureCalls()

        var hotNanos = 0L
        repeat(iterations) {
            val t0 = System.nanoTime()
            engine.layout(text, W, H)
            hotNanos += System.nanoTime() - t0
        }
        val hotAvg = hotNanos / iterations

        assertTrue("热路径必须复用字位缓存", engine.lastLayoutReusedCache)
        // 重排 ×3 写死断言：热路径平均耗时 ≤ 冷路径 / 3。
        assertTrue(
            "重排未达 ×3：hotAvg=${hotAvg}ns coldAvg=${coldAvg}ns",
            hotAvg * SPEEDUP <= coldAvg,
        )
        // 热路径走缓存重建，不应再触发真实字形测量。
        assertEquals(measureBeforeHot, engine.debugMeasureCalls())
    }

    @Test
    fun `hitTest stays within 80ms budget per call`() {
        val engine = newEngine()
        val page = engine.layout(benchmarkText(), W, H)
        val centers = page.columns.flatMap { col ->
            col.cells.map { it.x + it.width / 2f to it.y + it.height / 2f }
        }
        assertTrue("bench 页必须有字位", centers.isNotEmpty())

        // 预热命中路径。
        repeat(200) { centers.forEach { (x, y) -> engine.hitTest(x, y) } }

        var maxNanos = 0L
        repeat(10) {
            centers.forEach { (x, y) ->
                val t0 = System.nanoTime()
                engine.hitTest(x, y)
                maxNanos = maxOf(maxNanos, System.nanoTime() - t0)
            }
        }
        assertTrue("单次命中超 80ms 预算：${maxNanos}ns", maxNanos <= HIT_BUDGET_NS)
    }
}
