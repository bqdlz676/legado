package io.legado.app.ui.map.crossing.shell

import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.RasterDecoder
import io.legado.app.ui.map.crossing.engine.CampaignController
import io.legado.app.ui.map.crossing.engine.CampaignJsonParser
import io.legado.app.ui.map.sandbox.DemTerrainElevation
import io.legado.app.ui.map.sandbox.TerrainElevation

/**
 * 高程桥接（壳层专属，F12）：把沙盘层**已解码**的 [TerrainElevation] 采样成引擎吃的
 * [HeightRaster] 0..255 灰度栅格。
 *
 * 红线口径：
 * - 不在本层二次解码 PNG；PNG 只由 sandbox `DemTerrainLoader` 解码一次，这里只做网格重采样。
 * - 引擎层（model / engine / terrain）零 Android / sandbox 引用；sandbox 类型只在本壳层文件出现。
 * - 高程世界单位 → 米 → 灰度的反算系数与 [DemTerrainElevation] 正算口径严格互逆，避免灰度漂移。
 */
object CrossingRasterBridge {

    /**
     * 按战役焦点投影把高程采样到 [gridN]×[gridN] 栅格。
     *
     * @param elevation 已解码高程；[TerrainElevation.Flat] 或退化实例回落合成场并置降级标志
     */
    fun fromElevation(elevation: TerrainElevation, focus: TerrainFocus, gridN: Int): HeightRaster {
        if (elevation === TerrainElevation.Flat) {
            return syntheticFallback(gridN)
        }
        val projection = GeoProjection(focus, gridN)
        val degraded = (elevation as? DemTerrainElevation)?.degraded ?: false
        val data = IntArray(gridN * gridN)
        for (row in 0 until gridN) {
            for (col in 0 until gridN) {
                val geo = projection.toGeo(Cell(col, row))
                val units = elevation.heightAt(geo.lat, geo.lng)
                // 正算：units = meters × VERTICAL_EXAGGERATION；此处严格反算回米再折灰度。
                val meters = units / DemTerrainElevation.VERTICAL_EXAGGERATION
                val gray = (meters / DemTerrainElevation.DEM_MAX_METERS * 255.0)
                    .coerceIn(0.0, 255.0)
                    .toInt()
                data[row * gridN + col] = gray
            }
        }
        return HeightRaster.create(gridN, gridN, data, degradedHint = degraded)
    }

    /**
     * B13 解码装配：按战役 JSON 声明的 `heightMap` 名，经注入的 [RasterDecoder] 解码成引擎栅格。
     *
     * 兜底口径（不空白、不崩溃、置降级标志）：
     * - decoder 缺失、资产名为空、解码返回 null、解码抛异常，任一命中即回落 [syntheticFallback]；
     * - 解码成功但栅格尺寸 / 内容退化时，由 [HeightRaster.create] 内部再兜一层合成场；
     * - 本方法不引 Android 类型，平台 PNG 解码由 [RasterDecoder] 的 Android 薄适配实现注入。
     *
     * @param decoder 平台解码器；null 表示当前壳层未接入解码能力，直接走合成场
     * @param assetName JSON `heightMap` 字段值（如 `gaixia.png`），空白按无图处理
     */
    fun fromDecoder(decoder: RasterDecoder?, assetName: String?, gridN: Int): HeightRaster {
        val name = assetName?.trim().orEmpty()
        if (decoder == null || name.isEmpty()) return syntheticFallback(gridN)
        val raster = try {
            decoder.decode(name)
        } catch (t: Throwable) {
            null
        }
        return raster ?: syntheticFallback(gridN)
    }

    /** 合成兜底场：两层正弦缓丘谷地，degraded = true 明示非真实高程图。 */
    private fun syntheticFallback(gridN: Int): HeightRaster =
        HeightRaster.synthetic(seed = RASTER_SEED, gridN = gridN, degraded = true)

    /** 合成兜底场种子（与 controller 兜底口径区分开，便于追溯）。 */
    private const val RASTER_SEED: Long = 1207L
}
