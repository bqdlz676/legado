package io.legado.app.ui.map.sandbox

import kotlin.math.floor

/**
 * DEM 灰度栅格地形采样（纯 JVM，切片-06 §一 / 缺陷 D-APK-1）。
 *
 * 口径（与逆向文档「三、地形怎么起来的」及 crossing / globe 既有 DEM 约定一致）：
 * - 栅格行优先，行 0 = 北缘；每像素 0..255 灰度。
 * - 灰度 g → 米制：m = g / 255 × [DEM_MAX_METERS]（0 ↔ 0m，255 ↔ 7000m）。
 * - 米制 → 沙盘世界单位：y = m × [VERTICAL_EXAGGERATION]（旧版沙盘 K=0.009；
 *   7000m ↔ 63 世界单位，对照 300 单位盘面与 12 单位光柱，隆起保持克制）。
 * - 经纬度按等经纬度线性归一化（legacy latV 已弃墨卡托，避免山脊与城池错位）。
 *
 * 兜底铁律：栅格为空 / 尺寸不符 / 包围盒退化 / 坐标 NaN 时，heightAt 恒返回 0.0；
 * 越界经纬度钳到边缘像素；全程不抛异常、不产出 NaN（沿用 [TerrainElevation] 接口约定）。
 *
 * 为什么本类不引 Android：网格由 Android 侧薄适配（[DemTerrainLoader]）解码资产后
 * 注入，模型 / 单测层只认 IntArray 与经纬度范围，普通 JVM 即可构造与压测。
 */
class DemTerrainElevation private constructor(
    private val width: Int,
    private val height: Int,
    private val data: IntArray,
    private val bbox: Bbox,
    /** true = 栅格来自兜底空场，heightAt 恒 0.0；壳层可据此明示降级。 */
    val degraded: Boolean,
) : TerrainElevation {

    /** 栅格覆盖的经纬度范围：南纬 / 北纬 / 西经 / 东经。 */
    data class Bbox(
        val south: Double,
        val north: Double,
        val west: Double,
        val east: Double,
    )

    override fun heightAt(lat: Double, lng: Double): Double {
        if (degraded) return 0.0
        if (!lat.isFinite() || !lng.isFinite()) return 0.0

        val spanLat = bbox.north - bbox.south
        val spanLng = bbox.east - bbox.west
        if (spanLat <= 0.0 || spanLng <= 0.0) return 0.0

        // 归一化到 [0,1]：经度自西向东，纬度自北向南（行 0 = 北缘）；越界钳到边缘。
        val u = ((lng - bbox.west) / spanLng).coerceIn(0.0, 1.0)
        val v = ((bbox.north - lat) / spanLat).coerceIn(0.0, 1.0)

        val gx = u * (width - 1)
        val gy = v * (height - 1)
        val x0 = floor(gx).toInt().coerceIn(0, width - 1)
        val y0 = floor(gy).toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = gx - x0
        val fy = gy - y0

        val nw = data[y0 * width + x0]
        val ne = data[y0 * width + x1]
        val sw = data[y1 * width + x0]
        val se = data[y1 * width + x1]
        val top = nw + (ne - nw) * fx
        val bottom = sw + (se - sw) * fx
        val gray = top + (bottom - top) * fy

        val meters = (gray.coerceIn(0.0, 255.0) / 255.0) * DEM_MAX_METERS
        val units = meters * VERTICAL_EXAGGERATION
        return if (units.isFinite()) units else 0.0
    }

    companion object {

        /** DEM 满幅高程（米）：0 ↔ 0m，255 ↔ 7000m，与 crossing HeightRaster / globe FbmNoise 同口径。 */
        const val DEM_MAX_METERS: Double = 7000.0

        /** 垂直夸张系数（旧版沙盘 K=0.009：y = 米 × K）；7000m ↔ 63 世界单位。 */
        const val VERTICAL_EXAGGERATION: Double = 0.009

        /** 高程图默认包围盒：104–122°E / 24–42°N（legacy HG，china_height.png 覆盖范围）。 */
        val DEFAULT_BBOX: Bbox = Bbox(south = 24.0, north = 42.0, west = 104.0, east = 122.0)

        /**
         * 校验并构造栅格；输入为空 / 尺寸不符 / 包围盒退化时返回退化实例（heightAt 恒 0.0），
         * 不抛异常。灰度值在构造时统一夹紧到 0..255。
         */
        fun create(
            width: Int,
            height: Int,
            data: IntArray?,
            bbox: Bbox = DEFAULT_BBOX,
        ): DemTerrainElevation {
            val valid = width > 0 && height > 0 && data != null &&
                data.size == width * height &&
                bbox.north > bbox.south && bbox.east > bbox.west
            if (!valid) {
                return DemTerrainElevation(
                    width = width.coerceAtLeast(0),
                    height = height.coerceAtLeast(0),
                    data = IntArray(0),
                    bbox = bbox,
                    degraded = true,
                )
            }
            val clamped = IntArray(data!!.size) { data[it].coerceIn(0, 255) }
            return DemTerrainElevation(width, height, clamped, bbox, degraded = false)
        }

        /** ByteArray 入口：按无符号字节口径取灰度（补码 -1 字节 = 255）。 */
        fun create(
            width: Int,
            height: Int,
            data: ByteArray?,
            bbox: Bbox = DEFAULT_BBOX,
        ): DemTerrainElevation {
            val gray = data?.let { bytes -> IntArray(bytes.size) { i -> bytes[i].toInt() and 0xFF } }
            return create(width, height, gray, bbox)
        }
    }
}
