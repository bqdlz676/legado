package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.geo.Geo
import io.legado.app.ui.map.globe.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 渲染相机单测（纯 JVM）：正面 / 背面深度、反投影往返、圆盘边界、地轴 23.5° 倾斜
 * 施加、偏航俯仰改变方位、球面均匀方向投影数值有限。
 */
class GlobeCameraTest {

    private val camera = GlobeCamera(
        viewportWidth = 1000f,
        viewportHeight = 1000f,
        radiusPx = 200f,
        yawDeg = 0.0,
        pitchDeg = 0.0,
    )

    @Test
    fun `正面方向可见且深度为正`() {
        val p = camera.project(Vec3.UNIT_Z)
        assertTrue("正面应可见", p.visible)
        assertTrue("深度应为正", p.depth > 0f)
        assertEquals(500f, p.x, 1e-2f)
        assertEquals(500f, p.y, 1e-2f)
    }

    @Test
    fun `背面方向深度为负且不可见`() {
        val p = camera.project(Vec3(0.0, 0.0, -1.0))
        assertTrue("背面深度应为负", p.depth < 0f)
        assertTrue("背面不可见", !p.visible)
    }

    @Test
    fun `反投影圆盘中心往返一致`() {
        val dir = camera.unproject(500f, 500f)
        assertNotNull(dir)
        val back = camera.project(dir!!)
        assertEquals(500f, back.x, 1e-2f)
        assertEquals(500f, back.y, 1e-2f)
    }

    @Test
    fun `圆盘之外反投影返回 null 不造点位`() {
        assertNull(camera.unproject(500f + 400f, 500f))
        assertNull(camera.unproject(500f, 500f - 400f))
    }

    @Test
    fun `地轴倾斜确实施加到模型法线`() {
        val tilted = camera.transform(Vec3.UNIT_Y)
        // 绕 X 轴倾斜 23.5° 后，Y 轴方向带上 Z 分量，不再是纯 Y。
        assertTrue(abs(tilted.z) > 1e-6)
        assertEquals(1.0, tilted.length, 1e-6)
    }

    @Test
    fun `偏航 90 度改变投影方位`() {
        val rotated = GlobeCamera(1000f, 1000f, 200f, yawDeg = 90.0)
        val p = rotated.project(Vec3.UNIT_Z)
        assertTrue(abs(p.x - 500f) > 1f)
    }

    @Test
    fun `俯仰改变屏幕纵向位置`() {
        val pitched = GlobeCamera(1000f, 1000f, 200f, pitchDeg = 30.0)
        val p = pitched.project(Vec3.UNIT_Z)
        // 俯仰绕 X 轴旋转，正面点仍在屏幕中轴，但 y 会偏离圆心。
        assertTrue(abs(p.y - 500f) > 1f)
    }

    @Test
    fun `球面均匀方向投影坐标全部有限`() {
        repeat(360) { i ->
            val lat = -89.0 + i * 0.5
            val lng = -180.0 + (i % 360)
            val p = camera.project(Geo.direction(lat, lng))
            assertTrue("x 应有限", p.x.isFinite())
            assertTrue("y 应有限", p.y.isFinite())
            assertTrue("depth 应有限", p.depth.isFinite())
        }
    }

    @Test
    fun `投影点落在圆盘半径内`() {
        repeat(180) { i ->
            val lat = -89.0 + i
            val lng = -180.0 + (i % 180) * 2
            val p = camera.project(Geo.direction(lat, lng))
            val dx = p.x - 500f
            val dy = p.y - 500f
            assertTrue("投影应在圆盘内", dx * dx + dy * dy <= 200f * 200f + 1e-3f)
        }
    }
}
