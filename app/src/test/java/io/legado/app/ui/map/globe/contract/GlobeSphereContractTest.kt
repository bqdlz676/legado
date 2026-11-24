package io.legado.app.ui.map.globe.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * S-19 地球仪 · 参数单源与经纬映射 / 惯性衰减契约验收（纯 JVM，只用 org.junit + java.*，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖本单断言：
 *  ① 赤道经度 0 指向 +z；② 北极 y 最大；③ 南极 y 最小；
 *  ④ 经度 181 与 -179 同点；⑤ 纬度 91 钳制到 90；
 *  ⑥ spinDecay 随时间单调递减并归零；⑦ 负时长 / NaN 速度 / 负速度返回 0；
 *  ⑧ 球面点到球心距离等于半径；⑨ 经度 180 与 -180 同点；⑩ 契约常量与规格行对齐。
 */
class GlobeSphereContractTest {

    private companion object {
        const val EPS = 1e-6f
    }

    @Test
    fun `赤道经度零度指向正z轴`() {
        val p = latLonToSphere(0.0, 0.0, 2f)

        assertEquals(0f, p.x, EPS)
        assertEquals(0f, p.y, EPS)
        assertEquals(2f, p.z, EPS)
    }

    @Test
    fun `北极y坐标取正半径`() {
        val p = latLonToSphere(90.0, 123.0, 3f)

        assertEquals(3f, p.y, EPS)
        assertEquals(0f, p.x, EPS)
        assertEquals(0f, p.z, EPS)
    }

    @Test
    fun `南极y坐标取负半径`() {
        val p = latLonToSphere(-90.0, -45.0, 3f)

        assertEquals(-3f, p.y, EPS)
        assertEquals(0f, p.x, EPS)
        assertEquals(0f, p.z, EPS)
    }

    @Test
    fun `经度一百八十一与负一百七十九映射同一点`() {
        val a = latLonToSphere(20.0, 181.0, 4f)
        val b = latLonToSphere(20.0, -179.0, 4f)

        assertEquals(a.x, b.x, EPS)
        assertEquals(a.y, b.y, EPS)
        assertEquals(a.z, b.z, EPS)
    }

    @Test
    fun `纬度九十一钳制到九十`() {
        val clamped = latLonToSphere(91.0, 30.0, 5f)
        val north = latLonToSphere(90.0, 30.0, 5f)

        assertEquals(north.x, clamped.x, EPS)
        assertEquals(north.y, clamped.y, EPS)
        assertEquals(north.z, clamped.z, EPS)
    }

    @Test
    fun `衰减角速度随时间单调递减并归零`() {
        val v0 = spinDecay(100f, 0L)
        val vMid = spinDecay(100f, 400L)
        val vStop = spinDecay(100f, 800L)
        val vAfter = spinDecay(100f, 1000L)

        assertEquals(100f, v0, EPS)
        assertEquals(50f, vMid, EPS)
        assertEquals(0f, vStop, EPS)
        assertEquals(0f, vAfter, EPS)
        assertTrue(v0 >= vMid)
        assertTrue(vMid >= vStop)
        assertTrue(vStop == vAfter)
    }

    @Test
    fun `负时长NaN速度或负速度返回零`() {
        assertEquals(0f, spinDecay(100f, -1L), EPS)
        assertEquals(0f, spinDecay(Float.NaN, 100L), EPS)
        assertEquals(0f, spinDecay(-10f, 100L), EPS)
    }

    @Test
    fun `球面点到球心距离等于半径`() {
        val r = 3.5f
        val p = latLonToSphere(35.0, 110.0, r)
        val norm = sqrt(p.x * p.x + p.y * p.y + p.z * p.z)

        assertEquals(r, norm, EPS)
    }

    @Test
    fun `经度一百八十与负一百八十映射同一点`() {
        val a = latLonToSphere(-10.0, 180.0, 6f)
        val b = latLonToSphere(-10.0, -180.0, 6f)

        assertEquals(a.x, b.x, EPS)
        assertEquals(a.y, b.y, EPS)
        assertEquals(a.z, b.z, EPS)
    }

    @Test
    fun `契约常量与规格行对齐`() {
        assertEquals(23.5, GlobeParams.AXIAL_TILT_DEG, 1e-9)
        assertEquals(800L, GlobeParams.INERTIA_DECAY_MS)
        assertEquals(128, GlobeParams.GRID_MERIDIANS)
        assertEquals(128, GlobeParams.GRID_PARALLELS)
        assertEquals(5.0, GlobeParams.TERMINATOR_BAND_DEG, 1e-9)
        assertEquals(0.15f, GlobeParams.NIGHT_BRIGHTNESS, EPS)
    }
}
