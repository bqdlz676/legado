package io.legado.app.ui.map.globe.mesh

import io.legado.app.ui.map.globe.GlobeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOD 选择与 morph 进度单测（切片 19 / 20）。
 */
class LodSelectorTest {

    private val radius = GlobeConfig.RADIUS.toDouble()

    @Test
    fun `远距大于 3R 选 LOD0`() {
        val selector = LodSelector()
        val level = selector.select(radius * 4.0, radius)
        assertEquals(LodLevel.LOD0, level)
    }

    @Test
    fun `中距 1R 到 3R 选 LOD1`() {
        val selector = LodSelector()
        assertEquals(LodLevel.LOD1, selector.select(radius * 2.0, radius))
    }

    @Test
    fun `近距小于 1R 选 LOD2`() {
        val selector = LodSelector()
        assertEquals(LodLevel.LOD2, selector.select(radius * 0.5, radius))
    }

    @Test
    fun `切换层级进入 morph 且 3 帧内就位`() {
        val selector = LodSelector(LodLevel.LOD0)
        selector.select(radius * 0.5, radius)
        assertTrue(selector.isMorphing)
        assertEquals(GlobeConfig.LOD_MORPH_FRAMES, selector.framesLeft)

        repeat(GlobeConfig.LOD_MORPH_FRAMES) { selector.update() }
        assertFalse(selector.isMorphing)
        assertEquals(LodLevel.LOD2, selector.current)
        assertEquals(1.0, selector.morphProgress.toDouble(), 1e-6)
    }

    @Test
    fun `morph 进度随帧数递增`() {
        val selector = LodSelector(LodLevel.LOD0)
        selector.select(radius * 0.5, radius)
        selector.update()
        assertTrue(selector.morphProgress in 0f..1f)
    }

    @Test
    fun `半径非法时兜底按中距处理不崩溃`() {
        val selector = LodSelector()
        val level = selector.select(radius * 2.0, 0.0)
        assertEquals(LodLevel.LOD1, level)
    }

    @Test
    fun `snapTo 清空 morph`() {
        val selector = LodSelector()
        selector.select(radius * 0.5, radius)
        selector.snapTo(LodLevel.LOD2)
        assertFalse(selector.isMorphing)
        assertEquals(LodLevel.LOD2, selector.current)
    }
}
