package io.legado.app.ui.map.crossing.shell

import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.RasterDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B13 高程桥接单测（纯 JVM）。
 * 覆盖：解码器成功重采样 / 东西向梯度方向保持 / 解码器缺失·名空白·null·抛异常回落合成场 /
 * 内嵌像素合法缩放 / 内嵌像素畸形回落降级。
 */
class CrossingRasterBridgeTest {

    private val gridN: Int = 32

    private class FakeDecoder(
        private val result: HeightRaster? = null,
        private val boom: Boolean = false,
    ) : RasterDecoder {
        override fun decode(assetName: String): HeightRaster? {
            if (boom) throw IllegalStateException("decode boom: $assetName")
            return result
        }
    }

    private fun flatRaster(n: Int, gray: Int): HeightRaster =
        HeightRaster.create(n, n, IntArray(n * n) { gray }, degradedHint = false)

    @Test
    fun `解码器成功：栅格重采样到 gridN 且不降级`() {
        val source = flatRaster(64, 200)
        val out = CrossingRasterBridge.fromDecoder(FakeDecoder(source), "gaixia.png", gridN)

        assertFalse("解码成功不得置降级标志", out.degraded)
        assertEquals(gridN, out.width)
        assertEquals(gridN, out.height)
        assertEquals(gridN * gridN, out.size)
        // 平地场双线性插值后灰度应保持 200，无采样漂移。
        for (row in 0 until gridN) {
            for (col in 0 until gridN) {
                assertEquals(200, out.pixel(col, row))
            }
        }
    }

    @Test
    fun `重采样保持东西向灰度梯度：西缘暗、东缘亮`() {
        // 源栅格列 0 = 0（西缘暗），列 63 = 255（东缘亮）；行向无变化。
        val source = HeightRaster.create(
            64, 64,
            IntArray(64 * 64) { i -> (i % 64) * 255 / 63 },
            degradedHint = false,
        )
        val out = CrossingRasterBridge.fromDecoder(FakeDecoder(source), "x.png", gridN)

        assertFalse(out.degraded)
        val midRow = gridN / 2
        assertTrue("西缘应暗于东缘", out.pixel(0, midRow) < out.pixel(gridN - 1, midRow))
        // 同行灰度随列单调不减（双线性 + 单调源场）。
        for (col in 1 until gridN) {
            assertTrue(
                "列 $col 不应暗于前列",
                out.pixel(col, midRow) >= out.pixel(col - 1, midRow),
            )
        }
    }

    @Test
    fun `解码器缺失或资产名空白时回落合成场并置降级`() {
        val noDecoder = CrossingRasterBridge.fromDecoder(null, "gaixia.png", gridN)
        val blankName = CrossingRasterBridge.fromDecoder(FakeDecoder(), "   ", gridN)
        val nullName = CrossingRasterBridge.fromDecoder(FakeDecoder(), null, gridN)

        listOf(noDecoder, blankName, nullName).forEach { out ->
            assertTrue("无解码能力必须降级", out.degraded)
            assertEquals(gridN, out.width)
            assertEquals(gridN, out.height)
        }
    }

    @Test
    fun `解码器返回 null 或抛异常时回落合成场且不抛穿`() {
        val nullResult = CrossingRasterBridge.fromDecoder(FakeDecoder(null), "missing.png", gridN)
        val boom = CrossingRasterBridge.fromDecoder(FakeDecoder(boom = true), "boom.png", gridN)

        listOf(nullResult, boom).forEach { out ->
            assertTrue("解码失败必须降级", out.degraded)
            assertEquals(gridN, out.width)
            assertEquals(gridN, out.height)
            assertEquals(gridN * gridN, out.size)
        }
    }

    @Test
    fun `内嵌像素合法：96 乘 96 缩放到 gridN 且不降级`() {
        val out = CrossingRasterBridge.fromInlinePixels(96, 96, IntArray(96 * 96) { 128 }, gridN)

        assertFalse(out.degraded)
        assertEquals(gridN, out.width)
        assertEquals(gridN, out.height)
        for (row in 0 until gridN) {
            for (col in 0 until gridN) {
                assertEquals(128, out.pixel(col, row))
            }
        }
    }

    @Test
    fun `解码器返回 0 乘 0 畸形栅格时回落合成场且不抛穿`() {
        // 绕过 HeightRaster.create 守卫直接构造 0×0 栅格，模拟解码器返回畸形结果。
        val zeroDims = HeightRaster(0, 0, IntArray(0), degraded = false)
        val out = CrossingRasterBridge.fromDecoder(FakeDecoder(zeroDims), "zero.png", gridN)

        assertTrue("0 维畸形栅格必须降级", out.degraded)
        assertTrue("兜底栅格边长必须为正", out.width > 0 && out.height > 0)
        assertEquals(gridN, out.width)
        assertEquals(gridN, out.height)
    }

    @Test
    fun `解码器返回长度不符畸形栅格时回落合成场且不抛穿`() {
        // 声明 64×64 但 data 仅 100 像素；进 resample 会在索引处 AIOOBE，桥接层须先收口。
        val wrongSize = HeightRaster(64, 64, IntArray(100) { 128 }, degraded = false)
        val out = CrossingRasterBridge.fromDecoder(FakeDecoder(wrongSize), "truncated.png", gridN)

        assertTrue("长度不符畸形栅格必须降级", out.degraded)
        assertTrue("兜底栅格边长必须为正", out.width > 0 && out.height > 0)
        assertEquals(gridN, out.width)
        assertEquals(gridN, out.height)
    }

    @Test
    fun `内嵌像素尺寸不符或为空时回落合成场并降级`() {
        val wrongSize = CrossingRasterBridge.fromInlinePixels(
            96, 96, IntArray(100) { 128 }, gridN,
        )
        val nullPixels = CrossingRasterBridge.fromInlinePixels(96, 96, null, gridN)
        val zeroDims = CrossingRasterBridge.fromInlinePixels(0, 0, null, gridN)

        listOf(wrongSize, nullPixels, zeroDims).forEach { out ->
            assertTrue("畸形内嵌栅格必须降级", out.degraded)
            assertTrue("兜底栅格边长必须为正", out.width > 0 && out.height > 0)
            assertEquals(out.size, out.width * out.height)
        }
    }
}
