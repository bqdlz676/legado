package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三源交叉置信度验收（锁定规则）：
 *  3/3 = high 默认命中；2/3 = medium 标「待校」；≤1/3 = low 标「待考·来源冲突」，不进默认命中；
 *  今址参考层一律 low，永不默认。
 */
class ConfidenceGradeTest {

    @Test
    fun `source strings classify into channels`() {
        assertEquals(SourceChannel.ARCHAEOLOGY, SourceChannel.classify("考古遗址实测"))
        assertEquals(SourceChannel.ARCHAEOLOGY, SourceChannel.classify("遗址实测"))
        assertEquals(SourceChannel.ATLAS, SourceChannel.classify("CHGIS/谭其骧《中国历史地图集》"))
        assertEquals(SourceChannel.ATLAS, SourceChannel.classify("谭其骧/CHGIS 考证"))
        assertEquals(SourceChannel.TEXT, SourceChannel.classify("ctext 地理注释"))
        assertEquals(SourceChannel.TEXT, SourceChannel.classify("文献推定"))
        assertEquals(SourceChannel.REFERENCE, SourceChannel.classify("今址参考层"))
        assertNull(SourceChannel.classify(""))
        assertNull(SourceChannel.classify(null))
    }

    @Test
    fun `three of three sources grade high and can default`() {
        val channels = setOf(
            SourceChannel.ARCHAEOLOGY, SourceChannel.ATLAS, SourceChannel.TEXT
        )
        val c = ContextAnchor.Confidence.grade(channels, refLayer = false, tier = 1)
        assertEquals(ContextAnchor.Confidence.HIGH, c)
        assertEquals("high", c.wire)
        assertEquals("", c.label)
        assertTrue(c.canDefault)
    }

    @Test
    fun `two of three sources grade medium marked pending check`() {
        val channels = setOf(SourceChannel.ARCHAEOLOGY, SourceChannel.ATLAS)
        val c = ContextAnchor.Confidence.grade(channels, refLayer = false, tier = 2)
        assertEquals(ContextAnchor.Confidence.MEDIUM, c)
        assertEquals("待校", c.label)
        assertTrue(c.canDefault)
    }

    @Test
    fun `one or zero source grades low marked conflicting and cannot default`() {
        val one = ContextAnchor.Confidence.grade(setOf(SourceChannel.TEXT), refLayer = false, tier = 3)
        assertEquals(ContextAnchor.Confidence.LOW, one)
        assertEquals("待考·来源冲突", one.label)
        assertFalse(one.canDefault)

        val none = ContextAnchor.Confidence.grade(emptySet(), refLayer = false, tier = 3)
        assertEquals(ContextAnchor.Confidence.LOW, none)
        assertFalse(none.canDefault)
    }

    @Test
    fun `reference layer is always low and never defaults`() {
        val c = ContextAnchor.Confidence.grade(
            setOf(SourceChannel.REFERENCE), refLayer = true, tier = 3
        )
        assertEquals(ContextAnchor.Confidence.LOW, c)
        assertFalse(c.canDefault)
    }

    @Test
    fun `explicit confidence raw mapping`() {
        assertEquals(ContextAnchor.Confidence.HIGH, ContextAnchor.Confidence.of("high"))
        assertEquals(ContextAnchor.Confidence.MEDIUM, ContextAnchor.Confidence.of("medium"))
        assertEquals(ContextAnchor.Confidence.LOW, ContextAnchor.Confidence.of("low"))
        assertEquals(ContextAnchor.Confidence.LOW, ContextAnchor.Confidence.of(null))
        assertEquals(ContextAnchor.Confidence.LOW, ContextAnchor.Confidence.of("unknown"))
    }
}
