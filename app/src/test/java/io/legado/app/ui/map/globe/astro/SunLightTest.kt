package io.legado.app.ui.map.globe.astro

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 太阳方向、昼夜系数、晨昏线侧别单测（切片 19 / 20）。
 */
class SunLightTest {

    @Test
    fun `太阳方向为单位向量`() {
        val dir = Sun.direction(System.currentTimeMillis())
        assertEquals(1.0, dir.length, 1e-6)
    }

    @Test
    fun `太阳位置锁定 400 单位距离`() {
        val pos = Sun.position(System.currentTimeMillis())
        assertEquals(GlobeConfig.SUN_DISTANCE.toDouble(), pos.length, 1e-6)
    }

    /** 地轴倾角弧度，与 Sun 内部口径一致。 */
    private val tiltRad = GlobeConfig.AXIAL_TILT_DEG * Geo.DEG2RAD

    @Test
    fun `正对太阳法线日照系数为 1`() {
        val sun = Vec3.UNIT_Z
        // 入参是模型空间法线：先逆倾斜，使施加 +23.5° 倾斜后的世界法线正对太阳。
        val modelNormal = Geo.tiltAroundX(Vec3.UNIT_Z, -tiltRad)
        assertEquals(1f, Sun.dayFactor(modelNormal, sun), 1e-6)
    }

    @Test
    fun `背向太阳法线日照系数为 0`() {
        val sun = Vec3.UNIT_Z
        val modelNormal = Geo.tiltAroundX(Vec3(0.0, 0.0, -1.0), -tiltRad)
        assertEquals(0f, Sun.dayFactor(modelNormal, sun), 1e-6)
    }

    @Test
    fun `倾斜前后法线对照：旋转保模、夹角等于倾角、光照点积走倾斜后法线`() {
        val modelNormal = Vec3.UNIT_Z
        val worldNormal = Geo.tiltAroundX(modelNormal, tiltRad)

        // 绕轴旋转不改变法线长度。
        assertEquals(1.0, worldNormal.length, 1e-6)

        // 倾斜前后夹角应等于地轴倾角 23.5°。
        val angle = Math.acos(modelNormal.dot(worldNormal).coerceIn(-1.0, 1.0))
        assertEquals(tiltRad, angle, 1e-6)

        val sun = Vec3.UNIT_Z
        // Sun 内部先倾斜再点积，结果应等于「倾斜后世界法线 · 太阳方向」。
        assertEquals(worldNormal.dot(sun), Sun.dot(modelNormal, sun), 1e-9)

        // 对照：未倾斜的朴素点积与倾斜后结果不同，证明倾斜确实进入了光照链路。
        val naiveDot = modelNormal.dot(sun)
        assertTrue(Math.abs(naiveDot - Sun.dot(modelNormal, sun)) > 1e-6)
    }

    @Test
    fun `晨昏带内系数介于 0 与 1`() {
        val sun = Vec3.UNIT_Z
        // 与太阳方向成 90° 附近，落在 5° 带宽过渡区
        val normal = Vec3.UNIT_X
        val f = Sun.dayFactor(normal, sun)
        assertTrue(f in 0f..1f)
    }

    @Test
    fun `夜侧亮度压到 0.15`() {
        assertEquals(GlobeConfig.NIGHT_BRIGHTNESS, Sun.nightBrightness(0f), 1e-6)
        assertEquals(1f, Sun.nightBrightness(1f), 1e-6)
    }

    @Test
    fun `同一 UTC 时刻太阳方向可复现`() {
        val t = 1_700_000_000_000L
        assertEquals(Sun.direction(t), Sun.direction(t))
    }

    @Test
    fun `晨昏线侧别取值限于 -1 0 1`() {
        val sun = Vec3.UNIT_Z
        val side = Sun.terminatorSide(Vec3.UNIT_X, sun)
        assertTrue(side in -1..1)
    }

    @Test
    fun `赤纬随季节在倾角范围内`() {
        val summer = Sun.direction(1_721_000_000_000L) // 约 6 月
        val lat = Geo.latLng(summer).first
        assertTrue(Math.abs(lat) <= GlobeConfig.AXIAL_TILT_DEG + 1.0)
    }
}
