package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * C1 三源旁证锚定验收（切片-05：纪年换算 + 人物朝代 + 地名年代 流水线三段）。
 *  - 人物：D2 生卒年 → 候选区间；
 *  - 地名：D3 存在区间 → 候选区间；
 *  - 强纪年锚定优先于旁证区间；干支-only 弱锚不定年。
 */
class ChronoAnchorSourceTest {

    @Before
    fun setUp() {
        ChronoPlaces.resetForTest()
        ContextAnchor.reset()
        Chronology.personNameProvider = null
    }

    @Test
    fun `person anchor derives range from lifespan`() {
        val anchor = Chronology.personAnchor("孔子")
        assertNotNull(anchor)
        assertEquals(-551, anchor!!.from)
        assertEquals(-479, anchor.to)
        assertNull(anchor.year)
        assertEquals("medium", anchor.confidence)
    }

    @Test
    fun `person anchor returns null for unknown person`() {
        assertNull(Chronology.personAnchor("不存在的虚构人物甲乙丙"))
    }

    @Test
    fun `place anchor derives existence range from D3`() {
        val anchor = Chronology.placeAnchor("晋")
        assertNotNull(anchor)
        // 春秋晋都绛 / 新田 / 晋国面的并集区间覆盖春秋
        assertTrue(anchor!!.from!! <= -722)
        assertTrue(anchor!!.to!! >= -453)
        assertEquals("medium", anchor.confidence)
    }

    @Test
    fun `person catalog injected into text scan contributes range candidate`() {
        Chronology.personNameProvider = { listOf("孔子") }
        val cands = Chronology.anchorCandidates("孔子生于鲁")
        val person = cands.firstOrNull { it.label == "孔子" }
        assertNotNull(person)
        assertEquals(-551, person!!.from)
        assertEquals(-479, person.to)
    }

    @Test
    fun `strong prince anchor outranks person and place range candidates`() {
        Chronology.personNameProvider = { listOf("孔子") }
        val cands = Chronology.anchorCandidates("鲁隐公元年，孔子之语")
        val best = cands.first()
        assertEquals(-722, best.year)
        assertEquals("high", best.confidence)
    }

    @Test
    fun `lu yin gong year one full acceptance chain`() {
        val report = Chronology.timelineReport(-722)
        assertEquals("己未", report.ganzhi)
        assertEquals(2672, report.yearsAgo)

        val rows = report.rows.associateBy { it.state }
        assertEquals(49, rows["周"]?.nth)
        assertEquals(1, rows["鲁"]?.nth)
        assertEquals(22, rows["郑"]?.nth)

        val legacy = Chronology.timelineFor(-722).toMap()
        assertEquals(49, legacy["周平王"])
        assertEquals(22, legacy["郑庄公"])
    }
}
