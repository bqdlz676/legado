package io.legado.app.ui.map.globe.replay.route

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.math.Vec3
import kotlin.math.ceil

/**
 * 球面大圆寻路（纯 JVM，切片 18）。
 *
 * 切片 18 原文：「地球仪没有网格——直接在大圆上走。两点间最短路径永远是大圆上的弧段，
 * 参数化方程一步到位，不需要 A*。」「球面弧长 = 地球半径 × 弧度角，换算公里 →
 * 日行 30/50/80 里标示。」
 *
 * 球面无坡度、无关隘、无渡口，不需要 TerrainAnalyzer；寻路全是大圆数学，毫秒级。
 * 本对象只做纯数学：夹角、公里弧长、高圆弧内插、切向；不依赖 Android / GL。
 */
object GreatCircle {

    /** 两方向夹角（弧度），复用 Geo 的 clamp 防浮点越界。 */
    fun centralAngle(a: Vec3, b: Vec3): Double = Geo.centralAngle(a, b)

    /** 球面弧长（公里）= 地球半径 6371 km × 弧度角。 */
    fun arcKm(a: Vec3, b: Vec3): Double =
        GlobeConfig.EARTH_RADIUS_KM * centralAngle(a, b)

    /** 弧长在渲染球面上的单位长度 = 球半径 × 弧度角。 */
    fun arcUnits(radius: Double, a: Vec3, b: Vec3): Double =
        radius * centralAngle(a, b)

    /**
     * 高圆弧上 t ∈ [0,1] 处的世界坐标。
     * 先在单位球面上 slerp 到大圆方向，再沿径向抬升 [lift] 单位（切片 18：凸起 1.5）。
     */
    fun liftedPoint(a: Vec3, b: Vec3, t: Double, radius: Double, lift: Double): Vec3 {
        val dir = Geo.greatCircleSlerp(a, b, t.coerceIn(0.0, 1.0))
        return dir * (radius + lift)
    }

    /** 弧上 t 处沿 a→b 方向的单位切向（数值微分，边界钳制）。 */
    fun tangent(a: Vec3, b: Vec3, t: Double): Vec3 {
        val lo = t.coerceIn(0.0, 1.0)
        val hi = (lo + 1e-3).coerceAtMost(1.0)
        val p = Geo.greatCircleSlerp(a, b, lo)
        val q = Geo.greatCircleSlerp(a, b, hi)
        return (q - p).normalize()
    }

    /**
     * 日程换算：公里 ÷（日行里数 × 0.5 km/里），向上取整。
     * 日行三档：30（山地/辎重）、50（常行军）、80（轻骑/海路急行）。
     */
    fun marchDays(km: Double, dailyLi: Int): Int {
        val dailyKm = dailyLi * GlobeConfig.KM_PER_LI
        if (dailyKm <= 0.0) return 0
        return ceil(km / dailyKm).toInt().coerceAtLeast(0)
    }
}
