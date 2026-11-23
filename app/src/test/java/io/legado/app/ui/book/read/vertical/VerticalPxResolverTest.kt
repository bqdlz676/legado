package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · sp/dp → px 纯 JVM 解析器单测（交接清单 #4）。
 *
 * 口径对齐 TypedValue：dp 乘 density，sp 乘 scaledDensity；选号规则
 * （标题 = 正文 + 2sp、注音 10sp、标题间距 24dp）在此断言。
 * Android 工厂读取系统密度的绑定路径【未验证】，由云端 CI / 真机复验。
 */
class VerticalPxResolverTest {

    private companion object {
        const val DENSITY = 2.75f
        const val FONT_SCALE = 1.0f
    }

    @Test
    fun `default tokens match spec locked values`() {
        val spec = VerticalPxResolver.resolve(density = DENSITY, fontScale = FONT_SCALE)
        assertEquals(VerticalTokens.BODY_SP, 18f, 1e-6f)
        assertEquals(VerticalTokens.ANNOTATION_SP, 10f, 1e-6f)
        assertEquals(VerticalTokens.TITLE_GAP_DP, 24f, 1e-6f)
        assertEquals(VerticalTokens.TITLE_SIZE_OFFSET_SP, 2f, 1e-6f)
    }

    @Test
    fun `sp sizes multiply fontScale and dp gap multiplies density`() {
        val spec = VerticalPxResolver.resolve(
            density = DENSITY,
            fontScale = FONT_SCALE,
        )
        // sp → px：sp × scaledDensity(fontScale)。
        assertEquals(18f * FONT_SCALE, spec.bodySizePx, 1e-4f)
        assertEquals(20f * FONT_SCALE, spec.titleSizePx, 1e-4f)
        assertEquals(10f * FONT_SCALE, spec.annotationSizePx, 1e-4f)
        // dp → px：dp × density（不随字号缩放）。
        assertEquals(24f * DENSITY, spec.titleGapPx, 1e-4f)
    }

    @Test
    fun `fontScale scales all sp values but not dp gap`() {
        val fs = 1.15f
        val spec = VerticalPxResolver.resolve(density = DENSITY, fontScale = fs)

        assertEquals(18f * fs, spec.bodySizePx, 1e-4f)
        assertEquals(20f * fs, spec.titleSizePx, 1e-4f)
        assertEquals(10f * fs, spec.annotationSizePx, 1e-4f)
        // 标题与正文的字号差 = 2sp × fontScale。
        assertEquals(2f * fs, spec.titleSizePx - spec.bodySizePx, 1e-4f)
        // 间距仍只乘 density。
        assertEquals(24f * DENSITY, spec.titleGapPx, 1e-4f)
    }

    @Test
    fun `custom bodySp honored and title stays two sp above`() {
        val spec = VerticalPxResolver.resolve(bodySp = 20f, density = 2f, fontScale = 1.2f)
        assertEquals(20f * 1.2f, spec.bodySizePx, 1e-4f)
        assertEquals(22f * 1.2f, spec.titleSizePx, 1e-4f)
    }

    @Test
    fun `title larger than body and annotation smaller than body`() {
        val spec = VerticalPxResolver.resolve(density = DENSITY, fontScale = FONT_SCALE)
        assertTrue(spec.titleSizePx > spec.bodySizePx)
        assertTrue(spec.annotationSizePx < spec.bodySizePx)
    }
}
