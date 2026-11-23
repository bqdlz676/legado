package io.legado.app.ui.map.replay

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 回放模块本地经纬度坐标（纯 JVM）。
 *
 * 为什么不复用沙盘的 LatLng：两个模块文件域独占，类型互相引用会让回放依赖沙盘的
 * 几何约定；坐标结构本身只有两个 Double，本地一份更利于独立编译与单测。
 */
data class LatLng(val lat: Double, val lng: Double) {

    /** 与另一坐标的球面距离（公里，haversine）。 */
    fun distanceKm(other: LatLng): Double {
        val r = EARTH_RADIUS_KM
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    companion object {
        const val EARTH_RADIUS_KM: Double = 6371.0
        const val KM_PER_LI: Double = 0.5

        /** 行军天数：公里 ÷ 日行公里，向上取整；非法输入兜底 0。 */
        fun marchDays(km: Double, dailyLi: Int): Int {
            if (km <= 0.0 || dailyLi <= 0) return 0
            val dailyKm = dailyLi * KM_PER_LI
            return kotlin.math.ceil(km / dailyKm).toInt().coerceAtLeast(1)
        }
    }
}
