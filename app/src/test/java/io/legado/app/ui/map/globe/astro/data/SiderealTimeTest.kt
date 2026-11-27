package io.legado.app.ui.map.globe.astro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 地方恒星时公式单验收（D1 / §十二.1、§十四.2）。
 *
 * 口径：GMST(°) = 280.46061837 + 360.98564736629·d；LST = GMST + λ，归一 [0,360)。
 * 三个时刻与验收报告手算值对照，误差 < 0.01°；λ 取长安示意经度 108.94°E。
 * 纯 JVM：只用 java.time + org.junit，不碰 Android / Compose。
 */
class SiderealTimeTest {

    private companion object {
        const val CHANGAN_LON_DEG: Double = 108.94
        const val TOLERANCE_DEG: Double = 0.01
    }

    @Test
    fun `三时刻 LST 与手算值一致 误差小于 0 01 度`() {
        // 手算对照值（验收报告 D1 同口径）：2026-11-28 12/16/20 UTC。
        val cases = listOf(
            Instant.parse("2026-11-28T12:00:00Z") to 356.34,
            Instant.parse("2026-11-28T16:00:00Z") to 56.51,
            Instant.parse("2026-11-28T20:00:00Z") to 116.67,
        )
        for ((instant, expected) in cases) {
            val lst = SiderealTime.localSiderealTimeDeg(instant.toEpochMilli(), CHANGAN_LON_DEG)
            assertEquals("LST@${instant}", expected, lst, TOLERANCE_DEG)
        }
    }

    @Test
    fun `LST 归一到 0 360 区间 跨日不出现负角或超界`() {
        val early = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli()
        val lst = SiderealTime.localSiderealTimeDeg(early, CHANGAN_LON_DEG)
        assertTrue(lst >= 0.0 && lst < 360.0)
    }

    @Test
    fun `经度差一整圈 LST 不变`() {
        val now = Instant.parse("2026-11-28T16:00:00Z").toEpochMilli()
        val a = SiderealTime.localSiderealTimeDeg(now, 10.0)
        val b = SiderealTime.localSiderealTimeDeg(now, 370.0)
        assertEquals(a, b, TOLERANCE_DEG)
    }
}
