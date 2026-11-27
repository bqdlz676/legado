package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 落文 · 掀角弹簧积分器单测：真 spring 收敛到 1、无 NaN、过冲被上界夹住、整定落在 400ms 量级。
 * 纯 JVM 积分，不触 Android 动画类。
 */
class PeelSpringTest {

    private fun spring() = PeelSpring(
        stiffness = InkTokens.PEEL_SPRING_STIFFNESS,
        dampingRatio = InkTokens.PEEL_SPRING_DAMPING_RATIO,
    )

    @Test
    fun `400 毫秒量级推进到接近目标且过程无 NaN`() {
        val s = spring()
        val dt = 1f / 60f
        var peak = 0f
        repeat(24) { // 24 帧 ≈ 400ms
            val v = s.update(dt)
            assertFalse("第 ${it + 1} 帧出现 NaN", v.isNaN())
            peak = maxOf(peak, v)
        }
        assertTrue("400ms 后应推进到 0.8 以上，实际 ${s.value}", s.value > 0.8f)
        assertTrue("过冲不得超过 1.15，峰值 $peak", peak <= 1.15f + 1e-5f)
    }

    @Test
    fun `长时间积分最终整定到 1`() {
        val s = spring()
        val dt = 1f / 60f
        repeat(600) { s.update(dt) } // 10 秒
        assertTrue(s.settled)
        assertTrue(abs(s.value - 1f) < 0.02f)
    }

    @Test
    fun `复位后回到未掀起状态`() {
        val s = spring()
        repeat(30) { s.update(1f / 60f) }
        s.reset()
        assertEquals(0f, s.value, 1e-5f)
        assertEquals(0f, s.velocity, 1e-5f)
        assertFalse(s.settled)
    }

    @Test
    fun `非正步长不产生 NaN`() {
        val s = spring()
        s.update(0f)
        assertFalse(s.value.isNaN())
        s.update(-1f)
        assertFalse(s.value.isNaN())
    }

    @Test
    fun `欠阻尼允许轻微回弹但不越过上界`() {
        val s = spring()
        val dt = 1f / 120f
        var peak = 0f
        repeat(120) {
            val v = s.update(dt)
            peak = maxOf(peak, v)
            assertFalse(v.isNaN())
        }
        assertTrue(peak <= 1.15f + 1e-5f)
    }

    @Test
    fun `非法弹簧参数回退默认值并留计数 不产生 NaN`() {
        val broken = PeelSpring(stiffness = Float.NaN, dampingRatio = -1f)
        assertTrue(broken.parameterClampedCount > 0)
        assertFalse(broken.stiffness.isNaN())
        assertTrue(broken.dampingRatio >= 0f)
        repeat(30) { broken.update(1f / 60f) }
        assertFalse(broken.value.isNaN())
        assertFalse(broken.velocity.isNaN())
    }

    @Test
    fun `过阻尼与无穷大阻尼比被夹紧到上限`() {
        val damped = PeelSpring(stiffness = 220f, dampingRatio = 100f)
        assertTrue(damped.parameterClampedCount > 0)
        assertTrue(damped.dampingRatio <= 2f)
        repeat(30) { damped.update(1f / 60f) }
        assertFalse(damped.value.isNaN())
    }

    @Test
    fun `长时间积分进度夹在零与上界之间`() {
        val s = spring()
        repeat(300) {
            val v = s.update(1f / 120f)
            assertFalse(v.isNaN())
            assertTrue(v >= 0f - 1e-5f)
            assertTrue(v <= 1.15f + 1e-5f)
        }
    }
}
