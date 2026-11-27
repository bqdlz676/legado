package io.legado.app.ui.map.globe.astro.data

import io.legado.app.ui.map.globe.astro.contract.SkySpecColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置星表 B-V 入库 / 取色分支与导入留痕单验收（D10、D12 / §六.14、§十四.6）。
 *
 * 纯 JVM：经 [StarCatalog.parseRows] 夹具行构造目录，不碰 AssetManager / Android。
 * 覆盖：B-V 正常入库、缺色返 NaN、越界坐标 / NaN 星等 / 重复主键计数留痕、
 * 取色两端锚点与 NaN 回落星等映射的分支口径。
 */
class StarCatalogBvTest {

    private fun row(
        id: Long, ra: String, dec: String, mag: String, bv: String, cons: String = "And",
    ): Array<String> = arrayOf(
        id.toString(), "TYC", ra, dec, mag, "", "", "", "", bv, "", "", cons,
    )

    @Test
    fun `B-V 正常入库 缺色与越界返回 NaN`() {
        val catalog = StarCatalog.parseRows(
            listOf(
                row(1, "20.0", "30.0", "1.0", "0.12"),
                row(2, "21.0", "31.0", "2.0", ""),
            ),
        )
        assertEquals(2, catalog.size)
        assertEquals(0.12, catalog.bvAt(0), 1e-9)
        assertTrue("缺色必须 NaN，渲染侧才会回落星等映射", catalog.bvAt(1).isNaN())
        assertTrue("越界索引同样 NaN", catalog.bvAt(99).isNaN())
        assertFalse(catalog.isPlaceholder)
    }

    @Test
    fun `导入留痕统计 越界坐标 NaN 星等 重复主键`() {
        val catalog = StarCatalog.parseRows(
            listOf(
                row(1, "20.0", "30.0", "1.0", "0.3"),          // 有效
                row(2, "400.0", "30.0", "1.0", "0.3"),         // 越界 RA
                row(3, "22.0", "32.0", "NaN", "0.3"),          // NaN 星等
                row(1, "23.0", "33.0", "1.0", "0.3"),          // 重复 id
            ),
        )
        assertEquals(1, catalog.size)
        assertEquals("异常计数 = 越界 + NaN 星等 + 重复", 3, catalog.importAnomalies)
        assertTrue(catalog.importReport.contains("badCoord=1"))
        assertTrue(catalog.importReport.contains("nanMag=1"))
        assertTrue(catalog.importReport.contains("dupId=1"))
    }

    @Test
    fun `B-V 取色两端锚点 蓝端偏蓝 红端偏红`() {
        val blue = SkySpecColor.starColorFromBV(-0.30)
        val red = SkySpecColor.starColorFromBV(1.70)
        val blueR = (blue shr 16) and 0xFF
        val blueB = blue and 0xFF
        val redR = (red shr 16) and 0xFF
        val redB = red and 0xFF
        assertTrue("蓝端 B 分量不低于 R", blueB >= blueR)
        assertTrue("红端 R 分量不低于 B", redR >= redB)
    }

    @Test
    fun `NaN B-V 回落星等映射 不猜色温`() {
        // 渲染侧分支口径：bv.isNaN() → starColorFromMag；这里校验两函数都可独立取色，
        // 渲染层按 NaN 判定分流（SkyCanvasRenderer pass3）。
        val magColor = SkySpecColor.starColorFromMag(1.0f)
        assertTrue(magColor in 0..0xFFFFFF)
        val bvColor = SkySpecColor.starColorFromBV(0.60)
        assertTrue(bvColor in 0..0xFFFFFF)
    }
}
