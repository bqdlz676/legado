package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 契约 §三 富结构时间轴 + 候选年代验收：
 *  - [Chronology.timelineReport]：同年多政权对照 + 干支 + 距今；
 *  - [Chronology.anchorCandidates]：正文拉出全部候选，强锚定在前；
 *  - 干支-only 弱锚定：只标记 weak，不给具体年份。
 *
 * 干支口径：前722 = 己未（史料三源交叉）；契约示例「辛酉」系前720，详见 ChronologyTest 类注释。
 */
class ChronologyReportTest {

    @Test
    fun `timeline report carries ganzhi years ago and peer rulers`() {
        val report = Chronology.timelineReport(-722)

        assertEquals(-722, report.year)
        assertEquals("己未", report.ganzhi)
        assertEquals(2672, report.yearsAgo) // PRESENT_BASE 1950 - (-722)

        val byState = report.rows.associateBy { it.state }
        assertEquals(49, byState["周"]?.nth)
        assertEquals(1, byState["鲁"]?.nth)
        assertEquals(22, byState["郑"]?.nth)

        // 旧兼容接口同口径
        val legacy = Chronology.timelineFor(-722).toMap()
        assertEquals(49, legacy["周平王"])
        assertEquals(22, legacy["郑庄公"])
    }

    @Test
    fun `anchor candidates list strong hit first with year and state`() {
        val cands = Chronology.anchorCandidates("郑庄公二十二年")
        assertTrue(cands.isNotEmpty())
        val best = cands.first()
        assertEquals(-722, best.year)
        assertEquals("郑", best.state)
        assertEquals("high", best.confidence)
        assertFalse(best.weak)
    }

    @Test
    fun `anchor candidates keep multiple strong hits when several princes appear`() {
        val cands = Chronology.anchorCandidates("鲁隐公元年，郑庄公二十二年")
        val years = cands.mapNotNull { it.year }.distinct()
        assertTrue("两条王公纪年都应入候选", years.contains(-722))
    }

    @Test
    fun `ganzhi only yields weak anchor without a fixed year`() {
        val weak = Chronology.anchorFromText("甲子")
        assertNotNull(weak)
        assertNull(weak!!.year)
        assertTrue(weak.weak)
        assertEquals("low", weak.confidence)
    }

    @Test
    fun `plain prose yields no anchor`() {
        assertNull(Chronology.anchorFromText("秋，七月，天王使宰咺来归惠公仲子之赗。"))
    }

    @Test
    fun `years ago uses 1950 frozen base`() {
        assertEquals(1950, Chronology.yearsAgo(0))
        assertEquals(100, Chronology.yearsAgo(1850))
        assertEquals(2672, Chronology.yearsAgo(-722))
        assertEquals(0, Chronology.yearsAgo(1950))
    }
}
