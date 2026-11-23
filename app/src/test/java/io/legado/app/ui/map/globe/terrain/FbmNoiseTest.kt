package io.legado.app.ui.map.globe.terrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 确定性噪声单测：同种子可复现、输出区间受控、森林凸起封顶。
 */
class FbmNoiseTest {

    @Test
    fun `同种子同坐标结果可复现`() {
        val a = FbmNoise(seed = 20261123)
        val b = FbmNoise(seed = 20261123)
        assertEquals(a.fbm(1.2, 3.4), b.fbm(1.2, 3.4), 1e-12)
    }

    @Test
    fun `fbm 输出落在 0 到 1`() {
        val noise = FbmNoise()
        for (i in 0 until 50) {
            val v = noise.fbm(i * 0.37, i * 0.91)
            assertTrue("fbm 越界: $v", v in 0.0..1.0)
        }
    }

    @Test
    fun `森林凸起封顶 2.5 单位`() {
        val noise = FbmNoise()
        for (i in 0 until 50) {
            val v = noise.forestBump(i * 0.5, i * 0.5)
            assertTrue("森林凸起越界: $v", v in 0.0..2.5)
        }
    }

    @Test
    fun `高程换算 7000 米对应 2 单位`() {
        assertEquals(2.0, FbmNoise.elevationToUnits(7000.0), 1e-9)
        assertEquals(0.0, FbmNoise.elevationToUnits(-500.0), 1e-9)
    }

    @Test
    fun `不同种子产生不同噪声场`() {
        val a = FbmNoise(seed = 1)
        val b = FbmNoise(seed = 2)
        assertTrue(Math.abs(a.fbm(2.0, 2.0) - b.fbm(2.0, 2.0)) > 1e-6)
    }
}
