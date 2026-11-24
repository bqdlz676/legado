package io.legado.app.ui.map.terrain.contract

import org.junit.Assert.*
import org.junit.Test

/**
 * S-17 地表覆盖层契约单测：[terrainCoverAlpha] 纯函数口径锁定，纯 JVM，不引用 Android 类型。
 */
class TerrainCoverContractTest {

    @Test
    fun `elevation zero returns base alpha`() {
        val alpha = terrainCoverAlpha(0f, 0f)
        assertEquals(TerrainCoverParams.BASE_ALPHA, alpha, 1e-6f)
    }

    @Test
    fun `elevation one clamps near upper bound and exceeds base`() {
        val alpha = terrainCoverAlpha(1f, 0f)
        assertTrue(alpha >= TerrainCoverParams.MIN_ALPHA)
        assertTrue(alpha <= TerrainCoverParams.MAX_ALPHA)
        assertTrue(alpha > TerrainCoverParams.BASE_ALPHA)
    }

    @Test
    fun `alpha is monotonic non decreasing with elevation`() {
        val low = terrainCoverAlpha(0.2f, 0f)
        val high = terrainCoverAlpha(0.8f, 0f)
        assertTrue(high > low)
    }

    @Test
    fun `out of range elevation stays inside min max`() {
        val alpha = terrainCoverAlpha(5f, 0f)
        assertTrue(alpha >= TerrainCoverParams.MIN_ALPHA)
        assertTrue(alpha <= TerrainCoverParams.MAX_ALPHA)
    }

    @Test
    fun `nan input does not throw and stays inside range`() {
        val alpha = terrainCoverAlpha(Float.NaN, Float.NaN)
        assertTrue(alpha >= TerrainCoverParams.MIN_ALPHA)
        assertTrue(alpha <= TerrainCoverParams.MAX_ALPHA)
    }

    @Test
    fun `higher latitude does not increase alpha`() {
        val lowerLat = terrainCoverAlpha(0.5f, 0.2f)
        val higherLat = terrainCoverAlpha(0.5f, 0.8f)
        assertTrue(lowerLat >= higherLat)
    }
}
