package io.legado.app.ui.globe.core.scene

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.astro.StarField
import io.legado.app.ui.globe.core.astro.Sun
import io.legado.app.ui.globe.core.data.GlobePlace
import io.legado.app.ui.globe.core.data.SeedAtlas
import io.legado.app.ui.globe.core.input.InertiaRotator
import io.legado.app.ui.globe.core.math.Vec3
import io.legado.app.ui.globe.core.mesh.LodLevel
import io.legado.app.ui.globe.core.mesh.LodSelector
import io.legado.app.ui.globe.core.replay.ReplayState
import kotlin.math.max
import kotlin.math.min

/**
 * 地球仪场景状态（纯 JVM，GL 无关）。
 *
 * 职责：把相机距离、惯性旋转、LOD 选择、UTC 太阳方向、云层独立自转、
 * 地名可见性聚合成一帧的不可视状态；渲染层每帧读它来组织各 pass。
 *
 * 沙盘与地球仪是同一个球（切片 20）：缩放只改变相机距离与 LOD，不切换页面。
 */
class GlobeScene(
    val radius: Float = GlobeConfig.RADIUS,
    initialDistance: Float = GlobeConfig.RADIUS * 3.2f,
) {
    val rotator: InertiaRotator = InertiaRotator()
    val lod: LodSelector = LodSelector()
    val starField: StarField = StarField()

    /** 战役回放状态（切片 18：路线 / 势力 / 光针 / 时间轴阶段）。 */
    val replay: ReplayState = ReplayState()

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
     */
    fun update(dt: Double, utcMillis: Long) {
        rotator.update(dt)
        replay.update(dt)

        // 云层独立自转：地球自转角速度 ×0.9
        cloudRotationDeg += GlobeConfig.AUTO_ROTATE_DEG_PER_SEC * GlobeConfig.CLOUD_SPEED_FACTOR * dt

        sunDir = Sun.direction(utcMillis)
        lod.select(cameraDistance.toDouble(), radius.toDouble())
        currentLod = lod.update()
    }

    /** 双指捏合缩放；factor >1 拉近，<1 拉远。钳制在 [1.2R, 6R]。 */
    fun zoomBy(factor: Float) {
        val next = cameraDistance / factor
        cameraDistance = next.coerceIn(
            radius * GlobeConfig.CAMERA_MIN_DISTANCE_RATIO,
            radius * GlobeConfig.CAMERA_MAX_DISTANCE_RATIO,
        )
    }

    fun setCameraDistance(distance: Float) {
        cameraDistance = distance.coerceIn(
            radius * GlobeConfig.CAMERA_MIN_DISTANCE_RATIO,
            radius * GlobeConfig.CAMERA_MAX_DISTANCE_RATIO,
        )
    }

    /** 当前帧可见地名 + 淡出系数。 */
    fun visiblePlaces(): List<Pair<GlobePlace, Float>> = SeedAtlas.visible(distanceRatio.toDouble())

    /** 太阳圆盘世界坐标（距离锁定 400 单位）。 */
    fun sunPosition(): Vec3 = sunDir * GlobeConfig.SUN_DISTANCE

    companion object {
        fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
    }
}
