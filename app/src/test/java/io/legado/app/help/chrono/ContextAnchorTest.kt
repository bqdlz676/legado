package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 契约 §一 消歧入口验收（D3 地名种子主键对齐 PlaceSeedData）：
 *  - resolve("晋", -700) 首选春秋晋都绛（诸侯国），现代晋城进候选且 isDefault=false
 *  - resolve("周", -700) → 东周 / 洛邑
 *  - resolve("郑", -700) → 郑韩故城
 *  - 锚点为空不猜；low 置信度不进默认命中
 */
class ContextAnchorTest {

    @Before
    fun setUp() {
        ChronoPlaces.resetForTest()
        ContextAnchor.reset()
        ContextAnchor.personProvider = null
        ContextAnchor.allusionProvider = null
    }

    @Test
    fun `resolve 晋 at -700 prefers spring-autumn jin capital not modern jincheng`() {
        val cands = ContextAnchor.resolve("晋", -700)
        assertTrue(cands.isNotEmpty())

        val hit = cands.first()
        assertEquals(ContextAnchor.Type.PLACE, hit.type)
        assertEquals("P003", hit.id)
        assertTrue(-700 in (hit.start ?: 0)..(hit.end ?: 0))
        assertTrue(hit.isDefault)
        assertTrue(hit.aliases.contains("晋国"))

        val modern = cands.firstOrNull { it.id == "P-JINCHENG" }
        assertNotNull("现代晋城应进今址参考层候选", modern)
        assertFalse(modern!!.isDefault)
        assertEquals(ContextAnchor.Confidence.LOW, modern.confidence)
    }

    @Test
    fun `resolve 周 at -700 maps to eastern zhou luoyi`() {
        val cands = ContextAnchor.resolve("周", -700)
        assertTrue(cands.isNotEmpty())
        val hit = cands.first()
        assertEquals("P020", hit.id)
        assertEquals("洛邑", hit.name)
        assertTrue(hit.isDefault)
        // ATLAS + TEXT 两源交叉 → MEDIUM（待校），可进默认
        assertEquals(ContextAnchor.Confidence.MEDIUM, hit.confidence)
    }

    @Test
    fun `resolve 郑 at -700 maps to zheng capital xinzheng`() {
        val cands = ContextAnchor.resolve("郑", -700)
        assertTrue(cands.isNotEmpty())
        val hit = cands.first()
        assertEquals("P010", hit.id)
        assertEquals("郑韩故城", hit.name)
        assertTrue(hit.isDefault)
        assertTrue(-700 in (hit.start ?: 0)..(hit.end ?: 0))
    }

    @Test
    fun `resolve without anchor year returns all candidates with no default`() {
        val cands = ContextAnchor.resolve("晋", null)
        assertTrue(cands.size > 1)
        assertTrue(cands.all { !it.isDefault })
    }

    @Test
    fun `low confidence candidates never become default`() {
        // 2000 年：种子里只有现代晋城在区间内，且为 low → 不设默认命中
        val cands = ContextAnchor.resolve("晋", 2000)
        assertTrue(cands.isNotEmpty())
        assertTrue(cands.all { !it.isDefault })
        assertTrue(cands.any { it.confidence == ContextAnchor.Confidence.LOW })
    }

    @Test
    fun `blank name resolves to empty`() {
        assertTrue(ContextAnchor.resolve("", -700).isEmpty())
        assertTrue(ContextAnchor.resolve("   ", null).isEmpty())
    }

    @Test
    fun `person provider feeds person candidates`() {
        ContextAnchor.personProvider = { q ->
            listOf(
                ContextAnchor.Candidate(
                    id = "person:$q", name = q, type = ContextAnchor.Type.PERSON,
                    tier = 0, start = -551, end = -479, aliases = emptyList(),
                    confidence = ContextAnchor.Confidence.HIGH, source = "鲁 · 思想家",
                )
            )
        }
        val cands = ContextAnchor.resolve("孔子", -500)
        val person = cands.firstOrNull { it.type == ContextAnchor.Type.PERSON }
        assertNotNull(person)
        assertTrue(person!!.isDefault)
        assertEquals(-551, person.start)
        assertEquals(-479, person.end)
    }

    @Test
    fun `guess from text writes year and state anchor`() {
        ContextAnchor.guessFromText("隐公元年")
        assertEquals(-722, ContextAnchor.getYear())
        assertEquals("鲁", ContextAnchor.state)
        assertEquals("春秋", ContextAnchor.getDynasty())
        assertTrue(ContextAnchor.hasAnchor())
    }

    @Test
    fun `resolve uses anchor year when name resolves without explicit year`() {
        ContextAnchor.setYear(-722, "鲁")

        val result = ContextAnchor.resolve("晋")
        assertTrue(result.isNotEmpty())

        val defaults = result.filter { it.isDefault }
        assertEquals(1, defaults.size)
        val hit = defaults.first()
        assertEquals(ContextAnchor.Type.PLACE, hit.type)
        assertEquals("P003", hit.id)
        assertFalse(hit.refLayer)

        val modern = result.firstOrNull { it.id == "P-JINCHENG" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
        assertFalse(modern.isDefault)
        assertEquals("low", modern.confidence.wire)
    }

    @Test
    fun `range anchor resolves spring autumn jin without fixed year`() {
        // 《左传》只锚到 -722~-453 区间（无精确年），区间相交仍应命中春秋晋国
        ContextAnchor.setYearRange(-722..-453, "鲁")
        val result = ContextAnchor.resolve("晋")
        val defaults = result.filter { it.isDefault }
        assertEquals(1, defaults.size)
        assertEquals("P003", defaults.first().id)
    }

    @Test
    fun `chapter and book state round trip`() {
        ContextAnchor.setChapter("春秋左传", "隐公元年")
        assertEquals("春秋左传", ContextAnchor.bookId)
        assertEquals("隐公元年", ContextAnchor.title)

        val state = ContextAnchor.get()
        assertEquals("春秋左传", state.bookName)
        assertEquals("隐公元年", state.chapterTitle)

        ContextAnchor.setYearRange(-722..-453, "鲁")
        assertEquals(-722, ContextAnchor.getRange()?.first)
        assertEquals(-453, ContextAnchor.getRange()?.last)
        assertNotNull(ContextAnchor.getDynasty())

        ContextAnchor.reset()
        assertFalse(ContextAnchor.hasAnchor())
        assertNull(ContextAnchor.getYear())
    }

    @Test
    fun `eraLabel composes dynasty ganzhi and year for fixed anchor`() {
        ContextAnchor.setYear(-722)
        val label = ContextAnchor.eraLabel()
        assertNotNull(label)
        assertTrue(label!!.contains("春秋"))
        assertTrue(label.contains("己未"))
        assertTrue(label.contains("公元前722"))
    }

    @Test
    fun `eraLabel gives range text for weak range anchor and null without anchor`() {
        ContextAnchor.setYearRange(-722..-453)
        val label = ContextAnchor.eraLabel()
        assertNotNull(label)
        assertTrue(label!!.contains("春秋"))
        assertTrue(label.contains("公元前722"))

        ContextAnchor.reset()
        assertNull(ContextAnchor.eraLabel())
    }
}
