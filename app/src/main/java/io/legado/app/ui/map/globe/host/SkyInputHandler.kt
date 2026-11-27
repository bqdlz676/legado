package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode

/**
 * 星空指针手势状态机（纯 JVM 逻辑，不引 Compose / Android 控件，§九.3）。
 *
 * 职责：把 Compose 侧指针事件翻译成门面写回入口——天外旋转 / 地上平移走
 * [GlobeController.setSkyPose]，捏合走 [GlobeController.onZoom]（混合曲线在门面内），
 * 点星走 [SkyCanvasRenderer.pickStar] 拾取后 [GlobeController.selectStar] 写回；
 * 热区命中优先于星点拾取。未入星空时，拖动 / 缩放原样转发地球仪态路径，行为一字不变。
 *
 * 〔零分配口径〕状态全标量；拾取复用渲染器既有 [io.legado.app.ui.map.globe.astro.pick.StarPicker]
 * 24dp 网格 / 20dp 半径 / 重叠取最亮，本类不另建分桶结构。
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

    // ───────────────────── 点按：热区优先 → 星点拾取 ─────────────────────

    fun onTap(x: Float, y: Float) {
        if (!isSky()) return
        renderer.hotZones.firstOrNull { it.rect.contains(x, y) }?.let { zone ->
            dispatchZone(zone.id)
            return
        }
        val id = renderer.pickStar(x, y)
        controller.selectStar(if (id >= 0L) id else null)
    }

    // ───────────────────── 长按空白：显隐全部名称（§九.3）─────────────────────

    fun onLongPress(x: Float, y: Float) {
        if (!isSky()) return
        if (renderer.pickStar(x, y) < 0L) renderer.showNames = !renderer.showNames
    }

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
        const val ZONE_ASTERISM: String = "mode-asterism"
        const val ZONE_CONSTELLATION: String = "mode-constellation"
        const val ZONE_HEAVEN: String = "view-heaven"
        const val ZONE_EARTH: String = "view-earth"
    }
}
