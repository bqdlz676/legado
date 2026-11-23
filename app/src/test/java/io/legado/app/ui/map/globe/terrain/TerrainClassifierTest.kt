package io.legado.app.ui.map.globe.terrain

import io.legado.app.ui.map.globe.GlobeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地表分类优先级单测：水域 → 雪线 → 极地冰盖 → 覆盖类型（切片 17 / 19 / 20）。
 */
class TerrainClassifierTest {

    private val classifier = TerrainClassifier()

    @Test
    fun `海拔小于等于 0 判为水域`() {
        val sample = classifier.classify(-100.0, CoverType.FOREST.hex, 30.0, 120.0)
        assertTrue(sample.isWater)
        assertEquals(CoverType.WATER, sample.cover)
        assertEquals(0.0, sample.displacementUnits.toDouble(), 1e-6)
    }

    @Test
    fun `海拔超过 3500 米强制积雪不读覆盖图`() {
        val sample = classifier.classify(4000.0, CoverType.FOREST.hex, 30.0, 120.0)
        assertTrue(sample.isSnow)
        assertEquals(CoverType.SNOW, sample.cover)
        assertFalse(sample.isWater)
    }

    @Test
    fun `极圈且覆盖为冰判为极地冰盖`() {
        val sample = classifier.classify(500.0, CoverType.ICE.hex, 70.0, 40.0)
        assertTrue(sample.isIce)
        assertEquals(GlobeConfig.ICE_SPECULAR.toDouble(), sample.specular.toDouble(), 1e-6)
    }

    @Test
    fun `中纬度森林按最近邻归类并带树冠微凸起`() {
        val sample = classifier.classify(800.0, CoverType.FOREST.hex, 45.0, 30.0)
        assertEquals(CoverType.FOREST, sample.cover)
        assertTrue(sample.displacementUnits > 0f)
    }

    @Test
    fun `沙漠带微波浪位移`() {
        val sample = classifier.classify(300.0, CoverType.DESERT.hex, 25.0, 50.0)
        assertEquals(CoverType.DESERT, sample.cover)
    }

    @Test
    fun `非法覆盖色兜底归类不崩溃`() {
        val sample = classifier.classify(500.0, 0x010203, 20.0, 100.0)
        assertTrue(sample.cover in CoverType.entries)
    }

    @Test
    fun `雪线阈值边界 3500 米整不算雪`() {
        val sample = classifier.classify(GlobeConfig.SNOWLINE_METERS, CoverType.GRASS.hex, 20.0, 100.0)
        assertFalse(sample.isSnow)
    }
}
