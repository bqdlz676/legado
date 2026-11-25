package io.legado.app.ui.map.globe.contract

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S-19 3D 地球仪 · 拍板数值契约验收（纯 JVM，只用 org.junit + java.*，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖 2026-11-25 用户拍板落码值：
 *  ① 自转周期 1_800_000L，且 360 / 0.2°/s 换算成立；② 球面半径 100f；
 *  ③ 标记点直径 4f；④ 标记点脉冲周期 1200L。
 */
class GlobeParamsTest {

    @Test
    fun `自转周期拍板为一百八十万毫秒且换算成立`() {
        // 0.2°/s：转一圈 360 / 0.2 = 1800 秒 = 1,800,000ms，与自动旋转同一口径。
        val expectedMs = (360.0 / 0.2 * 1000).toLong()
        assertEquals(1_800_000L, expectedMs)
        assertEquals(1_800_000L, GlobeParams.ROTATION_PERIOD_MS)
    }

    @Test
    fun `球面半径拍板为一百`() {
        assertEquals(100f, GlobeParams.RADIUS_DP, 1e-6f)
    }

    @Test
    fun `标记点直径拍板为四dp`() {
        assertEquals(4f, GlobeParams.MARKER_DOT_DIAMETER_DP, 1e-6f)
    }

    @Test
    fun `标记点脉冲周期拍板为一千二百毫秒`() {
        assertEquals(1200L, GlobeParams.MARKER_PULSE_PERIOD_MS)
    }
}
