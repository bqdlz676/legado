package io.legado.app.ui.globe.core.replay.marker

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.data.PlaceTier
import io.legado.app.ui.globe.core.geo.Geo
import io.legado.app.ui.globe.core.math.Vec3
import io.legado.app.ui.globe.core.replay.route.RouteWaypoint

/**
 * 城池 / 地点光针标记（纯 JVM，切片 18）。
 *
 * 切片 18：「光针从球面垂直穿出（沿球心到地标的方向向量），金色光针 + 顶部金点；
 * 所有地名标记合到一张 atlas。」
 *
 * 几何全部沿地标径向（球心→地标）方向：针脚在球面半径处，针尖在 radius + 针高处，
 * 顶部金点再外扩一个点半径。方向向量即球面法线，不随切线偏移。
 */
data class GlobeMarker(
    val name: String,
    val lat: Double,
    val lng: Double,
    val tier: PlaceTier,
    val factionId: String? = null,
) {

    /** 球心→地标单位方向（即球面法线）。 */
    fun direction(): Vec3 = Geo.direction(lat, lng)

    /** 光针脚点（球面半径处）。 */
    fun needleBase(radius: Double): Vec3 = direction() * radius

    /** 光针尖点（沿法线穿出 needleHeight）。 */
    fun needleTip(radius: Double, height: Double = GlobeConfig.NEEDLE_HEIGHT.toDouble()): Vec3 =
        direction() * (radius + height)

    /** 顶部金点中心（针尖再外扩半个点半径）。 */
    fun topDotCenter(radius: Double, height: Double = GlobeConfig.NEEDLE_HEIGHT.toDouble()): Vec3 =
        direction() * (radius + height + GlobeConfig.NEEDLE_TOP_DOT_RADIUS)

    companion object {
        /** 由路线途经点生成光针标记。 */
        fun fromWaypoint(wp: RouteWaypoint, factionId: String? = null): GlobeMarker =
            GlobeMarker(wp.name, wp.lat, wp.lng, PlaceTier.CAPITAL, factionId)
    }
}
