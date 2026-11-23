package io.legado.app.ui.map.globe.render

/**
 * 软渲染帧缓冲（纯 JVM，只保留可测的覆盖 / 深度部分）。
 *
 * 职责：一块离屏深度缓冲 + 覆盖统计。颜色着色留给后续真机渲染层（GLSL / Compose Canvas），
 * 这里不实现片元着色——软渲染可测的核心是「哪些像素被覆盖、近处是否盖住远处」。
 *
 * 深度约定：相机在 +Z，正面 z > 0，越大越近；缓冲里保留已写入的最大 z，
 * 新片元 z 更大（更近）才覆盖。未写入的像素深度为 [Float.NEGATIVE_INFINITY]。
 *
 * @param width  帧缓冲宽（像素）
 * @param height 帧缓冲高（像素）
 */
class Framebuffer(val width: Int, val height: Int) {

    init {
        require(width > 0 && height > 0) { "帧缓冲尺寸必须为正，got ${width}x$height" }
    }

    private val depth = FloatArray(width * height) { Float.NEGATIVE_INFINITY }

    /** 本帧通过深度测试写入的片元数。 */
    var fragmentsWritten: Int = 0
        private set

    /** 本帧被深度测试拒掉的片元数（被更近片元盖住）。 */
    var fragmentsRejectedDepth: Int = 0
        private set

    /** 本帧已覆盖的像素数（深度缓冲非空）。 */
    var coveredPixels: Int = 0
        private set

    fun clear() {
        depth.fill(Float.NEGATIVE_INFINITY)
        fragmentsWritten = 0
        fragmentsRejectedDepth = 0
        coveredPixels = 0
    }

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun depthAt(x: Int, y: Int): Float =
        if (inBounds(x, y)) depth[y * width + x] else Float.NEGATIVE_INFINITY

    /**
     * 尝试写入一个像素。
     * @return true 表示通过深度测试并写入；false 表示越界或被更近片元盖住。
     */
    fun tryWrite(x: Int, y: Int, depthValue: Float): Boolean {
        if (!inBounds(x, y)) return false
        if (!depthValue.isFinite()) return false
        val idx = y * width + x
        val stored = depth[idx]
        if (depthValue <= stored) {
            fragmentsRejectedDepth++
            return false
        }
        if (stored == Float.NEGATIVE_INFINITY) coveredPixels++
        depth[idx] = depthValue
        fragmentsWritten++
        return true
    }
}
