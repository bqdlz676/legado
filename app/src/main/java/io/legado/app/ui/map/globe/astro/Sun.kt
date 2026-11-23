package io.legado.app.ui.map.globe.astro

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 太阳位置与昼夜光照（纯 JVM）。
 *
 * 切片 19 / 20：
 *  - 太阳金色圆盘，直径 8 单位，与地球距离锁定 400 单位；
 *  - 位置按真实 UTC 时间计算（手机几点，光照到对应位置）；
 *  - 暗半球亮度 ×0.15 + 偏蓝，叠加夜景纹理；
 *  - 晨昏线 5° 宽渐变带：东侧（日出）暖橙，西侧（日落）蓝紫，按太阳方向自动算，不左右对称。
 */
object Sun {

    /** 地轴（模型空间 Y 轴）。 */
    val spinAxis: Vec3 = Vec3.UNIT_Y

    /**
     * 太阳在世界空间的方向（单位向量，从球心指向太阳）。
     * @param utcMillis UTC 毫秒时间戳
     */
    fun direction(utcMillis: Long): Vec3 {
        val utcSec = utcMillis / 1000.0
        val hourOfDay = ((utcSec % 86400.0) + 86400.0) % 86400.0 / 3600.0
        val dayOfYear = (((utcSec / 86400.0) % 365.25) + 365.25) % 365.25

        // 太阳赤纬：简化为地轴倾角 × 周年正弦（春秋分≈0，二至±23.5°）
        val declination = GlobeConfig.AXIAL_TILT_DEG *
            sin(2.0 * PI * (dayOfYear - 81) / 365.25)

        // 太阳直射经度：UTC 12:00 直射 0° 经线，每小时向西 15°
        val subsolarLng = (12.0 - hourOfDay) * 15.0

        return Geo.direction(declination, subsolarLng)
    }

    /** 太阳圆盘在世界空间的位置（距离锁定 400 单位）。 */
    fun position(utcMillis: Long): Vec3 = direction(utcMillis) * GlobeConfig.SUN_DISTANCE

    /** 法线与太阳方向的点积；>0 朝向太阳。 */
    fun dot(normal: Vec3, sunDir: Vec3): Double = normal.normalize().dot(sunDir.normalize())

    /**
     * 日照系数 0（夜）→ 1（正午）。
     * 晨昏线按 5° 带宽做 smoothstep，不是硬切（切片 19）。
     */
    fun dayFactor(normal: Vec3, sunDir: Vec3): Float {
        val d = dot(normal, sunDir)
        val band = cos(GlobeConfig.TERMINATOR_BAND_DEG * Geo.DEG2RAD)
        if (d >= band) return 1f
        if (d <= -band) return 0f
        return ((d + band) / (2.0 * band)).toFloat().coerceIn(0f, 1f)
    }

    /** 暗半球亮度系数：夜侧 ×0.15（切片 19 / 20）。 */
    fun nightBrightness(dayFactor: Float): Float =
        GlobeConfig.NIGHT_BRIGHTNESS + (1f - GlobeConfig.NIGHT_BRIGHTNESS) * dayFactor

    /**
     * 晨昏线侧别：+1 = 日出侧（东，暖橙），-1 = 日落侧（西，蓝紫），0 = 不在晨昏带。
     * 用 `太阳方向 × 自转轴` 的水平方向判断东西。
     */
    fun terminatorSide(normal: Vec3, sunDir: Vec3): Int {
        val east = sunDir.cross(spinAxis).normalize()
        val v = east.dot(normal.normalize())
        return when {
            v > 0.05 -> 1
            v < -0.05 -> -1
            else -> 0
        }
    }
}
