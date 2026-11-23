package io.legado.app.ui.map.globe.render

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 软渲染三角形光栅化器（纯 JVM，只做覆盖 + 深度，不做片元着色）。
 *
 * 算法：包围盒扫描 + 重心坐标判据；像素中心落在三角形内才参与深度测试。
 * 风序兼容：同时接受逆时针与顺时针屏幕风序，避免屏幕 y 轴翻转后把正面片剔光。
 * 颜色 / 光照留给后续真机渲染层，本类只回报覆盖片元数与深度胜负。
 */
class SoftwareRasterizer(private val framebuffer: Framebuffer) {

    data class RasterResult(
        /** 落在三角形内的候选像素数。 */
        val pixels: Int,
        /** 通过深度测试写入的片元数。 */
        val accepted: Int,
        /** 被深度测试拒掉的片元数。 */
        val rejectedDepth: Int,
    ) {
        val empty: Boolean get() = pixels == 0
    }

    /**
     * 光栅化一片已投影三角形。
     * @param a,b,c 屏幕投影点（含世界深度）
     */
    fun rasterize(a: ScreenPoint, b: ScreenPoint, c: ScreenPoint): RasterResult {
        val minX = max(0, minOf(a.x, b.x, c.x).toInt())
        val maxX = min(framebuffer.width - 1, maxOf(a.x, b.x, c.x).toInt())
        val minY = max(0, minOf(a.y, b.y, c.y).toInt())
        val maxY = min(framebuffer.height - 1, maxOf(a.y, b.y, c.y).toInt())

        val area = signedArea(a, b, c)
        if (abs(area) < 1e-9) return RasterResult(0, 0, 0)

        var pixels = 0
        var accepted = 0
        var rejected = 0

        for (py in minY..maxY) {
            val pY = py + 0.5f
            for (px in minX..maxX) {
                val pX = px + 0.5f
                val w = barycentric(a, b, c, pX, pY, area) ?: continue
                val depth = w.first * a.depth + w.second * b.depth + w.third * c.depth
                pixels++
                if (framebuffer.tryWrite(px, py, depth)) accepted++ else rejected++
            }
        }
        return RasterResult(pixels, accepted, rejected)
    }

    private fun signedArea(a: ScreenPoint, b: ScreenPoint, c: ScreenPoint): Float =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun edge(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        px: Float, py: Float,
    ): Float = (bx - ax) * (py - ay) - (by - ay) * (px - ax)

    /**
     * 重心坐标；像素在三角形外返回 null。
     * @return (w0, w1, w2)，对应 a / b / c
     */
    private fun barycentric(
        a: ScreenPoint,
        b: ScreenPoint,
        c: ScreenPoint,
        px: Float,
        py: Float,
        area: Float,
    ): Triple<Float, Float, Float>? {
        // w1 = edge(c, a, p) / edge(c, a, b)；w2 = edge(a, b, p) / edge(a, b, c)；w0 补余量。
        val w1 = edge(c.x, c.y, a.x, a.y, px, py) / edge(c.x, c.y, a.x, a.y, b.x, b.y)
        val w2 = edge(a.x, a.y, b.x, b.y, px, py) / area
        val w0 = 1f - w1 - w2
        if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) return null
        return Triple(w0, w1, w2)
    }
}
