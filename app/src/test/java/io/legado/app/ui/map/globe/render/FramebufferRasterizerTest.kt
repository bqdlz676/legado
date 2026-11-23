package io.legado.app.ui.map.globe.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 帧缓冲深度测试 + 软渲染光栅化单测（纯 JVM）：覆盖统计、近处盖住远处、
 * 越界拒绝、退化三角形空跑、清缓冲复位。
 */
class FramebufferRasterizerTest {

    private fun newBuffer() = Framebuffer(100, 100)

    @Test
    fun `写入像素通过深度测试并计入覆盖`() {
        val fb = newBuffer()
        assertTrue(fb.tryWrite(50, 50, 0.8f))
        assertEquals(1, fb.coveredPixels)
        assertEquals(1, fb.fragmentsWritten)
    }

    @Test
    fun `更近片元覆盖更远片元`() {
        val fb = newBuffer()
        assertTrue(fb.tryWrite(50, 50, 0.5f))
        // 更远的片元被拒。
        assertFalse(fb.tryWrite(50, 50, 0.3f))
        assertEquals(1, fb.fragmentsRejectedDepth)
        // 更近的片元写入。
        assertTrue(fb.tryWrite(50, 50, 0.9f))
        assertEquals(0.9f, fb.depthAt(50, 50), 1e-6f)
    }

    @Test
    fun `越界与非有限深度拒绝写入`() {
        val fb = newBuffer()
        assertFalse(fb.tryWrite(-1, 50, 0.5f))
        assertFalse(fb.tryWrite(100, 50, 0.5f))
        assertFalse(fb.tryWrite(50, 100, 0.5f))
        assertFalse(fb.tryWrite(50, 50, Float.NaN))
        assertEquals(0, fb.coveredPixels)
    }

    @Test
    fun `清缓冲复位全部深度与统计`() {
        val fb = newBuffer()
        fb.tryWrite(50, 50, 0.8f)
        fb.tryWrite(20, 20, 0.6f)
        fb.clear()
        assertEquals(0, fb.coveredPixels)
        assertEquals(0, fb.fragmentsWritten)
        assertEquals(0, fb.fragmentsRejectedDepth)
        assertTrue(fb.depthAt(50, 50).isNaN().not() && fb.depthAt(50, 50) == Float.NEGATIVE_INFINITY)
    }

    @Test
    fun `光栅化三角形覆盖中心区域`() {
        val fb = newBuffer()
        val raster = SoftwareRasterizer(fb)
        val a = ScreenPoint(40f, 40f, 0.8f, true)
        val b = ScreenPoint(60f, 40f, 0.8f, true)
        val c = ScreenPoint(50f, 60f, 0.8f, true)
        val result = raster.rasterize(a, b, c)
        assertTrue("三角形应覆盖若干像素", result.pixels > 0)
        assertTrue("应写入片元", result.accepted > 0)
        assertEquals(0, result.rejectedDepth)
        assertTrue(fb.coveredPixels > 0)
    }

    @Test
    fun `近处三角形盖住远处同区域三角形`() {
        val fb = newBuffer()
        val raster = SoftwareRasterizer(fb)
        val far = listOf(
            ScreenPoint(40f, 40f, 0.2f, true),
            ScreenPoint(60f, 40f, 0.2f, true),
            ScreenPoint(50f, 60f, 0.2f, true),
        )
        val near = listOf(
            ScreenPoint(42f, 42f, 0.9f, true),
            ScreenPoint(58f, 42f, 0.9f, true),
            ScreenPoint(50f, 58f, 0.9f, true),
        )
        raster.rasterize(far[0], far[1], far[2])
        val before = fb.depthAt(50, 50)
        raster.rasterize(near[0], near[1], near[2])
        val after = fb.depthAt(50, 50)
        assertTrue("远处深度应已写入", before > 0f)
        assertTrue("近处深度应覆盖远处", after > before)
    }

    @Test
    fun `退化三角形不产生片元`() {
        val fb = newBuffer()
        val raster = SoftwareRasterizer(fb)
        val p = ScreenPoint(50f, 50f, 0.8f, true)
        val result = raster.rasterize(p, p, p)
        assertTrue(result.empty)
        assertEquals(0, fb.coveredPixels)
    }
}
