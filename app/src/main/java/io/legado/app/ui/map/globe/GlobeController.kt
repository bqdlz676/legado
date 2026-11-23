package io.legado.app.ui.map.globe

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

    /** 双指捏合：scaleFactor >1 拉近，<1 推远。 */
    fun onZoom(scaleFactor: Float) = scene.zoom(scaleFactor)

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
}
