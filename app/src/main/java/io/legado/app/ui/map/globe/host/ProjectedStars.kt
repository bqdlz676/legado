package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.astro.proj.StarProjectionSource

/**
 * 帧间复用的投影星缓冲；扩容 2 倍拷贝，热路径不 new 列表（§十二.2）。
 * 独立成文件是为给 [SkyCanvasRenderer] 留出行数预算（≤400 行硬口径）。
 *
 * 实现 [StarProjectionSource.ProjectionSink]：投影行星源把 L0 ∪ 已加载金字塔层的可见星
 * 经同一出口写入本缓冲，宿主拾取与绘制只读本缓冲，不感知层来源。
 */
internal class ProjectedStars : StarProjectionSource.ProjectionSink {
    var count = 0
    var x = FloatArray(256); var y = FloatArray(256)
    var mag = FloatArray(256); var id = LongArray(256)
    var bv = DoubleArray(256)

    fun clear() { count = 0 }

    override fun add(px: Float, py: Float, mag: Float, id: Long, colorBv: Double) {
        // 字段名与入参同名：扩容与落槽一律走 this.mag / this.id，避免入参遮蔽字段后 copyOf 解到标量上。
        if (count >= x.size) {
            val cap = x.size * 2
            x = x.copyOf(cap); y = y.copyOf(cap); this.mag = this.mag.copyOf(cap)
            this.id = this.id.copyOf(cap); bv = bv.copyOf(cap)
        }
        x[count] = px; y[count] = py; this.mag[count] = mag; this.id[count] = id; bv[count] = colorBv
        count++
    }
}
