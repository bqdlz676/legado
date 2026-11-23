package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.data.PlaceTier
import io.legado.app.ui.globe.core.geo.Geo
import io.legado.app.ui.globe.core.replay.marker.GlobeMarker
import io.legado.app.ui.globe.core.replay.route.RouteWaypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切片 18：城池光针沿球心→地标法线方向穿出，顶部金点在针尖之外。
 */
class MarkerNeedleTest {

    @Test
    fun `光针方向等于地标球面法线且经纬度往返一致`() {
        val marker = GlobeMarker("罗马", 41.90, 12.50, PlaceTier.CAPITAL)
        val dir = marker.direction()
        val (lat, lng) = Geo.latLng(dir)
        assertEquals(41.90, lat, 1e-4)
        assertEquals(12.50, lng, 1e-4)
        assertTrue("方向应为单位向量", Math.abs(dir.length - 1.0) < 1e-6)
    }

    @Test
    fun `针脚在球面半径，针尖沿法线抬升，金点在针尖之外`() {
        val radius = GlobeConfig.RADIUS.toDouble()
        val height = GlobeConfig.NEEDLE_HEIGHT.toDouble()
        val marker = GlobeMarker("耶路撒冷", 31.78, 35.22, PlaceTier.CAPITAL)
        val dir = marker.direction()

        val base = marker.needleBase(radius)
        val tip = marker.needleTip(radius, height)
        val dot = marker.topDotCenter(radius, height)

        assertEquals(radius, base.length, 1e-6)
        assertEquals(radius + height, tip.length, 1e-6)
        assertTrue("金点应在针尖之外", dot.length > tip.length)
        // 三点共线（同径向）
        assertTrue("针脚与法线同向", Math.abs(base.normalize().dot(dir)) > 0.999)
        assertTrue("针尖与法线同向", Math.abs(tip.normalize().dot(dir)) > 0.999)
    }

    @Test
    fun `途经点可派生光针标记并带阵营`() {
        val wp = RouteWaypoint("安条克", 36.20, 36.16)
        val marker = GlobeMarker.fromWaypoint(wp, "crusader")
        assertEquals("安条克", marker.name)
        assertEquals("crusader", marker.factionId)
        assertEquals(36.20, marker.lat, 1e-9)
        assertEquals(36.16, marker.lng, 1e-9)
    }
}
