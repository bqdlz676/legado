package io.legado.app.ui.map.globe.scene

import io.legado.app.ui.map.globe.replay.marker.GlobePlace
import io.legado.app.ui.map.globe.replay.marker.PlaceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地名分级可见性单测（切片 19：都城恒显，一般地名近距浮出、远距淡出）。
 */
class LabelSchemeTest {

    private val capital = GlobePlace("罗马", 41.9, 12.5, PlaceTier.CAPITAL, "意大利")
    private val normal = GlobePlace("撒马尔罕", 39.65, 66.96, PlaceTier.NORMAL, "中亚")

    @Test
    fun `都城任何缩放级别透明度为 1`() {
        assertEquals(1f, LabelScheme.fadeOf(capital, 5.0), 1e-6)
        assertEquals(1f, LabelScheme.fadeOf(capital, 0.3), 1e-6)
    }

    @Test
    fun `一般地名远距淡出到 0`() {
        assertEquals(0f, LabelScheme.fadeOf(normal, LabelScheme.NORMAL_LABEL_NEAR_RATIO), 1e-6)
        assertEquals(0f, LabelScheme.fadeOf(normal, 4.0), 1e-6)
    }

    @Test
    fun `一般地名近距透明度线性回升`() {
        val fade = LabelScheme.fadeOf(normal, 1.0)
        assertTrue(fade in 0f..1f)
        assertTrue(fade > 0f)
    }

    @Test
    fun `可见清单只保留 fade 大于 0 的地名`() {
        val places = listOf(capital, normal)
        val far = LabelScheme.visible(places, 5.0)
        assertEquals(1, far.size)
        assertEquals(capital, far.first().first)
    }
}
