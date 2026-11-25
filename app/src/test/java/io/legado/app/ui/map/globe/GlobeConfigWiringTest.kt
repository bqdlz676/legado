package io.legado.app.ui.map.globe

import io.legado.app.ui.map.globe.contract.GlobeParams
import io.legado.app.ui.map.globe.input.InertiaRotator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S-19 接线验收：2026-11-25 拍板四值在渲染侧（[GlobeConfig] 转发 / 派生通道）必须读到
 * 契约层 [GlobeParams] 的同一来源，不允许渲染层再留一份独立字面量。
 *
 * 覆盖：
 *  ① 球面半径：GlobeConfig.RADIUS 转发 GlobeParams.RADIUS_DP；
 *  ② 自转周期：渲染侧读到的角速度由 ROTATION_PERIOD_MS 派生，反推周期与契约一致；
 *  ③ 标记点直径：转发 MARKER_DOT_DIAMETER_DP；
 *  ④ 标记点脉冲周期：转发 MARKER_PULSE_PERIOD_MS；
 *  ⑤ 输入层自动旋转一帧的进动量走派生速率，与契约周期闭环。
 */
class GlobeConfigWiringTest {

    @Test
    fun `渲染球半径转发契约层拍板半径`() {
        assertEquals(GlobeParams.RADIUS_DP, GlobeConfig.RADIUS, 1e-6f)
    }

    @Test
    fun `自转速率由契约周期派生且反推周期一致`() {
        // 派生式：°/s = 360° ÷（周期 ms / 1000）；反推周期 ms = 360 ÷ 速率 × 1000。
        val derivedDegPerSec = 360.0 / (GlobeParams.ROTATION_PERIOD_MS / 1000.0)
        assertEquals(derivedDegPerSec, GlobeConfig.AUTO_ROTATE_DEG_PER_SEC, 1e-12)

        val periodMsFromRate = (360.0 / GlobeConfig.AUTO_ROTATE_DEG_PER_SEC * 1000.0).toLong()
        assertEquals(GlobeParams.ROTATION_PERIOD_MS, periodMsFromRate)

        // 拍板口径 0.2°/s 作为换算旁证，不作为第二来源。
        assertEquals(0.2, GlobeConfig.AUTO_ROTATE_DEG_PER_SEC, 1e-12)
    }

    @Test
    fun `标记点直径转发契约层拍板直径`() {
        assertEquals(
            GlobeParams.MARKER_DOT_DIAMETER_DP,
            GlobeConfig.MARKER_DOT_DIAMETER_DP,
            1e-6f,
        )
    }

    @Test
    fun `标记点脉冲周期转发契约层拍板周期`() {
        assertEquals(
            GlobeParams.MARKER_PULSE_PERIOD_MS,
            GlobeConfig.MARKER_PULSE_PERIOD_MS,
        )
    }

    @Test
    fun `输入层自动旋转进动量走派生速率并与周期闭环`() {
        val rotator = InertiaRotator()
        rotator.autoRotate = true
        val before = rotator.yawDeg
        rotator.update(1.0)

        // 1 秒进动角 = 派生速率；按契约周期折算 1 秒应进动 360° × 1000 / 周期 ms。
        val expectedDeg = 360.0 * 1000.0 / GlobeParams.ROTATION_PERIOD_MS
        assertEquals(expectedDeg, rotator.yawDeg - before, 1e-9)
        assertEquals(GlobeConfig.AUTO_ROTATE_DEG_PER_SEC, rotator.yawDeg - before, 1e-9)
    }
}
