package io.legado.app.ui.map.globe.astro.ui

import io.legado.app.data.entities.StarEntity
import io.legado.app.ui.map.globe.astro.data.StarCatalog
import io.legado.app.ui.map.globe.astro.data.StarStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sheet 标题回退链与距离哨兵抑制纯 JVM 单测（判据 4 · ①②；§十四.4、§七.6）。
 *
 * 覆盖：星名三级优先、星名缺失回落星表编号、再缺回落坐标串、未选中空态、字面 "null"
 * 串剔除；距离哨兵 / null / NaN 不渲染距离行，有限正常值放行。纯 JVM：不碰 Compose 运行时。
 */
class SkySheetFallbackTest {

    private fun catalogRow(id: Long, ra: String, dec: String): Array<String> = arrayOf(
        id.toString(), "TYC", ra, dec, "1.0", "", "", "25", "224750", "0.3", "", "81.37", "And",
    )

    private fun storeWith(id: Long, ra: String, dec: String): StarStore =
        StarStore(StarCatalog.parseRows(listOf(catalogRow(id, ra, dec))))

    private fun entity(
        id: Long = 1L,
        hd: Long? = 224750L,
        hip: Long? = 25L,
        distanceLy: Double? = 81.37,
    ) = StarEntity(
        id = id,
        raDeg = 20.0,
        decDeg = 30.0,
        magVt = 1.0,
        hip = hip,
        hd = hd,
        distanceLy = distanceLy,
        constellation = "And",
    )

    @Test
    fun `星名命中 今名优先于星官名与西名`() {
        val names = SkyStarNames(zhXingguan = "参宿四", zhModern = "天狼", enIau = "Sirius")
        assertEquals("天狼", resolveSheetTitle(names, null, null, 1L))
    }

    @Test
    fun `今名缺失 回落星官名`() {
        val names = SkyStarNames(zhXingguan = "参宿四", enIau = "Sirius")
        assertEquals("参宿四", resolveSheetTitle(names, null, null, 1L))
    }

    @Test
    fun `星名全空 实体HD编号兜底`() {
        assertEquals("HD 224750", resolveSheetTitle(null, entity(), null, 1L))
    }

    @Test
    fun `星名实体皆缺 坐标串兜底不含空白与null`() {
        val store = storeWith(1L, "20.0", "30.0")
        val title = resolveSheetTitle(null, null, store, 1L)
        assertTrue("坐标串应含 RA 标记", title.startsWith("RA"))
        assertTrue("坐标串应含 Dec 标记", title.contains("Dec"))
        assertNotEquals("空白串", "", title.trim())
        assertFalse("不得出现字面 null", title.contains("null"))
    }

    @Test
    fun `未选中星体 返回未选中空态文案`() {
        assertEquals("未选中星体", resolveSheetTitle(null, null, null, null))
    }

    @Test
    fun `字面null串视为空 回落实体编号`() {
        val names = SkyStarNames(zhModern = "null")
        assertEquals("HD 224750", resolveSheetTitle(names, entity(), null, 1L))
    }

    @Test
    fun `空白星名字段 回落坐标串`() {
        val names = SkyStarNames(zhModern = "   ")
        val store = storeWith(1L, "20.0", "30.0")
        val title = resolveSheetTitle(names, null, store, 1L)
        assertTrue(title.startsWith("RA"))
    }

    @Test
    fun `距离哨兵不渲染 有限正常值放行`() {
        assertFalse("哨兵 100000.00 必须抑制", isValidDistanceLy(DISTANCE_SENTINEL_LY))
        assertFalse("null 距离不渲染", isValidDistanceLy(null))
        assertFalse("NaN 距离不渲染", isValidDistanceLy(Double.NaN))
        assertTrue("有限正常值放行", isValidDistanceLy(81.37))
        assertTrue("零距离属有效记录", isValidDistanceLy(0.0))
    }

    @Test
    fun `无实体无目录 主键编号末路可读`() {
        assertEquals("星表 #7", resolveSheetTitle(null, null, null, 7L))
    }
}
