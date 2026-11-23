package io.legado.app.ui.map.crossing.terrain

import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.TerrainFocus
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 经纬度 ↔ 网格投影（纯 JVM，等距圆柱近似；战役半径 ≤ 百公里级，误差可忽略）。
 *
 * 网格以 [TerrainFocus.center] 为中心、[TerrainFocus.radiusKm] 为半幅；
 * 行列钳制在 [0, gridN) 内，越界坐标不抛，回收到边缘单元。
 */
class GeoProjection(
    val focus: TerrainFocus,
    val gridN: Int,
) {
    private val centerLatRad = Math.toRadians(focus.center.lat)
    private val extentLatDeg = 2.0 * focus.radiusKm / KM_PER_DEG_LAT
    private val extentLngDeg = extentLatDeg / cos(centerLatRad).coerceIn(0.2, 1.0)

    /** 单格边长（米）。 */
    val cellSizeMeters: Double = (focus.radiusKm * 1000.0) / (gridN / 2.0)

    fun toCell(geo: Geo): Cell {
        val fx = ((geo.lng - focus.center.lng) / extentLngDeg) + 0.5
        val fy = ((focus.center.lat - geo.lat) / extentLatDeg) + 0.5
        val col = (fx * gridN).toInt().coerceIn(0, gridN - 1)
        val row = (fy * gridN).toInt().coerceIn(0, gridN - 1)
        return Cell(col, row)
    }

    fun toGeo(cell: Cell): Geo {
        val fx = (cell.col + 0.5) / gridN
        val fy = (cell.row + 0.5) / gridN
        val lng = focus.center.lng + (fx - 0.5) * extentLngDeg
        val lat = focus.center.lat - (fy - 0.5) * extentLatDeg
        return Geo(lat = lat, lng = lng)
    }

    /** 两点大圆距离（米），haversine 公式；小半径战役内与等距圆柱近似一致，口径收口于此。 */
    fun distanceMeters(a: Geo, b: Geo): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2.0 * EARTH_RADIUS_KM * 1000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    companion object {
        /** 纬度每度约 111.32 km（投影横向修正用）。 */
        const val KM_PER_DEG_LAT: Double = 111.32

        /** 地球半径（公里），大圆距离口径。 */
        const val EARTH_RADIUS_KM: Double = 6371.0
    }
}
