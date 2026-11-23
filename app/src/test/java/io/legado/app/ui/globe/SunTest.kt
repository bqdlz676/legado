package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.astro.Sun
import io.legado.app.ui.globe.core.geo.Geo
import io.legado.app.ui.globe.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * UTC 太阳位置 / 昼夜系数 / 晨昏侧别（纯 JVM）。
 * 切片 19：太阳按 UTC 算位置；暗半球 ×0.15；晨昏线 5° 渐变，东橙西蓝紫。
 */
class SunTest {

    private fun utcMillis(hour: Int, minute: Int = 0): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2026, Calendar.JUNE, 21, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `太阳方向为单位向量`() {
        val d = Sun.direction(utcMillis(12))
        val len = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
        assertEquals(1.0, len, 1e-6)
    }

    @Test
    fun `UTC 正午直射 0 度经线`() {
        val d = Sun.direction(utcMillis(12))
        val (_, lng) = Geo.latLng(d)
        assertTrue("正午直射经度应接近 0，实际 $lng", abs(lng) < 1.0)
    }

    @Test
    fun `UTC 子夜直射对侧经线`() {
        val d = Sun.direction(utcMillis(0))
        val (_, lng) = Geo.latLng(d)
        assertTrue("子夜直射经度应接近 ±180，实际 $lng", abs(abs(lng) - 180.0) < 1.0)
    }

    @Test
    fun `朝向太阳 dayFactor 为 1，背向为 0`() {
        val sun = Sun.direction(utcMillis(12))
        assertEquals(1f, Sun.dayFactor(sun, sun), 1e-6f)
        assertEquals(0f, Sun.dayFactor(sun * -1.0, sun), 1e-6f)
    }

    @Test
    fun `夜侧亮度系数 0 点 15`() {
        assertEquals(1f, Sun.nightBrightness(1f), 1e-6f)
        assertEquals(GlobeConfig.NIGHT_BRIGHTNESS, Sun.nightBrightness(0f), 1e-6f)
    }

    @Test
    fun `晨昏侧别：东为 +1 西为 -1`() {
        val sun = Vec3(1.0, 0.0, 0.0)
        val east = sun.cross(Sun.spinAxis).normalize()
        assertEquals(1, Sun.terminatorSide(east, sun))
        assertEquals(-1, Sun.terminatorSide(east * -1.0, sun))
    }

    @Test
    fun `太阳位置距离锁定 400 单位`() {
        val p = Sun.position(utcMillis(12))
        val len = sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
        assertEquals(GlobeConfig.SUN_DISTANCE.toDouble(), len, 1e-3)
    }
}
