package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.terrain.FbmNoise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fBm 噪声确定性与取值范围（纯 JVM）。
 * 切片 17：森林格 fbm(x,z)×1.5 树冠微凸起，2–3 单位高。
 */
class FbmNoiseTest {

    @Test
    fun `同种子输出确定可复现`() {
        val a = FbmNoise(seed = 42)
        val b = FbmNoise(seed = 42)
        assertEquals(a.fbm(3.7, 9.2), b.fbm(3.7, 9.2), 1e-9)
        assertEquals(a.forestBump(1.1, 2.2), b.forestBump(1.1, 2.2), 1e-9)
    }

    @Test
    fun `fbm 输出落在 0 到 1`() {
        val n = FbmNoise()
        for (i in 0 until 50) {
            val v = n.fbm(i * 0.37, i * 0.91)
            assertTrue(v in 0.0..1.0)
        }
    }

    @Test
    fun `森林凸起封顶 2 点 5 单位`() {
        val n = FbmNoise()
        for (i in 0 until 50) {
            val b = n.forestBump(i * 0.53, i * 0.29)
            assertTrue(b in 0.0..2.5)
        }
    }

    @Test
    fun `高程米换算世界单位封顶 2`() {
        assertEquals(0.0, FbmNoise.elevationToUnits(-500.0), 1e-9)
        assertTrue(FbmNoise.elevationToUnits(7000.0) <= 2.0)
    }
}
