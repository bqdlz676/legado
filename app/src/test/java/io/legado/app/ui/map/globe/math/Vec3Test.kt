package io.legado.app.ui.map.globe.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * [Vec3] 基础几何运算单测。
 */
class Vec3Test {

    @Test
    fun `加减数乘与点积符合向量代数`() {
        val a = Vec3(1.0, 2.0, 3.0)
        val b = Vec3(4.0, 5.0, 6.0)
        assertEquals(Vec3(5.0, 7.0, 9.0), a + b)
        assertEquals(Vec3(-3.0, -3.0, -3.0), a - b)
        assertEquals(Vec3(2.0, 4.0, 6.0), a * 2)
        assertEquals(32.0, a.dot(b), 1e-9)
    }

    @Test
    fun `叉乘方向与右手系一致`() {
        val x = Vec3.UNIT_X
        val y = Vec3.UNIT_Y
        assertEquals(Vec3.UNIT_Z, x.cross(y))
    }

    @Test
    fun `单位化保持长度为一`() {
        val v = Vec3(3.0, 4.0, 0.0)
        val n = v.normalize()
        assertEquals(1.0, n.length, 1e-9)
    }

    @Test
    fun `零向量单位化返回零向量不产生 NaN`() {
        val n = Vec3.ZERO.normalize()
        assertTrue(n.x.isNaN().not() && n.y.isNaN().not() && n.z.isNaN().not())
        assertEquals(Vec3.ZERO, n)
    }

    @Test
    fun `lerp 端点等于输入`() {
        val a = Vec3(0.0, 0.0, 0.0)
        val b = Vec3(10.0, 0.0, 0.0)
        assertEquals(a, a.lerp(b, 0.0))
        assertEquals(b, a.lerp(b, 1.0))
        assertEquals(Vec3(5.0, 0.0, 0.0), a.lerp(b, 0.5))
    }

    @Test
    fun `长度平方与长度一致`() {
        val v = Vec3(2.0, 3.0, 6.0)
        assertEquals(sqrt(v.lengthSquared()), v.length, 1e-9)
    }
}
