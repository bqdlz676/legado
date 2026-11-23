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
 *  按名字拉出全部候选（春秋晋都绛 / 晚期晋都新田 / 晋国面 / 北魏至唐宋晋州 / 现代山西晋城），
 *  时空锚定 -722~-453 后命中「春秋晋国」；现代晋城降级为今址参考层候选，不得默认命中；
 *  唐史语境（700 年）下春秋晋国出区间，晋州进候选但 tier3 单源 low，不默认命中。
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

        val modern = res.others.firstOrNull { it.id == "P-JINCHENG" }
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

        val modern = res.candidates.firstOrNull { it.id == "P-JINCHENG" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
    }

    @Test
    fun `modern jincheng never defaults in ancient context`() {
        val res = ChronoPlaces.resolve("晋城", year = -722)
        assertTrue(res.anchored)
        // 前722 现代晋城尚未建置，区间内无命中 → 不设默认
        assertNull(res.hit)
        val modern = res.candidates.firstOrNull { it.id == "P-JINCHENG" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
    }

    @Test
    fun `tang era anchor surfaces jinzhou as non-default low candidate`() {
        val res = ChronoPlaces.resolve("晋", year = 700)
        assertTrue(res.anchored)
        val jinzhou = res.candidates.firstOrNull { it.id == "P-JINZHOU" }
        assertNotNull("唐史语境应出现晋州候选", jinzhou)
        assertTrue(jinzhou!!.inSpan(700))
        // tier3 文献推定单源 → low，不进默认命中
        assertEquals(ContextAnchor.Confidence.LOW, jinzhou.confidenceTag)
        assertFalse(jinzhou.confidenceTag.canDefault)
        // 春秋晋国在 700 年早已出区间
        assertTrue(res.candidates.none { it.id == "P003" && it.inSpan(700) })
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
        val jinState = ChronoPlaces.all().first { it.id == "P-JIN" }
        assertFalse(jinState.isPoint)
    }
}
