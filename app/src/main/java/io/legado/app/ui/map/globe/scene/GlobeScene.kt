package io.legado.app.ui.map.globe.scene

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.astro.StarField
import io.legado.app.ui.map.globe.astro.Sun
import io.legado.app.ui.map.globe.input.InertiaRotator
import io.legado.app.ui.map.globe.math.Vec3
import io.legado.app.ui.map.globe.mesh.LodLevel
import io.legado.app.ui.map.globe.mesh.LodSelector
import io.legado.app.ui.map.globe.offline.OfflineFallback
import io.legado.app.ui.map.globe.replay.ReplayState

/**
 * 地球仪场景状态（纯 JVM，GL 无关）。
 *
 * 职责：把相机距离、惯性旋转、LOD 选择、UTC 太阳方向、云层独立自转、
 * 离线兜底档位聚合成一帧的不可视状态；渲染层每帧读它来组织各 pass。
 *
 * 沙盘与地球仪是同一个球（切片 20）：缩放只改变相机距离与 LOD，不切换页面。
 */
class GlobeScene(
    val radius: Float = GlobeConfig.RADIUS,
    initialDistance: Float = GlobeConfig.RADIUS * 3.2f,
    val fallback: OfflineFallback = OfflineFallback(),
    val replay: ReplayState = ReplayState(),
) {
    val rotator: InertiaRotator = InertiaRotator()
    val lod: LodSelector = LodSelector()
    val starField: StarField = StarField()

    val atmosphere: AtmosphereSpec = AtmosphereSpec()
    val clouds: CloudSpec = CloudSpec()
    val ocean: OceanSpec = OceanSpec()

    var cameraDistance: Float = initialDistance
        private set

    /** 云层自转角（度），独立于地球自转，速度 ×0.9（切片 19）。 */
    var cloudRotationDeg: Double = 0.0
        private set

    /** 当前太阳方向（世界空间单位向量）。 */
    var sunDir: Vec3 = Sun.direction(System.currentTimeMillis())
        private set

    /** 本帧应使用的 LOD 层级。 */
    var currentLod: LodLevel = LodLevel.LOD0
        private set

    val distanceRatio: Float get() = cameraDistance / radius

    /**
     * 推进一帧。
     * @param dt 帧间隔（秒）
     * @param utcMillis UTC 毫秒时间戳
     * @param turnsPerSec 战役回放每秒推进回合数
     */
    fun update(dt: Double, utcMillis: Long, turnsPerSec: Double = 1.0) {
        rotator.update(dt)
        replay.update(dt, turnsPerSec)

        lod.select(cameraDistance.toDouble(), radius.toDouble())
        currentLod = lod.update()

        // 云层以地球自转 0.9 倍速独立旋转；地球静止时跟随自动旋转速率。
        val earthSpinDegPerSec = if (rotator.autoRotate) {
            GlobeConfig.AUTO_ROTATE_DEG_PER_SEC
        } else {
            0.0
        }
        cloudRotationDeg = (cloudRotationDeg + clouds.speedFactor * earthSpinDegPerSec * dt) % 360.0

        sunDir = Sun.direction(utcMillis)
    }

    /** 双指捏合缩放：按距离比放大 / 缩小，钳制在 [0.6R, 6R]（切片 20 交互表）。 */
    fun zoom(scaleFactor: Float) {
        val next = cameraDistance / scaleFactor
        val min = radius * GlobeConfig.CAMERA_MIN_DISTANCE_RATIO
        val max = radius * GlobeConfig.CAMERA_MAX_DISTANCE_RATIO
        cameraDistance = next.coerceIn(min, max)
    }

    /** 双击飞到指定经纬度（测试 / 兜底用：只改姿态，飞行补间由渲染层做）。 */
    fun flyTo(lat: Double, lng: Double) {
        rotator.setPose(-lng, lat)
    }

    /** 兜底用：直接设定相机距离。 */
    fun setCameraDistance(distance: Float) {
        val min = radius * GlobeConfig.CAMERA_MIN_DISTANCE_RATIO
        val max = radius * GlobeConfig.CAMERA_MAX_DISTANCE_RATIO
        cameraDistance = distance.coerceIn(min, max)
    }
}
