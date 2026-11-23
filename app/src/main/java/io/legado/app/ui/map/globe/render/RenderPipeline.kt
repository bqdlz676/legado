package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.mesh.LodLevel

/**
 * 软渲染管线（纯 JVM，渲染核心总装）。
 *
 * 一帧的固定流程：网格顶点施加相机姿态（倾斜 → 偏航 → 俯仰）→ 背面半球 / 面剔除 →
 * 世界方向投影到屏幕 → 包围盒光栅化 + 深度测试。管线只产出几何与覆盖统计，
 * 不做纹理采样 / 光照着色，那些由后续 GLES / Compose 真机渲染层接走。
 *
 * draw call 口径：每类材质 pass 计一次（球壳 / 大气 / 云层 / 路线 …），本管线默认
 * 球壳 1 个 pass；预算红线由 [DrawBudget] 按 LOD 核算，超支时上层合并同材质 pass，
 * 不砍功能、不留空白态。
 *
 * @param camera      渲染相机（姿态 + 视口）
 * @param framebuffer 离屏深度缓冲
 * @param budget      本帧 draw call 预算（按 LOD 构造）
 */
class RenderPipeline(
    val camera: GlobeCamera,
    val framebuffer: Framebuffer,
    val budget: DrawBudget,
) {
    private val rasterizer = SoftwareRasterizer(framebuffer)

    /** 一帧渲染统计（纯数据，供单测 / bench / 自检报告断言）。 */
    data class FrameStats(
        val trianglesTotal: Int,
        val trianglesCulled: Int,
        val trianglesVisible: Int,
        val fragmentsWritten: Int,
        val fragmentsRejectedDepth: Int,
        val coveredPixels: Int,
        val drawCalls: Int,
        val budgetExceeded: Boolean,
    )

    /**
     * 渲染一片球面网格。
     * @param mesh          细分球面网格
     * @param passDrawCalls 本网格占用的 pass 数（默认球壳 1）
     */
    fun render(mesh: IcosphereMesh, passDrawCalls: Int = 1): FrameStats {
        framebuffer.clear()
        budget.add(passDrawCalls)

        // 顶点先转到世界空间；正交投影下半径不影响方位，深度取世界方向 z。
        val world = Array(mesh.vertexCount) { i -> camera.transform(mesh.normalOf(i)) }

        var culled = 0
        var visible = 0
        var accepted = 0
        var rejected = 0

        for (i in 0 until mesh.triangleCount) {
            val t = mesh.triangle(i)
            val wa = world[t.a]
            val wb = world[t.b]
            val wc = world[t.c]

            val decision = BackFaceCuller.shouldCull(wa, wb, wc)
            if (decision.culled) {
                culled++
                continue
            }
            visible++

            val pa = camera.projectWorld(wa)
            val pb = camera.projectWorld(wb)
            val pc = camera.projectWorld(wc)
            val res = rasterizer.rasterize(pa, pb, pc)
            accepted += res.accepted
            rejected += res.rejectedDepth
        }

        return FrameStats(
            trianglesTotal = mesh.triangleCount,
            trianglesCulled = culled,
            trianglesVisible = visible,
            fragmentsWritten = accepted,
            fragmentsRejectedDepth = rejected,
            coveredPixels = framebuffer.coveredPixels,
            drawCalls = budget.current(),
            budgetExceeded = budget.exceeded(),
        )
    }

    /** 按 LOD 构建网格并渲染（测试 / 兜底用）。 */
    fun renderLod(level: LodLevel, radius: Float, passDrawCalls: Int = 1): FrameStats {
        val mesh = RenderLod.meshFor(level, radius)
        return render(mesh, passDrawCalls)
    }

    companion object {
        /** 兜底用：标准视口管线（与 host 投影同口径，1000×1000 画布、200px 球径）。 */
        fun defaultViewport(level: LodLevel, radius: Float): RenderPipeline {
            val camera = GlobeCamera(1000f, 1000f, 200f)
            val fb = Framebuffer(1000, 1000)
            return RenderPipeline(camera, fb, DrawBudget(level))
        }
    }
}
