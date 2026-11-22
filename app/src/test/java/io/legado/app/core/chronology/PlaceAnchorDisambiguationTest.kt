package io.legado.app.core.chronology

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
        PlaceAnchor.resetForTest()
    }

    @Test
    fun `click jin anchored at -722 hits spring autumn jin capital not modern jincheng`() {
        val res = PlaceAnchor.resolve("晋", year = -722)

        assertTrue(res.anchored)
        assertNotNull(res.hit)
        val hit = res.hit!!
        // 春秋晋国（绛，今翼城）
        assertEquals("P003", hit.id)
        assertEquals("绛", hit.name)
        assertEquals("春秋", hit.era)
        assertFalse(hit.refLayer)
        assertTrue(hit.confidence.canDefault)

        // 现代晋城收进「还有同名」，参考层，不进默认命中
        val modern = res.others.firstOrNull { it.id == "P057" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
        assertEquals("low", modern.confidence.wire)
        assertFalse(modern.confidence.canDefault)
        assertEquals("待考·来源冲突", modern.confidence.label)
    }

    @Test
    fun `click jin anchored by range -722 to -453 stays in spring autumn jin`() {
        val res = PlaceAnchor.resolve("晋", range = -722..-453)

        assertTrue(res.anchored)
        assertNotNull(res.hit)
        val hit = res.hit!!
        // 区间内只可能是春秋晋都（绛 / 新田），绝不落到现代晋城或汉以后泛称
        assertEquals("春秋", hit.era)
        assertFalse(hit.refLayer)
        assertNull(res.others.firstOrNull { it.id == hit.id })

        val modern = res.candidates.firstOrNull { it.id == "P057" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
    }

    @Test
    fun `modern jincheng never defaults in ancient context`() {
        val res = PlaceAnchor.resolve("晋城", year = -722)
        assertTrue(res.anchored)
        // 前722 现代晋城尚未建置，区间内无命中 → 不设默认
        assertNull(res.hit)
        val modern = res.candidates.firstOrNull { it.id == "P057" }
        assertNotNull(modern)
        assertTrue(modern!!.refLayer)
    }

    @Test
    fun `no anchor returns all candidates without default`() {
        val res = PlaceAnchor.resolve("晋")
        assertFalse(res.anchored)
        assertNull(res.hit)
        assertTrue(res.candidates.size >= 4)
    }

    @Test
    fun `entity parser marks modern reference layer`() {
        val line = "P057\tP\t晋城\t112.85\t35.49\t1912\t2024\t近现代\t3\t现代山西晋城；晋都遗址在翼城/侯马，非此城\t晋|晋城\t\t\t今址参考层\t\tlow"
        val rec = PlaceRecord.parseEntityLine(line)
        assertNotNull(rec)
        assertEquals("P057", rec!!.id)
        assertTrue(rec.refLayer)
        assertEquals(35.49, rec.lat, 1e-9)
        assertEquals(112.85, rec.lng, 1e-9)
        assertEquals(Confidence.LOW, rec.confidence)
    }

    @Test
    fun `name link parser and channel merge`() {
        val link = NameLink.parseNameLine("晋\tP003\t-1035\t-376\t春秋\t别名\t\t谭其骧/CHGIS 考证\tmedium")
        assertNotNull(link)
        assertEquals("P003", link!!.entityId)
        assertEquals(SourceChannel.ATLAS, link.channel)

        PlaceAnchor.applyNameLinks(listOf(link))
        val rec = PlaceAnchor.all().first { it.id == "P003" }
        assertTrue(rec.aliases.contains("晋"))
        assertTrue(rec.channels.contains(SourceChannel.ATLAS))
    }

    @Test
    fun `three source records carry tier and point flag`() {
        val jiang = PlaceAnchor.all().first { it.id == "P003" }
        assertTrue(jiang.isPoint)
        assertTrue(jiang.asPoint) // tier2 考证点可画点
        val fan = PlaceAnchor.all().first { it.id == "A012" }
        assertFalse(fan.isPoint)
        assertFalse(fan.asPoint) // 面实体不画点
    }
}
