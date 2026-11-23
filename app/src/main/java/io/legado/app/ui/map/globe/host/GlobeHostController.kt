package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.offline.TextureKind
import io.legado.app.ui.map.globe.render.FrameSnapshot
import io.legado.app.ui.map.globe.replay.campaign.Campaign

/**
 * 地球仪宿主粘合层（纯 JVM，不引用 Android / Compose 类型，便于在单测里驱动完整关键路径）。
 *
 * 职责：把 [GlobeController] 门面的手势、回放、离线兜底入口收口成宿主可直接消费的
 * 状态与派生文案；Compose 壳只负责按帧重读快照并自绘，不直接摸 rotator / lod / fallback。
 * 为什么不再包一层场景逻辑：门面已经是纯 JVM 唯一入口，这里只做转发与展示态派生，
 * 避免在宿主层复制一份回放 / 离线状态机。
 *
 * 边界兜底：无匹配战役、离线无缓存、素材未就位等分支都由门面返回非空兜底结果；
 * 本层派生文案同样不允许出现空白态。
 */
class GlobeHostController(
    val controller: GlobeController = GlobeController(),
) {

    // ───────────────────── 帧驱动 ─────────────────────

    /** 推进一帧并取回不可视快照；dtSec 为帧间隔秒，utcMillis 用于太阳方位。 */
    fun frame(dtSec: Double, utcMillis: Long, turnsPerSec: Double = 1.0): FrameSnapshot =
        controller.nextFrame(dtSec, utcMillis, turnsPerSec)

    // ───────────────────── 手势（转发门面，切片 20 交互表）─────────────────────

    fun dragStart() = controller.onDragStart()

    fun drag(dxPx: Double, dyPx: Double) = controller.onDrag(dxPx, dyPx)

    fun dragEnd() = controller.onDragEnd()

    /** 双指捏合：scaleFactor >1 拉近，<1 推远。 */
    fun zoom(scaleFactor: Float) = controller.onZoom(scaleFactor)

    /** 双击飞到点击经纬度。 */
    fun flyTo(lat: Double, lng: Double) = controller.onFlyTo(lat, lng)

    /** 右下角按钮：自动旋转开关。 */
    fun toggleAutoRotate() = controller.toggleAutoRotate()

    // ───────────────────── 战役回放 ─────────────────────

    val campaigns: List<Campaign> get() = controller.scene.replay.campaigns

    val currentCampaign: Campaign? get() = controller.scene.replay.current

    fun selectCampaign(id: String) = controller.selectCampaign(id)

    fun play() = controller.play()

    fun pause() = controller.pause()

    fun resetReplay() = controller.resetReplay()

    fun seekTurn(turn: Double) = controller.seekTurn(turn)

    // ───────────────────── 离线兜底 ─────────────────────

    fun setOnline(online: Boolean) = controller.setOnline(online)

    fun markAssetCached(kind: TextureKind, handle: Any? = null) =
        controller.markAssetCached(kind, handle)

    // ───────────────────── 展示态派生（纯 JVM 可断言）─────────────────────

    val playing: Boolean get() = controller.scene.replay.playing

    val turn: Double get() = controller.scene.replay.turn

    val autoRotate: Boolean get() = controller.scene.rotator.autoRotate

    val distanceRatio: Float get() = controller.scene.distanceRatio

    /** 当前回合所处阶段名；无阶段返回 null，宿主回退为不显示阶段签。 */
    fun phaseLabel(): String? = controller.scene.replay.currentPhase()?.label

    /** 退化档位 → 兜底文案；三档都有可画结果，不出现空白说明。 */
    fun degradationLabel(level: DegradationLevel): String = when (level) {
        DegradationLevel.FULL_TEXTURES -> "纹理就位"
        DegradationLevel.CACHED_MIX -> "部分缓存 · 离线可读"
        DegradationLevel.PROCEDURAL_ONLY -> "离线兜底 · 程序化舆图"
    }

    /** 路线汇总：弧长公里 + 日程（按日行里数换算）。 */
    fun routeSummary(snapshot: FrameSnapshot): String {
        val route = snapshot.route ?: return "无行军路线"
        val km = route.totalKm()
        val days = route.marchDays()
        return "弧长 ${km.toInt()} 公里 · 行程约 $days 日"
    }

    /** 素材就位计数：real/total，离线未命中时仍显示兜底覆盖情况。 */
    fun fallbackSummary(snapshot: FrameSnapshot): String {
        val real = snapshot.fallbackResolutions.count { it.status.hasRealTexture }
        val total = snapshot.fallbackResolutions.size
        return "$real/$total 素材就位"
    }

    /** 当前战役阵营名串（顶栏 / 信息卡用）。 */
    fun sidesLabel(): String =
        currentCampaign?.sides?.joinToString(" · ") { it.name }.orEmpty()
}
