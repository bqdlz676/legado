package io.legado.app.ui.map.globe.geo

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * 球面地理换算与大圆（纯 JVM）。
 */
class GeoTest {

    @Test
    fun `经纬度与方向向量往返一致`() {
        val dir = Geo.direction(39.9, 116.4)
        val (lat, lng) = Geo.latLng(dir)
        assertEquals(39.9, lat, 1e-4)
        assertEquals(116.4, lng, 1e-4)
    }

    @Test
    fun `经度归一化到 -180 到 180`() {
        assertEquals(-160.0, Geo.wrapLng(200.0), 1e-9)
        assertEquals(170.0, Geo.wrapLng(-190.0), 1e-9)
        assertEquals(20.0, Geo.wrapLng(20.0), 1e-9)
    }

    @Test
    fun `大圆 slerp 端点等于输入且内插点在单位球面上`() {
        val a = Geo.direction(30.0, 120.0)
        val b = Geo.direction(-20.0, -60.0)
        assertEquals(a, Geo.greatCircleSlerp(a, b, 0.0))
        assertEquals(b, Geo.greatCircleSlerp(a, b, 1.0))
        val mid = Geo.greatCircleSlerp(a, b, 0.5)
        val len = sqrt(mid.x * mid.x + mid.y * mid.y + mid.z * mid.z)
        assertEquals(1.0, len, 1e-6)
    }

    @Test
    fun `弧长等于半径乘弧度角`() {
        val a = Geo.direction(0.0, 0.0)
        val b = Geo.direction(0.0, 90.0)
        val arc = Geo.arcLength(100.0, a, b)
        assertEquals(100.0 * Math.PI / 2.0, arc, 1e-6)
    }

    @Test
    fun `地轴倾斜不改变向量长度`() {
        val v = Geo.direction(45.0, 30.0)
        val t = Geo.tiltAroundX(v, Math.toRadians(23.5))
        val len = sqrt(t.x * t.x + t.y * t.y + t.z * t.z)
        assertEquals(1.0, len, 1e-6)
    }

    @Test
    fun `两极方向正确`() {
        val north = Geo.direction(90.0, 0.0)
        assertEquals(0.0, north.x, 1e-6)
        assertEquals(1.0, north.y, 1e-6)
        assertEquals(0.0, north.z, 1e-6)
    }

    @Test
    fun `对跖点夹角为 pi 且 slerp 不退化崩溃`() {
        val a = Geo.direction(0.0, 0.0)
        val b = Geo.direction(0.0, 180.0)
        assertEquals(Math.PI, Geo.centralAngle(a, b), 1e-6)
        val mid = Geo.greatCircleSlerp(a, b, 0.5)
        assertEquals(1.0, mid.length, 1e-6)
    }
}
