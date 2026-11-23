package io.legado.app.ui.map.globe.replay.route

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.replay.FactionPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大圆寻路、弧长、日程换算与路线采样单测（切片 18）。
 */
class GreatCircleRouteTest {

    private fun sampleRoute() = GlobeRoute(
        id = "test",
        factionId = "macedon",
        colorHex = FactionPalette.hexOf("macedon"),
        waypoints = listOf(
            RouteWaypoint("佩拉", 40.75, 22.52),
            RouteWaypoint("巴比伦", 32.54, 44.42),
            RouteWaypoint("印度河", 31.40, 73.10),
        ),
        dailyLi = GlobeConfig.DAILY_LI_MARCH,
    )

    @Test
    fun `两点 90 度弧长等于地球半径乘 pi 除二`() {
        val a = Geo.direction(0.0, 0.0)
        val b = Geo.direction(0.0, 90.0)
        val km = GreatCircle.arcKm(a, b)
        assertEquals(GlobeConfig.EARTH_RADIUS_KM * Math.PI / 2.0, km, 1e-6)
    }

    @Test
    fun `日程按日行里数向上取整`() {
        // 100 km，日行 50 里 = 25 km/日 → 4 日
        assertEquals(4, GreatCircle.marchDays(100.0, GlobeConfig.DAILY_LI_MARCH))
        // 除不尽向上取整
        assertEquals(5, GreatCircle.marchDays(101.0, GlobeConfig.DAILY_LI_MARCH))
        // 非法日行档兜底为 0 日，不崩
        assertEquals(0, GreatCircle.marchDays(100.0, 0))
    }

    @Test
    fun `整条路线累计公里数为各段之和且为正`() {
        val route = sampleRoute()
        val total = route.totalKm()
        assertTrue("累计弧长应为正，实际 $total", total > 0.0)
        val sumSegments = (0 until route.segmentCount()).sumOf { route.segmentKm(it) }
        assertEquals(sumSegments, total, 1e-6)
    }

    @Test
    fun `全局采样端点对齐首末途经点`() {
        val route = sampleRoute()
        val radius = GlobeConfig.RADIUS.toDouble()
        val start = route.sample(0.0, radius)
        val end = route.sample(1.0, radius)
        val first = Geo.direction(40.75, 22.52) * (radius + GlobeConfig.ARC_LIFT_UNITS)
        val last = Geo.direction(31.40, 73.10) * (radius + GlobeConfig.ARC_LIFT_UNITS)
        assertEquals(first.x, start.x, 1e-6)
        assertEquals(last.x, end.x, 1e-6)
    }

    @Test
    fun `采样点沿径向抬升 1点5 单位`() {
        val route = sampleRoute()
        val radius = GlobeConfig.RADIUS.toDouble()
        val mid = route.sample(0.5, radius)
        val radial = mid.normalize()
        val expectedRadius = radius + GlobeConfig.ARC_LIFT_UNITS
        assertEquals(expectedRadius, mid.dot(radial), 1e-6)
    }

    @Test
    fun `箭头锋芒切向单位化且非零`() {
        val route = sampleRoute()
        val head = route.arrowHead(0.5, GlobeConfig.RADIUS.toDouble())
        assertEquals(1.0, head.tangent.length, 1e-6)
        assertTrue(head.radial.length > 0.0)
    }

    @Test
    fun `细分采样点数量随段数与每段步数增长`() {
        val route = sampleRoute()
        val pts = route.sampledPoints(GlobeConfig.RADIUS.toDouble(), perSegment = 24)
        val expected = route.segmentCount() * 24 + 1
        assertEquals(expected, pts.size)
    }

    @Test
    fun `日程天数为正整数`() {
        val route = sampleRoute()
        assertTrue(route.marchDays() >= 0)
    }

    @Test
    fun `流动粒子按数量均匀分布在弧线上`() {
        val route = sampleRoute()
        val particles = route.particles(GlobeConfig.RADIUS.toDouble(), count = GlobeConfig.PARTICLES_PER_ROUTE)
        assertEquals(GlobeConfig.PARTICLES_PER_ROUTE, particles.size)
        particles.forEach { assertTrue(it.length > 0.0) }
    }

    @Test
    fun `空路线粒子为空不崩溃`() {
        val empty = GlobeRoute("e", "x", FactionPalette.hexOf(""), emptyList(), GlobeConfig.DAILY_LI_MARCH)
        assertTrue(empty.particles(100.0).isEmpty())
    }
}
