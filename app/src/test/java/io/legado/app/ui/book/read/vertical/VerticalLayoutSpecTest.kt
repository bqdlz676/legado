package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 竖排排版规格单测：把切片-09 一、3.1 的参数逐字落成可断言的几何量。
 *
 * 口径：列宽（正文版心）= 屏宽 × 0.82；左侧 18% 注音/批注栏；正文 18sp；
 * 行距 = 字号 × 1.8；列间距 = 字号 × 2.5；标题大 2sp；标题与正文间距 24dp。
 */
class VerticalLayoutSpecTest {

    private companion object {
        const val W = 1080f
        const val H = 1920f
        const val BODY = 36f
        const val TITLE = 40f
        const val TITLE_GAP = 24f
    }

    private fun spec() = VerticalLayoutSpec.create(
        viewWidthPx = W,
        viewHeightPx = H,
        bodySizePx = BODY,
        titleSizePx = TITLE,
        titleGapPx = TITLE_GAP,
    )

    @Test
    fun `token constants match slice-09 verbatim ratios`() {
        assertEquals(18f, VerticalTokens.BODY_SP, 1e-6f)
        assertEquals(10f, VerticalTokens.ANNOTATION_SP, 1e-6f)
        assertEquals(1.8f, VerticalTokens.LINE_HEIGHT_RATIO, 1e-6f)
        assertEquals(2.5f, VerticalTokens.COLUMN_GAP_RATIO, 1e-6f)
        assertEquals(0.82f, VerticalTokens.TEXT_BLOCK_RATIO, 1e-6f)
        assertEquals(0.18f, VerticalTokens.NOTE_BAND_RATIO, 1e-6f)
        assertEquals(2f, VerticalTokens.TITLE_SIZE_OFFSET_SP, 1e-6f)
        assertEquals(24f, VerticalTokens.TITLE_GAP_DP, 1e-6f)
    }

    @Test
    fun `text block occupies right 82 percent and note band left 18 percent`() {
        val s = spec()
        assertEquals(W * 0.82f, s.textBlockWidthPx, 1e-4f)
        assertEquals(W * 0.18f, s.noteBandWidthPx, 1e-4f)
        assertEquals(s.noteBandWidthPx, s.textBlockLeft, 1e-4f)
        assertEquals(W, s.textBlockRight, 1e-4f)
    }

    @Test
    fun `line pitch is body times 18 and column gap body times 25`() {
        val s = spec()
        assertEquals(BODY * 1.8f, s.charPitchPx, 1e-4f)
        assertEquals(BODY * 2.5f, s.columnGapPx, 1e-4f)
        assertEquals(BODY, s.bodyThicknessPx, 1e-4f)
        assertEquals(TITLE, s.titleThicknessPx, 1e-4f)
        assertEquals(TITLE_GAP, s.titleGapPx, 1e-4f)
    }

    @Test
    fun `page holds at least one column and one row`() {
        val s = spec()
        assertTrue(s.columnsPerPage >= 1)
        assertTrue(s.rowsPerColumn >= 1)
        assertTrue(s.contentHeight > 0f)
    }

    @Test
    fun `edge zones each take one fifth of width`() {
        val s = spec()
        assertEquals(W / 5f, s.edgeZonePx, 1e-4f)
        assertEquals(s.edgeZonePx, s.centerLeftPx, 1e-4f)
        assertEquals(W - s.edgeZonePx, s.centerRightPx, 1e-4f)
    }
}
