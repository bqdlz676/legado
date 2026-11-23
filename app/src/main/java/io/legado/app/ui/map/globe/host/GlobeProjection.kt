package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.math.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 世界方向 → 屏幕坐标的正交投影（宿主层，纯 JVM，不引用 Android / Compose，可直接单测）。
 *
 * 为什么投影放在宿主而不是门面：门面只产球面几何与姿态（yaw / pitch / 太阳方向），
 * 不关心画布尺寸；本类把模型空间方向依次施加「地轴 23.5° 倾斜 → 偏航 → 俯仰」后
 * 正交投到屏幕平面，背面半球按深度剔除。后续换 GLES 渲染后端时，门面几何不动，
 * 只替换本投影为矩阵投影。
 *
 * 坐标约定：与 [Geo] 一致，球心在原点，Y 轴过两极；屏幕 y 轴向下，故世界 y 取反。
 */
class GlobeProjection(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val radiusPx: Float,
    yawDeg: Double = 0.0,
    pitchDeg: Double = 0.0,
) {

    private val tiltRad: Double = GlobeConfig.AXIAL_TILT_DEG * Geo.DEG2RAD
    private val yawRad: Double = yawDeg * Geo.DEG2RAD
    private val pitchRad: Double = pitchDeg * Geo.DEG2RAD

    /** 模型空间方向 → 世界空间方向（倾斜 + 偏航 + 俯仰）。 */
    fun transform(dir: Vec3): Vec3 {
        var v = Geo.tiltAroundX(dir.normalize(), tiltRad)
        v = rotateY(v, yawRad)
        v = rotateX(v, pitchRad)
        return v
    }

    /**
     * 投影一个单位方向到屏幕。
     * @param visible 该点是否在朝向相机的半球（depth > 0 为正面）。
     */
    fun project(dir: Vec3): ProjectedPoint {
        val v = transform(dir)
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f
        val x = cx + (v.x * radiusPx).toFloat()
        val y = cy - (v.y * radiusPx).toFloat()
        // 背面半球保留极浅边缘色时也可画，但光针 / 路线 / 标签只画正面，避免球面穿帮。
        val visible = v.z > -0.001
        return ProjectedPoint(x, y, v.z.toFloat(), visible)
    }

    /**
     * 反投影：屏幕点击位置 → 模型空间方向（双击飞到点击位置用，切片 20 交互表）。
     * 落在圆盘之外（r > 1）返回 null，由宿主回退为不飞行，不凭空造点位。
     */
    fun unproject(xPx: Float, yPx: Float): Vec3? {
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f
        val nx = ((xPx - cx) / radiusPx).toDouble()
        val ny = -((yPx - cy) / radiusPx).toDouble()
        val r2 = nx * nx + ny * ny
        if (r2 > 1.0) return null
        val nz = sqrt(1.0 - r2)
        // 逆变换顺序：先逆俯仰，再逆偏航，最后逆地轴倾斜。
        var v = Vec3(nx, ny, nz)
        v = rotateX(v, -pitchRad)
        v = rotateY(v, -yawRad)
        v = Geo.tiltAroundX(v, -tiltRad)
        return v.normalize()
    }

    private fun rotateY(v: Vec3, a: Double): Vec3 {
        val c = cos(a)
        val s = sin(a)
        return Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
    }

    private fun rotateX(v: Vec3, a: Double): Vec3 {
        val c = cos(a)
        val s = sin(a)
        return Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
    }
}

/** 单点投影结果。 */
data class ProjectedPoint(
    val x: Float,
    val y: Float,
    /** 深度：>0 正面，<0 背面。 */
    val depth: Float,
    val visible: Boolean,
)
