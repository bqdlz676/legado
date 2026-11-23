package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.mesh.LodLevel

/**
 * LOD 与渲染核心的桥接（纯 JVM，切片 19 §球体精度 / 切片 20 §LOD 系统）。
 *
 * [io.legado.app.ui.map.globe.mesh.LodSelector] 负责距离选层与 morph 帧进度；
 * 本对象只把冻结的 [LodLevel] 翻译成渲染核心用的细分层级、draw call 上限与帧率预算，
 * 避免渲染代码再散落一套 LOD 阈值。阈值口径全部取自 [GlobeConfig]，不在此重复写魔法数字。
 *
 * 性能红线（切片 19 / 20）：LOD 切换 2–3 帧完成；手机 55fps、桌面 120fps；
 * 手机端 draw call ≤ 15，且不超过各 LOD 自身上限。
 */
object RenderLod {

    /**
     * 各 LOD 对应的二十面体细分次数。
     *
     * 口径说明：经纬网格的段数（128 / 256 / 512）是 GPU 主几何口径；这里的细分次数
     * 是软渲染核心的均匀三角片口径，三角形量级随层级单调递增，供单测与 bench 校验
     * 「近距细节更多」这一趋势，不替代 SphereGrid 上传 GPU 的真实段数。
     */
    fun subdivisions(level: LodLevel): Int = when (level) {
        LodLevel.LOD0 -> 2
        LodLevel.LOD1 -> 3
        LodLevel.LOD2 -> 4
    }

    /** LOD 切换 morph 帧数（红线：2–3 帧内完成）。 */
    fun morphFrames(): Int = GlobeConfig.LOD_MORPH_FRAMES

    /** 当前 LOD 允许的 draw call 上限（取 LOD 自身上限与手机端总上限的较小者）。 */
    fun maxDrawCalls(level: LodLevel): Int =
        minOf(level.maxDrawCalls, GlobeConfig.MAX_DRAW_CALLS)

    /** 手机端目标帧率（红线 55fps）。 */
    val mobileFps: Int get() = GlobeConfig.TARGET_FPS_MOBILE

    /** 桌面端目标帧率（红线 120fps）。 */
    val desktopFps: Int get() = GlobeConfig.TARGET_FPS_DESKTOP

    /** 目标帧率对应的单帧预算（毫秒）。 */
    fun frameBudgetMs(fps: Int): Double = 1000.0 / fps.coerceAtLeast(1)

    /** 手机端单帧预算（毫秒）。 */
    val mobileFrameBudgetMs: Double get() = frameBudgetMs(mobileFps)

    /** 桌面端单帧预算（毫秒）。 */
    val desktopFrameBudgetMs: Double get() = frameBudgetMs(desktopFps)

    /** 按 LOD 直接构建软渲染球面网格（测试 / 兜底用）。 */
    fun meshFor(level: LodLevel, radius: Float, displace: (io.legado.app.ui.map.globe.math.Vec3) -> Float = { 0f }): IcosphereMesh =
        IcosphereMesh.build(subdivisions(level), radius, displace)
}
