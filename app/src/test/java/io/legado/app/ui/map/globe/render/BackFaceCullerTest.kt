package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 背面剔除器单测（纯 JVM）：正面保留、背面面序剔除、整面片半球剔除、跨轮廓混合片保留。
 */
class BackFaceCullerTest {

    // 正面片：三顶点 z=1，法线朝 +Z。
    private val frontA = Vec3(1.0, 0.0, 1.0)
    private val frontB = Vec3(0.0, 1.0, 1.0)
    private val frontC = Vec3(-1.0, 0.0, 1.0)

    @Test
    fun `正面三角形保留`() {
        val decision = BackFaceCuller.shouldCull(frontA, frontB, frontC)
        assertFalse(decision.culled)
        assertEquals(CullReason.NONE, decision.reason)
    }

    @Test
    fun `翻面风序的背面片按 BACK_FACE 剔除`() {
        val decision = BackFaceCuller.shouldCull(frontA, frontC, frontB)
        assertTrue(decision.culled)
        assertEquals(CullReason.BACK_FACE, decision.reason)
    }

    @Test
    fun `整片在背面半球按 BACK_HEMISPHERE 剔除`() {
        val a = Vec3(1.0, 0.0, -1.0)
        val b = Vec3(0.0, 1.0, -1.0)
        val c = Vec3(-1.0, 0.0, -1.0)
        val decision = BackFaceCuller.shouldCull(a, b, c)
        assertTrue(decision.culled)
        assertEquals(CullReason.BACK_HEMISPHERE, decision.reason)
    }

    @Test
    fun `跨轮廓混合片不被半球剔除`() {
        // 两点在正面、一点在背面，面法线仍朝 +Z，保留进投影由深度测试兜底。
        val mixedC = Vec3(-1.0, 0.0, -1.0)
        val decision = BackFaceCuller.shouldCull(frontA, frontB, mixedC)
        assertFalse(decision.culled)
    }

    @Test
    fun `法线朝相机点积为正`() {
        assertTrue(BackFaceCuller.faceFacing(Vec3.UNIT_Z))
        assertFalse(BackFaceCuller.faceFacing(Vec3(0.0, 0.0, -1.0)))
    }

    @Test
    fun `背面半球顶点判定`() {
        assertTrue(BackFaceCuller.hemisphereVisible(Vec3(0.0, 0.0, 1.0)))
        assertFalse(BackFaceCuller.hemisphereVisible(Vec3(0.0, 0.0, -1.0)))
    }

    @Test
    fun `视线方向改变时朝向判定随之改变`() {
        val normal = Vec3.UNIT_Z
        assertTrue(BackFaceCuller.faceFacing(normal, Vec3.UNIT_Z))
        assertFalse(BackFaceCuller.faceFacing(normal, Vec3(0.0, 0.0, -1.0)))
    }
}
