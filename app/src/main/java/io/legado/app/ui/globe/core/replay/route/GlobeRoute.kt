package io.legado.app.ui.globe.core.replay.route

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.geo.Geo
import io.legado.app.ui.globe.core.math.Vec3
import kotlin.math.ceil

/** 路线途经点（港口 / 海峡 / 补给站 / 关隘，切片 18）。坐标为公开经纬度事实。 */
data class RouteWaypoint(
    val name: String,
    val lat: Double,
    val lng: Double,
)

/**
 * 一条球面行军路线（纯 JVM，切片 18）。
 *
 * 路线 = 一串途经点，每两点之间一个高圆弧，串联成完整路线；
 * 弧长按大圆公式累计，日程按日行里数换算。渲染层只取采样点与切向，不重算几何。
 *
 * @param factionId 阵营 id（配色走 FactionPalette 同一张 12 色表）
 * @param colorHex  阵营色（#RRGGBB）
 * @param dailyLi   日行里数：30 / 50 / 80
 * @param lift      弧线凸起单位，默认切片 18 的 1.5
 */
data class GlobeRoute(
    val id: String,
    val factionId: String,
    val colorHex: String,
    val waypoints: List<RouteWaypoint>,
    val dailyLi: Int,
    val lift: Double = GlobeConfig.ARC_LIFT_UNITS,
) {

    /** 途经点单位球面方向（未施加地轴倾斜；倾斜由模型矩阵统一处理）。 */
    val dirs: List<Vec3> by lazy {
        waypoints.map { Geo.direction(it.lat, it.lng) }
    }

    /** 相邻途经点组成的大圆段。 */
    fun segments(): List<Pair<Vec3, Vec3>> = dirs.zipWithNext()

    fun segmentCount(): Int = (waypoints.size - 1).coerceAtLeast(0)

    /** 第 i 段弧长（公里）。 */
    fun segmentKm(i: Int): Double {
        val segs = segments()
        if (i !in segs.indices) return 0.0
        val (a, b) = segs[i]
        return GreatCircle.arcKm(a, b)
    }

    /** 整条路线累计弧长（公里）。 */
    fun totalKm(): Double = segments().sumOf { (a, b) -> GreatCircle.arcKm(a, b) }

    /** 整条路线在渲染球面上的单位弧长。 */
    fun totalUnits(radius: Double): Double =
        segments().sumOf { (a, b) -> GreatCircle.arcUnits(radius, a, b) }

    /** 日程：公里 ÷ 日行公里，向上取整。 */
    fun marchDays(): Int = GreatCircle.marchDays(totalKm(), dailyLi)

    /** 各段公里数（缓存，供全局按长度比例定位）。 */
    private val segmentKm: List<Double> by lazy {
        segments().map { (a, b) -> GreatCircle.arcKm(a, b) }
    }

    /** 全局比例 t ∈ [0,1] 落到哪一段、段内局部比例。 */
    private fun locate(t: Double): Pair<Int, Double> {
        val segs = segments()
        if (segs.isEmpty()) return 0 to 0.0
        val total = segmentKm.sum()
        if (total <= 1e-9) return 0 to 0.0
        var target = (t.coerceIn(0.0, 1.0)) * total
        for (i in segmentKm.indices) {
            val len = segmentKm[i]
            if (target <= len || i == segmentKm.lastIndex) {
                val local = if (len <= 1e-9) 0.0 else (target / len).coerceIn(0.0, 1.0)
                return i to local
            }
            target -= len
        }
        return segmentKm.lastIndex to 1.0
    }

    /** 全局比例处的高圆弧世界坐标（渲染半径 radius）。 */
    fun sample(t: Double, radius: Double): Vec3 {
        val segs = segments()
        if (segs.isEmpty()) return Vec3.ZERO
        val (i, local) = locate(t)
        val (a, b) = segs[i]
        return GreatCircle.liftedPoint(a, b, local, radius, lift)
    }

    /** 全局比例处沿行进方向的单位切向。 */
    fun sampleTangent(t: Double): Vec3 {
        val segs = segments()
        if (segs.isEmpty()) return Vec3.UNIT_X
        val (i, local) = locate(t)
        val (a, b) = segs[i]
        return GreatCircle.tangent(a, b, local)
    }

    /** 箭头锋芒位置 + 切向 + 径向（供 GL 层摆三角形锋芒，尾细锋尖）。 */
    fun arrowHead(t: Double, radius: Double): ArrowHead {
        val segs = segments()
        if (segs.isEmpty()) return ArrowHead(Vec3.ZERO, Vec3.UNIT_X, Vec3.UNIT_Y)
        val pos = sample(t, radius)
        val tangent = sampleTangent(t)
        val radial = pos.normalize()
        return ArrowHead(pos, tangent, radial)
    }

    /** 整条路线细分采样点（渲染 ribbon / 粒子用）。 */
    fun sampledPoints(radius: Double, perSegment: Int = GlobeConfig.ARC_SAMPLES_PER_SEGMENT): List<Vec3> {
        val out = ArrayList<Vec3>()
        val segs = segments()
        segs.forEachIndexed { i, (a, b) ->
            val steps = perSegment.coerceAtLeast(2)
            for (k in 0 until steps) {
                val local = k.toDouble() / steps
                out.add(GreatCircle.liftedPoint(a, b, local, radius, lift))
            }
            if (i == segs.lastIndex) {
                out.add(GreatCircle.liftedPoint(a, b, 1.0, radius, lift))
            }
        }
        return out
    }

    companion object {
        /** 把公里数按日行里数换算成日程（静态兜底，供单测/上层直接调用）。 */
        fun daysForKm(km: Double, dailyLi: Int): Int = GreatCircle.marchDays(km, dailyLi)
    }
}

/** 箭头锋芒：位置、行进切向、径向（向上）。 */
data class ArrowHead(
    val position: Vec3,
    val tangent: Vec3,
    val radial: Vec3,
)
