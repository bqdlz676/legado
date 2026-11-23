package io.legado.app.ui.map.globe.replay.power

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.replay.FactionPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 势力多边形射线判点、网格采样与贴球壳色块单测（切片 18 / 20）。
 */
class PowerPolygonTest {

    private fun square() = PowerPolygon(
        id = "test",
        name = "测试势力",
        factionId = "rome",
        colorHex = FactionPalette.hexOf("rome"),
        relation = PowerRelation.OWN,
        pts = listOf(30.0 to 10.0, 40.0 to 10.0, 40.0 to 20.0, 30.0 to 20.0),
    )

    @Test
    fun `多边形内点判为命中`() {
        val p = square()
        assertTrue(p.contains(35.0, 15.0))
    }

    @Test
    fun `多边形外点判为未命中`() {
        val p = square()
        assertFalse(p.contains(10.0, 15.0))
        assertFalse(p.contains(35.0, 50.0))
    }

    @Test
    fun `不足三个顶点不构成势力`() {
        val p = PowerPolygon("x", "x", "x", "not-a-color", PowerRelation.NEUTRAL, listOf(1.0 to 1.0, 2.0 to 2.0))
        assertFalse(p.contains(1.5, 1.5))
    }

    @Test
    fun `关系透明度对齐切片 20 阈值`() {
        assertEquals(GlobeConfig.POWER_OWN_ALPHA, PowerRelation.OWN.alpha(), 1e-6)
        assertEquals(GlobeConfig.POWER_ENEMY_ALPHA, PowerRelation.ENEMY.alpha(), 1e-6)
        assertEquals(GlobeConfig.POWER_ALLY_ALPHA, PowerRelation.ALLY.alpha(), 1e-6)
    }

    @Test
    fun `网格采样返回命中方向且数量非负`() {
        val layer = PowerLayer(listOf(square()))
        val hits = layer.sampleGrid(32, 64)
        assertTrue(hits.isNotEmpty())
        hits.forEach { assertTrue(it.dir.length > 0.9) }
    }

    @Test
    fun `空势力图层采样与色块均为空不崩溃`() {
        val layer = PowerLayer(emptyList())
        assertTrue(layer.sampleGrid().isEmpty())
        assertTrue(layer.shellQuads().isEmpty())
    }

    @Test
    fun `种子势力共 12 套且关键城市归属正确`() {
        val layer = io.legado.app.ui.map.globe.data.SeedPowers.let { PowerLayer(it.all) }
        assertEquals(12, layer.powers.size)
        assertEquals("rome", layer.assign(41.90, 12.50)?.id)
        assertEquals("persia", layer.assign(29.93, 52.89)?.id)
        assertEquals("mongol", layer.assign(47.19, 102.82)?.id)
        assertTrue(layer.assign(39.90, 116.40) == null)
    }

    @Test
    fun `贴球壳色块四角同势力才出块`() {
        val layer = PowerLayer(listOf(square()))
        val quads = layer.shellQuads(32, 64)
        assertTrue(quads.isNotEmpty())
        quads.forEach { assertEquals(4, it.corners.size) }
    }
}
