package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 朝代 18 套图层区间验收（迁移自 core.chronology ChronologyEngineTest）。
 * 区间互斥；空档就近归入前一政权，不返回 null 空白态。
 */
class ChronologyDynastyTest {

    @Test
    fun `dynasty bands follow eighteen layer table`() {
        assertEquals("西周", Chronology.dynastyOf(-1046))
        assertEquals("春秋", Chronology.dynastyOf(-722))
        assertEquals("战国", Chronology.dynastyOf(-400))
        assertEquals("秦", Chronology.dynastyOf(-221))
        assertEquals("秦", Chronology.dynastyOf(-202))
        assertEquals("西汉", Chronology.dynastyOf(-201))
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
    fun `bce direct year parses to astronomical year`() {
        val guess = Chronology.anchorFromText("前六百三十八年")
        requireNotNull(guess)
        // 前638 = 天文纪年 -637
        assertEquals(-637, guess.year)
    }

    @Test
    fun `anchor candidates keep multiple strong hits and order strong first`() {
        val cands = Chronology.anchorCandidates("鲁隐公元年，郑庄公二十二年")
        assert(cands.isNotEmpty())
        val years = cands.mapNotNull { it.year }.distinct()
        assert(years.contains(-722))
        // 强锚定（有 year）排在弱锚定之前
        assertEquals(0, cands.indexOfFirst { it.year != null })
    }
}
