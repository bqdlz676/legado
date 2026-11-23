package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.mesh.LodLevel

/**
 * draw call 预算核算（纯 JVM，切片 19 / 20 性能表）。
 *
 * 红线：手机端 ≤15 draw call；LOD0 ≤10，LOD1 ≤14，LOD2（战场高精）≤18。
 * 渲染层每注册一个 pass 调 [add]，帧末 [exceeded] 检查是否超预算；
 * 超预算时降级策略是合并同材质 pass，而不是砍功能或留白。
 */
class DrawBudget(private val lod: LodLevel) {

    private var calls = 0

    fun add(passCount: Int = 1) {
        calls += passCount
    }

    fun current(): Int = calls

    /** 当前 LOD 允许的 draw call 上限。 */
    fun limit(): Int = minOf(lod.maxDrawCalls, GlobeConfig.MAX_DRAW_CALLS)

    fun exceeded(): Boolean = calls > limit()

    /** 自检报告用：余量（负数表示超支）。 */
    fun remaining(): Int = limit() - calls
}
