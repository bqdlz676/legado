package io.legado.app.core.chronology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 纪年内核验收（规格切片 05 · 锁定锚点）：
 *  鲁隐公元年 = 前722 = 周平王49年 = 郑庄公22年，干支辛酉；
 *  [Chronology.anchorFromText] 扫段落王公 / 公元直书 / 年号，回候选年代；
 *  [Chronology.timelineFor] 出同年多政权对照 + 干支 + 距今。
 */
class ChronologyEngineTest {

    @Before
    fun setUp() {
        Chronology.resetForTest()
    }

    @Test
    fun `lu yin gong year one aligns zhou ping wang 49 and zhuang gong 22`() {
        // 锁定换算：鲁隐公元年 = 前722
        assertEquals(-722, Chronology.princeYear("鲁隐公", 1))
        assertEquals(-722, Chronology.princeYear("郑庄公", 22))
        assertNull(Chronology.princeYear("鲁隐公", 99))

        val rows = Chronology.timelineFor(-722).toMap()
        assertEquals(49, rows["周平王"])
        assertEquals(1, rows["鲁隐公"])
        assertEquals(22, rows["郑庄公"])
    }

    @Test
    fun `timeline report carries ganzhi xinyou years ago and peer rulers`() {
        val report = Chronology.timelineReport(-722)

        assertEquals(-722, report.year)
        // 锁定干支：前722 = 辛酉
        assertEquals("辛酉", report.ganzhi)
        assertEquals(2672, report.yearsAgo) // PRESENT_BASE 1950 - (-722)

        val byState = report.rows.associateBy { it.state }
        assertEquals(49, byState["周"]?.nth)
        assertEquals(1, byState["鲁"]?.nth)
        assertEquals(22, byState["郑"]?.nth)

        val zhou = byState["周"]
        assertNotNull(zhou)
        assertEquals("平王", zhou!!.title)
        assertEquals(-770, zhou.start)
    }

    @Test
    fun `anchor from text full and short prince mention`() {
        val full = Chronology.anchorFromText("郑庄公二十二年")
        assertNotNull(full)
        assertEquals(-722, full!!.year)
        assertEquals("郑", full.state)
        assertEquals("high", full.confidence)
        assertFalse(full.weak)

        val short = Chronology.anchorFromText("隐公元年")
        assertNotNull(short)
        assertEquals(-722, short!!.year)
        assertEquals("鲁", short.state)
    }

    @Test
    fun `anchor candidates keep multiple strong hits and order strong first`() {
        val cands = Chronology.anchorCandidates("鲁隐公元年，郑庄公二十二年")
        assertTrue(cands.isNotEmpty())
        val years = cands.mapNotNull { it.year }.distinct()
        assertTrue(years.contains(-722))
        // 强锚定（有 year）排在弱锚定之前
        assertEquals(0, cands.indexOfFirst { it.year != null })
    }

    @Test
    fun `ganzhi only yields weak anchor without fixed year`() {
        val weak = Chronology.anchorFromText("甲子")
        assertNotNull(weak)
        assertTrue(weak!!.weak)
        assertNull(weak.year)
        assertEquals("low", weak.confidence)
    }

    @Test
    fun `bce direct year parsing`() {
        val guess = Chronology.anchorFromText("前六百三十八年")
        assertNotNull(guess)
        // 前638 = 天文纪年 -637
        assertEquals(-637, guess!!.year)
    }

    @Test
    fun `parse rank title splits state and rank`() {
        assertEquals("楚" to "子", Chronology.parseStateTitle("楚子"))
        assertEquals("郑" to "伯", Chronology.parseStateTitle("郑伯"))
        assertNull(Chronology.parseStateTitle("孔子"))
    }

    @Test
    fun `ruler of state at year`() {
        assertEquals("平王", Chronology.rulerOf("周", -722)?.title)
        assertNull(Chronology.rulerOf("周", -800))
    }

    @Test
    fun `chinese number parsing`() {
        assertEquals(1, Chronology.chineseNumber("元"))
        assertEquals(22, Chronology.chineseNumber("二十二"))
        assertEquals(10, Chronology.chineseNumber("十"))
        assertNull(Chronology.chineseNumber(""))
    }

    @Test
    fun `dynasty bands follow eighteen layer table`() {
        assertEquals("西周", Chronology.dynastyOf(-1046))
        assertEquals("春秋", Chronology.dynastyOf(-722))
        assertEquals("战国", Chronology.dynastyOf(-400))
        assertEquals("秦", Chronology.dynastyOf(-221))
        assertEquals("西汉", Chronology.dynastyOf(-202))
        assertEquals("东汉", Chronology.dynastyOf(100))
        assertEquals("三国", Chronology.dynastyOf(222))
        assertEquals("西晋", Chronology.dynastyOf(280))
        assertEquals("东晋/十六国", Chronology.dynastyOf(350))
        assertEquals("南北朝", Chronology.dynastyOf(450))
        assertEquals("隋", Chronology.dynastyOf(600))
        assertEquals("唐", Chronology.dynastyOf(700))
        assertEquals("五代十国", Chronology.dynastyOf(930))
        assertEquals("北宋/辽", Chronology.dynastyOf(1000))
        assertEquals("南宋/金/西夏", Chronology.dynastyOf(1200))
        assertEquals("元", Chronology.dynastyOf(1300))
        assertEquals("明", Chronology.dynastyOf(1500))
        assertEquals("清", Chronology.dynastyOf(1700))
        assertEquals("近现代", Chronology.dynastyOf(2000))
    }

    @Test
    fun `years ago uses 1950 base`() {
        assertEquals(2672, Chronology.yearsAgo(-722))
        assertEquals(0, Chronology.yearsAgo(1950))
    }
}
