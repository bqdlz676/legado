package io.legado.app.ui.map.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DEM 栅格采样单测（纯 JVM fixture，缺陷 D-APK-1）。
 *
 * 口径：0..255 灰度 ↔ 0..7000m；米制 × [DemTerrainElevation.VERTICAL_EXAGGERATION]
 * 得沙盘世界单位（7000m ↔ 63 单位）。包围盒沿用 legacy HG：104–122°E / 24–42°N。
 */
class DemTerrainElevationTest {

    private val bbox = DemTerrainElevation.DEFAULT_BBOX

    /** 2×2 fixture：西北 / 西南 = 0，东北 / 东南 = 255；行 0 = 北缘。 */
    private fun twoByTwo(): DemTerrainElevation {
        val data = intArrayOf(
            0, 255, // row0 北
            0, 255, // row1 南
        )
        return DemTerrainElevation.create(2, 2, data, bbox)
    }

    // ── 映射口径 ──

    @Test
    fun `灰度 0 映射 0 米 0 世界单位`() {
        val dem = twoByTwo()
        // 西北角（北纬 42，西经 104）灰度 0。
        assertEquals(0.0, dem.heightAt(42.0, 104.0), 1e-9)
    }

    @Test
    fun `灰度 255 映射 7000 米与 63 世界单位`() {
        val dem = twoByTwo()
        // 东南角（北纬 24，东经 122）灰度 255。
        val units = dem.heightAt(24.0, 122.0)
        assertEquals(DemTerrainElevation.DEM_MAX_METERS, 7000.0, 1e-9)
        assertEquals(7000.0 * DemTerrainElevation.VERTICAL_EXAGGERATION, units, 1e-9)
        assertEquals(63.0, units, 1e-9)
    }

    @Test
    fun `中点双线性插值取四边加权平均`() {
        val dem = twoByTwo()
        // 正中点（33°N, 113°E）：x 方向 0↔255 各半，y 方向上下同值 → 灰度 127.5。
        val units = dem.heightAt(33.0, 113.0)
        val expectedGray = 127.5
        val expected = expectedGray / 255.0 * 7000.0 *
            DemTerrainElevation.VERTICAL_EXAGGERATION
        assertEquals(expected, units, 1e-9)
        assertEquals(31.5, units, 1e-9)
    }

    // ── 边界钳制 ──

    @Test
    fun `越界经纬度钳到边缘像素不抛异常`() {
        val dem = twoByTwo()
        // 北缘以西：纬度超出北界、经度超出西界 → 钳西北角灰度 0。
        assertEquals(0.0, dem.heightAt(50.0, 100.0), 1e-9)
        // 南缘以东：纬度超出南界、经度超出东界 → 钳东南角灰度 255。
        assertEquals(63.0, dem.heightAt(10.0, 130.0), 1e-9)
        // 北缘以东 → 东北角 255。
        assertEquals(63.0, dem.heightAt(45.0, 125.0), 1e-9)
    }

    // ── 空网格 / 退化兜底 ──

    @Test
    fun `空网格构造为退化实例且高度恒 0`() {
        val dem = DemTerrainElevation.create(0, 0, null, bbox)
        assertTrue(dem.degraded)
        assertEquals(0.0, dem.heightAt(33.0, 113.0), 1e-9)
    }

    @Test
    fun `数组长度与尺寸不符时兜底 0`() {
        val dem = DemTerrainElevation.create(2, 2, IntArray(3), bbox)
        assertTrue(dem.degraded)
        assertEquals(0.0, dem.heightAt(33.0, 113.0), 1e-9)
    }

    @Test
    fun `退化包围盒兜底 0 不抛异常`() {
        val badBbox = DemTerrainElevation.Bbox(
            south = 42.0, north = 24.0, west = 104.0, east = 122.0,
        )
        val dem = DemTerrainElevation.create(2, 2, intArrayOf(0, 255, 0, 255), badBbox)
        assertTrue(dem.degraded)
        assertEquals(0.0, dem.heightAt(33.0, 113.0), 1e-9)
    }

    @Test
    fun `NaN 坐标兜底 0 不产出 NaN`() {
        val dem = twoByTwo()
        assertEquals(0.0, dem.heightAt(Double.NaN, 113.0), 1e-9)
        assertEquals(0.0, dem.heightAt(33.0, Double.NaN), 1e-9)
        assertFalse(dem.heightAt(33.0, 113.0).isNaN())
    }

    // ── ByteArray 入口 ──

    @Test
    fun `ByteArray 按无符号字节取灰度`() {
        // 补码 -1 字节 = 255；0 字节 = 0。
        val dem = DemTerrainElevation.create(1, 2, byteArrayOf(-1, 0), bbox)
        assertFalse(dem.degraded)
        assertEquals(63.0, dem.heightAt(42.0, 113.0), 1e-9)
        assertEquals(0.0, dem.heightAt(24.0, 113.0), 1e-9)
    }
}
