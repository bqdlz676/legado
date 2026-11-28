package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.PyramidResult
import io.legado.app.ui.map.globe.astro.data.RegionHitResolver
import io.legado.app.ui.map.globe.geo.Geo
import kotlin.math.min

/**
 * 星空指针手势状态机（纯 JVM 逻辑，不引 Compose 控件，§九.3）。
 *
 * 职责：把 Compose 侧指针事件翻译成门面写回入口——天外旋转 / 地上平移走
 * [GlobeController.setSkyPose]，捏合走 [GlobeController.onZoom]（混合曲线在门面内），
 * 点星走 [SkyCanvasRenderer.pickStar] 拾取后 [GlobeController.selectStar] 写回；
 * 热区命中优先于星点拾取。未入星空时，拖动 / 缩放原样转发地球仪态路径，行为一字不变。
 *
 * 〔零分配口径〕状态全标量；拾取复用渲染器既有 [io.legado.app.ui.map.globe.astro.pick.StarPicker]
 * 24dp 网格 / 20dp 半径 / 重叠取最亮，本类不另建分桶结构。
 *
 * 〔D9 收口〕宿主 Compose 壳只做薄指针转发：双击反投影飞行、错误提示重试判定等细节
 * 都在本类内，宿主不堆手势细节代码。
 */
class SkyInputHandler(
    private val controller: GlobeController,
    private val renderer: SkyCanvasRenderer,
    private val resolver: RegionHitResolver = RegionHitResolver(RegionHitResolver.defaultAnchors()),
    /** 金字塔层加载入口（宿主绑定到 `StarStore.ensurePyramid`）；默认空实现 = 不触发。 */
    private val pyramidLoader: (level: Int) -> PyramidResult? = { null },
    /** 加载进度观测（真实比值；见 catalog 进度钩子口径）；默认空实现。 */
    private val onPyramidProgress: (level: Int, done: Long, total: Long) -> Unit = { _, _, _ -> },
    /** 金字塔错误态回填（Corrupt / LowMemory）；默认空实现。 */
    private val onPyramidError: (String) -> Unit = {},
    /** 可注入时间源（ms epoch）；测试推进时钟用，默认系统时钟。 */
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private var dragging = false
    private var skyYaw = 0.0
    private var skyPitch = 0.0

    /** 双指捏合倍率累积；≥2× 触发局部星图（§九.3），松手后归零。 */
    private var pinchAccum = 1f
    private var chartAnnounced = false

    /** 最近一次视图尺寸（px）；双击路径回填，宿主亦可经 [setViewport] 注入。 */
    private var viewport: Pair<Float, Float> = 0f to 0f

    // ── 金字塔触发链（A 件）：在途去重集合 + 失败冷却截止时刻表 ──
    private val inFlight = HashSet<Int>()
    private val cooldownUntil = HashMap<Int, Long>()

    // ── 悬停回弹计时（D 件）：当前悬停星 id 与起算时刻；-1 = 无悬停 ──
    private var hoverTrackId: Long = -1L
    private var hoverTrackStartMs: Long = 0L

    /** 注入视图尺寸（px）；宿主 Composable 尺寸变化时调用，供地球侧点按反投影反查州域。 */
    fun setViewport(widthPx: Float, heightPx: Float) {
        viewport = widthPx to heightPx
    }

    // ───────────────────── 单指拖动（天外旋转天球 / 地上平移方位）─────────────────────

    fun onDragStart(x: Float, y: Float) {
        dragging = true
        if (isSky()) {
            val pose = controller.scene.sky?.cameraYawPitch
            skyYaw = pose?.yawDeg ?: 0.0
            skyPitch = pose?.pitchDeg ?: 0.0
        } else {
            controller.onDragStart()
        }
    }

    fun onDrag(dxPx: Float, dyPx: Float) {
        if (!dragging) return
        if (isSky()) {
            // 与地球仪 rotator 同一灵敏度 0.25°/px（切片 20 交互表）；俯仰钳 ±85°。
            skyYaw += dxPx * SENSITIVITY_DEG_PER_PX
            skyPitch = (skyPitch - dyPx * SENSITIVITY_DEG_PER_PX).coerceIn(-PITCH_LIMIT_DEG, PITCH_LIMIT_DEG)
            controller.setSkyPose(skyYaw, skyPitch)
        } else {
            controller.onDrag(dxPx.toDouble(), dyPx.toDouble())
        }
    }

    fun onDragEnd() {
        dragging = false
        pinchAccum = 1f
        chartAnnounced = false
        if (!isSky()) controller.onDragEnd()
    }

    // ───────────────────── 双指捏合（走门面缩放 / 混合同一条曲线）─────────────────────

    fun onZoom(scaleFactor: Float) {
        controller.onZoom(scaleFactor)
        // §九.3：双指 ≥2× 进局部星图；按需下载留 TODO，先出可观测反馈，不许静默。
        // 只累计拉近倍率（>1）；推远不抬高局部星图档位，松手归零。
        pinchAccum *= if (scaleFactor > 1f) scaleFactor.coerceAtMost(10f) else 1f
        if (!chartAnnounced && pinchAccum >= CHART_ZOOM_THRESHOLD) {
            chartAnnounced = true
            renderer.notifyLocalChartRequested()
        }
        // 缩放改变 blend → magLimit 随之连续变化；按当前星等上限评估金字塔层需求。
        evaluatePyramidDemand()
    }

    /**
     * 按当前帧 `magLimit` 评估金字塔层需求（A 件触发链）：
     *  - magLimit > 6.5 触发 L1 (6.5, 9.0]；
     *  - magLimit > 9.0 再触发 L2 (9.0, 11.5]。
     * 本方法只读门面当前星等，不主动改相机；宿主帧节拍 / 进入星空后也可调 [refreshPyramidDemand]。
     */
    fun evaluatePyramidDemand() {
        val magLimit = controller.scene.sky?.magLimit ?: return
        if (magLimit > LEVEL2_TRIGGER_MAG) {
            triggerPyramid(2)
            triggerPyramid(1)
        } else if (magLimit > MAG_BRIGHT_ENTITY) {
            triggerPyramid(1)
        }
    }

    /** 对外暴露的需求刷新入口（宿主帧节拍进入星空后调用，与缩放路径同一套去重冷却口径）。 */
    fun refreshPyramidDemand() = evaluatePyramidDemand()

    /**
     * 单层触发：在途去重 + 失败冷却，避免重试风暴。
     *
     * 去重：同一 level 已有加载在途时直接跳过，不并发重复拉片。
     * 冷却：失败（Missing / Corrupt / LowMemory）后该层进入 [PYRAMID_RETRY_COOLDOWN_MS]
     * 冷却，冷却期内不再触发。冷却长度取 30s 的工程依据：Missing 多为瞬时网络态，30s 后用户
     * 重连或再次缩放即可补齐；Corrupt 坏片已被隔离，短冷却避免反复扫描重扫；LowMemory 等待
     * 内存回收。再短会在弱网下刷请求风暴，再长则网络恢复后补片滞后，30s 取二者折中。
     */
    private fun triggerPyramid(level: Int) {
        if (inFlight.contains(level)) return
        val now = clock()
        val until = cooldownUntil[level] ?: 0L
        if (now < until) return
        inFlight.add(level)
        try {
            val result = pyramidLoader(level) ?: return
            when (result) {
                is PyramidResult.Loaded -> {
                    cooldownUntil.remove(level)
                    onPyramidProgress(level, result.added.toLong(), result.added.toLong())
                }
                PyramidResult.Missing -> {
                    // 瞬时网络态：记冷却但不弹错误提示，不判损坏、不清 L0。
                    cooldownUntil[level] = now + PYRAMID_RETRY_COOLDOWN_MS
                }
                PyramidResult.Corrupt, PyramidResult.LowMemory -> {
                    cooldownUntil[level] = now + PYRAMID_RETRY_COOLDOWN_MS
                    onPyramidError(
                        if (result is PyramidResult.Corrupt) "星表切片损坏，已回落内置亮星层；点按重试"
                        else "内存不足，暗星层暂未加载；已回落内置亮星层",
                    )
                }
            }
        } finally {
            inFlight.remove(level)
        }
    }

    // ───────────────────── 点按：错误重试优先 → 热区 → 星点拾取 ─────────────────────

    fun onTap(x: Float, y: Float) {
        // §七.7：错误态一次性提示可点击重试；点在顶部提示带内直接触发重加载，不走进星拾取。
        if (renderer.errorHint != null && y < ERROR_HINT_BAND_PX) {
            renderer.retryAction?.invoke()
            return
        }
        if (!isSky()) {
            focusRegionOnEarthTap(x, y)
            return
        }
        renderer.hotZones.firstOrNull { it.rect.contains(x, y) }?.let { zone ->
            dispatchZone(zone.id)
            return
        }
        val id = renderer.pickStar(x, y)
        controller.selectStar(if (id >= 0L) id else null)
    }

    /**
     * 地球仪态点按 → 反投影经纬度 → 分野州域反查（§十一.2 反向链路）。
     * 命中：观测点落到触点并聚焦该州域；未命中 / 视图尺寸未注入：地球仪态保持现状，
     * 不触发飞行、不进星空（施工图 2.3 边界 3）。
     */
    private fun focusRegionOnEarthTap(x: Float, y: Float) {
        val (w, h) = viewport
        if (w <= 0f || h <= 0f) return
        val rotator = controller.scene.rotator
        val proj = GlobeProjection(
            viewportWidth = w,
            viewportHeight = h,
            radiusPx = min(w, h) * 0.32f,
            yawDeg = rotator.yawDeg,
            pitchDeg = rotator.pitchDeg,
        )
        val dir = proj.unproject(x, y) ?: return
        val (lat, lng) = Geo.latLng(dir)
        val region = resolver.resolve(lat, lng) ?: return
        // 选点即观测点：触点经纬度写回观测点（N3 选点入口接线），再聚焦分野州域。
        controller.setObserver(lat, lng)
        controller.focusFieldRegion(region, null)
    }

    // ───────────────────── 按下 / 抬手：悬停预压与按下两阶段（§七.2、§七.3）─────────────────────

    /** 触点落下：触屏无 hover，落下即记 1.3× 预压；命中星同时挂按下态，门槛由渲染器 60ms 把关。 */
    fun onPressStart(x: Float, y: Float) {
        if (!isSky()) return
        val id = renderer.pickStar(x, y)
        if (id >= 0L) {
            markHover(id)
            renderer.pressId = id
            renderer.pressStartMs = System.currentTimeMillis()
        }
    }

    /** 抬手：悬停 / 按下态全部回弹，无残留（§七.2 抬手回弹无残留）。 */
    fun onPressEnd() {
        renderer.hoverId = -1L
        renderer.pressId = -1L
        hoverTrackId = -1L
    }

    /**
     * 起算悬停跟踪：记录悬停星 id 与起算时刻（D 件，可注入时间源便于测试推进时钟）。
     * 新命中星覆盖旧跟踪，旧悬停态一并回弹。internal 便于纯 JVM 单测直接标记悬停再推进时钟。
     */
    internal fun markHover(id: Long) {
        renderer.hoverId = id
        hoverTrackId = id
        hoverTrackStartMs = clock()
    }

    /**
     * 悬停回弹节拍（D 件）：距 [hoverTrackStartMs] 超过 [HOVER_RESET_MS] 仍无新操作，
     * 把悬停态回落（hoverId 归 -1），无残留。宿主帧节拍可逐帧调用；测试直接传推进后的时钟。
     * 120ms 内不回落，超时才回落；抬手路径 [onPressEnd] 已即时清态，不依赖本节拍。
     */
    fun tickHover(nowMs: Long = clock()) {
        if (hoverTrackId < 0L) return
        if (nowMs - hoverTrackStartMs > HOVER_RESET_MS) {
            renderer.hoverId = -1L
            hoverTrackId = -1L
        }
    }

    // ───────────────────── 双击飞行（地球仪态；细节从宿主壳下沉到此）─────────────────────

    /** 双击屏幕位置 → 反投影经纬度 → 门面飞行；落在球盘外不凭空造点位。 */
    fun onDoubleTap(x: Float, y: Float, viewport: Pair<Float, Float>) {
        this.viewport = viewport
        val (w, h) = viewport
        if (w <= 0f || h <= 0f) return
        val rotator = controller.scene.rotator
        val proj = GlobeProjection(
            viewportWidth = w,
            viewportHeight = h,
            radiusPx = min(w, h) * 0.32f,
            yawDeg = rotator.yawDeg,
            pitchDeg = rotator.pitchDeg,
        )
        val dir = proj.unproject(x, y) ?: return
        val (lat, lng) = Geo.latLng(dir)
        controller.onFlyTo(lat, lng)
    }

    // ───────────────────── 长按空白：显隐全部名称（§九.3）─────────────────────

    fun onLongPress(x: Float, y: Float) {
        if (!isSky()) return
        if (renderer.pickStar(x, y) < 0L) renderer.showNames = !renderer.showNames
    }

    // ───────────────────── 分野反向映射（§十一.2；地球命中路径缺口见交付报告）─────────────────────

    /** 州域 → 关联星官内码；地球侧命中路径目前只产经纬度、产不出州域名，拿到域名后接此映射转向。 */
    fun asterismCodesForRegion(region: String): List<String> = renderer.asterismCodesForRegion(region)

    // ───────────────────── 内部 ─────────────────────

    private fun dispatchZone(id: String) {
        when (id) {
            ZONE_HEAVEN -> controller.enterSky(SkyViewMode.SKY_HEAVEN)
            ZONE_EARTH -> controller.enterSky(SkyViewMode.SKY_EARTH)
            ZONE_ASTERISM -> renderer.contentMode = ZONE_ASTERISM
            ZONE_CONSTELLATION -> renderer.contentMode = ZONE_CONSTELLATION
        }
    }

    private fun isSky(): Boolean = controller.scene.sky != null

    private companion object {
        const val SENSITIVITY_DEG_PER_PX: Double = 0.25
        const val PITCH_LIMIT_DEG = 85.0
        /** 双指累计倍率 ≥2× 触发局部星图（§九.3）。 */
        const val CHART_ZOOM_THRESHOLD: Float = 2f
        /** 错误提示可点重试的顶部提示带高度（px，粗略口径；提示文案落顶部左侧）。 */
        const val ERROR_HINT_BAND_PX: Float = 60f
        /** 实体星与金字塔分界 6.5 等，单源引用契约常量，不写字面量副本。 */
        val MAG_BRIGHT_ENTITY: Float = io.legado.app.ui.map.globe.astro.contract.SkyParams.MAG_BRIGHT_ENTITY
        /** L1/L2 分界 9.0 等（方案 §1.3；提为 SkyParams 契约常量属交接项，本单不改契约层）。 */
        const val LEVEL2_TRIGGER_MAG: Float = 9.0f
        /** 悬停无操作回弹阈值 120ms（§七.2）。 */
        const val HOVER_RESET_MS: Long = 120L
        /** 金字塔单层失败后冷却时长 30s（去重防风暴；理由见 [triggerPyramid] 注释）。 */
        const val PYRAMID_RETRY_COOLDOWN_MS: Long = 30_000L
        const val ZONE_ASTERISM: String = "mode-asterism"
        const val ZONE_CONSTELLATION: String = "mode-constellation"
        const val ZONE_HEAVEN: String = "view-heaven"
        const val ZONE_EARTH: String = "view-earth"
    }
}
