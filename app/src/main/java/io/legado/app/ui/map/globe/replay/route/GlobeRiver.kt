package io.legado.app.ui.map.globe.replay.route

import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.math.Vec3

/**
 * 球面河流（纯 JVM，切片 20）。
 *
 * 切片 20：Natural Earth 河流矢量路径坐标 → 球面上的 CatmullRom 弧 → TubeGeometry，
 * 液态金线 shader（慢流反光，2fps）。本模型只负责把折线控制点铺成球面弧采样点；
 * 管体成型与流光 shader 属渲染层。
 *
 * 兜底：路径点不足两个时 [isValid] 为 false，渲染层跳过河流 pass，不崩、不留空管。
 *
 * @param pts 经纬度折线控制点：[纬度, 经度]，公开水系事实坐标
 * @param width 河道基准宽度（世界单位）
 */
data class GlobeRiver(
    val id: String,
    val name: String,
    val pts: List<Pair<Double, Double>>,
    val width: Double,
) {

    fun isValid(): Boolean = pts.size >= 2

    private fun dirs(): List<Vec3> = pts.map { (lat, lng) -> Geo.direction(lat, lng) }

    /**
     * 沿相邻控制点的大圆内插，铺出球面弧采样点。
     * 样条磨光由渲染管体完成；这里保证相邻段在球面连续、不穿地。
     */
    fun sampledPoints(radius: Double, perSegment: Int = 12): List<Vec3> {
        if (!isValid()) return emptyList()
        val dirs = dirs()
        val steps = perSegment.coerceAtLeast(2)
        val out = ArrayList<Vec3>()
        for (i in 0 until dirs.size - 1) {
            val a = dirs[i]
            val b = dirs[i + 1]
            for (k in 0 until steps) {
                val t = k.toDouble() / steps
                out.add(Geo.greatCircleSlerp(a, b, t) * radius)
            }
        }
        out.add(dirs.last() * radius)
        return out
    }
}
