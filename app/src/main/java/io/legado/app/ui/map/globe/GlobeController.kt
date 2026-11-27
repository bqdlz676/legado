package io.legado.app.ui.map.globe

import io.legado.app.ui.map.globe.astro.contract.CameraYawPitch
import io.legado.app.ui.map.globe.astro.contract.ObserverLatLon
import io.legado.app.ui.map.globe.astro.contract.SkyFrame
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.SiderealTime
import io.legado.app.ui.map.globe.offline.AssetStatus
import io.legado.app.ui.map.globe.offline.OfflineFallback
import io.legado.app.ui.map.globe.offline.TextureKind
import io.legado.app.ui.map.globe.render.FrameSnapshot
import io.legado.app.ui.map.globe.scene.GlobeScene

/**
 * 地球仪对外门面（纯 JVM，Compose / Android 层只绑定这一个对象）。
 *
 * 为什么留门面而不是让界面直接摸场景内部：手势、回放、网络状态、快照取数各自的
 * 入口在这里收口，界面层不直接操作 rotator / lod / fallback，后续换渲染后端
 * （GLES → Compose Canvas）也不动场景逻辑。
 *
 * 本类不引用任何 Android / Compose 类型，便于在 JVM 单测里驱动完整关键路径。
 */
class GlobeController(
    val scene: GlobeScene = GlobeScene(),
) {
    private val fallback: OfflineFallback get() = scene.fallback

    // ───────────────────── 观测点与恒星时（§十三.5、§十四.2）─────────────────────

    /** 观测点；无定位权限 / 未注入时默认长安示意，不弹权限墙、不空态。 */
    private var observer: ObserverLatLon =
        ObserverLatLon(DEFAULT_OBSERVER_LAT_DEG, DEFAULT_OBSERVER_LON_DEG)

    // ───────────────────── 双视角 spring 过场（§八.12、§十六.4）─────────────────────

    /** 弹簧当前姿态（位置状态）；打断续接时保留当前位置与速度，不跳帧。 */
    private var poseValue: CameraYawPitch = CameraYawPitch(0.0, 0.0)

    /** 弹簧角速度状态（度 / 秒）；目标切换时速度连续，避免二次弹跳。 */
    private var poseVel: CameraYawPitch = CameraYawPitch(0.0, 0.0)

    /** 过场目标姿态。 */
    private var poseTo: CameraYawPitch = CameraYawPitch(0.0, 0.0)

    /** 过场起算时刻（UTC 毫秒，与帧驱动同一时钟）。 */
    private var transitionStartMs: Long = 0L

    /** 是否处于 800ms spring 过场中；false = 姿态以手势 / 帧现值为准。 */
    private var transitioning: Boolean = false

    // ───────────────────── 手势（切片 20 交互表）─────────────────────

    fun onDragStart() = scene.rotator.onDragStart()

    fun onDrag(dxPx: Double, dyPx: Double) = scene.rotator.onDrag(dxPx, dyPx)

    fun onDragEnd() = scene.rotator.onDragEnd()

    /**
     * 双指捏合：scaleFactor >1 拉近，<1 推远。
     *
     * 相机先按既有地球仪态钳在 [0.6R, 10R]（§十.1）；10R 之后不再推相机，超出部分由
     * [skyZoom] 等效缩放标量续走 `skyBlend` 单曲线（§十.3），满混合锚 = [SkyParams.SKY_BLEND_FULL_ZOOM]。
     */
    fun onZoom(scaleFactor: Float) {
        scene.zoom(scaleFactor)
        applySkyZoom(scaleFactor)
    }

    /** 等效缩放标量（半径倍数）；0 = 尚未从场景距离比初始化，首次捏合时对齐。 */
    private var skyZoom: Float = 0f

    /** 相机 10R 以外的捏合增量 → blend；与 [SkyParams.magLimitFromBlend] 同一条曲线（§十.9）。 */
    private fun applySkyZoom(scaleFactor: Float) {
        if (scaleFactor == 1f) return
        if (skyZoom <= 0f) skyZoom = scene.distanceRatio
        skyZoom = (skyZoom / scaleFactor).coerceIn(
            GlobeConfig.CAMERA_MIN_DISTANCE_RATIO, SkyParams.SKY_BLEND_FULL_ZOOM,
        )
        val blend = blendFromZoom(skyZoom)
        when {
            blend > 0f && scene.sky == null -> enterSky(SkyViewMode.SKY_HEAVEN)
            blend <= 0f && scene.sky != null -> { exitSky(); skyZoom = scene.distanceRatio; return }
        }
        if (scene.sky != null) setSkyBlend(blend)
    }

    /** 等效缩放标量 → blend 0..1：≤10R 地球仪态 blend=0；10R→12R 连续升到 1（§十.3、§十六.14）。 */
    private fun blendFromZoom(zoom: Float): Float {
        if (zoom <= GlobeConfig.CAMERA_MAX_DISTANCE_RATIO) return 0f
        return ((zoom - GlobeConfig.CAMERA_MAX_DISTANCE_RATIO) /
            (SkyParams.SKY_BLEND_FULL_ZOOM - GlobeConfig.CAMERA_MAX_DISTANCE_RATIO))
            .coerceIn(0f, 1f)
    }

    /** 双击飞到点击经纬度。 */
    fun onFlyTo(lat: Double, lng: Double) = scene.flyTo(lat, lng)

    /** 右下角按钮：自动旋转开关（0.2°/s）。 */
    fun toggleAutoRotate() {
        scene.rotator.autoRotate = !scene.rotator.autoRotate
    }

    // ───────────────────── 战役回放（切片 18）─────────────────────

    fun selectCampaign(id: String) = scene.replay.select(id)

    fun play() = scene.replay.play()

    fun pause() = scene.replay.pause()

    fun resetReplay() = scene.replay.reset()

    fun seekTurn(turn: Double) = scene.replay.seek(turn)

    // ───────────────────── 离线兜底 ─────────────────────

    /** 网络观察层写入：在线 / 离线；离线时只读预缓存，未命中走程序化 / 种子兜底。 */
    fun setOnline(online: Boolean) {
        fallback.online = online
    }

    /** 素材下载完成后回填缓存（Android 渲染层调用）。 */
    fun markAssetCached(kind: TextureKind, handle: Any? = null) {
        fallback.textureCache.record(kind, AssetStatus.READY_CACHED, handle)
    }

    // ───────────────────── 帧驱动 ─────────────────────

    /** 推进一帧并产出不可视快照。 */
    fun nextFrame(dt: Double, utcMillis: Long, turnsPerSec: Double = 1.0): FrameSnapshot {
        scene.update(dt, utcMillis, turnsPerSec)
        scene.sky?.let { sky ->
            // 观测点 + 地方恒星时每帧回填；LST 只是两次浮点乘加，不做秒节流也不构成帧负担。
            val lst = SiderealTime.localSiderealTimeDeg(utcMillis, observer.lonDeg)
            val pose = springPose(dt, utcMillis, sky.cameraYawPitch)
            scene.sky = sky.copy(observerLatLon = observer, siderealTimeDeg = lst, cameraYawPitch = pose)
        }
        return FrameSnapshot.from(scene)
    }

    /**
     * 注入真实观测点（定位权限已授予时由 Android 层调用）；越界钳制，无弹窗。
     * 未调用时保持长安默认示意（§十三.5）。
     */
    fun setObserver(latDeg: Double, lonDeg: Double) {
        observer = ObserverLatLon(
            latDeg.coerceIn(-90.0, 90.0),
            lonDeg.coerceIn(-180.0, 180.0),
        )
    }

    // ───────────────────── 星空视角（§十二.3；既有方法签名只增不改）─────────────────────

    /**
     * 进入天外 / 地上星空视角（§九.1 模式互斥）。保留当前帧其它字段现值，仅切模式；
     * 尚未入星空时先建默认 [SkyFrame]。
     */
    fun enterSky(mode: SkyViewMode) {
        val prev = scene.sky
        if (prev != null && prev.mode == mode) return
        // 切换中重复点击：以当前弹簧位置为新起点、速度连续续接，不跳帧、不闪（§八.12 可打断）。
        val from = if (transitioning) poseValue
            else prev?.cameraYawPitch ?: CameraYawPitch(0.0, 0.0)
        beginTransition(from, CameraYawPitch(from.yawDeg, 0.0))
        scene.sky = (prev ?: SkyFrame()).copy(mode = mode)
    }

    /**
     * 连续混合标量：0 地球视觉直径 400px，1 缩到 40px 锚（§十.3）；钳 [0,1]，
     * 保留当前帧其它字段现值。magLimit 走同一曲线 [SkyParams.magLimitFromBlend]（§十.9），
     * 不再由调用方另算星等上限。
     */
    fun setSkyBlend(v: Float) {
        val blend = v.coerceIn(0f, 1f)
        scene.sky = (scene.sky ?: SkyFrame()).copy(
            skyBlend = blend,
            magLimit = SkyParams.magLimitFromBlend(blend),
        )
    }

    /** 退出星空：清空星空帧，地球仪态行为回到进入前一字不差（§十.3 反向对称）。 */
    fun exitSky() {
        scene.sky = null
    }

    /** 点选写回：选中星 / 星官 / 星座 / 分野 id；未入星空时忽略，不凭空建帧。 */
    fun selectStar(id: Long?) {
        scene.sky = scene.sky?.copy(selectedId = id)
    }

    /** 星空相机姿态写回（天外旋转天球 / 地上平移方位）；未入星空时忽略。手势接管即终止过场。 */
    fun setSkyPose(yawDeg: Double, pitchDeg: Double) {
        val cur = scene.sky ?: return
        transitioning = false
        scene.sky = cur.copy(cameraYawPitch = CameraYawPitch(yawDeg, pitchDeg))
    }

    // ───────────────────── 过场 spring 内部 ─────────────────────

    private fun beginTransition(from: CameraYawPitch, to: CameraYawPitch) {
        poseValue = from
        poseVel = CameraYawPitch(0.0, 0.0)
        poseTo = to
        transitionStartMs = System.currentTimeMillis()
        transitioning = true
    }

    /**
     * 二阶弹簧逐帧积分：a = ωn²·(to−x) − 2ζωn·v；位置 / 速度状态帧间保留。
     * 阻尼比 ζ、固有角频率 ωn 见伴生常量；过场时长由 [SkyParams.VIEW_SWITCH_MS]（800ms）
     * 收口，到时强制落目标位，禁 linear 收尾（§八.12、§十六.4）。
     */
    private fun springPose(dtSec: Double, nowMs: Long, current: CameraYawPitch?): CameraYawPitch {
        if (!transitioning) return current ?: poseTo
        if (nowMs - transitionStartMs >= SkyParams.VIEW_SWITCH_MS) {
            transitioning = false
            poseVel = CameraYawPitch(0.0, 0.0)
            return poseTo
        }
        val dt = dtSec.coerceIn(0.0, 0.1)
        val w = SPRING_NATURAL_FREQ_RAD_PER_S
        val zeta = SPRING_DAMPING_RATIO
        val ay = w * w * (poseTo.yawDeg - poseValue.yawDeg) - 2.0 * zeta * w * poseVel.yawDeg
        val ap = w * w * (poseTo.pitchDeg - poseValue.pitchDeg) - 2.0 * zeta * w * poseVel.pitchDeg
        val vy = poseVel.yawDeg + ay * dt
        val vp = poseVel.pitchDeg + ap * dt
        poseVel = CameraYawPitch(vy, vp)
        poseValue = CameraYawPitch(poseValue.yawDeg + vy * dt, poseValue.pitchDeg + vp * dt)
        return poseValue
    }

    private companion object {
        /** 无定位时默认观测点：长安（34.34°N / 108.94°E），标「示意」（§十三.5）。 */
        const val DEFAULT_OBSERVER_LAT_DEG: Double = 34.34
        const val DEFAULT_OBSERVER_LON_DEG: Double = 108.94

        /** 阻尼比 ζ=1.0（临界阻尼）：视角切换不弹回、不振荡；余量留给姿态衔接而非过冲装饰。 */
        const val SPRING_DAMPING_RATIO: Double = 1.0

        /** 固有角频率 ωn=6.25 rad/s：ζ·ωn·0.8s≈5，指数衰减项 e⁻⁵≈0.7%，800ms 内收敛进 2% 误差带。 */
        const val SPRING_NATURAL_FREQ_RAD_PER_S: Double = 6.25
    }
}
