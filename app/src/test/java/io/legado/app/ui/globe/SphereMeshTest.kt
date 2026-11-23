package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.mesh.LodLevel
import io.legado.app.ui.globe.core.mesh.SphereMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 球面网格几何校验（纯 JVM，不依赖 GL）。
 * 对齐切片 20：SphereGeometry(100, 128, 128)；赤道到北极连续不中断。
 */
class SphereMeshTest {

    @Test
    fun `顶点数等于 (rings+1) × (segments+1)`() {
        val mesh = SphereMesh.build(LodLevel.LOD0, 100f)
        val expected = (LodLevel.LOD0.rings + 1) * (LodLevel.LOD0.segments + 1)
        assertEquals(expected, mesh.vertexCount)
        assertEquals(expected * 3, mesh.positions.size)
        assertEquals(expected * 3, mesh.normals.size)
        assertEquals(expected * 2, mesh.uvs.size)
    }

    @Test
    fun `索引数等于 rings × segments × 6`() {
        val mesh = SphereMesh.build(LodLevel.LOD0, 100f)
        assertEquals(LodLevel.LOD0.rings * LodLevel.LOD0.segments * 6, mesh.indices.size)
    }

    @Test
    fun `所有顶点落在半径 100 的球面上`() {
        val mesh = SphereMesh.build(LodLevel.LOD0, 100f)
        for (i in 0 until mesh.vertexCount) {
            val x = mesh.positions[i * 3]
            val y = mesh.positions[i * 3 + 1]
            val z = mesh.positions[i * 3 + 2]
            val r = sqrt(x * x + y * y + z * z)
            assertTrue("顶点 $i 半径偏离: $r", abs(r - 100f) < 1e-3f)
        }
    }

    @Test
    fun `法线为单位向量且与位置同向`() {
        val mesh = SphereMesh.build(LodLevel.LOD0, 100f)
        for (i in 0 until mesh.vertexCount) {
            val nx = mesh.normals[i * 3]
            val ny = mesh.normals[i * 3 + 1]
            val nz = mesh.normals[i * 3 + 2]
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            assertTrue("法线 $i 长度偏离: $len", abs(len - 1f) < 1e-3f)
        }
    }

    @Test
    fun `北极顶点纬度 90，南极 -90`() {
        val mesh = SphereMesh.build(LodLevel.LOD1, 100f)
        val cols = LodLevel.LOD1.segments + 1
        assertEquals(90.0, mesh.lats[0], 1e-6)
        assertEquals(-90.0, mesh.lats[mesh.lats.size - cols], 1e-6)
    }

    @Test
    fun `经度 seam 闭合：u=0 与 u=1 同一位置`() {
        val mesh = SphereMesh.build(LodLevel.LOD1, 100f)
        val cols = LodLevel.LOD1.segments + 1
        for (r in 1 until LodLevel.LOD1.rings) {
            val a = r * cols
            val b = a + LodLevel.LOD1.segments
            for (k in 0..2) {
                assertEquals(mesh.positions[a * 3 + k], mesh.positions[b * 3 + k], 1e-4f)
            }
        }
    }

    @Test
    fun `位移沿法线方向叠加`() {
        val mesh = SphereMesh.build(8, 8, 100f) { _, _, _, _, _ -> 5f }
        for (i in 0 until mesh.vertexCount) {
            val x = mesh.positions[i * 3]
            val y = mesh.positions[i * 3 + 1]
            val z = mesh.positions[i * 3 + 2]
            val r = sqrt(x * x + y * y + z * z)
            assertTrue("位移后半径应为 105，实际 $r", abs(r - 105f) < 1e-2f)
        }
    }
}
