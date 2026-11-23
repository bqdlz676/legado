package io.legado.app.ui.map.globe.mesh

import io.legado.app.ui.map.globe.GlobeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 球面网格几何单测：顶点 / 索引数量、UV 闭合、法线为单位向量。
 */
class SphereGridTest {

    @Test
    fun `顶点数与索引数符合经纬网格公式`() {
        val grid = SphereGrid.build(LodLevel.LOD0, GlobeConfig.RADIUS)
        assertEquals((128 + 1) * (128 + 1), grid.vertexCount)
        assertEquals(128 * 128 * 6, grid.indices.size)
    }

    @Test
    fun `法线全部为单位向量`() {
        val grid = SphereGrid.build(16, 16, GlobeConfig.RADIUS)
        for (i in grid.normals.indices step 3) {
            val nx = grid.normals[i]
            val ny = grid.normals[i + 1]
            val nz = grid.normals[i + 2]
            val len = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble())
            assertEquals(1.0, len, 1e-5)
        }
    }

    @Test
    fun `UV 横向 seam 复制一列保证闭合`() {
        val grid = SphereGrid.build(16, 16, GlobeConfig.RADIUS)
        // 同一行首列 u=0，末列 u=1
        assertEquals(0f, grid.uvs[0], 1e-6)
        assertEquals(1f, grid.uvs[1 * 17 * 2 - 2], 1e-6)
    }

    @Test
    fun `位移回调沿法线抬高顶点半径`() {
        val grid = SphereGrid.build(
            segments = 16,
            rings = 16,
            radius = GlobeConfig.RADIUS,
        ) { _, _, _, _, _ -> 2f }
        // 任取一顶点，半径应在 radius+2 附近
        val px = grid.positions[0]
        val py = grid.positions[1]
        val pz = grid.positions[2]
        val len = Math.sqrt((px * px + py * py + pz * pz).toDouble())
        assertEquals((GlobeConfig.RADIUS + 2f).toDouble(), len, 1e-3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `段数过小直接拒绝`() {
        SphereGrid.build(2, 2, GlobeConfig.RADIUS)
    }

    @Test
    fun `经纬度数组与顶点一一对应`() {
        val grid = SphereGrid.build(8, 8, GlobeConfig.RADIUS)
        assertEquals(grid.vertexCount, grid.lats.size)
        assertEquals(grid.vertexCount, grid.lngs.size)
        assertTrue(grid.lats.all { it in -90.0..90.0 })
        assertTrue(grid.lngs.all { it in -180.0..180.0 })
    }
}
