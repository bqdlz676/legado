package io.legado.app.ui.map.globe.geo

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * 球面地理换算与大圆（纯 JVM）。
 *
 * 坐标口径（与 contract/GlobeSphere 对齐）：球心在原点，北极 +Y；经度 0 指向 +z 轴，
 * 东经绕 +Y 右手系递增，赤道东经 90° 落在 +x 轴。direction 与 latLng 必须互逆。
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
    fun `经度零度指向正z轴`() {
        val d = Geo.direction(0.0, 0.0)
        assertEquals(0.0, d.x, 1e-9)
        assertEquals(0.0, d.y, 1e-9)
        assertEquals(1.0, d.z, 1e-9)
    }

    @Test
    fun `东经九十度落在正x轴`() {
        val d = Geo.direction(0.0, 90.0)
        assertEquals(1.0, d.x, 1e-9)
        assertEquals(0.0, d.y, 1e-9)
        assertEquals(0.0, d.z, 1e-9)
    }

    @Test
    fun `西经九十度落在负x轴`() {
        // 东经为正向的镜像：西经 90° 应落在 -x，与东经 90° 关于 z 轴对称。
        val d = Geo.direction(0.0, -90.0)
        assertEquals(-1.0, d.x, 1e-9)
        assertEquals(0.0, d.y, 1e-9)
        assertEquals(0.0, d.z, 1e-9)
    }

    @Test
    fun `北极方向为正y轴不变`() {
        val north = Geo.direction(90.0, 0.0)
        assertEquals(0.0, north.x, 1e-6)
        assertEquals(1.0, north.y, 1e-6)
        assertEquals(0.0, north.z, 1e-6)
    }

    @Test
    fun `十二组经纬方向往返误差小于十的负九次方`() {
        val samples = listOf(
            0.0 to 0.0,
            0.0 to 90.0,
            0.0 to -90.0,
            0.0 to 180.0,
            0.0 to -180.0,
            90.0 to 0.0,
            -90.0 to 0.0,
            39.9 to 116.4,
            -33.86 to 151.21,
            51.5 to -0.12,
            -23.5 to -46.63,
            60.0 to -120.0,
        )
        for ((lat, lng) in samples) {
            val (backLat, backLng) = Geo.latLng(Geo.direction(lat, lng))
            assertEquals("纬度往返应一致 lat=$lat", lat, backLat, 1e-9)
            assertEquals("经度往返应一致 lng=$lng", Geo.wrapLng(lng), backLng, 1e-9)
        }
    }

    @Test
    fun `跨一百八十度经线回绕后为同一点`() {
        // 181° 与 -179° 是同一经线；方向向量应重合，往返经度落在 (-180, 180]。
        val a = Geo.direction(10.0, 181.0)
        val b = Geo.direction(10.0, -179.0)
        assertEquals(a.x, b.x, 1e-9)
        assertEquals(a.y, b.y, 1e-9)
        assertEquals(a.z, b.z, 1e-9)

        val (lat180, lng180) = Geo.latLng(Geo.direction(10.0, 180.0))
        assertEquals(10.0, lat180, 1e-9)
        assertEquals(180.0, lng180, 1e-9)

        val (latWrap, lngWrap) = Geo.latLng(Geo.direction(10.0, 181.0))
        assertEquals(10.0, latWrap, 1e-9)
        assertEquals(-179.0, lngWrap, 1e-9)
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
    fun `对跖点夹角为 pi 且 slerp 不退化崩溃`() {
        val a = Geo.direction(0.0, 0.0)
        val b = Geo.direction(0.0, 180.0)
        assertEquals(Math.PI, Geo.centralAngle(a, b), 1e-6)
        val mid = Geo.greatCircleSlerp(a, b, 0.5)
        assertEquals(1.0, mid.length, 1e-6)
    }
}
