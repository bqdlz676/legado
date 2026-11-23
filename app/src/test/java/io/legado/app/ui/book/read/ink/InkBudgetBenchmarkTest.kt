package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * 落文 · 帧预算 benchmark 断言（纯 JVM，离线可跑）：
 *  - 翻页单帧：spring 积分 + 粒子明灭计算须落在 [InkTokens.FRAME_BUDGET_MS]（16ms）内；
 *  - 气泡热路径：定位计算 + 候选循环须落在 [InkTokens.BUBBLE_HOT_BUDGET_MS]（80ms）内。
 * 注意：这是纯计算路径的预算基线；Compose 重组 / 真实绘制 / 触摸派发仍需真机复测。
 */
class InkBudgetBenchmarkTest {

    @Test
    fun `翻页单帧 spring 积分与粒子明灭在 16ms 预算内`() {
        val spring = PeelSpring(
            stiffness = InkTokens.PEEL_SPRING_STIFFNESS,
            dampingRatio = InkTokens.PEEL_SPRING_DAMPING_RATIO,
        )
        val dt = 1f / 60f
        val start = System.nanoTime()
        repeat(24) { frame ->
            spring.update(dt)
            // 模拟一帧粒子明灭的三角函数计算
            sin(2f * Math.PI.toFloat() * frame / InkTokens.PARTICLE_TWINKLE_PERIOD_MS.toFloat())
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("翻页帧耗时 ${elapsedMs}ms 超出预算 ${InkTokens.FRAME_BUDGET_MS}ms",
            elapsedMs <= InkTokens.FRAME_BUDGET_MS)
    }

    @Test
    fun `气泡热路径定位与候选循环在 80ms 预算内`() {
        val anchor = BubbleAnchor(
            centerX = 200f,
            topY = 100f,
            bottomY = 120f,
            lineTopY = 100f,
            lineBottomY = 120f,
        )
        val start = System.nanoTime()
        repeat(50) { i ->
            BubblePlacer.place(
                anchor = anchor,
                bubbleW = 240f,
                bubbleH = 120f,
                viewW = 1080f,
                viewH = 2400f,
                gapPx = 8f,
                topSafePx = 40f,
                bottomReservedPx = 160f,
                marginPx = 12f,
            )
            nextCandidateIndex(3, i % 3)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("气泡热路径 ${elapsedMs}ms 超出预算 ${InkTokens.BUBBLE_HOT_BUDGET_MS}ms",
            elapsedMs <= InkTokens.BUBBLE_HOT_BUDGET_MS)
    }
}
