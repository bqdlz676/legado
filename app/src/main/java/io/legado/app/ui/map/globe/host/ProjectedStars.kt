package io.legado.app.ui.map.globe.host

/**
 * 帧间复用的投影星缓冲；扩容 2 倍拷贝，热路径不 new 列表（§十二.2）。
 * 独立成文件是为给 [SkyCanvasRenderer] 留出行数预算（≤400 行硬口径）。
 */
internal class ProjectedStars {
    var count = 0
    var x = FloatArray(256); var y = FloatArray(256)
    var mag = FloatArray(256); var id = LongArray(256)
    var bv = DoubleArray(256)

    fun clear() { count = 0 }

    fun add(px: Float, py: Float, m: Float, starId: Long, colorBv: Double) {
        if (count >= x.size) {
            val cap = x.size * 2
            x = x.copyOf(cap); y = y.copyOf(cap); mag = mag.copyOf(cap)
            id = id.copyOf(cap); bv = bv.copyOf(cap)
        }
        x[count] = px; y[count] = py; mag[count] = m; id[count] = starId; bv[count] = colorBv
        count++
    }
}
