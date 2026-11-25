package io.legado.app.ui.map.globe.mesh

import io.legado.app.ui.map.globe.GlobeConfig
import io.legado.app.ui.map.globe.geo.Geo
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
        assertEquals(0.0, grid.uvs[0].toDouble(), 1e-6)
        assertEquals(1.0, grid.uvs[1 * 17 * 2 - 2].toDouble(), 1e-6)
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

    @Test
    fun `八个采样点顶点与法线对齐Geo方向`() {
        val grid = SphereGrid.build(LodLevel.LOD0, GlobeConfig.RADIUS)
        val cols = LodLevel.LOD0.segments + 1
        val radius = GlobeConfig.RADIUS.toDouble()
        // 跨北极 / 赤道 / 南极、跨 seam 的 8 个采样行列。
        val samples = listOf(
            0 to 0, 0 to 128, 32 to 40, 64 to 0,
            64 to 64, 64 to 128, 96 to 80, 128 to 64,
        )
        for ((r, s) in samples) {
            val i = r * cols + s
            val dir = Geo.direction(grid.lats[i], grid.lngs[i])
            val nx = grid.normals[i * 3].toDouble()
            val ny = grid.normals[i * 3 + 1].toDouble()
            val nz = grid.normals[i * 3 + 2].toDouble()
            // 法线即 Geo 单位方向（阵列存 Float，容差按 Float 精度取 1e-6）。
            assertEquals(dir.x, nx, 1e-6)
            assertEquals(dir.y, ny, 1e-6)
            assertEquals(dir.z, nz, 1e-6)
            // 顶点沿法线径向放置：位置 / 半径与法线同向。
            assertEquals(nx, grid.positions[i * 3].toDouble() / radius, 1e-6)
            assertEquals(ny, grid.positions[i * 3 + 1].toDouble() / radius, 1e-6)
            assertEquals(nz, grid.positions[i * 3 + 2].toDouble() / radius, 1e-6)
        }
    }

    @Test
    fun `两极整行顶点收束到同一极轴不重复爆炸`() {
        val grid = SphereGrid.build(16, 16, GlobeConfig.RADIUS)
        val cols = 17
        val radius = GlobeConfig.RADIUS.toDouble()
        for (s in 0 until cols) {
            val north = s
            assertEquals(0.0, grid.positions[north * 3].toDouble(), 1e-6)
            assertEquals(radius, grid.positions[north * 3 + 1].toDouble(), 1e-6)
            assertEquals(0.0, grid.positions[north * 3 + 2].toDouble(), 1e-6)
            val south = 16 * cols + s
            assertEquals(0.0, grid.positions[south * 3].toDouble(), 1e-6)
            assertEquals(-radius, grid.positions[south * 3 + 1].toDouble(), 1e-6)
            assertEquals(0.0, grid.positions[south * 3 + 2].toDouble(), 1e-6)
        }
    }
}
