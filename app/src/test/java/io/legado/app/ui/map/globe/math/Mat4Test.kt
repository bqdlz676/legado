package io.legado.app.ui.map.globe.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4×4 矩阵单测：单位阵、旋转、观察矩阵、点变换。
 */
class Mat4Test {

    @Test
    fun `单位阵变换点保持不变`() {
        val m = Mat4.identity()
        val p = m.transformPoint(Vec3(1.0, 2.0, 3.0))
        assertEquals(1.0, p.x, 1e-6)
        assertEquals(2.0, p.y, 1e-6)
        assertEquals(3.0, p.z, 1e-6)
    }

    @Test
    fun `绕 Y 轴旋转 90 度把 X 轴转到 Z 轴`() {
        val m = Mat4.rotationY(Math.PI / 2)
        val p = m.transformPoint(Vec3.UNIT_X)
        assertEquals(0.0, p.x, 1e-6)
        assertEquals(0.0, p.y, 1e-6)
        assertEquals(1.0, p.z, 1e-6)
    }

    @Test
    fun `lookAt 观察矩阵基向量正交`() {
        val view = Mat4.lookAt(Vec3(0.0, 0.0, 5.0), Vec3.ZERO, Vec3.UNIT_Y)
        // 相机在 +Z 看向原点，z 轴基向量应指向 +Z
        assertEquals(0.0, view.m[2].toDouble(), 1e-6)
        assertEquals(0.0, view.m[6].toDouble(), 1e-6)
        assertEquals(1.0, view.m[10].toDouble(), 1e-6)
    }

    @Test
    fun `透视投影远平面 z 分量为负`() {
        val proj = Mat4.perspective(Math.PI / 4, 1.0, 1f, 100f)
        assertTrue(proj.m[10] < 0f)
        assertEquals(-1.0, proj.m[11].toDouble(), 1e-6)
    }
}
