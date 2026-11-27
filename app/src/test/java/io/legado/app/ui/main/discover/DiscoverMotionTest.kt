package io.legado.app.ui.main.discover

import io.legado.app.ui.components.ComponentPalette
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.motion.contract.MotionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6-04 发现页动效编排纯 JVM 单测。
 *
 * 覆盖：stagger 顺序与延迟序列、reduce-motion 时长 / 延迟归零、空列表 / 单条 / 50 条不越界、
 * 未知档位兜底、令牌取值逐一比对单源常量（不只断言非空）。不引 Android 类、不拉起 Compose
 * 运行时；每条断言均带实际谓词，改坏即红。
 */
class DiscoverMotionTest {

    // ─────────────────────────── stagger 顺序与延迟序列 ───────────────────────────

    @Test
    fun `stagger delays are non decreasing and stay inside slow window`() {
        val count = 6
        val delays = (0 until count).map { staggerDelayMs(it, count, reduceMotion = false) }
        assertEquals(count, delays.size)
        assertEquals(0L, delays.first())
        for (i in 1 until delays.size) {
            assertTrue("第 $i 条延迟不应小于前一条", delays[i] >= delays[i - 1])
        }
        val window = ComponentPalette.DURATION_PAGE.toLong()
        assertTrue("末条延迟不应超出慢时长窗口", delays.last() <= window)
    }

    @Test
    fun `stagger delay for single item is zero`() {
        assertEquals(0L, staggerDelayMs(0, 1, reduceMotion = false))
        assertEquals(0L, staggerDelayMs(3, 1, reduceMotion = false))
    }

    @Test
    fun `stagger delay for empty list is zero`() {
        assertEquals(0L, staggerDelayMs(0, 0, reduceMotion = false))
        assertEquals(0L, staggerDelayMs(-1, 0, reduceMotion = false))
    }

    @Test
    fun `stagger fifty items stay in window and keep order`() {
        val count = 50
        val delays = (0 until count).map { staggerDelayMs(it, count, reduceMotion = false) }
        assertEquals(count, delays.size)
        val window = ComponentPalette.DURATION_PAGE.toLong()
        delays.forEach { delay ->
            assertTrue("延迟不应为负", delay >= 0L)
            assertTrue("延迟不应超出慢时长窗口", delay <= window)
        }
        for (i in 1 until count) {
            assertTrue("50 条序列应保持非递减", delays[i] >= delays[i - 1])
        }
    }

    @Test
    fun `out of range index clamps without throwing`() {
        val count = 4
        assertEquals(
            "越界上限 index 应钳到末条",
            staggerDelayMs(3, count, reduceMotion = false),
            staggerDelayMs(99, count, reduceMotion = false),
        )
        assertEquals(
            "越界下限 index 应钳到首条",
            staggerDelayMs(0, count, reduceMotion = false),
            staggerDelayMs(-7, count, reduceMotion = false),
        )
    }

    // ─────────────────────────── reduce-motion 开关 ───────────────────────────

    @Test
    fun `reduce motion cancels every stagger delay`() {
        for (index in 0 until 50) {
            assertEquals(0L, staggerDelayMs(index, 50, reduceMotion = true))
        }
        assertEquals(0L, staggerDelayMs(0, 1, reduceMotion = true))
        assertEquals(0L, staggerDelayMs(0, 0, reduceMotion = true))
    }

    @Test
    fun `reduce motion schedule has zero delay zero duration and instant spring`() {
        val schedule = discoverRevealSchedule(2, 8, reduceMotion = true)
        assertEquals(0L, schedule.delayMs)
        assertEquals(0, schedule.durationMs)
        assertEquals(1000f, schedule.spring.stiffness, 1e-6f)
        assertEquals(1.0f, schedule.spring.dampingRatio, 1e-6f)
        assertEquals("instant", schedule.spring.label)
    }

    // ─────────────────────────── 令牌取值逐一比对单源 ───────────────────────────

    @Test
    fun `reveal duration equals MotionSpec PANEL single source`() {
        assertEquals(ComponentPalette.DURATION_PAGE, revealDurationMs(reduceMotion = false))
        assertEquals(0, revealDurationMs(reduceMotion = true))
    }

    @Test
    fun `reveal spring equals MotionSpec PANEL spec`() {
        val spring = revealSpring(reduceMotion = false)
        assertSame(MotionSpec.PANEL, spring)
        assertEquals(ComponentPalette.REVEAL_SPRING_DAMPING, spring.dampingRatio, 1e-6f)
        assertEquals(ComponentPalette.REVEAL_SPRING_STIFFNESS, spring.stiffness, 1e-6f)
        assertEquals("panel", spring.label)
    }

    @Test
    fun `reveal easing forwards ComponentTokens card enter easing`() {
        assertSame(ComponentTokens.cardEnterEasing, discoverRevealEasing)
    }

    @Test
    fun `schedule gathers delay duration and spring from single source`() {
        val schedule = discoverRevealSchedule(1, 4, reduceMotion = false)
        assertEquals(revealDurationMs(reduceMotion = false), schedule.durationMs)
        assertSame(revealSpring(reduceMotion = false), schedule.spring)
        assertEquals(staggerDelayMs(1, 4, reduceMotion = false), schedule.delayMs)
    }

    // ─────────────────────────── 未知动效档位兜底 ───────────────────────────

    @Test
    fun `unknown tier falls back to contract press tier without throwing`() {
        val duration = revealDurationMs(tier = "bogus-tier", reduceMotion = false)
        assertEquals(ComponentPalette.DURATION_MICRO, duration)
        val spring = revealSpring(tier = "bogus-tier", reduceMotion = false)
        assertSame(MotionSpec.PRESS, spring)

        val blank = discoverRevealSchedule(0, 3, reduceMotion = false, tier = "   ")
        assertEquals(ComponentPalette.DURATION_PAGE, blank.durationMs)
        assertSame(MotionSpec.PANEL, blank.spring)
    }

    @Test
    fun `unknown tier reduce motion still reports zero duration`() {
        assertEquals(0, revealDurationMs(tier = "bogus", reduceMotion = true))
    }
}
