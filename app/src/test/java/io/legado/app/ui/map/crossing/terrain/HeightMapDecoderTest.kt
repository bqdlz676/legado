package io.legado.app.ui.map.crossing.terrain

import io.legado.app.ui.map.crossing.engine.CampaignJsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import kotlin.math.abs

/**
 * HeightMapDecoder 单测（纯 JVM，B13）。
 * 覆盖：正常灰度 / RGBA 带 alpha / 空字节 / 非 PNG 头 / 超大图降采样 / 合成回落确定性 /
 * 全零图 / 1×1 图 / base64 内联往返 / 来源分类 / 解码失败回落合成。
 *
 * 测试内自带最小 PNG 编码器（filter 0、CRC 写 0；解码器不校验 CRC），不引第三方图像库。
 */
class HeightMapDecoderTest {

    // ── 测试用最小 PNG 编码 ──────────────────────────────────────

    private fun be32(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(),
        (v ushr 8).toByte(), v.toByte(),
    )

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(be32(data.size))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(data)
        out.write(ByteArray(4)) // CRC：解码器跳过校验，测试写 0 占位
        return out.toByteArray()
    }

    private fun encodePng(
        width: Int,
        height: Int,
        colorType: Int,
        channels: Int,
        pixel: (x: Int, y: Int) -> IntArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val ihdr = ByteArray(13)
        System.arraycopy(be32(width), 0, ihdr, 0, 4)
        System.arraycopy(be32(height), 0, ihdr, 4, 4)
        ihdr[8] = 8 // bit depth
        ihdr[9] = colorType.toByte()
        out.write(chunk("IHDR", ihdr))

        val raw = ByteArray(height * (1 + width * channels))
        var p = 0
        for (y in 0 until height) {
            raw[p++] = 0 // filter: None
            for (x in 0 until width) {
                val px = pixel(x, y)
                for (c in 0 until channels) raw[p++] = px[c].toByte()
            }
        }
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val comp = ByteArray(raw.size * 2 + 64)
        val n = deflater.deflate(comp)
        deflater.end()
        out.write(chunk("IDAT", comp.copyOf(n)))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    // ── DoD 5 用例 ──────────────────────────────────────────────

    @Test
    fun `正常灰度图解码为 0 到 1 归一化栅格`() {
        val png = encodePng(4, 4, colorType = 0, channels = 1) { x, y ->
            intArrayOf((x * 64 + y * 16).coerceIn(0, 255))
        }
        val raster = HeightMapDecoder.decodeBytes(png)
        assertNotNull(raster)
        raster!!
        assertEquals(4, raster.width)
        assertEquals(4, raster.height)
        assertEquals(16, raster.size)
        assertTrue(raster.data.all { it in 0f..1f })
        val expect00 = 0 / 255f
        assertEquals(expect00, raster.data[0], 1e-6f)
        val expect33 = (3 * 64 + 3 * 16) / 255f
        assertEquals(expect33, raster.data[15], 1e-6f)
        assertTrue(!raster.degraded)
    }

    @Test
    fun `RGBA 图忽略 Alpha 取 RGB 平均亮度`() {
        val png = encodePng(2, 2, colorType = 6, channels = 4) { _, _ ->
            intArrayOf(100, 150, 200, 0) // alpha=0 不应影响高程
        }
        val raster = HeightMapDecoder.decodeBytes(png)
        assertNotNull(raster)
        raster!!
        assertEquals(2, raster.width)
        assertEquals(2, raster.height)
        val expect = (100 + 150 + 200) / 3 / 255f
        assertTrue(raster.data.all { abs(it - expect) < 1e-5f })
    }

    @Test
    fun `空字节与 null 输入返回 null`() {
        assertNull(HeightMapDecoder.decodeBytes(null))
        assertNull(HeightMapDecoder.decodeBytes(ByteArray(0)))
        assertNull(HeightMapDecoder.decodeInline(null))
        assertNull(HeightMapDecoder.decodeInline(""))
    }

    @Test
    fun `非 PNG 头返回 null 不抛异常`() {
        val bogus = "this is not a png stream".toByteArray(Charsets.US_ASCII)
        assertNull(HeightMapDecoder.decodeBytes(bogus))
    }

    @Test
    fun `超大栅格经降采样护栏收口到上限内`() {
        val source = HeightMapRaster(
            width = 100, height = 40,
            data = FloatArray(4000) { (it % 256) / 255f },
            degraded = false, source = SourceKind.ASSET,
        )
        val capped = HeightMapDecoder.downsample(source, maxDimension = 50, maxPixels = 2000)
        assertTrue(capped.width <= 50)
        assertTrue(capped.height <= 50)
        assertTrue(capped.size <= 2000)
        assertTrue(capped.data.all { it in 0f..1f })
        // 未超护栏的栅格原样返回
        val small = HeightMapRaster(8, 8, FloatArray(64) { 0.5f }, false, SourceKind.ASSET)
        val untouched = HeightMapDecoder.downsample(small, 2048, 4_000_000)
        assertEquals(8, untouched.width)
        assertEquals(8, untouched.height)
    }

    @Test
    fun `合成回落确定性：同字符串两次结果逐格相同`() {
        val a = HeightMapDecoder.synthesize("gaixia.png")
        val b = HeightMapDecoder.synthesize("gaixia.png")
        assertArrayEquals(a.data, b.data, 1e-7f)
        assertTrue(a.degraded)
        assertEquals(SourceKind.SYNTHETIC, a.source)
        assertTrue(a.data.all { it in 0f..1f })
        assertEquals(HeightMapDecoder.DEFAULT_GRID_N, a.width)
        assertEquals(HeightMapDecoder.DEFAULT_GRID_N, a.height)
    }

    @Test
    fun `全零图解码为全零栅格`() {
        val png = encodePng(3, 3, colorType = 0, channels = 1) { _, _ -> intArrayOf(0) }
        val raster = HeightMapDecoder.decodeBytes(png)
        assertNotNull(raster)
        raster!!
        assertTrue(raster.data.all { it == 0f })
    }

    @Test
    fun `1 乘 1 图解码单格高程`() {
        val png = encodePng(1, 1, colorType = 0, channels = 1) { _, _ -> intArrayOf(200) }
        val raster = HeightMapDecoder.decodeBytes(png)
        assertNotNull(raster)
        raster!!
        assertEquals(1, raster.width)
        assertEquals(1, raster.height)
        assertEquals(200 / 255f, raster.data[0], 1e-6f)
    }

    // ── 扩展：内联 base64 / 分类 / 回落 ──────────────────────────

    @Test
    fun `base64 内联通路往返解码成功`() {
        val png = encodePng(4, 4, colorType = 0, channels = 1) { x, y ->
            intArrayOf((x * 40 + y * 20).coerceIn(0, 255))
        }
        val b64 = Base64.getEncoder().encodeToString(png)
        val raster = HeightMapDecoder.decodeInline(b64)
        assertNotNull(raster)
        raster!!
        assertEquals(4, raster.width)
        assertEquals(4, raster.height)
        assertEquals(SourceKind.BASE64, raster.source)

        val dataUri = "data:image/png;base64,$b64"
        val fromUri = HeightMapDecoder.decodeInline(dataUri)
        assertNotNull(fromUri)
        assertArrayEquals(raster.data, fromUri!!.data, 1e-7f)
    }

    @Test
    fun `来源分类识别资产名 路径 内联 base64 与空值`() {
        assertEquals(SourceKind.ASSET, HeightMapDecoder.classify("gaixia.png"))
        assertEquals(SourceKind.LOCAL_PATH, HeightMapDecoder.classify("/sdcard/map/x.png"))
        assertEquals(SourceKind.LOCAL_PATH, HeightMapDecoder.classify("file:///map/x.png"))
        assertEquals(SourceKind.BASE64, HeightMapDecoder.classify("data:image/png;base64,AAAA"))
        val longB64 = Base64.getEncoder().encodeToString(ByteArray(48) { it.toByte() })
        assertEquals(SourceKind.BASE64, HeightMapDecoder.classify(longB64))
        assertEquals(SourceKind.NONE, HeightMapDecoder.classify(null))
        assertEquals(SourceKind.NONE, HeightMapDecoder.classify("   "))
    }

    @Test
    fun `资产 reader 缺失或解码失败回落合成场并置降级标志`() {
        val raster = HeightMapDecoder.decode("gaixia.png", reader = null)
        assertTrue(raster.degraded)
        assertEquals(SourceKind.SYNTHETIC, raster.source)
        assertTrue(raster.data.all { it in 0f..1f })

        val badInline = HeightMapDecoder.decode("data:image/png;base64,@@@not-base64@@@", reader = null)
        assertTrue(badInline.degraded)
        assertEquals(SourceKind.SYNTHETIC, badInline.source)
    }

    @Test
    fun `parser 既有 API 暴露解码器：无 reader 时回落合成栅格`() {
        // 经 CampaignJsonParser 暴露的既有 API 装配；model 暂未接纳栅格字段，壳层后续经此取栅格。
        val raster = CampaignJsonParser.decodeHeightMap("gaixia.png", null)
        assertTrue(raster.degraded)
        assertTrue(raster.data.all { it in 0f..1f })
    }
}
