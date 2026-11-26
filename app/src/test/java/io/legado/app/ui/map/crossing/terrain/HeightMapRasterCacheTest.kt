package io.legado.app.ui.map.crossing.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * HeightMapRasterCache 缓存共享与不可变性单测（Q-247，纯 JVM）。
 *
 * 覆盖：
 * - 同一 key 两次命中返回同一栅格实例，生产者只调一次；
 * - 命中栅格导出的副本互相独立，改写任一副本都不污染缓存源栅格；
 * - 两个调用方各取副本，读值互不影响；
 * - 槽位被 LRU 逐出后再次命中同一 key，走重新解码（生产者再次调用）。
 *
 * 不可变口径：[HeightMapRaster] backing 数组私有，对外只有标量读取 [HeightMapRaster.valueAt]
 * 与防御性副本 [HeightMapRaster.toFloatArray]；本类用「改副本后源栅格不变」反向锁定该约定，
 * 改坏（backing 数组泄漏成公共可变引用）时对应断言会红。
 */
class HeightMapRasterCacheTest {

    private fun stubRaster(seed: Float, w: Int = 4, h: Int = 4): HeightMapRaster =
        HeightMapRaster(
            w, h,
            FloatArray(w * h) { (seed + it / 100f).coerceIn(0f, 1f) },
            false, SourceKind.ASSET,
        )

    @Test
    fun `同一 key 两次命中返回同一栅格实例 生产者只调用一次`() {
        HeightMapRasterCache.resetCache()
        var producerCalls = 0
        val producer = { producerCalls++; stubRaster(0.1f) }

        val first = HeightMapRasterCache.getOrDecode("q247-same", producer)
        val second = HeightMapRasterCache.getOrDecode("q247-same", producer)

        assertNotNull(first)
        assertNotNull(second)
        assertSame(first, second)
        assertEquals(1, producerCalls)
        assertEquals(1, HeightMapRasterCache.cacheMisses)
        assertEquals(1, HeightMapRasterCache.cacheHits)
    }

    @Test
    fun `导出副本互相独立 改写副本不污染缓存源栅格`() {
        HeightMapRasterCache.resetCache()
        val raster = HeightMapRasterCache.getOrDecode("q247-iso") { stubRaster(0.2f) }!!
        val originalFirst = raster.valueAt(0)

        val copyA = raster.toFloatArray()
        val copyB = raster.toFloatArray()
        assertNotSame(copyA, copyB)

        copyA[0] = 0.99f

        // 改 A 既不回写源栅格，也不影响另一份副本 B。
        assertEquals(originalFirst, raster.valueAt(0), 1e-7f)
        assertEquals(originalFirst, copyB[0], 1e-7f)
    }

    @Test
    fun `两个调用方各取副本读值互不影响`() {
        HeightMapRasterCache.resetCache()
        val raster = HeightMapRasterCache.getOrDecode("q247-two") { stubRaster(0.3f) }!!
        val sourceThird = raster.valueAt(2)

        val callerA = raster.toFloatArray()
        val callerB = raster.toFloatArray()
        callerA[2] = 0.0f
        callerB[2] = 0.8f

        assertEquals(0.0f, callerA[2], 1e-7f)
        assertEquals(0.8f, callerB[2], 1e-7f)
        // 源栅格保持解码原值，不被任一调用方的副本改写带偏。
        assertEquals(sourceThird, raster.valueAt(2), 1e-7f)
    }

    @Test
    fun `槽位逐出后再命中同一 key 重新解码 生产者再次调用`() {
        HeightMapRasterCache.resetCache()
        var producerCallsA = 0

        HeightMapRasterCache.getOrDecode("q247-a") { producerCallsA++; stubRaster(0.4f) }
        HeightMapRasterCache.getOrDecode("q247-b") { stubRaster(0.5f) }
        // 容量上限 2：插入 c 逐出最久未用的 a。
        HeightMapRasterCache.getOrDecode("q247-c") { stubRaster(0.6f) }
        assertEquals(1, HeightMapRasterCache.cacheEvictions)

        val again = HeightMapRasterCache.getOrDecode("q247-a") { producerCallsA++; stubRaster(0.4f) }
        assertNotNull(again)
        assertEquals(2, producerCallsA)
        // a 首解、b、c、a 逐出后重解：共 4 次未命中，0 次命中。
        assertEquals(4, HeightMapRasterCache.cacheMisses)
        assertEquals(0, HeightMapRasterCache.cacheHits)
    }
}
