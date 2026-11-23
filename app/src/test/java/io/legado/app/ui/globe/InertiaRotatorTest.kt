package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.input.InertiaRotator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 惯性旋转状态机（纯 JVM）。
 * 切片 19：松手惯性 0.8s 停稳；自动旋转 0.2°/s；俯仰角钳制。
 */
class InertiaRotatorTest {

    @Test
    fun `单指拖动改变偏航与俯仰`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(100.0, 40.0)
        assertEquals(100.0 * rotator.dragSensitivity, rotator.yawDeg, 1e-9)
        assertEquals(-40.0 * rotator.dragSensitivity, rotator.pitchDeg, 1e-9)
    }

    @Test
    fun `松手后惯性滑行，约 0 点 8 秒后停稳`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(120.0, 0.0)
        rotator.onDragEnd()
        assertTrue(rotator.isCoasting)

        var t = 0.0
        while (t < 3.0 && rotator.isCoasting) {
            rotator.update(0.1)
            t += 0.1
        }
        assertTrue("惯性应在 3 秒内停稳，实际耗时 ${t}s", t <= 3.0)
        assertTrue(!rotator.isCoasting)
    }

    @Test
    fun `自动旋转每秒推进 0 点 2 度`() {
        val rotator = InertiaRotator()
        rotator.autoRotate = true
        val before = rotator.yawDeg
        repeat(10) { rotator.update(0.1) }
        assertEquals(before + GlobeConfig.AUTO_ROTATE_DEG_PER_SEC, rotator.yawDeg, 1e-6)
    }

    @Test
    fun `俯仰角钳制在 ±85 度`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(0.0, -10000.0)
        assertTrue(abs(rotator.pitchDeg) <= 85.0)
    }

    @Test
    fun `拖动期间不叠加惯性衰减`() {
        val rotator = InertiaRotator()
        rotator.onDragStart()
        rotator.onDrag(50.0, 0.0)
        val yaw = rotator.yawDeg
        rotator.update(0.1)
        assertEquals(yaw, rotator.yawDeg, 1e-9)
    }
}
