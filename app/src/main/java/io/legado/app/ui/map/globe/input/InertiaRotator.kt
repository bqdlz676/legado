package io.legado.app.ui.map.globe.input

import io.legado.app.ui.map.globe.GlobeConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 球体惯性旋转（纯 JVM 状态机，输入层只喂位移增量与 dt）。
 *
 * 切片 19 / 20：
 *  - 单指拖动旋转；手指离开后带惯性继续转 0.8s，速度线性衰减到 0，像拨实心球；
 *  - 自动旋转 0.2°/s；
 *  - 俯仰角钳制，避免翻到极点之外。
 */
class InertiaRotator(
    var yawDeg: Double = 0.0,
    var pitchDeg: Double = 0.0,
) {
    private var velocityYaw = 0.0
    private var velocityPitch = 0.0
    private var dragStartYaw = 0.0
    private var dragStartPitch = 0.0
    private var coastTime = 0.0
    private var v0Yaw = 0.0
    private var v0Pitch = 0.0
    private var dragging = false

    var autoRotate: Boolean = false

    /** 拖动灵敏度：屏幕像素 → 角度。 */
    var dragSensitivity: Double = 0.25

    private val pitchLimit = 85.0

    fun onDragStart() {
        dragging = true
        coastTime = 0.0
        v0Yaw = 0.0
        v0Pitch = 0.0
        velocityYaw = 0.0
        velocityPitch = 0.0
        dragStartYaw = yawDeg
        dragStartPitch = pitchDeg
    }

    /**
     * @param dxPx 水平位移（像素，右为正）
     * @param dyPx 垂直位移（像素，下为正）
     */
    fun onDrag(dxPx: Double, dyPx: Double) {
        val dyaw = dxPx * dragSensitivity
        val dpitch = -dyPx * dragSensitivity
        yawDeg += dyaw
        pitchDeg = (pitchDeg + dpitch).coerceIn(-pitchLimit, pitchLimit)
        // 记录本次拖动累计增量，松手后折算为惯性初速度
        velocityYaw = yawDeg - dragStartYaw
        velocityPitch = pitchDeg - dragStartPitch
    }

    fun onDragEnd() {
        dragging = false
        coastTime = 0.0
        v0Yaw = velocityYaw
        v0Pitch = velocityPitch
    }

    /**
     * 每帧推进。
     * @param dt 帧间隔（秒）
     */
    fun update(dt: Double) {
        if (dragging) return

        // 惯性：以松手初速度为基准，0.8s 内线性衰减到 0（切片 19：0.8s 停稳）
        val k = (1.0 - coastTime / GlobeConfig.INERTIA_DECAY_SECONDS).coerceAtLeast(0.0)
        velocityYaw = v0Yaw * k
        velocityPitch = v0Pitch * k
        coastTime += dt
        if (k <= 0.0) {
            velocityYaw = 0.0
            velocityPitch = 0.0
        }

        yawDeg += velocityYaw * dt
        pitchDeg = (pitchDeg + velocityPitch * dt).coerceIn(-pitchLimit, pitchLimit)

        if (autoRotate) {
            yawDeg += GlobeConfig.AUTO_ROTATE_DEG_PER_SEC * dt
        }
    }

    /** 当前是否还在惯性滑行（供渲染层判断是否需要继续重绘）。 */
    val isCoasting: Boolean
        get() = !dragging && coastTime < GlobeConfig.INERTIA_DECAY_SECONDS &&
            (abs(v0Yaw) > 1e-4 || abs(v0Pitch) > 1e-4)

    /** 测试 / 兜底用：直接设置姿态。 */
    fun setPose(yaw: Double, pitch: Double) {
        yawDeg = yaw
        pitchDeg = pitch.coerceIn(-pitchLimit, pitchLimit)
        velocityYaw = 0.0
        velocityPitch = 0.0
        v0Yaw = 0.0
        v0Pitch = 0.0
        coastTime = 0.0
    }

    companion object {
        fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
    }
}
