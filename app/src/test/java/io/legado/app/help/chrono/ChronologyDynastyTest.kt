package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 朝代 18 套图层区间验收（切片-05 §「扩到 18 套」表格）。
 * 互斥单标签划分：[-1046, 1911] 逐年命中唯一朝代，无重叠、无遗漏空档；
 * 交叠年（西晋叠三国、隋唐易代、元明易代、明清易代）归更晚建立的政权，
 * 空档（楚汉之际、新莽之际）就近归入前一政权。
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
    fun `band boundaries are exclusive and contiguous over bce to 1911`() {
        val bands = Chronology.bands
        // 区间首尾相接：上一带止年 +1 = 下一带起年
        for (i in 1 until bands.size) {
            assertEquals(bands[i - 1].to + 1, bands[i].from)
        }
        assertEquals(-1046, bands.first().from)
        assertEquals(1911, bands.last().to)

        // 逐年扫描：[-1046, 1911] 每年都能命中朝代，且只命中一带
        for (year in -1046..1911) {
            val label = Chronology.dynastyOf(year)
            assertNotNull("年份 $year 不应返回空白朝代", label)
            val hits = bands.count { year in it.from..it.to }
            assertEquals("年份 $year 应只命中一个朝代带", 1, hits)
        }
    }

    @Test
    fun `key boundary years map to correct dynasty`() {
        assertEquals("西周", Chronology.dynastyOf(-771))
        assertEquals("春秋", Chronology.dynastyOf(-770))
        assertEquals("春秋", Chronology.dynastyOf(-476))
        assertEquals("战国", Chronology.dynastyOf(-475))
        assertEquals("战国", Chronology.dynastyOf(-222))
        assertEquals("秦", Chronology.dynastyOf(-221))
        assertEquals("东汉", Chronology.dynastyOf(220))
        assertEquals("三国", Chronology.dynastyOf(221))
        assertEquals("西晋", Chronology.dynastyOf(265))
        assertEquals("东晋/十六国", Chronology.dynastyOf(317))
        assertEquals("南北朝", Chronology.dynastyOf(420))
        assertEquals("隋", Chronology.dynastyOf(581))
        assertEquals("唐", Chronology.dynastyOf(618))
        assertEquals("五代十国", Chronology.dynastyOf(907))
        assertEquals("北宋/辽", Chronology.dynastyOf(960))
        assertEquals("南宋/金/西夏", Chronology.dynastyOf(1127))
        assertEquals("元", Chronology.dynastyOf(1271))
        assertEquals("明", Chronology.dynastyOf(1368))
        assertEquals("清", Chronology.dynastyOf(1644))
    }

    @Test
    fun `dynasty outside known range returns null without crashing`() {
        assertNull(Chronology.dynastyOf(-1047))
        assertNotNull(Chronology.dynastyOf(1912))
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

    @Test
    fun `ganzhi computes across bce one and ad one boundary without crash`() {
        // 前1年（天文 0）与公元1年是干支换年的临界，公式必须可算、不崩、不空白
        val bce1 = Chronology.yearGanzhi(-1)
        val ad1 = Chronology.yearGanzhi(1)
        val ad0 = Chronology.yearGanzhi(0)
        assertEquals(2, bce1.length)
        assertEquals(2, ad1.length)
        assertEquals(2, ad0.length)
        assertEquals("辛酉", ad1)
    }
}
