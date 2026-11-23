package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 同名消歧验收（规格切片 05 · 点「晋」）：
 *  按名字拉出全部候选（春秋晋都绛 / 晚期晋都新田 / 晋地泛称 / 现代山西晋城），
 *  时空锚定 -722~-453 后命中「春秋晋国」；现代晋城降级为今址参考层候选，不得默认命中。
 */
class PlaceAnchorDisambiguationTest {

    @Before
    fun setUp() {
        ChronoPlaces.resetForTest()
    }

    @Test
    fun `click jin anchored at -722 hits spring autumn jin capital not modern jincheng`() {
        val res = ChronoPlaces.resolve("晋", year = -722)

        assertTrue(res.anchored)
        assertNotNull(res.hit)
        val hit = res.hit!!
        assertEquals("P003", hit.id)
        assertEquals("绛", hit.name)
        assertEquals("春秋", hit.era)
        assertFalse(hit.refLayer)
        assertTrue(hit.confidenceTag.canDefault)

        val modern = res.others.firstOrNull { it.id == "P057" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
        assertEquals(ContextAnchor.Confidence.LOW, modern.confidenceTag)
        assertFalse(modern.confidenceTag.canDefault)
        assertEquals("待考·来源冲突", modern.confidenceTag.label)
    }

    @Test
    fun `click jin anchored by range -722 to -453 stays in spring autumn jin`() {
        val res = ChronoPlaces.resolve("晋", range = -722..-453)

        assertTrue(res.anchored)
        assertNotNull(res.hit)
        val hit = res.hit!!
        assertEquals("春秋", hit.era)
        assertFalse(hit.refLayer)
        assertNull(res.others.firstOrNull { it.id == hit.id })

        val modern = res.candidates.firstOrNull { it.id == "P057" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
    }

    @Test
    fun `modern jincheng never defaults in ancient context`() {
        val res = ChronoPlaces.resolve("晋城", year = -722)
        assertTrue(res.anchored)
        // 前722 现代晋城尚未建置，区间内无命中 → 不设默认
        assertNull(res.hit)
        val modern = res.candidates.firstOrNull { it.id == "P057" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
    }

    @Test
    fun `no anchor returns all candidates without default`() {
        val res = ChronoPlaces.resolve("晋")
        assertFalse(res.anchored)
        assertNull(res.hit)
        assertTrue(res.candidates.size >= 4)
    }

    @Test
    fun `three source records carry tier and point flag`() {
        val jiang = ChronoPlaces.all().first { it.id == "P003" }
        assertTrue(jiang.isPoint)
        assertTrue(jiang.tier <= 2) // tier2 考证点可画点
        val fan = ChronoPlaces.all().first { it.id == "A012" }
        assertFalse(fan.isPoint)
    }
}
