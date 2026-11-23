package io.legado.app.ui.map.sandbox

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 沙盘地理基础（纯 JVM，无 Android 依赖）。
 *
 * 为什么坐标模型用 (lat, lng) 而数据资产里是 [lng, lat] 数组：JS 资产沿用地图 API 惯例
 * 先经度后纬度；模型层统一成先纬度后经度，解析入口 [LatLng.fromLngLat] 做一次翻转，
 * 避免渲染层到处记反顺序。
 */
data class LatLng(val lat: Double, val lng: Double) {

    companion object {
        /** 从资产数组 [lng, lat] 构造模型坐标。 */
        fun fromLngLat(pair: List<Double>): LatLng {
            require(pair.size >= 2) { "坐标数组至少需要 [lng, lat] 两个值" }
            return LatLng(lat = pair[1], lng = pair[0])
        }
    }

    /** 与另一坐标的球面距离（公里，haversine）。 */
    fun distanceKm(other: LatLng): Double {
        val r = SandboxConfig.EARTH_RADIUS_KM
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * 以 [focus] 为原点的局部平面投影（等距圆柱近似，单位公里）。
     * 沙盘视野范围有限，局部投影的弧形误差可忽略；相机平移 / 测距 / 箭头贴地都用这套平面坐标。
     */
    fun toLocalKm(focus: LatLng): Pair<Double, Double> {
        val midLat = Math.toRadians((focus.lat + lat) / 2)
        val east = (lng - focus.lng) * SandboxConfig.EARTH_RADIUS_KM * cos(midLat)
        val north = (lat - focus.lat) * SandboxConfig.EARTH_RADIUS_KM
        return east to north
    }

    /** 方位角（度，正北 0，顺时针），供包抄弧线 / 关隘短线朝向用。 */
    fun bearingDeg(to: LatLng): Double {
        val f = Math.toRadians(lat)
        val t = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - lng)
        val x = sin(dLng) * cos(t)
        val y = cos(f) * sin(t) - sin(f) * cos(t) * cos(dLng)
        return (Math.toDegrees(atan2(x, y)) + 360.0) % 360.0
    }

    companion object March {
        /**
         * 行军天数：公里 ÷（日行里数 × 每里公里数），向上取整。
         * 边界兜底：里程为 0 或日行里数非法时返回 0，不抛异常。
         */
        fun marchDays(km: Double, dailyLi: Int): Int {
            if (km <= 0.0 || dailyLi <= 0) return 0
            val dailyKm = dailyLi * SandboxConfig.KM_PER_LI
            return (km / dailyKm).roundToInt().coerceAtLeast(1)
        }
    }
}
