package io.legado.app.ci.screenshot

import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import io.legado.app.ci.screenshot.SkyRenderView
import io.legado.app.ci.screenshot.SkyStateFixtures
import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.render.FrameSnapshot
import org.junit.Rule
import org.junit.Test

/**
 * 判据 1（规格 §十四.1）连续缩放序列帧：10.0R→12.0R 区间内等步长出帧，两端外侧各补一档。
 *
 * 〔本单能证明什么〕相邻帧像素差分量化是沙箱内可拿到的最强静态证据：若渲染被改回档位制，
 * 档边两帧之间会吞掉整段连续变化，差分曲线上出现尖峰；连续单曲线则相邻差异平滑、无尖峰。
 * 〔本单不能证明什么〕真机捏合录屏的逐帧观感（无网格换面、无星点突增、无整层淡入）仍须
 * 真机或带屏 CI 复核；layoutlib 帧不含真机 GPU 栅格与触控采样。
 *
 * 缩放档与 blend 的映射逐字对照生产 [GlobeController.blendFromZoom]（GlobeController.kt:93-98）：
 * ≤10R 为 blend=0 平台段；10R→12R 线性升到 1；12R 以上钳在 blend=1 平台段。测试侧只复刻
 * 同一条公式，不另写档位表。
 *
 * 帧后差分分析脚本：`screenshot/src/test/python/zoom_frame_diff.py`；差分数值与帧清单落盘
 * 路径见证据报告 `docs/近似单-星空四项真机前证据.md`。
 */
class SkyZoomSequenceSnapshotTest {

    @get:Rule
    val paparazzi: Paparazzi = Paparazzi()

    /**
     * 缩放档序列：首档 9.00R（10R 外侧），末档 12.50R（12R 外侧），中间 12 档覆盖 10.00→12.00。
     * 档名固定两位小数，便于差分脚本按文件名排序配对相邻帧。
     */
    private val zoomLevels: List<Pair<Float, String>> = listOf(
        9.00f to "09.00",
        10.00f to "10.00",
        10.18f to "10.18",
        10.36f to "10.36",
        10.55f to "10.55",
        10.73f to "10.73",
        10.91f to "10.91",
        11.09f to "11.09",
        11.27f to "11.27",
        11.45f to "11.45",
        11.64f to "11.64",
        11.82f to "11.82",
        12.00f to "12.00",
        12.50f to "12.50",
    )

    /** 与 GlobeController.blendFromZoom 同公式；常量引用契约层，不写字面量副本。 */
    private fun blendFromZoom(zoom: Float): Float {
        if (zoom <= GlobeConfig.CAMERA_MAX_DISTANCE_RATIO) return 0f
        return ((zoom - GlobeConfig.CAMERA_MAX_DISTANCE_RATIO) /
            (SkyParams.SKY_BLEND_FULL_ZOOM - GlobeConfig.CAMERA_MAX_DISTANCE_RATIO))
            .coerceIn(0f, 1f)
    }

    private fun snapshotFor(zoom: Float): FrameSnapshot {
        val controller = GlobeController()
        controller.enterSky(SkyViewMode.SKY_HEAVEN)
        controller.setSkyBlend(blendFromZoom(zoom))
        controller.setSkyPose(0.0, 0.0)
        return controller.nextFrame(0.016, SkyStateFixtures.FIXED_UTC_MILLIS)
            .copy(
                skyDegradation = DegradationLevel.FULL_TEXTURES,
                fallbackResolutions = emptyList(),
            )
    }

    /**
     * 一轮出完全部缩放档：每档复位渲染器跨态状态面，装同一星表与固定时刻，避免闪烁相位
     * 或 hover/press 残留混入帧间差分。
     */
    @Test
    fun `连续缩放十四档序列帧`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        for ((zoom, label) in zoomLevels) {
            SkyStateFixtures.resetRendererState()
            val view = SkyRenderView(paparazzi.context)
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            view.snapshot = snapshotFor(zoom)
            view.store = store
            view.nowMs = SkyStateFixtures.FIXED_UTC_MILLIS
            paparazzi.snapshot(view, "sky-zoom-$label")
        }
    }
}
