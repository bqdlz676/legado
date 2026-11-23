package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.data.PlaceTier
import io.legado.app.ui.globe.core.data.SeedAtlas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置种子地名兜底（纯 JVM）。
 * 切片 19：都城任何缩放级别可见，一般地名放大后浮出，远距淡出。
 */
class SeedAtlasTest {

    @Test
    fun `种子地名非空且含都城`() {
        assertTrue(SeedAtlas.places.isNotEmpty())
        assertTrue(SeedAtlas.places.any { it.tier == PlaceTier.CAPITAL })
    }

    @Test
    fun `远距都城常显，一般地名淡出`() {
        val visible = SeedAtlas.visible(4.0)
        visible.forEach { (place, fade) ->
            if (place.tier == PlaceTier.CAPITAL) assertEquals(1f, fade, 1e-6f)
            else assertEquals(0f, fade, 1e-6f)
        }
    }

    @Test
    fun `近距一般地名浮现`() {
        val visible = SeedAtlas.visible(0.5)
        val normal = visible.first { it.first.tier == PlaceTier.NORMAL }
        assertTrue(normal.second > 0f)
    }

    @Test
    fun `地名经纬度落在合法范围`() {
        SeedAtlas.places.forEach {
            assertTrue(it.lat in -90.0..90.0)
            assertTrue(it.lng in -180.0..180.0)
        }
    }
}
