package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
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
) {

    private var dragging = false
    private var skyYaw = 0.0
    private var skyPitch = 0.0

    /** 双指捏合倍率累积；≥2× 触发局部星图（§九.3），松手后归零。 */
    private var pinchAccum = 1f
    private var chartAnnounced = false

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
    }

    // ───────────────────── 点按：错误重试优先 → 热区 → 星点拾取 ─────────────────────

    fun onTap(x: Float, y: Float) {
        // §七.7：错误态一次性提示可点击重试；点在顶部提示带内直接触发重加载，不走进星拾取。
        if (renderer.errorHint != null && y < ERROR_HINT_BAND_PX) {
            renderer.retryAction?.invoke()
            return
        }
        if (!isSky()) return
        renderer.hotZones.firstOrNull { it.rect.contains(x, y) }?.let { zone ->
            dispatchZone(zone.id)
            return
        }
        val id = renderer.pickStar(x, y)
        controller.selectStar(if (id >= 0L) id else null)
    }

    // ───────────────────── 按下 / 抬手：悬停预压与按下两阶段（§七.2、§七.3）─────────────────────

    /** 触点落下：触屏无 hover，落下即记 1.3× 预压；命中星同时挂按下态，门槛由渲染器 60ms 把关。 */
    fun onPressStart(x: Float, y: Float) {
        if (!isSky()) return
        val id = renderer.pickStar(x, y)
        if (id >= 0L) {
            renderer.hoverId = id
            renderer.pressId = id
            renderer.pressStartMs = System.currentTimeMillis()
        }
    }

    /** 抬手：悬停 / 按下态全部回弹，无残留（§七.2 抬手回弹无残留）。 */
    fun onPressEnd() {
        renderer.hoverId = -1L
        renderer.pressId = -1L
    }

    // ───────────────────── 双击飞行（地球仪态；细节从宿主壳下沉到此）─────────────────────

    /** 双击屏幕位置 → 反投影经纬度 → 门面飞行；落在球盘外不凭空造点位。 */
    fun onDoubleTap(x: Float, y: Float, viewport: Pair<Float, Float>) {
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
        const val ZONE_ASTERISM: String = "mode-asterism"
        const val ZONE_CONSTELLATION: String = "mode-constellation"
        const val ZONE_HEAVEN: String = "view-heaven"
        const val ZONE_EARTH: String = "view-earth"
    }
}
