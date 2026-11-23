package io.legado.app.ui.map.crossing.shell

import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.RasterDecoder
import io.legado.app.ui.map.sandbox.TerrainElevation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 高程桥接单测（纯 JVM）。
 * 覆盖 B13：decoder 装配 / 资产名缺失 / 解码返回 null / 解码抛异常 四条兜底通路，
 * 以及 sandbox 平面地形回落合成场；任一路径都不得抛穿、不得产出空白栅格。
 */
class CrossingRasterBridgeTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val gridN = 16

    private fun validRaster(degradedHint: Boolean = false): HeightRaster {
        val data = IntArray(gridN * gridN) { 128 }
        return HeightRaster.create(gridN, gridN, data, degradedHint = degradedHint)
    }

    @Test
    fun `B13 decoder 返回有效栅格时原样透传`() {
        val expected = validRaster(degradedHint = false)
        val decoder = RasterDecoder { _ -> expected }
        val raster = CrossingRasterBridge.fromDecoder(decoder, "gaixia.png", gridN)
        assertFalse("有效栅格不应标记降级", raster.degraded)
        assertEquals(gridN, raster.width)
        assertEquals(gridN, raster.height)
    }

    @Test
    fun `B13 decoder 缺失时回落合成兜底场并置降级标志`() {
        val raster = CrossingRasterBridge.fromDecoder(null, "gaixia.png", gridN)
        assertTrue(raster.degraded)
        assertTrue(raster.width > 0 && raster.height > 0)
    }

    @Test
    fun `B13 资产名为空时回落合成兜底场并置降级标志`() {
        val decoder = RasterDecoder { _ -> validRaster() }
        val blank1 = CrossingRasterBridge.fromDecoder(decoder, "", gridN)
        val blank2 = CrossingRasterBridge.fromDecoder(decoder, "   ", gridN)
        val blank3 = CrossingRasterBridge.fromDecoder(decoder, null, gridN)
        assertTrue(blank1.degraded)
        assertTrue(blank2.degraded)
        assertTrue(blank3.degraded)
    }

    @Test
    fun `B13 解码返回 null 时回落合成兜底场并置降级标志`() {
        val decoder = RasterDecoder { _ -> null }
        val raster = CrossingRasterBridge.fromDecoder(decoder, "missing.png", gridN)
        assertTrue(raster.degraded)
        assertTrue(raster.size > 0)
    }

    @Test
    fun `B13 解码抛异常时不抛穿，回落合成兜底场并置降级标志`() {
        val decoder = RasterDecoder { _ -> throw IllegalStateException("asset corrupted") }
        var raster: HeightRaster? = null
        var thrown: Throwable? = null
        try {
            raster = CrossingRasterBridge.fromDecoder(decoder, "corrupt.png", gridN)
        } catch (t: Throwable) {
            thrown = t
        }
        assertEquals("桥接层不得把解码异常抛到 UI", null, thrown)
        assertTrue(raster!!.degraded)
    }

    @Test
    fun `平面地形 Flat 回落合成兜底场并置降级标志`() {
        val raster = CrossingRasterBridge.fromElevation(TerrainElevation.Flat, focus, gridN)
        assertTrue(raster.degraded)
        assertTrue(raster.width > 0 && raster.height > 0)
    }
}
