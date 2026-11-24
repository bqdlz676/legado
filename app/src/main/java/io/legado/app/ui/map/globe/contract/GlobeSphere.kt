package io.legado.app.ui.map.globe.contract

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 球面点（x / y / z，单位 = 调用方传入的 radiusDp）。
 *
 * 坐标系（本契约层约定，纯 JVM，不依赖渲染层矩阵）：
 *  - 球心在原点；Y 轴过两极，北极 y 为正（上北下南）；
 *  - 经度 0 指向 +z 轴；东经为 lon 正向，绕 Y 轴右手系。
 *
 * 注意：既有 `geo/Geo.kt` 的方向向量以经度 0 指向 +x；两套约定不同，渲染层接线时由投影层
 * 做轴映射，本契约层不迁就既有几何实现，也不反向改动它。
 */
data class SpherePoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

private const val DEG2RAD: Double = PI / 180.0

/**
 * 经度归一化到 (-180, 180]：超过 ±180 回绕，NaN / 无穷按 0 处理，不抛异常。
 */
private fun wrapLon(lonDeg: Double): Double {
    if (lonDeg.isNaN() || lonDeg.isInfinite()) return 0.0
    var l = lonDeg % 360.0
    if (l > 180.0) l -= 360.0
    if (l < -180.0) l += 360.0
    return l
}

/**
 * 经纬度 → 球面坐标（纯函数，纯 JVM，不抛异常）。
 *
 * 口径：
 *  - 纬度超出 [-90, 90] 时钳制到边界；经度超出 ±180 时按 360° 回绕；
 *  - 北极（lat = 90）y 取正最大值，南极（lat = -90）y 取负最小值；
 *  - 赤道（lat = 0）经度 0 指向 +z；
 *  - radiusDp 为负或 NaN 时按 0 处理，避免产出非法半径点。
 *
 * @param latDeg 纬度（度，南负北正）
 * @param lonDeg 经度（度，西负东正）
 * @param radiusDp 球面半径（dp，由调用方传入；契约层默认值见 [GlobeParams.RADIUS_DP]）
 */
fun latLonToSphere(latDeg: Double, lonDeg: Double, radiusDp: Float): SpherePoint {
    val lat = if (latDeg.isNaN() || latDeg.isInfinite()) 0.0 else latDeg.coerceIn(-90.0, 90.0)
    val lon = wrapLon(lonDeg)
    val r = if (radiusDp.isNaN() || radiusDp < 0f) 0f else radiusDp

    val latRad = lat * DEG2RAD
    val lonRad = lon * DEG2RAD
    val cosLat = cos(latRad)

    return SpherePoint(
        x = (r * cosLat * sin(lonRad)).toFloat(),
        y = (r * sin(latRad)).toFloat(),
        z = (r * cosLat * cos(lonRad)).toFloat(),
    )
}

/**
 * 松手后角速度衰减（纯函数，线性衰减，纯 JVM，不抛异常）。
 *
 * 口径（切片-19:61：0.8s 内速度衰减到 0；时长常量见 [GlobeParams.INERTIA_DECAY_MS]）：
 *  - elapsedMs < 0、angularVelocity 为 NaN 或非正时返回 0；
 *  - 返回值恒不为负；超过衰减时长后恒为 0；
 *  - 衰减曲线为线性：v(t) = v0 · max(0, 1 - t / decayMs)，与既有输入层 InertiaRotator
 *    的线性口径一致，避免手感两套曲线。
 *
 * @param angularVelocity 松手时角速度（非负，量纲由调用方约定）
 * @param elapsedMs 松手后经过的毫秒数
 */
fun spinDecay(angularVelocity: Float, elapsedMs: Long): Float {
    if (angularVelocity.isNaN() || angularVelocity < 0f) return 0f
    if (elapsedMs < 0L) return 0f

    val decayMs = GlobeParams.INERTIA_DECAY_MS.toDouble()
    // 衰减时长未拍板（≤0）时不做滑行，直接归静止，避免除零或无限惯性。
    if (decayMs <= 0.0) return 0f

    val factor = (1.0 - elapsedMs.toDouble() / decayMs).coerceIn(0.0, 1.0)
    // Float 与 Double 混算会推到 Double，必须先统一量纲再钳制，否则返回类型不匹配（CI 编译红 K-16）。
    return (angularVelocity.toDouble() * factor).coerceAtLeast(0.0).toFloat()
}
