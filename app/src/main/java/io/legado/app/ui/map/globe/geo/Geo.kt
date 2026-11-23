package io.legado.app.ui.map.globe.geo

import io.legado.app.ui.map.globe.math.Vec3
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 球面地理换算（纯 JVM）。
 *
 * 坐标系：球心在原点；Y 轴过两极；方向向量 = 单位球面坐标。
 * 纬度 lat ∈ [-90, 90]，经度 lng ∈ [-180, 180]。
 * 切片 20：平面 `lonU/latV` 映射换成球面坐标；从赤道平滑到北极不中断，无边界接缝。
 */
object Geo {

    const val DEG2RAD: Double = PI / 180.0
    const val RAD2DEG: Double = 180.0 / PI

    /** 经度归一化到 (-180, 180]，避免跨 180° 经线时路线折返。 */
    fun wrapLng(lng: Double): Double {
        var l = lng % 360.0
        if (l > 180.0) l -= 360.0
        if (l < -180.0) l += 360.0
        return l
    }

    /** 经纬度 → 单位球面方向（地轴倾斜由模型矩阵统一施加，这里不掺）。 */
    fun direction(latDeg: Double, lngDeg: Double): Vec3 {
        val lat = latDeg * DEG2RAD
        val lng = wrapLng(lngDeg) * DEG2RAD
        val cosLat = cos(lat)
        return Vec3(cosLat * cos(lng), sin(lat), cosLat * sin(lng))
    }

    /** 单位球面方向 → 经纬度。 */
    fun latLng(dir: Vec3): Pair<Double, Double> {
        val n = dir.normalize()
        val lat = asin(n.y.coerceIn(-1.0, 1.0)) * RAD2DEG
        val lng = atan2(n.z, n.x) * RAD2DEG
        return lat to wrapLng(lng)
    }

    /** 两方向夹角（弧度）；点积 clamp 到 [-1,1]，防浮点越界导致 acos 返回 NaN。 */
    fun centralAngle(a: Vec3, b: Vec3): Double {
        val d = a.normalize().dot(b.normalize()).coerceIn(-1.0, 1.0)
        return acos(d)
    }

    /** 球面弧长 = 半径 × 弧度角（切片 18 大圆公式）。 */
    fun arcLength(radius: Double, a: Vec3, b: Vec3): Double = radius * centralAngle(a, b)

    /** 大圆等距内插（slerp），t ∈ [0,1]；对跖 / 极近退化时退化为线性归一化。 */
    fun greatCircleSlerp(a: Vec3, b: Vec3, t: Double): Vec3 {
        val an = a.normalize()
        val bn = b.normalize()
        val omega = centralAngle(an, bn)
        if (omega < 1e-6) return an.lerp(bn, t).normalize()
        val so = sin(omega)
        val wa = sin((1 - t) * omega) / so
        val wb = sin(t * omega) / so
        return (an * wa + bn * wb).normalize()
    }

    /** 绕 X 轴旋转（地轴 23.5° 倾斜施加在整个地球模型上）。 */
    fun tiltAroundX(v: Vec3, tiltRad: Double): Vec3 {
        val c = cos(tiltRad)
        val s = sin(tiltRad)
        return Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
    }
}
