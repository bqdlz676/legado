package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 宿主投影器单测（纯 JVM）：正面 / 背面剔除、圆盘边界、反投影往返、数值有限性、
 * 地轴倾斜施加。投影是 Compose 壳把球面方向落到屏幕的唯一换算口，几何错了会导致
 * 光针 / 路线 / 标签穿球面或飞出台面，这里逐路径兜底。
 */
class GlobeProjectionTest {

    private val projection = GlobeProjection(
        viewportWidth = 1000f,
        viewportHeight = 1000f,
        radiusPx = 200f,
        yawDeg = 0.0,
        pitchDeg = 0.0,
    )

    @Test
    fun `正面方向可见且落在圆盘半径内`() {
        val p = projection.project(Vec3.UNIT_Z)
        assertTrue("正面应可见", p.visible)
        assertTrue("深度应为正", p.depth > 0f)
        val cx = 500f
        val cy = 500f
        assertTrue(abs(p.x - cx) <= 200f + 1e-3f)
        assertTrue(abs(p.y - cy) <= 200f + 1e-3f)
    }

    @Test
    fun `背面方向剔除不可见`() {
        val p = projection.project(Vec3(0.0, 0.0, -1.0))
        assertTrue("背面深度应为负", p.depth < 0f)
        assertTrue("背面不可见", !p.visible)
    }

    @Test
    fun `反投影圆盘中心往返一致`() {
        val dir = projection.unproject(500f, 500f)
        assertNotNull(dir)
        val back = projection.project(dir!!)
        assertEquals(500f, back.x, 1e-2f)
        assertEquals(500f, back.y, 1e-2f)
    }

    @Test
    fun `圆盘之外反投影返回 null 不造点位`() {
        assertNull(projection.unproject(500f + 400f, 500f))
        assertNull(projection.unproject(500f, 500f - 400f))
    }

    @Test
    fun `球面均匀方向投影坐标全部有限`() {
        repeat(360) { i ->
            val lat = -89.0 + i * 0.5
            val lng = -180.0 + (i % 360)
            val dir = io.legado.app.ui.map.globe.geo.Geo.direction(lat, lng)
            val p = projection.project(dir)
            assertTrue("x 应有限", p.x.isFinite())
            assertTrue("y 应有限", p.y.isFinite())
            assertTrue("depth 应有限", p.depth.isFinite())
        }
    }

    @Test
    fun `地轴倾斜确实施加到模型法线`() {
        val tilted = projection.transform(Vec3.UNIT_Y)
        // 绕 X 轴倾斜 23.5° 后，Y 轴方向会带上 Z 分量，不再是纯 Y。
        assertTrue(abs(tilted.z) > 1e-6)
        assertEquals(1.0, tilted.length, 1e-6)
    }

    @Test
    fun `偏航俯仰改变投影方位`() {
        val rotated = GlobeProjection(1000f, 1000f, 200f, yawDeg = 90.0, pitchDeg = 0.0)
        val p = rotated.project(Vec3.UNIT_Z)
        // 绕 Y 转 90° 后原 Z 方向落到屏幕侧方，不应仍在圆心。
        assertTrue(abs(p.x - 500f) > 1f)
    }
}
