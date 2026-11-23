package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.terrain.CoverType
import io.legado.app.ui.globe.core.terrain.TerrainClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地表覆盖分类 + 雪线 + 冰盖判定（纯 JVM）。
 * 切片 17：7 种覆盖；雪线 >3500m 从高程自动算；切片 19：极地冰盖从覆盖图读，specular 0.15。
 */
class TerrainClassifierTest {

    private val classifier = TerrainClassifier()

    private fun rgb(t: CoverType) = (t.r shl 16) or (t.g shl 8) or t.b

    @Test
    fun `海拔不大于 0 自动判为水域`() {
        val s = classifier.classify(-100.0, rgb(CoverType.FOREST), 30.0, 120.0)
        assertEquals(CoverType.WATER, s.cover)
        assertTrue(s.isWater)
        assertEquals(0f, s.displacementUnits, 1e-6f)
    }

    @Test
    fun `海拔超过 3500m 强制积雪，不读覆盖图`() {
        val s = classifier.classify(4000.0, rgb(CoverType.FOREST), 28.0, 86.0)
        assertEquals(CoverType.SNOW, s.cover)
        assertTrue(s.isSnow)
    }

    @Test
    fun `雪线边界：3500 不积雪，3501 积雪`() {
        val atLine = classifier.classify(3500.0, rgb(CoverType.GRASS), 30.0, 100.0)
        val above = classifier.classify(3501.0, rgb(CoverType.GRASS), 30.0, 100.0)
        assertTrue(!atLine.isSnow)
        assertTrue(above.isSnow)
    }

    @Test
    fun `极地高纬冰覆盖判为冰盖 specular 0 点 15`() {
        val s = classifier.classify(800.0, rgb(CoverType.ICE), 72.0, 40.0)
        assertEquals(CoverType.ICE, s.cover)
        assertTrue(s.isIce)
        assertEquals(GlobeConfig.ICE_SPECULAR, s.specular, 1e-6f)
    }

    @Test
    fun `低纬冰色覆盖不判冰盖`() {
        val s = classifier.classify(800.0, rgb(CoverType.ICE), 20.0, 40.0)
        assertTrue(!s.isIce)
    }

    @Test
    fun `森林格带回 fbm 微凸起位移`() {
        val s = classifier.classify(500.0, rgb(CoverType.FOREST), 30.0, 120.0)
        assertEquals(CoverType.FOREST, s.cover)
        assertTrue("森林位移应大于纯高程基准", s.displacementUnits > 0f)
    }

    @Test
    fun `覆盖色最近邻归类`() {
        assertEquals(CoverType.GRASS, classifier.classify(300.0, rgb(CoverType.GRASS), 30.0, 120.0).cover)
        assertEquals(CoverType.DESERT, classifier.classify(300.0, rgb(CoverType.DESERT), 25.0, 50.0).cover)
        assertEquals(CoverType.SOIL, classifier.classify(300.0, rgb(CoverType.SOIL), 35.0, 110.0).cover)
        assertEquals(CoverType.URBAN, classifier.classify(300.0, rgb(CoverType.URBAN), 39.0, 116.0).cover)
    }

    @Test
    fun `七种地形类型齐全`() {
        assertEquals(7, CoverType.TERRAIN_TYPES.size)
    }
}
