package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.mesh.LodLevel
import io.legado.app.ui.globe.core.mesh.LodSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOD 距离分级与 morph 帧进度（纯 JVM）。
 * 切片 20：>3R → 128×128；1R–3R → 256×256；0.1R–1R → 512×512；切换 2–3 帧完成。
 */
class LodSelectorTest {

    private val radius = 100.0

    @Test
    fun `远距大于 3R 选 LOD0`() {
        val selector = LodSelector()
        assertEquals(LodLevel.LOD0, selector.select(radius * 4, radius))
    }

    @Test
    fun `中距 1R 到 3R 选 LOD1`() {
        val selector = LodSelector()
        assertEquals(LodLevel.LOD1, selector.select(radius * 2.0, radius))
        assertEquals(LodLevel.LOD1, selector.select(radius * 3.0, radius))
        assertEquals(LodLevel.LOD1, selector.select(radius * 1.0, radius))
    }

    @Test
    fun `近距小于 1R 选 LOD2`() {
        val selector = LodSelector()
        assertEquals(LodLevel.LOD2, selector.select(radius * 0.5, radius))
    }

    @Test
    fun `层级切换在 3 帧内完成 morph`() {
        val selector = LodSelector(LodLevel.LOD0)
        selector.select(radius * 0.5, radius) // 切到 LOD2
        assertTrue(selector.isMorphing)
        assertEquals(GlobeConfig.LOD_MORPH_FRAMES, selector.framesLeft)

        repeat(GlobeConfig.LOD_MORPH_FRAMES) {
            selector.update()
        }
        assertFalse(selector.isMorphing)
        assertEquals(LodLevel.LOD2, selector.current)
        assertEquals(1f, selector.morphProgress, 1e-6f)
    }

    @Test
    fun `morph 进度随帧推进`() {
        val selector = LodSelector(LodLevel.LOD0)
        selector.select(radius * 0.5, radius)
        selector.update()
        assertTrue(selector.morphProgress in 0f..1f)
        assertTrue(selector.morphProgress < 1f)
    }

    @Test
    fun `各层级段数与纹理档对齐规格`() {
        assertEquals(128, LodLevel.LOD0.segments)
        assertEquals(256, LodLevel.LOD1.segments)
        assertEquals(512, LodLevel.LOD2.segments)
        assertEquals("2K", LodLevel.LOD0.textureTier)
        assertEquals("4K", LodLevel.LOD1.textureTier)
        assertEquals("8K", LodLevel.LOD2.textureTier)
    }
}
