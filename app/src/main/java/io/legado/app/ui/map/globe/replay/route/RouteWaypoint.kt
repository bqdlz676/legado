package io.legado.app.ui.map.globe.replay.route

/**
 * 路线途经点（港口 / 海峡 / 补给站 / 关隘，切片 18）。
 * 坐标为公开经纬度事实，不承载文案版权。
 */
data class RouteWaypoint(
    val name: String,
    val lat: Double,
    val lng: Double,
)
