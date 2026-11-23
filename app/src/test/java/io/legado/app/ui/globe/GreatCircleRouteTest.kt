package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.geo.Geo
import io.legado.app.ui.globe.core.replay.route.GlobeRoute
import io.legado.app.ui.globe.core.replay.route.GreatCircle
import io.legado.app.ui.globe.core.replay.route.RouteWaypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 切片 18：球面大圆寻路 / 途经点串联 / 弧长 / 日程换算（纯 JVM）。
 */
class GreatCircleRouteTest {

    @Test
    fun `四分之一大圆弧长等于地球半径乘九十度弧度`() {
        val a = Geo.direction(0.0, 0.0)
        val b = Geo.direction(0.0, 90.0)
        val km = GreatCircle.arcKm(a, b)
        val expected = GlobeConfig.EARTH_RADIUS_KM * (PI / 2.0)
        assertEquals(expected, km, 1e-6)
    }

    @Test
    fun `路线途经点串联成 n 减一段大圆`() {
        val route = GlobeRoute(
            id = "r", factionId = "macedon", colorHex = "#D4A54A",
            waypoints = listOf(
                RouteWaypoint("A", 37.98, 23.73),
                RouteWaypoint("B", 33.51, 36.30),
                RouteWaypoint("C", 29.85, 31.25),
            ),
            dailyLi = GlobeConfig.DAILY_LI_MARCH,
        )
        assertEquals(3, route.waypoints.size)
        assertEquals(2, route.segmentCount())
        assertEquals(2, route.segments().size)
        assertTrue("累计弧长应大于 0", route.totalKm() > 0.0)
    }

    @Test
    fun `全局采样首尾对齐途经点`() {
        val route = GlobeRoute(
            id = "r", factionId = "persia", colorHex = "#8B6914",
            waypoints = listOf(
                RouteWaypoint("A", 37.98, 23.73),
                RouteWaypoint("B", 31.40, 73.10),
            ),
            dailyLi = GlobeConfig.DAILY_LI_FAST,
        )
        val radius = GlobeConfig.RADIUS.toDouble()
        val start = route.sample(0.0, radius)
        val end = route.sample(1.0, radius)
        val expectStart = Geo.direction(37.98, 23.73) * (radius + GlobeConfig.ARC_LIFT_UNITS)
        val expectEnd = Geo.direction(31.40, 73.10) * (radius + GlobeConfig.ARC_LIFT_UNITS)
        assertEquals(expectStart.x, start.x, 1e-6)
        assertEquals(expectStart.y, start.y, 1e-6)
        assertEquals(expectEnd.x, end.x, 1e-6)
        assertEquals(expectEnd.y, end.y, 1e-6)
    }

    @Test
    fun `弧上切向非零且与径向近似垂直`() {
        val route = GlobeRoute(
            id = "r", factionId = "spain", colorHex = "#8A5C4C",
            waypoints = listOf(
                RouteWaypoint("A", 37.39, -5.99),
                RouteWaypoint("B", 24.05, -74.45),
            ),
            dailyLi = GlobeConfig.DAILY_LI_FAST,
        )
        val tangent = route.sampleTangent(0.5)
        assertTrue("切向长度应接近 1", abs(tangent.length - 1.0) < 1e-3)
        val mid = route.sample(0.5, GlobeConfig.RADIUS.toDouble())
        val radial = mid.normalize()
        assertTrue("切向与径向近似垂直", abs(tangent.dot(radial)) < 0.1)
    }

    @Test
    fun `日程按日行里数向上取整`() {
        // 日行 50 里 = 25 km/日；100 km → 4 日
        assertEquals(4, GlobeRoute.daysForKm(100.0, GlobeConfig.DAILY_LI_MARCH))
        // 日行 30 里 = 15 km/日；100 km → ceil(6.67)=7 日
        assertEquals(7, GlobeRoute.daysForKm(100.0, GlobeConfig.DAILY_LI_SLOW))
        // 日行 80 里 = 40 km/日；100 km → 3 日
        assertEquals(3, GlobeRoute.daysForKm(100.0, GlobeConfig.DAILY_LI_FAST))
        // 边界：0 公里 → 0 日
        assertEquals(0, GlobeRoute.daysForKm(0.0, GlobeConfig.DAILY_LI_MARCH))
    }

    @Test
    fun `路线日程与公里数自洽`() {
        val route = GlobeRoute(
            id = "r", factionId = "mongol", colorHex = "#C23B2E",
            waypoints = listOf(
                RouteWaypoint("A", 47.19, 102.82),
                RouteWaypoint("B", 50.06, 19.94),
            ),
            dailyLi = GlobeConfig.DAILY_LI_FAST,
        )
        val days = route.marchDays()
        val expected = ceil(route.totalKm() / (GlobeConfig.DAILY_LI_FAST * GlobeConfig.KM_PER_LI)).toInt()
        assertEquals(expected, days)
        assertTrue("长途西征日程应大于 0", days > 0)
    }
}
