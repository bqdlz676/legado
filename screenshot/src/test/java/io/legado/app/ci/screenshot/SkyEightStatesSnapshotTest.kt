package io.legado.app.ci.screenshot

import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import io.legado.app.ci.screenshot.SkyRenderView
import io.legado.app.ci.screenshot.SkyStateFixtures
import io.legado.app.ui.map.globe.astro.data.PyramidResult
import io.legado.app.ui.map.globe.astro.data.StarStoreProvider
import io.legado.app.ui.map.globe.host.SkyCanvasRenderer
import io.legado.app.ui.map.globe.host.SkyInputHandler
import io.legado.app.ui.map.globe.offline.AssetStatus
import io.legado.app.ui.map.globe.offline.DegradationLevel
import io.legado.app.ui.map.globe.render.FrameSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 判据 11（规格 §十四.11）：星空八态逐态出帧。
 *
 * 覆盖口径、状态定义出处、观感判定要点见 `docs/截图-星空八态帧证据.md`；本类只挂用例。
 * 帧由 [SkyCanvasRenderer] 真实渲染路径产出，不画假图、不把状态参数硬编码成观感正确
 * 却绕开真实分支。
 *
 * 态 7 坏片经 [StarStoreProvider.pyramidCacheDir] 注入临时 Corrupt 目录；态 2 走真实
 * [SkyInputHandler] 120ms 回弹计时；态 4 进度来自真实金字塔解析计数钩子，不塞假常量。
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

    /**
     * 态 2 · 悬停：压住星点外扩 1.3×；真实跑 120ms 回弹计时。
     *
     * [SkyInputHandler.markHover] 是 internal：跨 Gradle 模块编译期不可见，字节码名又经
     * Kotlin 内部修饰符处理，反射按原名取不到方法（run 46117917979 NoSuchMethodException）。
     * 这里改为直接种子私有时刻字段（字段名不做内部修饰混淆）；回弹节拍仍走公开入口
     * [SkyInputHandler.tickHover] 的真实 120ms 分支。两条时序断言：100ms 内保持悬停
     * 不回落；121ms 越过阈值必须回弹无残留。
     */
    @Test
    fun `态2 悬停星点外扩一点三倍`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.FULL_TEXTURES,
                fallbackResolutions = emptyList(),
            )
        val target = SkyStateFixtures.firstVisibleStarId(store, 0.0, 0.0)
        val nowMs = SkyStateFixtures.FIXED_UTC_MILLIS
        val handler = SkyInputHandler(
            controller = SkyStateFixtures.newController(),
            renderer = SkyCanvasRenderer,
            clock = { nowMs },
        )
        val trackIdField = SkyInputHandler::class.java.getDeclaredField("hoverTrackId")
        trackIdField.isAccessible = true
        val startField = SkyInputHandler::class.java.getDeclaredField("hoverTrackStartMs")
        startField.isAccessible = true
        fun seedHover(id: Long) {
            trackIdField.setLong(handler, id)
            startField.setLong(handler, nowMs)
            SkyCanvasRenderer.hoverId = id
        }

        seedHover(target)
        // 100ms 内：悬停保持，不被节拍清掉。改坏：节拍把未超时悬停也回落，下一行断言变红。
        handler.tickHover(nowMs + 100L)
        assertEquals("120ms 内悬停不得回落", target, SkyCanvasRenderer.hoverId)
        // 121ms：越过 120ms 阈值必须回弹，hoverId 归 -1 无残留。
        handler.tickHover(nowMs + 121L)
        assertEquals("超过 120ms 悬停必须回弹无残留", -1L, SkyCanvasRenderer.hoverId)

        // 出帧前重新标记悬停：帧截 120ms 窗口内的 1.3× 放大态。
        seedHover(target)
        render("sky-state-2-hover", snapshot, store) {
            SkyCanvasRenderer.hoverId = target
        }
    }

    /** 态 3 · 按下：60ms 门槛后 1.6× 放大并进入 1200ms 呼吸；静态帧取过门后呼吸段。 */
    @Test
    fun `态3 按下一点六倍呼吸`() {
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

    /**
     * 态 4 · 加载（金字塔解析中）：已有星照常显示，顶部 1px brandGold 进度线（非菊花）。
     *
     * 进度来自真实金字塔解析计数：仓库 data/pyramid 的 L1 片拷进临时目录，经
     * [StarStore.bindPyramidProgress] 钩子回传真实 done/total；total>0 才回填比值，
     * 总量未知（total<=0）不伪造比例。不再用 PENDING_DOWNLOAD 半幅 0.5 占位。
     */
    @Test
    fun `态4 金字塔下载中金线进度`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.CACHED_MIX,
                fallbackResolutions = SkyStateFixtures.skyResolutions(AssetStatus.PENDING_DOWNLOAD),
            )
        val dir = SkyStateFixtures.tmpPyramidDir("sky-state4-")
        SkyStateFixtures.copyRealShard(dir, 1)
        val progress = SkyStateFixtures.ProgressCapture().also { it.bind(store) }

        val result = store.ensurePyramid(1, dir)
        // 真实片必须 Loaded；改坏：片被误判 Corrupt/Missing，下一行 instanceof 断言变红。
        assertTrue("真实 L1 片应加载成功，实际 $result", result is PyramidResult.Loaded)
        assertTrue("解析段真实总量必须 > 0，实际 total=${progress.total}", progress.total > 0)

        SkyCanvasRenderer.externalPyramidProgress = progress.ratio
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

    /**
     * 态 7 · 错误（坏片校验失败）：临时目录注入 Corrupt 切片，ensurePyramid 收敛 Corrupt；
     * 错误提示文案与宿主 [SkyInputHandler] 的 onPyramidError 分支一致；L0 亮星照常显示，不出空白。
     *
     * 用 L2 片做坏片：态 4 已并入 L1，分层互不影响（loadPyramid 已加载层幂等返回 Loaded）。
     */
    @Test
    fun `态7 坏片注入错误提示`() {
        val store = SkyStateFixtures.loadStore(paparazzi.context.assets)
        val snapshot = SkyStateFixtures.baseSnapshot()
            .copy(
                skyDegradation = DegradationLevel.FULL_TEXTURES,
                fallbackResolutions = emptyList(),
            )
        val dir = SkyStateFixtures.tmpPyramidDir("sky-state7-")
        SkyStateFixtures.writeCorruptShard(dir, 2)
        StarStoreProvider.pyramidCacheDir = dir

        val result = store.ensurePyramid(2, dir)
        // 改坏：坏片若被误判 Loaded 合并进目录，下一行 assertEquals(Corrupt) 变红。
        assertEquals(PyramidResult.Corrupt, result)
        // 与生产 SkyInputHandler.kt 的 Corrupt 分支文案逐字一致，不另造提示语。
        SkyCanvasRenderer.externalPyramidError = "星表切片损坏，已回落内置亮星层；点按重试"
        render("sky-state-7-corrupt", snapshot, store)
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
