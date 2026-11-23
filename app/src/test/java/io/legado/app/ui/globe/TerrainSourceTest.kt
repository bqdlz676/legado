package io.legado.app.ui.globe

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.mesh.SphereMesh
import io.legado.app.ui.globe.core.terrain.CoverType
import io.legado.app.ui.globe.core.terrain.TerrainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * 地形采样源 + 球体顶点位移接线（纯 JVM，不依赖 GL）。
 *
 * 校验：高程范围 / 种子可复现 / 极地冰盖 / 水域贴球零位移 / 位移非负有限 /
 * UV 映射 / 网格顶点经位移后半径不塌回基准球内。
 */
class TerrainSourceTest {

    private val source = TerrainSource()

    @Test
    fun `高程落在海下 100 米到 6500 米区间`() {
        for (lat in -80..80 step 10) {
            for (lng in -170..170 step 20) {
                val h = source.elevationMeters(lat.toDouble(), lng.toDouble())
                assertTrue("高程越界: $h", h >= -100.0 && h <= 6500.0)
            }
        }
    }

    @Test
    fun `同一经纬度两次采样结果一致（种子确定可复现）`() {
        val a = source.sample(31.2, 121.5)
        val b = source.sample(31.2, 121.5)
        assertEquals(a.cover, b.cover)
        assertEquals(a.displacementUnits, b.displacementUnits, 1e-6f)
        assertEquals(a.colorRgb, b.colorRgb)
        assertEquals(a.isSnow, b.isSnow)
        assertEquals(a.isWater, b.isWater)
    }

    @Test
    fun `极地高纬覆盖判为冰盖`() {
        val s = source.sample(75.0, 40.0)
        assertEquals(CoverType.ICE, s.cover)
        assertTrue(s.isIce)
    }

    @Test
    fun `水域顶点位移为零，贴球面`() {
        var foundWater = false
        for (lat in -80..80 step 5) {
            for (lng in -175..175 step 10) {
                val s = source.sample(lat.toDouble(), lng.toDouble())
                if (s.isWater) {
                    foundWater = true
                    assertEquals("水域位移应为 0", 0f, s.displacementUnits, 1e-6f)
                }
            }
        }
        assertTrue("网格内应能采样到水域", foundWater)
    }

    @Test
    fun `所有采样位移非负且有限`() {
        for (lat in -85..85 step 8) {
            for (lng in -170..170 step 16) {
                val d = source.sample(lat.toDouble(), lng.toDouble()).displacementUnits
                assertTrue("位移为负: $d", d >= -1e-6f)
                assertTrue("位移非有限: $d", d.isFinite())
            }
        }
    }

    @Test
    fun `UV 映射：北极 v=0，西经 180 u=0`() {
        val (uTop, vTop) = source.uvOf(90.0, 0.0)
        assertEquals(0.0, vTop, 1e-9)
        val (uLeft, _) = source.uvOf(0.0, -180.0)
        assertEquals(0.0, uLeft, 1e-9)
    }

    @Test
    fun `覆盖类型全部落在枚举内`() {
        for (lat in -80..80 step 10) {
            assertNotNull(CoverType.entries.firstOrNull { it == source.coverType(lat.toDouble(), 0.0) })
        }
    }

    @Test
    fun `位移接入球体网格：半径不塌回基准球内`() {
        val radius = GlobeConfig.RADIUS
        val mesh = SphereMesh.build(8, 8, radius) { lat, lng, _, _, _ ->
            source.sample(lat, lng).displacementUnits
        }
        for (i in 0 until mesh.vertexCount) {
            val x = mesh.positions[i * 3]
            val y = mesh.positions[i * 3 + 1]
            val z = mesh.positions[i * 3 + 2]
            val r = sqrt(x * x + y * y + z * z)
            assertTrue("顶点 $i 半径 $r 小于基准球 $radius", r >= radius - 1e-3f)
            // 基准起伏 ≤2 单位 + 森林凸起 ≤2.5 单位，封顶 5 单位内
            assertTrue("顶点 $i 半径 $r 隆起超限", r <= radius + 5.1f)
        }
    }
}
