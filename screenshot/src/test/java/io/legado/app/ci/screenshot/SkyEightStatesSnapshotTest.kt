package io.legado.app.ci.screenshot

import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import io.legado.app.ci.screenshot.SkyRenderView
import io.legado.app.ci.screenshot.SkyStateFixtures
import io.legado.app.ui.map.globe.host.SkyCanvasRenderer
import io.legado.app.ui.map.globe.offline.AssetStatus
import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.render.FrameSnapshot
import org.junit.Rule
import org.junit.Test

/**
 * 判据 11（规格 §十四）：星空八态逐态出帧。
 *
 * 覆盖口径、状态定义出处、观感判定要点与不可自动化缺口见
 * `docs/截图-星空八态帧证据.md`；本类只挂用例。帧由 [SkyCanvasRenderer] 真实渲染路径
 * 产出，不画假图、不把状态参数硬编码成观感正确却绕开真实分支。
 *
 * 态 7（错误 / 校验失败）当前无公开入口可注入异常计数目录，本类不出伪造帧；
 * 缺口与所需入口见证据文档「反例边界」一节。
 */
class SkyEightStatesSnapshotTest {

    @get:Rule
    val paparazzi: Paparazzi = Paparazzi()

    private fun render(
        name: String,
        snapshot: FrameSnapshot,
        store: io.legado.app.ui.map.globe.astro.data.StarStore?,
        setup: SkyRenderView.() -> Unit = {},
    ) {
        SkyStateFixtures.resetRendererState()
        val view = SkyRenderView(paparazzi.context)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        view.snapshot = snapshot
        view.store = store
        view.setup()
        paparazzi.snapshot(view, name)
    }

    /** 态 1 · 默认（星空就绪）：skyBlend=1，星点按 magLimit 连续裁剪，无整层突现。 */
    @Test
    fun `态1 默认星空就绪`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.FULL_TEXTURES,
                fallbackResolutions = emptyList(),
            )
        render("sky-state-1-default", snapshot, store)
    }

    /** 态 2 · 悬停：压住星点外扩 1.3×；120ms 回弹时序见证据文档缺口说明。 */
    @Test
    fun `态2 悬停星点外扩1.3倍`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.FULL_TEXTURES,
                fallbackResolutions = emptyList(),
            )
        val target = SkyStateFixtures.firstVisibleStarId(store, 0.0, 0.0)
        render("sky-state-2-hover", snapshot, store) {
            SkyCanvasRenderer.hoverId = target
        }
    }

    /** 态 3 · 按下：60ms 门槛后 1.6× 放大并进入 1200ms 呼吸；静态帧取过门后呼吸段。 */
    @Test
    fun `态3 按下1.6倍呼吸`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.FULL_TEXTURES,
                fallbackResolutions = emptyList(),
            )
        val target = SkyStateFixtures.firstVisibleStarId(store, 0.0, 0.0)
        render("sky-state-3-press", snapshot, store) {
            SkyCanvasRenderer.pressId = target
            SkyCanvasRenderer.pressStartMs = nowMs - 200L
        }
    }

    /** 态 4 · 加载（金字塔下载中）：已有星照常显示，顶部 1px brandGold 进度线（非菊花）。 */
    @Test
    fun `态4 金字塔下载中金线进度`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.CACHED_MIX,
                fallbackResolutions = SkyStateFixtures.skyResolutions(AssetStatus.PENDING_DOWNLOAD),
            )
        render("sky-state-4-loading", snapshot, store)
    }

    /** 态 5 · 禁用（低端机）：magLimit 封顶 6.5，银河 / 尘粒 / 流星关闭，功能不消失只减层。 */
    @Test
    fun `态5 低端机禁用减层`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.PROCEDURAL_ONLY,
                fallbackResolutions = emptyList(),
            )
        render("sky-state-5-lowpower", snapshot, store)
    }

    /** 态 6 · 空（无星表未下载）：种子 9173 程序化兜底散点 + 示意横幅，绝不空白、绝不崩。 */
    @Test
    fun `态6 无星表种子兜底`() {
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.FULL_TEXTURES,
                fallbackResolutions = emptyList(),
            )
        render("sky-state-6-empty", snapshot, null)
    }

    /** 态 8 · 离线：已缓存全可用，未缓存天区走低层密度底图；线框与亮星仍在。 */
    @Test
    fun `态8 离线低层密度底图`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.CACHED_MIX,
                fallbackResolutions = SkyStateFixtures.skyResolutions(AssetStatus.READY_FALLBACK),
            )
        render("sky-state-8-offline", snapshot, store) {
            SkyCanvasRenderer.offline = true
        }
    }
}
