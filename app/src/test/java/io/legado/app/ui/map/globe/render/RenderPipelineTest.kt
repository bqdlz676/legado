package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.mesh.LodLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染管线单测（纯 JVM）：整球剔除 / 投影 / 光栅化闭环、LOD 细节递增、
 * draw call 预算红线、morph 帧数红线、各 LOD 上限核算。
 */
class RenderPipelineTest {

    @Test
    fun `LOD0 整球渲染产出可见片元且不超预算`() {
        val pipeline = RenderPipeline.defaultViewport(LodLevel.LOD0, GlobeConfig.RADIUS)
        val stats = pipeline.renderLod(LodLevel.LOD0, GlobeConfig.RADIUS)

        assertTrue("应有总三角片", stats.trianglesTotal > 0)
        assertTrue("背面应有剔除片", stats.trianglesCulled > 0)
        assertTrue("正面应有可见片", stats.trianglesVisible > 0)
        assertTrue("帧缓冲应有覆盖像素", stats.coveredPixels > 0)
        assertTrue("应有写入片元", stats.fragmentsWritten > 0)
        assertFalse("不得超 draw call 预算", stats.budgetExceeded)
        assertTrue(
            "draw call ${stats.drawCalls} 不得超过 LOD0 上限 ${RenderLod.maxDrawCalls(LodLevel.LOD0)}",
            stats.drawCalls <= RenderLod.maxDrawCalls(LodLevel.LOD0),
        )
        assertTrue(
            "draw call 不得超过手机端上限 ${GlobeConfig.MAX_DRAW_CALLS}",
            stats.drawCalls <= GlobeConfig.MAX_DRAW_CALLS,
        )
    }

    @Test
    fun `近距 LOD2 可见三角片不少于远距 LOD0`() {
        val p0 = RenderPipeline.defaultViewport(LodLevel.LOD0, GlobeConfig.RADIUS)
        val s0 = p0.renderLod(LodLevel.LOD0, GlobeConfig.RADIUS)

        val p2 = RenderPipeline.defaultViewport(LodLevel.LOD2, GlobeConfig.RADIUS)
        val s2 = p2.renderLod(LodLevel.LOD2, GlobeConfig.RADIUS)

        assertTrue("LOD2 总片数应多于 LOD0", s2.trianglesTotal > s0.trianglesTotal)
        assertTrue("LOD2 可见片应不少于 LOD0", s2.trianglesVisible >= s0.trianglesVisible)
    }

    @Test
    fun `LOD 切换 morph 帧数在 2 到 3 帧红线内`() {
        val frames = RenderLod.morphFrames()
        assertTrue("morph 帧数 $frames 应在 2..3", frames in 2..GlobeConfig.LOD_MORPH_FRAMES)
        assertEquals(GlobeConfig.LOD_MORPH_FRAMES, frames)
    }

    @Test
    fun `各 LOD draw call 上限按规格核算`() {
        assertEquals(10, RenderLod.maxDrawCalls(LodLevel.LOD0))
        assertEquals(14, RenderLod.maxDrawCalls(LodLevel.LOD1))
        // LOD2 自身上限 18，但手机端总上限 15 收口。
        assertEquals(GlobeConfig.MAX_DRAW_CALLS, RenderLod.maxDrawCalls(LodLevel.LOD2))
    }

    @Test
    fun `渲染统计数值全部有限且自洽`() {
        val pipeline = RenderPipeline.defaultViewport(LodLevel.LOD1, GlobeConfig.RADIUS)
        val stats = pipeline.renderLod(LodLevel.LOD1, GlobeConfig.RADIUS)

        assertEquals(stats.trianglesTotal, stats.trianglesCulled + stats.trianglesVisible)
        assertTrue(stats.fragmentsWritten >= 0)
        assertTrue(stats.fragmentsRejectedDepth >= 0)
        assertTrue("覆盖像素不得超过帧缓冲总量", stats.coveredPixels <= 1000 * 1000)
    }

    @Test
    fun `多 pass 累计 draw call 并在超支时标记`() {
        val pipeline = RenderPipeline.defaultViewport(LodLevel.LOD0, GlobeConfig.RADIUS)
        // 球壳 + 大气 + 云层 + 路线 + 标记等 10 个 pass，对齐 LOD0 上限。
        repeat(9) { pipeline.budget.add() }
        val stats = pipeline.renderLod(LodLevel.LOD0, GlobeConfig.RADIUS, passDrawCalls = 1)
        assertFalse("10 个 pass 不应超 LOD0 预算", stats.budgetExceeded)

        pipeline.budget.add()
        assertTrue("第 11 个 pass 应超 LOD0 预算", pipeline.budget.exceeded())
    }

    @Test
    fun `帧率预算对齐 55fps 手机与 120fps 桌面红线`() {
        assertEquals(GlobeConfig.TARGET_FPS_MOBILE, RenderLod.mobileFps)
        assertEquals(GlobeConfig.TARGET_FPS_DESKTOP, RenderLod.desktopFps)
        assertTrue(RenderLod.mobileFrameBudgetMs > 0.0)
        assertTrue(RenderLod.desktopFrameBudgetMs > 0.0)
        // 手机帧预算应宽于桌面（55fps 帧长 > 120fps 帧长）。
        assertTrue(RenderLod.mobileFrameBudgetMs > RenderLod.desktopFrameBudgetMs)
    }
}
