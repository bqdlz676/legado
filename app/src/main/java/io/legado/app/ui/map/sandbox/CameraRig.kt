package io.legado.app.ui.map.sandbox

/**
 * 沙盘视角状态机（纯 JVM，切片-06 §视角操控）。
 *
 * 手势口径：
 *  - 单指拖动 → 水平旋转 + 上下倾斜，倾角上限 [SandboxConfig.TILT_MAX_DEG]；
 *  - 双指捏合 → 缩放，距地面 [SandboxConfig.CAMERA_MIN_DISTANCE] ~ [CAMERA_MAX_DISTANCE]；
 *  - 双指同时滑动 → 平移视角，此期间锁定旋转，相机只水平移动；
 *  - 双击空地 → 重置视角到默认位置；双击棋子 → 飞到棋子正上方 45° 俯瞰。
 *
 * 模型只存数值与钳制规则，手势坐标到位移的换算由 Compose 壳层完成。
 */
class CameraRig(
    var focus: LatLng,
    var yawDeg: Double = DEFAULT_YAW_DEG,
    var tiltDeg: Double = DEFAULT_TILT_DEG,
    var distance: Double = DEFAULT_DISTANCE,
    var panEastKm: Double = 0.0,
    var panNorthKm: Double = 0.0,
) {

    /** 平移期间锁定旋转：true 时 rotateBy 不改变朝向。 */
    var rotationLocked: Boolean = false
        private set

    /** 默认俯瞰倾角。 */
    val defaultTilt: Double get() = DEFAULT_TILT_DEG

    /** 水平旋转（deltaYawDeg）与上下倾斜（deltaTiltDeg）。 */
    fun rotateBy(deltaYawDeg: Double, deltaTiltDeg: Double) {
        if (rotationLocked) return
        yawDeg = (yawDeg + deltaYawDeg) % 360.0
        tiltDeg = (tiltDeg + deltaTiltDeg).coerceIn(0.0, SandboxConfig.TILT_MAX_DEG)
    }

    /** 双指捏合缩放：factor>1 拉远，<1 拉近；距离钳制在 90~560 单位。 */
    fun pinchZoom(factor: Double) {
        if (factor <= 0.0) return
        distance = (distance * factor).coerceIn(
            SandboxConfig.CAMERA_MIN_DISTANCE,
            SandboxConfig.CAMERA_MAX_DISTANCE,
        )
    }

    /** 双指同滑平移：进入平移态锁旋转，结束后由 [endPan] 解锁。 */
    fun panBy(deltaEastKm: Double, deltaNorthKm: Double) {
        rotationLocked = true
        panEastKm += deltaEastKm
        panNorthKm += deltaNorthKm
    }

    /** 平移手势结束，恢复旋转可用。 */
    fun endPan() {
        rotationLocked = false
    }

    /** 双击空地：重置视角到默认位置，平移量清零。 */
    fun reset() {
        yawDeg = DEFAULT_YAW_DEG
        tiltDeg = DEFAULT_TILT_DEG
        distance = DEFAULT_DISTANCE
        panEastKm = 0.0
        panNorthKm = 0.0
        rotationLocked = false
    }

    /** 双击棋子：镜头飞到棋子正上方 [SandboxConfig.PIECE_FLYOVER_TILT_DEG] 俯瞰位。 */
    fun flyOver(target: LatLng) {
        focus = target
        tiltDeg = SandboxConfig.PIECE_FLYOVER_TILT_DEG
        distance = SandboxConfig.CAMERA_MIN_DISTANCE +
            (SandboxConfig.CAMERA_MAX_DISTANCE - SandboxConfig.CAMERA_MIN_DISTANCE) * 0.15
        panEastKm = 0.0
        panNorthKm = 0.0
        rotationLocked = false
    }

    companion object {
        const val DEFAULT_YAW_DEG: Double = 0.0
        const val DEFAULT_TILT_DEG: Double = 35.0
        const val DEFAULT_DISTANCE: Double = 300.0
    }
}
