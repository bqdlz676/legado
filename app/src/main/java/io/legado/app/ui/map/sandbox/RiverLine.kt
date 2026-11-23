package io.legado.app.ui.map.sandbox

import kotlin.math.max
import kotlin.math.min

/**
 * 真实河道路径（纯 JVM，切片-06 §三 河流）。
 *
 * 切片-06：程序化 fbm 曲线换成真实水系；每条河带 name + era，跟地名一样按年代显隐；
 * 黄河改道分唐前 / 唐宋 / 明清三版，不同战役选不同版。路径点为公开水系折线事实。
 *
 * @param version 改道版本标签（如「唐前」「唐宋」「明清」）；无版本的常流河为 null
 * @param valleyWidth 河谷基准宽度（世界单位）；实际管径沿样条按河谷宽窄起伏
 * @param widths 沿路径的宽度系数（0..1，与采样点等长）；空列表时取基准宽度恒定
 */
data class RiverLine(
    val id: String,
    val name: String,
    val eraLabel: String,
    val fromYear: Int?,
    val toYear: Int?,
    val version: String?,
    val pts: List<LatLng>,
    val valleyWidth: Double,
    val widths: List<Double> = emptyList(),
) {

    fun eraActive(year: Int?): Boolean {
        if (year == null) return true
        if (fromYear != null && year < fromYear) return false
        if (toYear != null && year > toYear) return false
        return true
    }

    /** 路径采样点数；不足两个点时河道不成立，渲染层跳过。 */
    fun isValid(): Boolean = pts.size >= 2

    /**
     * Catmull-Rom 样条插值：把折线控制点磨成平滑曲线（对应 Three.js 的 CatmullRomCurve3）。
     * 两端用端点复制兜底，避免首末段切线失真。返回细分后的经纬度序列。
     */
    fun sampledPath(perSegment: Int = SandboxConfig.RIVER_SAMPLES_PER_SEGMENT): List<LatLng> {
        if (!isValid()) return emptyList()
        val n = pts.size
        val steps = perSegment.coerceAtLeast(2)
        val out = ArrayList<LatLng>()
        for (i in 0 until n - 1) {
            val p0 = pts[max(i - 1, 0)]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = pts[min(i + 2, n - 1)]
            for (k in 0 until steps) {
                val t = k.toDouble() / steps
                out.add(catmullRom(p0, p1, p2, p3, t))
            }
        }
        out.add(pts.last())
        return out
    }

    /** 第 t 比例处的管径：取宽度系数曲线插值；无系数时用基准宽度。 */
    fun widthAt(t: Double): Double {
        if (widths.isEmpty()) return valleyWidth
        val x = t.coerceIn(0.0, 1.0) * (widths.size - 1)
        val i = x.toInt().coerceIn(0, widths.size - 2)
        val local = x - i
        val w = widths[i] + (widths[i + 1] - widths[i]) * local
        return valleyWidth * w.coerceIn(0.2, 1.5)
    }

    private fun catmullRom(
        p0: LatLng, p1: LatLng, p2: LatLng, p3: LatLng, t: Double,
    ): LatLng {
        val t2 = t * t
        val t3 = t2 * t
        fun cr(a: Double, b: Double, c: Double, d: Double): Double =
            0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 +
                (-a + 3 * b - 3 * c + d) * t3)
        return LatLng(
            lat = cr(p0.lat, p1.lat, p2.lat, p3.lat),
            lng = cr(p0.lng, p1.lng, p2.lng, p3.lng),
        )
    }

    companion object {

        /**
         * 按年代从同江河的多个改道版本里选一条：优先年代命中版；无命中时回退常流版，
         * 再不行回退列表第一条，保证任何年代都有河道可画，不出现空白河段。
         */
        fun selectVersion(candidates: List<RiverLine>, year: Int?): RiverLine? {
            if (candidates.isEmpty()) return null
            val active = candidates.filter { it.eraActive(year) }
            if (active.isEmpty()) return candidates.first()
            val perennial = active.firstOrNull { it.version == null }
            return perennial ?: active.first()
        }
    }
}
