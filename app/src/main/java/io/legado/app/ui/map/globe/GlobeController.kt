package io.legado.app.ui.map.globe

import io.legado.app.ui.map.globe.astro.contract.CameraYawPitch
import io.legado.app.ui.map.globe.astro.contract.SkyFrame
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
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
        return FrameSnapshot.from(scene)
    }

    // ───────────────────── 星空视角（§十二.3；既有方法签名只增不改）─────────────────────

    /**
     * 进入天外 / 地上星空视角（§九.1 模式互斥）。保留当前帧其它字段现值，仅切模式；
     * 尚未入星空时先建默认 [SkyFrame]。
     */
    fun enterSky(mode: SkyViewMode) {
        scene.sky = (scene.sky ?: SkyFrame()).copy(mode = mode)
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

    /** 星空相机姿态写回（天外旋转天球 / 地上平移方位）；未入星空时忽略。 */
    fun setSkyPose(yawDeg: Double, pitchDeg: Double) {
        val cur = scene.sky ?: return
        scene.sky = cur.copy(cameraYawPitch = CameraYawPitch(yawDeg, pitchDeg))
    }
}
