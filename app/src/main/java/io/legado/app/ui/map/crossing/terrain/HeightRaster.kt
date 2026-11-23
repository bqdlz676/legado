package io.legado.app.ui.map.crossing.terrain

import kotlin.math.PI
import kotlin.math.sin

/**
 * 高程栅格（纯 JVM）。
 *
 * 口径对齐 globe/terrain 既有 DEM：单像素 0..255 → 米制按 [DEM_MAX_METERS] 线性换算，
 * 海面 [OCEAN_ELEVATION_METERS]、雪线 [SNOWLINE_METERS] 与 `GlobeConfig` 保持同一套阈值。
 *
 * 兜底铁律（规格未写明，按任务书自补）：高程图缺失 / 断网 / 空数据 / 尺寸退化时，
 * 不抛异常、不空白——生成一层内置最小默认高程场，并把 [degraded] 置 true 让壳层明示降级。
 */
data class HeightRaster(
    val width: Int,
    val height: Int,
    /** 行优先，每像素 0..255；越界值在构造时统一夹紧。 */
    val data: IntArray,
    /** true = 栅格来自兜底合成场，而非真实 PNG 高程图。 */
    val degraded: Boolean,
) {

    val size: Int get() = width * height

    fun inBounds(col: Int, row: Int): Boolean = col in 0 until width && row in 0 until height

    /** 取像素值；越界返回边缘钳制值，不抛。 */
    fun pixel(col: Int, row: Int): Int {
        val c = col.coerceIn(0, width - 1)
        val r = row.coerceIn(0, height - 1)
        return data[r * width + c]
    }

    /** 像素 0..255 → 米制高程。 */
    fun metersAt(col: Int, row: Int): Double = metersFor(pixel(col, row))

    companion object {
        /** DEM 满幅高程（米）：与 FbmNoise.elevationToUnits 的 7000m 口径一致。 */
        const val DEM_MAX_METERS: Double = 7000.0

        /** 海面高程（米）：<= 此值按水域。 */
        const val OCEAN_ELEVATION_METERS: Double = 0.0

        /** 雪线（米）：> 此值按积雪地形。 */
        const val SNOWLINE_METERS: Double = 3500.0

        /** 0..255 像素值 → 米制。 */
        fun metersFor(value: Int): Double = (value.coerceIn(0, 255) / 255.0) * DEM_MAX_METERS

        /**
         * 校验并构造栅格；输入为空 / 尺寸退化 / 长度不符时返回合成兜底场，不抛异常。
         *
         * @param degradedHint 调用侧已知来源缺失（断网 / 文件读失败）时置 true
         */
        fun create(
            width: Int,
            height: Int,
            data: IntArray?,
            degradedHint: Boolean = false,
        ): HeightRaster {
            val valid = width > 0 && height > 0 && data != null && data.size == width * height
            if (!valid) {
                val n = if (width > 0 && height > 0) minOf(width, height).coerceIn(16, 128) else 64
                return synthetic(seed = n.toLong(), gridN = n, degraded = true)
            }
            val clamped = IntArray(data!!.size) { i -> data[i].coerceIn(0, 255) }
            return HeightRaster(width, height, clamped, degradedHint)
        }

        /**
         * 内置最小默认高程场：两层正弦叠加出缓丘谷地，中央留一条低洼带便于河流叠加。
         * 不伪装成真实地形；仅保证无图时推演与渲染不空白、不崩。
         */
        fun synthetic(seed: Long, gridN: Int, degraded: Boolean = true): HeightRaster {
            val n = gridN.coerceIn(16, 128)
            val data = IntArray(n * n)
            for (row in 0 until n) {
                for (col in 0 until n) {
                    val x = col.toDouble() / n
                    val y = row.toDouble() / n
                    val layerA = sin(x * PI * 2.0 + seed) * 0.5 + 0.5
                    val layerB = sin(y * PI * 3.0 + seed * 0.7) * 0.5 + 0.5
                    val valley = 1.0 - kotlin.math.abs(y - 0.5) * 1.6
                    val v = (0.25 * layerA + 0.25 * layerB + 0.5 * valley).coerceIn(0.05, 0.95)
                    data[row * n + col] = (v * 255).toInt().coerceIn(0, 255)
                }
            }
            return HeightRaster(n, n, data, degraded)
        }
    }
}
