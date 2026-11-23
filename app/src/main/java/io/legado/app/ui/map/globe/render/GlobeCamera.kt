package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.math.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 渲染核心相机（纯 JVM，无 Android / GL 依赖）。
 *
 * 变换链固定为「地轴 23.5° 倾斜 → 偏航 yaw → 俯仰 pitch」（切片 19：地球按 23.5°
 * 倾斜，光照基于倾斜后的法线；切片 20：单指拖动沿球面旋转）。与 host/GlobeProjection
 * 同一套坐标约定：球心在原点，Y 轴过两极，相机在 +Z 看向原点，屏幕 y 轴向下。
 *
 * 为什么渲染核心另持一份而不是直接复用 host 投影：host 投影是已接线的 Compose 壳适配层，
 * 冻结不动；本类是渲染管线内部的可测投影骨干，软渲染剔除 / 光栅化每帧调用它，
 * 后续 GLES 真机层可直接把同一变换链搬进 model-view 矩阵。
 *
 * @param viewportWidth  画布宽（像素）
 * @param viewportHeight 画布高（像素）
 * @param radiusPx       球面投影半径（像素）
 * @param yawDeg         偏航角（度，绕 Y 轴）
 * @param pitchDeg       俯仰角（度，绕 X 轴）
 * @param tiltDeg        地轴倾角（度，默认切片 19 的 23.5°）
 */
class GlobeCamera(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val radiusPx: Float,
    yawDeg: Double = 0.0,
    pitchDeg: Double = 0.0,
    tiltDeg: Double = GlobeConfig.AXIAL_TILT_DEG,
) {
    private val tiltRad: Double = tiltDeg * Geo.DEG2RAD
    private val yawRad: Double = yawDeg * Geo.DEG2RAD
    private val pitchRad: Double = pitchDeg * Geo.DEG2RAD

    /** 正面半球容差：深度略负的边缘点仍算可见，避免轮廓线被剔掉。 */
    var frontEpsilon: Double = 1e-3

    /** 模型空间方向 → 世界空间方向（倾斜 → 偏航 → 俯仰）。 */
    fun transform(dir: Vec3): Vec3 {
        var v = Geo.tiltAroundX(dir.normalize(), tiltRad)
        v = rotateY(v, yawRad)
        v = rotateX(v, pitchRad)
        return v
    }

    /**
     * 模型空间单位方向 → 屏幕投影（正交投影，适配软渲染与 2D 壳同口径）。
     * @param dir 模型空间单位方向
     */
    fun project(dir: Vec3): ScreenPoint {
        val v = transform(dir)
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f
        val x = cx + (v.x * radiusPx).toFloat()
        val y = cy - (v.y * radiusPx).toFloat()
        val visible = v.z > -frontEpsilon
        return ScreenPoint(x, y, v.z.toFloat(), visible)
    }

    /**
     * 世界空间方向（已变换）→ 屏幕坐标；剔除 / 光栅化管线内部用，避免重复施加姿态。
     */
    fun projectWorld(world: Vec3): ScreenPoint {
        val v = world.normalize()
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f
        val x = cx + (v.x * radiusPx).toFloat()
        val y = cy - (v.y * radiusPx).toFloat()
        return ScreenPoint(x, y, v.z.toFloat(), v.z > -frontEpsilon)
    }

    /**
     * 反投影：屏幕点击 → 模型空间方向（双击飞到点位用）。
     * 落在圆盘之外（r > 1）返回 null，由上层回退为不飞行，不凭空造点位。
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

/** 单点投影结果（纯数据，屏幕像素坐标 + 世界深度）。 */
data class ScreenPoint(
    val x: Float,
    val y: Float,
    /** 深度：>0 正面，<0 背面；相机在 +Z，越大越近。 */
    val depth: Float,
    val visible: Boolean,
)
