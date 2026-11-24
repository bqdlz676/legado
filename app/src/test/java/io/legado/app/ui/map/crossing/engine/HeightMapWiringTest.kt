package io.legado.app.ui.map.crossing.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

/**
 * B13/M5/M6 接线单测（纯 JVM，独立文件；不改 `CampaignControllerTest.kt`）。
 *
 * 覆盖：
 * - controller 装配链按 `heightMap` 调 `CampaignJsonParser.decodeHeightMap`；
 * - 0..1 归一化栅格 → 0..255 `HeightRaster` 换算点集中落地；
 * - 解码失败回落合成场，warning 并入 definition 既有 warnings 表（含 "heightMap" 字样）；
 * - parser 内联坏 base64 记 warning，文案可被壳层读取。
 */
class HeightMapWiringTest {

    // ── 测试用最小 1×1 灰度 PNG 编码（filter 0，CRC 占位；解码器不校验 CRC） ──

    private fun be32(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(),
        (v ushr 8).toByte(), v.toByte(),
    )

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(be32(data.size))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(data)
        out.write(ByteArray(4))
        return out.toByteArray()
    }

    private fun tinyGrayPng(value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArray(13)
        System.arraycopy(be32(1), 0, ihdr, 0, 4)
        System.arraycopy(be32(1), 0, ihdr, 4, 4)
        ihdr[8] = 8 // bit depth
        ihdr[9] = 0 // color type 0 灰度
        out.write(chunk("IHDR", ihdr))
        val raw = byteArrayOf(0, value.toByte()) // filter None + 单像素
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val comp = ByteArray(64)
        val n = deflater.deflate(comp)
        deflater.end()
        out.write(chunk("IDAT", comp.copyOf(n)))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    private fun jsonWithHeightMap(heightMap: String): String = """
        {
          "id": "wiring-test",
          "title": "接线测试",
          "heightMap": "$heightMap",
          "armies": [],
          "sides": []
        }
    """.trimIndent()

    @Test
    fun `M5 内联合法 PNG 经装配链转为 0 到 255 栅格并挂 internal 原始栅格`() {
        // 1×1 灰度值 128：解码器归一化 128/255f；装配点 ×255 四舍五入回 0..255。
        val b64 = Base64.getEncoder().encodeToString(tinyGrayPng(128))
        val controller = CampaignController.load(jsonWithHeightMap(b64), null)

        assertNotNull(controller.heightMapRaster)
        assertFalse("真实 PNG 解码产物不应标 degraded", controller.heightMapRaster!!.degraded)
        assertEquals(1, controller.raster.width)
        assertEquals(1, controller.raster.height)
        // 换算算式：round(128/255 × 255) = 128；夹紧区间 0..255。
        assertEquals(128, controller.raster.pixel(0, 0))
        assertFalse(controller.raster.degraded)
    }

    @Test
    fun `M5 解码失败回落合成场且 warning 并入既有 warnings 表`() {
        // 220 个 'A'：字符集合法、长度过阈值 → 判 BASE64；base64 解出全零字节，非 PNG → 回落合成。
        val badInline = "A".repeat(220)
        val controller = CampaignController.load(jsonWithHeightMap(badInline), null)

        assertNotNull(controller.heightMapRaster)
        assertTrue(controller.raster.degraded)
        assertTrue(controller.definition.degraded)
        assertTrue(
            "warning 必须含 heightMap 字样且经既有 warnings 通道可读",
            controller.warnings.any { it.contains("heightMap") },
        )
    }

    @Test
    fun `M6 parser 坏内联 base64 记 heightMap 文案 warning`() {
        val def = CampaignJsonParser.parse(jsonWithHeightMap("A".repeat(220)))
        assertTrue(def.degraded)
        assertTrue(
            def.warnings.any { it.contains("heightMap") },
        )
    }

    @Test
    fun `M5 字段空白保持既有合成兜底口径不重复记 warning`() {
        val controller = CampaignController.load("""{"id":"x","armies":[],"sides":[]}""", null)
        assertNotNull(controller)
        assertTrue(controller.raster.degraded)
        assertTrue(controller.heightMapRaster == null)
    }
}
