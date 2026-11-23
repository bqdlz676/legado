package io.legado.app.ui.map.globe.replay.route

import io.legado.app.ui.map.globe.GlobeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 球面河流模型单测：有效性兜底、采样连续、贴球面半径。
 */
class GlobeRiverTest {

    private fun river() = GlobeRiver(
        id = "r_test",
        name = "测试河",
        pts = listOf(
            (34.0 to 108.0),
            (35.0 to 110.0),
            (36.0 to 112.0),
        ),
        width = 0.4,
    )

    @Test
    fun `两点以上河道成立`() {
        assertTrue(river().isValid())
        assertFalse(GlobeRiver("x", "x", listOf(34.0 to 108.0), 0.4).isValid())
    }

    @Test
    fun `采样点数为段数乘每段步数加一`() {
        val pts = river().sampledPoints(GlobeConfig.RADIUS.toDouble(), perSegment = 12)
        assertEquals(2 * 12 + 1, pts.size)
    }

    @Test
    fun `采样点贴在球面半径上`() {
        val pts = river().sampledPoints(GlobeConfig.RADIUS.toDouble())
        pts.forEach {
            assertEquals(GlobeConfig.RADIUS.toDouble(), it.length, 1e-4)
        }
    }

    @Test
    fun `无效河道返回空采样不崩溃`() {
        val empty = GlobeRiver("x", "x", emptyList(), 0.4)
        assertTrue(empty.sampledPoints(100.0).isEmpty())
    }
}
