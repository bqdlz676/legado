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

    /** 地轴倾角（弧度），模型整体绕 X 轴偏转。 */
    private val axialTiltRad: Double = GlobeConfig.AXIAL_TILT_DEG * Geo.DEG2RAD

    /**
     * 模型空间法线 → 世界空间法线（切片 19：光照计算基于倾斜后的球面法线方向）。
     *
     * 坐标系约定：地球网格由经纬度在模型空间生成（Y 轴为模型地轴，法线未倾斜）；
     * 整个地球模型绕 X 轴偏转 23.5° 才进入世界空间，太阳方向也是世界空间向量。
     * 因此法线必须先施加地轴倾斜，再与世界太阳方向做点积；直接用模型法线会让
     * 昼夜分界与季节光照整体偏转一个倾角，晨昏线位置错误。
     */
    fun worldNormal(modelNormal: Vec3): Vec3 =
        Geo.tiltAroundX(modelNormal, axialTiltRad)

    /** 世界空间地轴（模型 Y 轴施加地轴倾斜后的结果），用于晨昏线东西判别。 */
    val worldSpinAxis: Vec3 = worldNormal(Vec3.UNIT_Y).normalize()

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

    /**
     * 法线与太阳方向的点积；>0 朝向太阳。
     * @param normal 模型空间法线，函数内先施加地轴倾斜转到世界空间再点积。
     */
    fun dot(normal: Vec3, sunDir: Vec3): Double =
        worldNormal(normal).normalize().dot(sunDir.normalize())

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
        // 法线与地轴都在世界空间比较，避免模型 / 世界两套坐标系混用导致东西判反。
        val east = sunDir.cross(worldSpinAxis).normalize()
        val v = east.dot(worldNormal(normal).normalize())
        return when {
            v > 0.05 -> 1
            v < -0.05 -> -1
            else -> 0
        }
    }
}
