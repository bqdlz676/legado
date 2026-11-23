package io.legado.app.ui.map.crossing.shell

import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.RasterDecoder
import kotlin.math.floor

/**
 * 高程桥接（壳层专属，B13）：把战役 JSON 声明的高程来源装配成引擎消费的 [HeightRaster]。
 *
 * 红线口径：
 * - 本层纯 JVM，不引 Android / sandbox 类型；平台 PNG 解码由注入的 [RasterDecoder] 完成。
 * - 不在本层二次解码 PNG；解码器返回什么栅格，本层只做校验 / 重采样 / 兜底。
 * - 引擎层零 sandbox 引用；sandbox `TerrainElevation` 采样归 sandbox 域，不在本桥接复用。
 *
 * 实测数据形态（`assets/map/campaigns/gaixia.json`，全仓唯一声明 `heightMap` 的战役）：
 * 1. **文件名形式**：`"heightMap": "gaixia.png"` —— 资产基名，由解码器在 `map/` 目录查找。
 *    实测现存灰度 PNG 仅 `map/china_height.png`（1024×1024，8-bit 灰度）；`gaixia.png`
 *    在 assets 中不存在，该路径当前必走兜底，不假装解码成功。
 * 2. **内嵌栅格形式**：`terrain.heightPixels` 扁平 IntArray（实测 96×96，0..255，行优先），
 *    配 `bbox:[west, south, east, north]`、`minEl/maxEl`。解析层尚未把该数组带入战役定义
 *    （见交付报告「交接」节）；本桥接先备好内嵌栅格入口，待解析 / 模型层接线。
 * 3. **Web base64 资产**：`map/china_height.js` 内 `CHINA_H_B64` 是 1024×1024 原始字节的
 *    base64，仅 legacy web 使用，战役 JSON 不引用，本通路不消费。
 *
 * 坐标映射（防棋盘错位）：
 * - 栅格行优先：`data[row * width + col]`；行 0 = 北缘（纬度最高），列 0 = 西缘（经度最小）。
 * - 目标栅格 `gridN×gridN` 与 `GeoProjection` 同口径：col 向东、row 向南；单元中心经纬度
 *   由投影 `toGeo` 给出，栅格像素与推演单元一一对应，不做旋转 / 翻转。
 * - 源栅格若自带 bbox：经度 `u=(lng-west)/spanLng`、纬度 `v=(north-lat)/spanLat`，越界钳
 *   边缘；bbox 对齐由解码器 / sandbox 层负责，本桥接只在像素空间重采样。
 */
object CrossingRasterBridge {

    /**
     * B13 解码装配：按战役 JSON `heightMap` 名经注入解码器解码成引擎栅格。
     *
     * 兜底口径（不空白、不崩溃、置降级标志）：
     * - decoder 缺失 / 资产名空白 / 解码返回 null / 解码抛异常，任一命中即回落合成兜底场；
     * - 解码成功但栅格尺寸 / 内容退化，由 [HeightRaster.create] 内部再兜一层合成场；
     * - 成功栅格统一重采样到 [gridN]×[gridN]，与推演网格对齐。
     *
     * @param decoder 平台解码器；null 表示当前壳层未接入解码能力，直接走合成场
     * @param assetName JSON `heightMap` 字段值；空白按无图处理
     * @param gridN 推演网格边长（格）；与 `CampaignController.GRID_N` 同口径
     */
    fun fromDecoder(decoder: RasterDecoder?, assetName: String?, gridN: Int): HeightRaster {
        val name = assetName?.trim().orEmpty()
        if (decoder == null || name.isEmpty()) return syntheticFallback(gridN)
        val raster = try {
            decoder.decode(name)
        } catch (t: Throwable) {
            // 平台解码器任何异常都不允许抛到 UI；按无图处理，降级标志随合成场置真。
            null
        } ?: return syntheticFallback(gridN)
        return resample(raster, gridN)
    }

    /**
     * 内嵌栅格装配：JSON `terrain.heightPixels` 扁平数组 → 引擎栅格。
     *
     * 尺寸不符 / 空数组 / 退化 → 合成兜底场并置降级标志，不抛异常；合法栅格重采样到
     * [gridN]×[gridN]。灰度值在 [HeightRaster.create] 构造时统一夹紧到 0..255。
     */
    fun fromInlinePixels(width: Int, height: Int, pixels: IntArray?, gridN: Int): HeightRaster {
        val source = HeightRaster.create(width, height, pixels, degradedHint = false)
        if (source.degraded) return syntheticFallback(gridN)
        return resample(source, gridN)
    }

    /**
     * 重采样到 `gridN×gridN`：双线性插值；行 0 = 北缘、列 0 = 西缘的方向口径保持不变。
     *
     * 源栅格已退化时直接回落合成场，不把退化栅格带进推演网格；目标边长钳制在
     * [16, 128]，与 [HeightRaster.synthetic] 的合法区间一致。
     */
    fun resample(source: HeightRaster, gridN: Int): HeightRaster {
        val n = gridN.coerceIn(16, 128)
        if (source.degraded) return syntheticFallback(n)
        val sw = source.width
        val sh = source.height
        val out = IntArray(n * n)
        for (row in 0 until n) {
            val sy = if (n == 1) 0.0 else row.toDouble() / (n - 1) * (sh - 1)
            val y0 = floor(sy).toInt().coerceIn(0, sh - 1)
            val y1 = (y0 + 1).coerceAtMost(sh - 1)
            val fy = (sy - y0).coerceIn(0.0, 1.0)
            for (col in 0 until n) {
                val sx = if (n == 1) 0.0 else col.toDouble() / (n - 1) * (sw - 1)
                val x0 = floor(sx).toInt().coerceIn(0, sw - 1)
                val x1 = (x0 + 1).coerceAtMost(sw - 1)
                val fx = (sx - x0).coerceIn(0.0, 1.0)
                val nw = source.data[y0 * sw + x0]
                val ne = source.data[y0 * sw + x1]
                val swv = source.data[y1 * sw + x0]
                val sev = source.data[y1 * sw + x1]
                val top = nw + (ne - nw) * fx
                val bottom = swv + (sev - swv) * fx
                val gray = top + (bottom - top) * fy
                out[row * n + col] = gray.coerceIn(0.0, 255.0).toInt()
            }
        }
        return HeightRaster(n, n, out, degraded = false)
    }

    /** 合成兜底场：两层正弦缓丘谷地，degraded = true 明示非真实高程图。 */
    private fun syntheticFallback(gridN: Int): HeightRaster =
        HeightRaster.synthetic(seed = RASTER_SEED, gridN = gridN, degraded = true)

    /** 合成兜底场种子（与 controller 兜底口径 42L 区分开，便于追溯来源）。 */
    private const val RASTER_SEED: Long = 1207L
}
