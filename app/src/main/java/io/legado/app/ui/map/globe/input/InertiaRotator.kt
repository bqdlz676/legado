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
    private var coastTime = 0.0
    private var v0Yaw = 0.0
    private var v0Pitch = 0.0
    private var dragging = false

    // 末段拖动窗口：角速度 = 末段角位移 ÷ 末段时长。旧实现把整段累计角位移当初速度，
    // 量纲是「角度」而非「角度/秒」，快拖会冲过头、慢拖又偏软；这里只取松手前最后一段。
    private var lastNowNanos = 0L
    private var lastDyaw = 0.0
    private var lastDpitch = 0.0
    private var lastDtSec = 0.0

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
        lastNowNanos = 0L
        lastDyaw = 0.0
        lastDpitch = 0.0
        lastDtSec = 0.0
    }

    /**
     * @param dxPx 水平位移（像素，右为正）
     * @param dyPx 垂直位移（像素，下为正）
     * @param nowNanos 当前纳秒时间戳；测试可显式传入以确定性估算角速度，默认取系统时钟。
     */
    fun onDrag(dxPx: Double, dyPx: Double, nowNanos: Long = System.nanoTime()) {
        val dtSec = if (lastNowNanos == 0L) 0.0 else (nowNanos - lastNowNanos) / 1_000_000_000.0
        val dyaw = dxPx * dragSensitivity
        val dpitch = -dyPx * dragSensitivity
        yawDeg += dyaw
        pitchDeg = (pitchDeg + dpitch).coerceIn(-pitchLimit, pitchLimit)
        // 覆盖式保留最后一段的角位移与耗时，松手时据此折算角速度。
        lastDyaw = dyaw
        lastDpitch = dpitch
        lastDtSec = dtSec.coerceAtLeast(0.0)
        lastNowNanos = nowNanos
    }

    fun onDragEnd() {
        dragging = false
        coastTime = 0.0
        // 末段时长缺失（首帧 / 点按）时按一帧兜底折算，避免速度为零或除零。
        val effDt = if (lastDtSec > 1e-3) lastDtSec else GlobeConfig.INERTIA_FALLBACK_FRAME_SEC
        val maxVel = GlobeConfig.INERTIA_MAX_VEL_DEG_PER_SEC
        v0Yaw = (lastDyaw / effDt).coerceIn(-maxVel, maxVel)
        v0Pitch = (lastDpitch / effDt).coerceIn(-maxVel, maxVel)
    }

    /** 松手初速度（°/s），供测试断言角速度量纲。 */
    val coastVelocityYaw: Double get() = v0Yaw

    /** 松手初速度（°/s），供测试断言角速度量纲。 */
    val coastVelocityPitch: Double get() = v0Pitch

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
        lastNowNanos = 0L
        lastDyaw = 0.0
        lastDpitch = 0.0
        lastDtSec = 0.0
    }

    companion object {
        fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
    }
}
