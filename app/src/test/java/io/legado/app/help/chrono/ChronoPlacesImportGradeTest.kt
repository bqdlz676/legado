package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 资产灌入集成验收：种子/资产经 [ChronoPlaces.importRecords] 进入生产内核后，
 * 无显式 confidence 列时按三源渠道交叉 + 参考层 + tier 走 [ContextAnchor.Confidence.grade] 打分；
 * 显式 confidence 字符串仅作覆盖。
 */
class ChronoPlacesImportGradeTest {

    @Before
    fun setUp() {
        ChronoPlaces.resetForTest()
    }

    private fun seed(
        id: String,
        tier: Int,
        channels: Set<SourceChannel>,
        refLayer: Boolean = false,
        explicit: String? = null,
    ) = ChronoPlaces.PlaceSeed(
        id = id, name = id, kind = 'P', start = -722, end = -453, tier = tier,
        aliases = listOf(id), confidence = explicit ?: "", source = "",
        channels = channels, refLayer = refLayer, declaredConfidence = explicit,
    )

    @Test
    fun `imported records are graded by channel cross count when no explicit confidence`() {
        ChronoPlaces.importRecords(
            listOf(
                seed("G3", 1, setOf(SourceChannel.ARCHAEOLOGY, SourceChannel.ATLAS, SourceChannel.TEXT)),
                seed("G2", 2, setOf(SourceChannel.ARCHAEOLOGY, SourceChannel.ATLAS)),
                seed("G1", 3, setOf(SourceChannel.TEXT)),
                seed("G0", 3, emptySet()),
                seed("GR", 3, setOf(SourceChannel.REFERENCE), refLayer = true),
            )
        )

        fun tag(id: String) = ChronoPlaces.candidates(id).first().confidenceTag

        assertEquals(ContextAnchor.Confidence.HIGH, tag("G3"))
        assertTrue(tag("G3").canDefault)

        assertEquals(ContextAnchor.Confidence.MEDIUM, tag("G2"))
        assertEquals("待校", tag("G2").label)
        assertTrue(tag("G2").canDefault)

        assertEquals(ContextAnchor.Confidence.LOW, tag("G1"))
        assertFalse(tag("G1").canDefault)
        assertEquals("待考·来源冲突", tag("G1").label)

        assertEquals(ContextAnchor.Confidence.LOW, tag("G0"))
        assertFalse(tag("G0").canDefault)

        assertEquals(ContextAnchor.Confidence.LOW, tag("GR"))
        assertFalse(tag("GR").canDefault)
        assertTrue(ChronoPlaces.candidates("GR").first().refLayer)
    }

    @Test
    fun `explicit confidence string overrides channel grade`() {
        ChronoPlaces.importRecords(
            listOf(
                // 单渠道 tier3 本应 LOW；显式 high 列覆盖为 HIGH
                seed("GX", 3, setOf(SourceChannel.TEXT), explicit = "high"),
                // 三渠道 tier1 本应 HIGH；显式 low 列覆盖为 LOW（显式优先）
                seed("GY", 1, setOf(SourceChannel.ARCHAEOLOGY, SourceChannel.ATLAS, SourceChannel.TEXT), explicit = "low"),
            )
        )

        assertEquals(ContextAnchor.Confidence.HIGH, ChronoPlaces.candidates("GX").first().confidenceTag)
        assertEquals(ContextAnchor.Confidence.LOW, ChronoPlaces.candidates("GY").first().confidenceTag)
    }
}
