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

    /**
     * 最小 PNG 编码器（测试自建，不引第三方图像库）。
     *
     * @param filter 扫描线滤波码 0..4（None/Sub/Up/Average/Paeth）；编码器按 PNG 规范反算滤波字节
     * @param palette 色彩类型 3 的 PLTE 表；非空时先写 PLTE chunk
     */
    private fun encodePng(
        width: Int,
        height: Int,
        colorType: Int,
        channels: Int,
        pixel: (x: Int, y: Int) -> IntArray,
        filter: Int = 0,
        palette: List<IntArray>? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val ihdr = ByteArray(13)
        System.arraycopy(be32(width), 0, ihdr, 0, 4)
        System.arraycopy(be32(height), 0, ihdr, 4, 4)
        ihdr[8] = 8 // bit depth
        ihdr[9] = colorType.toByte()
        out.write(chunk("IHDR", ihdr))

        if (palette != null) {
            val plte = ByteArray(palette.size * 3)
            palette.forEachIndexed { i, e ->
                plte[i * 3] = e[0].toByte()
                plte[i * 3 + 1] = e[1].toByte()
                plte[i * 3 + 2] = e[2].toByte()
            }
            out.write(chunk("PLTE", plte))
        }

        val bpp = channels
        val raw = ByteArray(height * (1 + width * channels))
        var p = 0
        var prev = IntArray(width * channels)
        for (y in 0 until height) {
            val curr = IntArray(width * channels)
            for (x in 0 until width) {
                val px = pixel(x, y)
                for (c in 0 until channels) curr[x * channels + c] = px[c] and 0xFF
            }
            raw[p++] = filter
            for (i in 0 until width * channels) {
                val left = if (i >= bpp) curr[i - bpp] else 0
                val up = prev[i]
                val upLeft = if (i >= bpp) prev[i - bpp] else 0
                val filt = when (filter) {
                    1 -> curr[i] - left
                    2 -> curr[i] - up
                    3 -> curr[i] - (left + up) / 2
                    4 -> curr[i] - paethPred(left, up, upLeft)
                    else -> curr[i]
                }
                raw[p++] = filt.toByte() // 低 8 位回绕，等价 mod 256
            }
            prev = curr
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

    /** 编码器侧 Paeth 预测，与解码器 `paeth` 同口径，用于反算滤波字节。 */
    private fun paethPred(a: Int, b: Int, c: Int): Int {
        val q = a + b - c
        val pa = abs(q - a)
        val pb = abs(q - b)
        val pc = abs(q - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    /** 仅声明 IHDR 尺寸的畸形流：用于硬拒绝护栏断言，避免在测试里真分配天文像素缓冲。 */
    private fun headerOnlyPng(width: Int, height: Int, colorType: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArray(13)
        System.arraycopy(be32(width), 0, ihdr, 0, 4)
        System.arraycopy(be32(height), 0, ihdr, 4, 4)
        ihdr[8] = 8
        ihdr[9] = colorType.toByte()
        out.write(chunk("IHDR", ihdr))
        out.write(chunk("IDAT", ByteArray(0)))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    // ── DoD 5 用例 ──────────────────────────────────────────────

    @Test
    fun `正常灰度图解码为 0 到 1 归一化栅格`() {
        val png = encodePng(4, 4, colorType = 0, channels = 1, pixel = { x, y ->
            intArrayOf((x * 64 + y * 16).coerceIn(0, 255))
        })
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
        val png = encodePng(2, 2, colorType = 6, channels = 4, pixel = { _, _ ->
            intArrayOf(100, 150, 200, 0) // alpha=0 不应影响高程
        })
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
        val png = encodePng(3, 3, colorType = 0, channels = 1, pixel = { _, _ -> intArrayOf(0) })
        val raster = HeightMapDecoder.decodeBytes(png)
        assertNotNull(raster)
        raster!!
        assertTrue(raster.data.all { it == 0f })
    }

    @Test
    fun `1 乘 1 图解码单格高程`() {
        val png = encodePng(1, 1, colorType = 0, channels = 1, pixel = { _, _ -> intArrayOf(200) })
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
        val png = encodePng(4, 4, colorType = 0, channels = 1, pixel = { x, y ->
            intArrayOf((x * 40 + y * 20).coerceIn(0, 255))
        })
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
        // ≥200 字符的纯 base64 字母表串才判 BASE64（M4 阈值）；160 字节 → 约 216 字符。
        val longB64 = Base64.getEncoder().encodeToString(ByteArray(160) { it.toByte() })
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

    // ── M2：滤波 1-4 与色彩类型 2/3 解码证据 ──────────────────────

    // 2×2 灰度重建值：row0 [80, 160]，row1 [40, 200]；期望值 = 灰度 / 255f。
    private val filterPixels: (x: Int, y: Int) -> IntArray = { x, y ->
        val v = if (y == 0) intArrayOf(80, 160)[x] else intArrayOf(40, 200)[x]
        intArrayOf(v)
    }

    private fun assertGray2x2(raster: HeightMapRaster?) {
        assertNotNull(raster)
        raster!!
        assertEquals(2, raster.width)
        assertEquals(2, raster.height)
        assertEquals(80 / 255f, raster.data[0], 1e-6f)
        assertEquals(160 / 255f, raster.data[1], 1e-6f)
        assertEquals(40 / 255f, raster.data[2], 1e-6f)
        assertEquals(200 / 255f, raster.data[3], 1e-6f)
    }

    @Test
    fun `M2 滤波 1 Sub 反重建正确`() {
        // Sub: Filt[x] = Raw[x] - Raw[x-bpp]；bpp=1，x=0 时 left=0。
        val png = encodePng(2, 2, colorType = 0, channels = 1, pixel = filterPixels, filter = 1)
        assertGray2x2(HeightMapDecoder.decodeBytes(png))
    }

    @Test
    fun `M2 滤波 2 Up 反重建正确`() {
        // Up: Filt[x] = Raw[x] - Prev[x]；首行 Prev 全 0，次行 Prev = row0。
        val png = encodePng(2, 2, colorType = 0, channels = 1, pixel = filterPixels, filter = 2)
        assertGray2x2(HeightMapDecoder.decodeBytes(png))
    }

    @Test
    fun `M2 滤波 3 Average 反重建正确`() {
        // Average: Filt[x] = Raw[x] - floor((left + up) / 2)。
        val png = encodePng(2, 2, colorType = 0, channels = 1, pixel = filterPixels, filter = 3)
        assertGray2x2(HeightMapDecoder.decodeBytes(png))
    }

    @Test
    fun `M2 滤波 4 Paeth 反重建正确`() {
        // Paeth: Filt[x] = Raw[x] - paeth(left, up, upLeft)；首行 up/upLeft=0。
        val png = encodePng(2, 2, colorType = 0, channels = 1, pixel = filterPixels, filter = 4)
        assertGray2x2(HeightMapDecoder.decodeBytes(png))
    }

    @Test
    fun `M2 色彩类型 2 RGB 取通道平均亮度`() {
        // 像素0 (60,120,180) → 灰度 (60+120+180)/3 = 120；
        // 像素1 (30,90,150) → 灰度 (30+90+150)/3 = 90。
        val png = encodePng(1, 2, colorType = 2, channels = 3, pixel = { x, _ ->
            if (x == 0) intArrayOf(60, 120, 180) else intArrayOf(30, 90, 150)
        })
        val raster = HeightMapDecoder.decodeBytes(png)
        assertNotNull(raster)
        raster!!
        assertEquals(120 / 255f, raster.data[0], 1e-6f)
        assertEquals(90 / 255f, raster.data[1], 1e-6f)
    }

    @Test
    fun `M2 色彩类型 3 调色板按 PLTE 索引取亮度`() {
        // PLTE[0]=(255,255,0) → 灰度 (255+255+0)/3 = 170；
        // PLTE[1]=(0,255,0) → 灰度 (0+255+0)/3 = 85。
        val palette = listOf(intArrayOf(255, 255, 0), intArrayOf(0, 255, 0))
        val png = encodePng(2, 1, colorType = 3, channels = 1, palette = palette, pixel = { x, _ ->
            intArrayOf(x)
        })
        val raster = HeightMapDecoder.decodeBytes(png)
        assertNotNull(raster)
        raster!!
        assertEquals(170 / 255f, raster.data[0], 1e-6f)
        assertEquals(85 / 255f, raster.data[1], 1e-6f)
    }

    // ── M4：分类优先级（base64 字符集先于路径分隔符） ──────────────

    @Test
    fun `M4 含斜杠的裸标准 base64 判 BASE64 不误判 ASSET`() {
        val bytes = ByteArray(160) { (it * 7 % 256).toByte() }
        val b64 = Base64.getEncoder().encodeToString(bytes)
        assertTrue("构造串长度应 ≥200", b64.length >= 200)
        assertTrue("标准 base64 字母表含 '/'", b64.contains('/') || b64.contains('+'))
        assertEquals(SourceKind.BASE64, HeightMapDecoder.classify(b64))
    }

    @Test
    fun `M4 含斜杠的短相对路径判 ASSET`() {
        assertEquals(SourceKind.ASSET, HeightMapDecoder.classify("assets/map/gaixia.png"))
    }

    @Test
    fun `M4 纯 base64 字符集但很短的串落 ASSET 兜底桶`() {
        // 11 字符，虽全在 base64 字母表内但低于 200 字符阈值，不进内联通路。
        assertEquals(SourceKind.ASSET, HeightMapDecoder.classify("YWJjZGVmZw"))
    }

    // ── M6：硬上限拒绝与失败可观测 ──────────────────────────────

    @Test
    fun `M6 超硬上限尺寸被硬拒绝返回 null 而非静默降级`() {
        // 单维 8193 > HARD_DIMENSION=8192。
        assertNull(HeightMapDecoder.decodeBytes(headerOnlyPng(8193, 1)))
        // 像素 3000×3001 = 9,003,000 > HARD_PIXEL_CAP=8,000,000。
        assertNull(HeightMapDecoder.decodeBytes(headerOnlyPng(3000, 3001)))
    }
}
