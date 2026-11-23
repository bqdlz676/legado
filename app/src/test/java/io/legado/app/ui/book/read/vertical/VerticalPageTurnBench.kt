package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 竖排翻页 bench（切片-08 性能红线：翻页全程 ≤16ms/帧）。
 *
 * 说明：本文件为 JVM 基准断言，沙箱内不执行；由云端 CI 跑 `testDebugUnitTest` 取真实耗时。
 * 断言写死指标，不允许在源码里放宽阈值；若 CI 未跑过，交付说明必须标注“bench 未验证”。
 *
 * 口径：翻页路径只切换当前页索引并映射已缓存的字位，不触发重新测量；
 * 这里逐次计量 next / prev / jumpTo 单次调用耗时，对标一帧 16ms 预算。
 */
class VerticalPageTurnBench {

    private companion object {
        const val W = 1080
        const val H = 1920
        const val BODY_PX = 36f
        const val TITLE_PX = 40f

        /** 翻页时延预算：16ms/帧（切片-08），单位 ns。 */
        const val TURN_BUDGET_NS = 16_000_000L

        const val ITERATIONS = 30
    }

    private fun newEngine() = VerticalLayoutEngine(
        bodySizePx = BODY_PX,
        titleSizePx = TITLE_PX,
        metrics = DefaultTypefaceMetrics(),
    )

    private fun benchmarkText(): String = "城濮之战晋师败楚".repeat(80)

    @Test
    fun `next prev and jumpTo each stay within 16ms per frame`() {
        val engine = newEngine()
        val text = benchmarkText()
        engine.layout(text, W, H)

        var lastIndex = 0
        while (engine.canNext) lastIndex = engine.next()!!.pageIndex
        assertTrue("bench 章节必须有多页", lastIndex > 0)

        // 预热：让 JIT 与页映射路径稳定。
        repeat(3) {
            engine.jumpTo(0)
            while (engine.canNext) engine.next()
            while (engine.canPrev) engine.prev()
        }

        var maxNextNanos = 0L
        var maxPrevNanos = 0L
        var maxJumpNanos = 0L

        repeat(ITERATIONS) {
            engine.jumpTo(0)

            while (engine.canNext) {
                val t0 = System.nanoTime()
                engine.next()
                maxNextNanos = maxOf(maxNextNanos, System.nanoTime() - t0)
            }

            while (engine.canPrev) {
                val t0 = System.nanoTime()
                engine.prev()
                maxPrevNanos = maxOf(maxPrevNanos, System.nanoTime() - t0)
            }

            for (target in 0..lastIndex) {
                val t0 = System.nanoTime()
                engine.jumpTo(target)
                maxJumpNanos = maxOf(maxJumpNanos, System.nanoTime() - t0)
            }
        }

        assertTrue("单页下翻超 16ms 预算：${maxNextNanos}ns", maxNextNanos <= TURN_BUDGET_NS)
        assertTrue("单页上翻超 16ms 预算：${maxPrevNanos}ns", maxPrevNanos <= TURN_BUDGET_NS)
        assertTrue("单次跳转超 16ms 预算：${maxJumpNanos}ns", maxJumpNanos <= TURN_BUDGET_NS)
    }
}
