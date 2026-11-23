package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.GlobeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 二十面体细分球面网格单测（纯 JVM）：三角化数量公式、顶点落球、法线单位、
 * 数值有限性、位移抬高与非法入参兜底。
 */
class IcosphereMeshTest {

    @Test
    fun `基础二十面体 12 顶点 20 三角片`() {
        val mesh = IcosphereMesh.build(0, GlobeConfig.RADIUS)
        assertEquals(12, mesh.vertexCount)
        assertEquals(20, mesh.triangleCount)
    }

    @Test
    fun `细分 n 次三角片数为 20 乘 4 的 n 次方`() {
        for (n in 0..4) {
            val mesh = IcosphereMesh.build(n, GlobeConfig.RADIUS)
            assertEquals(20 * (1 shl (2 * n)), mesh.triangleCount)
            // 顶点数公式 10·4^n + 2（边中点共享，缝处不分裂）。
            assertEquals(10 * (1 shl (2 * n)) + 2, mesh.vertexCount)
        }
    }

    @Test
    fun `所有顶点落在指定半径球面上`() {
        val mesh = IcosphereMesh.build(3, GlobeConfig.RADIUS)
        for (i in 0 until mesh.vertexCount) {
            val p = mesh.positionOf(i)
            assertEquals(GlobeConfig.RADIUS.toDouble(), p.length, 1e-3)
        }
    }

    @Test
    fun `所有法线为单位向量且数值有限`() {
        val mesh = IcosphereMesh.build(3, GlobeConfig.RADIUS)
        for (i in 0 until mesh.vertexCount) {
            val n = mesh.normalOf(i)
            assertEquals(1.0, n.length, 1e-5)
            assertTrue("法线分量必须有限", n.x.isFinite() && n.y.isFinite() && n.z.isFinite())
        }
        for (i in mesh.positions.indices) assertTrue("位置分量必须有限", mesh.positions[i].isFinite())
    }

    @Test
    fun `索引全部落在顶点范围内`() {
        val mesh = IcosphereMesh.build(2, GlobeConfig.RADIUS)
        mesh.indices.forEach { idx ->
            assertTrue("索引 $idx 越界", idx in 0 until mesh.vertexCount)
        }
    }

    @Test
    fun `位移回调沿法线抬高顶点半径`() {
        val mesh = IcosphereMesh.build(2, GlobeConfig.RADIUS) { _ -> 2f }
        val p = mesh.positionOf(0)
        assertEquals((GlobeConfig.RADIUS + 2f).toDouble(), p.length, 1e-3)
    }

    @Test
    fun `相邻细分层级三角形数单调递增`() {
        val coarse = IcosphereMesh.build(2, GlobeConfig.RADIUS)
        val fine = IcosphereMesh.build(3, GlobeConfig.RADIUS)
        assertTrue(fine.triangleCount > coarse.triangleCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `负细分次数直接拒绝`() {
        IcosphereMesh.build(-1, GlobeConfig.RADIUS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法半径直接拒绝`() {
        IcosphereMesh.build(1, 0f)
    }

    @Test
    fun `单位法线方向与位置方向一致`() {
        val mesh = IcosphereMesh.build(2, GlobeConfig.RADIUS)
        val p = mesh.positionOf(5).normalize()
        val n = mesh.normalOf(5)
        assertTrue(abs(p.dot(n)) > 0.999)
    }
}
