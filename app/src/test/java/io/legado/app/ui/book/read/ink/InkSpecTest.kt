package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 设备档位与帧预算守卫单测：粒子数减半、超预算 streak 累加/回落、降级阈值。
 * 纯 JVM 断言，不触 Android。
 */
class InkSpecTest {

    @Test
    fun `常规机粒子取满配`() {
        assertEquals(28, InkSpec.particleCount(full = 28, low = 14, lowSpec = false))
    }

    @Test
    fun `低端机粒子减半`() {
        assertEquals(14, InkSpec.particleCount(full = 28, low = 14, lowSpec = true))
    }

    @Test
    fun `帧内预算内清零超支计数`() {
        val streak = InkSpec.recordFrame(streak = 3, elapsedMs = 12.0, budgetMs = 16.0)
        assertEquals(0, streak)
    }

    @Test
    fun `超预算帧累加超支计数`() {
        val streak = InkSpec.recordFrame(streak = 1, elapsedMs = 20.0, budgetMs = 16.0)
        assertEquals(2, streak)
    }

    @Test
    fun `连续三帧超预算触发降级`() {
        assertTrue(InkSpec.shouldDegrade(streak = 3, threshold = 3))
    }

    @Test
    fun `未达阈值保持满配`() {
        assertFalse(InkSpec.shouldDegrade(streak = 2, threshold = 3))
    }

    @Test
    fun `启发式按核数与堆上限判低端`() {
        assertTrue(InkSpec.heuristicLowSpec(cores = 2, heapGb = 1.0))
        assertTrue(InkSpec.heuristicLowSpec(cores = 8, heapGb = 2.0))
        assertFalse(InkSpec.heuristicLowSpec(cores = 8, heapGb = 8.0))
    }

    @Test
    fun `显式覆盖档位优先于启发式`() {
        InkSpec.overrideLowSpec = true
        assertTrue(InkSpec.isLowSpec())
        InkSpec.overrideLowSpec = false
        assertFalse(InkSpec.isLowSpec())
        InkSpec.overrideLowSpec = null
    }

    @Test
    fun `帧预算红线常量达标`() {
        // 翻页帧预算 16ms（60fps），气泡热路径预算 80ms，点字墨爆 100ms
        assertEquals(16.0, InkTokens.FRAME_BUDGET_MS, 0.0)
        assertEquals(80.0, InkTokens.BUBBLE_HOT_BUDGET_MS, 0.0)
        assertEquals(100, InkTokens.BURST_DURATION_MS)
    }
}
