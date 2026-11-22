package io.legado.app.core.chronology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ContextAnchor 共享状态 + 唯一消歧入口验收：
 *  持当前书 / 章 / 候选年代区间；纪年写入锚点；地名按锚点年时空过滤；
 *  人物 / 典故经注册提供者接入；low 与今址参考层不进默认命中。
 */
class ContextAnchorResolveTest {

    @Before
    fun setUp() {
        ContextAnchor.resetForTest()
        PlaceAnchor.resetForTest()
        Chronology.resetForTest()
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
    fun `resolve jin at anchored year defaults to spring autumn jin`() {
        ContextAnchor.setYear(-722, "鲁")

        val result = ContextAnchor.resolve("晋")
        assertTrue(result.isNotEmpty())

        val defaults = result.filter { it.isDefault }
        assertEquals(1, defaults.size)
        val hit = defaults.first()
        assertEquals(ContextAnchor.Type.PLACE, hit.type)
        assertEquals("P003", hit.id)
        assertFalse(hit.refLayer)

        val modern = result.firstOrNull { it.id == "P057" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
        assertFalse(modern.isDefault)
        assertEquals("low", modern.confidence.wire)
    }

    @Test
    fun `resolve without anchor marks no default`() {
        val result = ContextAnchor.resolve("晋")
        assertTrue(result.isNotEmpty())
        assertTrue(result.none { it.isDefault })
    }

    @Test
    fun `person provider feeds candidates into resolve`() {
        ContextAnchor.personProvider = { q ->
            listOf(
                ContextAnchor.Candidate(
                    id = "person:$q",
                    name = q,
                    type = ContextAnchor.Type.PERSON,
                    tier = 0,
                    start = -722,
                    end = -712,
                    aliases = emptyList(),
                    confidence = Confidence.HIGH,
                    source = "鲁国公室",
                )
            )
        }
        ContextAnchor.setYear(-722)

        val result = ContextAnchor.resolve("隐公")
        val person = result.firstOrNull { it.type == ContextAnchor.Type.PERSON }
        assertNotNull(person)
        assertEquals("person:隐公", person!!.id)
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
}
