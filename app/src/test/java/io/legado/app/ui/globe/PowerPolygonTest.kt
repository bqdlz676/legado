package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.replay.FactionPalette
import io.legado.app.ui.globe.core.replay.power.PowerLayer
import io.legado.app.ui.globe.core.replay.power.SeedPowers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切片 18：势力多边形贴球判点（射线法）、128×128 网格采样、阵营配色。
 */
class PowerPolygonTest {

    private val layer = PowerLayer(SeedPowers.all)

    @Test
    fun `种子势力共 12 套`() {
        assertEquals(12, SeedPowers.all.size)
    }

    @Test
    fun `罗马城内判入罗马势力，北京不在任何势力内`() {
        val rome = layer.assign(41.90, 12.50)
        assertNotNull("罗马城应落入罗马势力", rome)
        assertEquals("rome", rome?.id)

        val beijing = layer.assign(39.90, 116.40)
        assertNull("北京不应落入任一国外势力示意多边形", beijing)
    }

    @Test
    fun `波斯波利斯判入波斯，哈拉和林判入蒙古`() {
        val persia = layer.assign(29.93, 52.89)
        assertNotNull(persia)
        assertEquals("persia", persia?.id)

        val mongol = layer.assign(47.19, 102.82)
        assertNotNull(mongol)
        assertEquals("mongol", mongol?.id)
    }

    @Test
    fun `网格采样产出非空命中`() {
        val hits = layer.sampleGrid()
        assertTrue("128×128 网格应有命中格", hits.isNotEmpty())
        assertTrue("命中格方向应为单位向量", hits.all { it.dir.length in 0.99..1.01 })
    }

    @Test
    fun `贴球壳四边形每块四角且带势力色`() {
        val quads = layer.shellQuads()
        assertTrue("应生成色块四边形", quads.isNotEmpty())
        quads.forEach { q ->
            assertEquals(4, q.corners.size)
            assertTrue("色值应为 #RRGGBB", q.colorHex.startsWith("#"))
            assertTrue("透明度在 0..1", q.alpha in 0.0f..1.0f)
        }
    }

    @Test
    fun `阵营配色对齐切片18：罗马暖金 波斯暗铜 蒙古朱砂红`() {
        assertEquals("#D4A54A", FactionPalette.colorOf("rome"))
        assertEquals("#8B6914", FactionPalette.colorOf("persia"))
        assertEquals("#C23B2E", FactionPalette.colorOf("mongol"))
        assertEquals("#D4A54A", FactionPalette.colorOf("macedon"))
        // 未知阵营兜底暖金
        assertEquals("#D4A54A", FactionPalette.colorOf("unknown-faction"))
    }

    @Test
    fun `hex 解析到 0 到 1 通道`() {
        val (r, g, b) = FactionPalette.rgb("#8B6914")
        assertTrue(r in 0.0f..1.0f)
        assertTrue(g in 0.0f..1.0f)
        assertTrue(b in 0.0f..1.0f)
        // 暗铜 R=139 G=105 B=20
        assertEquals(139 / 255f, r, 1e-3f)
        assertEquals(105 / 255f, g, 1e-3f)
        assertEquals(20 / 255f, b, 1e-3f)
    }
}
