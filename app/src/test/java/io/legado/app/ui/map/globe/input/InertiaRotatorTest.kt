package io.legado.app.ui.map.globe.input

import io.legado.app.ui.map.globe.GlobeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 惯性旋转单测：拖动姿态、松手 0.8s 衰减、俯仰钳制、自动旋转（切片 19 / 20）。
 */
class InertiaRotatorTest {

    @Test
    fun `拖动直接改变偏航与俯仰`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(100.0, 50.0)
        assertTrue(rotator.yawDeg > 0.0)
        assertTrue(rotator.pitchDeg < 0.0)
    }

    @Test
    fun `松手后惯性在 0.8 秒内衰减到零`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(200.0, 0.0)
        rotator.onDragEnd()
        assertTrue(rotator.isCoasting)

        // 推进超过衰减窗口，速度应归零
        repeat(10) { rotator.update(0.1) }
        assertFalse(rotator.isCoasting)
    }

    @Test
    fun `衰减窗口内姿态继续变化`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(200.0, 0.0)
        val before = rotator.yawDeg
        rotator.onDragEnd()
        rotator.update(0.1)
        assertTrue(rotator.yawDeg > before)
    }

    @Test
    fun `俯仰角钳制在正负 85 度`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(0.0, -10000.0)
        assertEquals(85.0, rotator.pitchDeg, 1e-6)
    }

    @Test
    fun `自动旋转按 0.2 度每秒推进`() {
        val rotator = InertiaRotator()
        rotator.autoRotate = true
        val before = rotator.yawDeg
        rotator.update(1.0)
        assertEquals(before + GlobeConfig.AUTO_ROTATE_DEG_PER_SEC, rotator.yawDeg, 1e-6)
    }

    @Test
    fun `拖动期间不施加惯性`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(100.0, 0.0)
        rotator.update(0.1)
        assertFalse(rotator.isCoasting)
    }

    @Test
    fun `松手初速度按末段位移除以时间估算（角速度量纲）`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        // 首帧无时长基准；0.1s 后第二段拖动，末段 dyaw = 100px × 0.25 = 25°。
        rotator.onDrag(100.0, 0.0, nowNanos = 0L)
        rotator.onDrag(100.0, 0.0, nowNanos = 100_000_000L)
        rotator.onDragEnd()

        // 角速度 = 25° / 0.1s = 250 °/s，而非整段累计角位移 50°。
        assertEquals(250.0, rotator.coastVelocityYaw, 1e-6)
        assertEquals(0.0, rotator.coastVelocityPitch, 1e-6)
    }

    @Test
    fun `首帧点按无实测时长时按兜底帧长折算且速度被钳制`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(10000.0, 0.0) // 不提供时间戳，走兜底帧长
        rotator.onDragEnd()
        assertTrue(abs(rotator.coastVelocityYaw) <= GlobeConfig.INERTIA_MAX_VEL_DEG_PER_SEC)
    }

    @Test
    fun `setPose 清空惯性状态`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(100.0, 0.0)
        rotator.onDragEnd()
        rotator.setPose(30.0, 20.0)
        assertEquals(30.0, rotator.yawDeg, 1e-6)
        assertFalse(rotator.isCoasting)
    }
}
