package io.legado.app.ui.perf.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-08 性能分档参数单测：纯 JVM，只用 org.junit 与 kotlin stdlib，
 * 不引用任何平台类型，不读资源（避免测试类 NoClassDefFoundError）。
 * 规格：docs/立项-补条款-第二批.md 第一节 1.1 低端机静态档 / 1.2 P90 21·33ms 两级 / 1.3 后台冻结。
 */
class PerfTierParamsTest {

    private val delta = 1e-9f

    @Test
    fun `high tier when p90 twenty millisecond and eight gigabyte memory`() {
        val tier = perfTierOf(p90FrameMs = 20f, deviceMemoryMb = 8000)
        assertEquals(PerfTier.HIGH, tier)
    }

    @Test
    fun `mid tier when p90 thirty millisecond`() {
        val tier = perfTierOf(p90FrameMs = 30f, deviceMemoryMb = 4000)
        assertEquals(PerfTier.MID, tier)
    }

    @Test
    fun `low tier when p90 forty millisecond`() {
        val tier = perfTierOf(p90FrameMs = 40f, deviceMemoryMb = 8000)
        assertEquals(PerfTier.LOW, tier)
    }

    @Test
    fun `nan frame time falls back to low without throwing`() {
        val tier = perfTierOf(p90FrameMs = Float.NaN, deviceMemoryMb = 8000)
        assertEquals(PerfTier.LOW, tier)
    }

    @Test
    fun `negative inputs fall back to low without throwing`() {
        val tier = perfTierOf(p90FrameMs = -6f, deviceMemoryMb = -512)
        assertEquals(PerfTier.LOW, tier)
    }

    @Test
    fun `background app returns zero particle scale`() {
        val scale = particleScaleFor(tier = PerfTier.HIGH, appInBackground = true)
        assertEquals(0f, scale, delta)
    }

    @Test
    fun `low tier static frame flag is true`() {
        assertTrue(PerfTierParams.STATIC_FRAME_LOW)
    }

    @Test
    fun `frozen tier returns zero particle scale`() {
        val scale = particleScaleFor(tier = PerfTier.FROZEN, appInBackground = false)
        assertEquals(0f, scale, delta)
    }
}
