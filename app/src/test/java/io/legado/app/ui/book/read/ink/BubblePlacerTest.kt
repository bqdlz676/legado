package io.legado.app.ui.book.read.ink

import androidx.compose.ui.geometry.Rect
import io.legado.app.ui.book.read.vertical.CharHit as VerticalCharHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 气泡定位纯函数单测（切片-02 §4：原文上方 8dp、箭头对字心、上方越界翻下方、
 * 不遮当前句；所有越界夹回安全区，不允许飞出屏幕或消失）。
 */
class BubblePlacerTest {

    private companion object {
        const val VIEW_W = 1000f
        const val VIEW_H = 1920f
        const val GAP = 10f
        const val TOP_SAFE = 40f
        const val BOTTOM_RESERVED = 180f
        const val MARGIN = 36f
    }

    private fun anchorAt(centerX: Float, topY: Float, bottomY: Float): BubbleAnchor {
        val engineHit = VerticalCharHit(char = '字', offset = 0, paragraphText = "段落原文", pos = 0)
        val rect = Rect(centerX - 20f, topY, centerX + 20f, bottomY)
        return BubbleAnchor(CharHit(anchor = rect, engineHit = engineHit))
    }

    private fun place(
        anchor: BubbleAnchor,
        bubbleW: Float,
        bubbleH: Float,
    ) = BubblePlacer.place(
        anchor = anchor,
        bubbleW = bubbleW,
        bubbleH = bubbleH,
        viewW = VIEW_W,
        viewH = VIEW_H,
        gapPx = GAP,
        topSafePx = TOP_SAFE,
        bottomReservedPx = BOTTOM_RESERVED,
        marginPx = MARGIN,
    )

    @Test
    fun `上方放得下时气泡在原字上方，箭头对准字心`() {
        val placement = place(anchorAt(centerX = 500f, topY = 300f, bottomY = 360f), 300f, 200f)
        assertTrue(placement.above)
        assertEquals(90f, placement.y, 0.01f)
        assertEquals(350f, placement.x, 0.01f)
        assertEquals(500f, placement.arrowX, 0.01f)
    }

    @Test
    fun `上方越界时翻到原字下方`() {
        val placement = place(anchorAt(centerX = 500f, topY = 80f, bottomY = 140f), 300f, 200f)
        assertFalse(placement.above)
        assertEquals(150f, placement.y, 0.01f)
    }

    @Test
    fun `上下都放不下时优先下方并整体夹回安全区，不飞出屏幕`() {
        val bubbleH = 1800f
        val belowMaxY = VIEW_H - BOTTOM_RESERVED - bubbleH
        val placement = place(anchorAt(centerX = 500f, topY = 300f, bottomY = 360f), 300f, bubbleH)
        assertFalse(placement.above)
        assertTrue(placement.y <= belowMaxY + 0.01f)
        assertTrue(placement.y.isFinite())
    }

    @Test
    fun `箭头水平中心贴左安全边夹取`() {
        val placement = place(anchorAt(centerX = 20f, topY = 500f, bottomY = 560f), 300f, 200f)
        assertEquals(MARGIN + 150f, placement.arrowX, 0.01f)
    }

    @Test
    fun `气泡左边贴右安全边夹取`() {
        val placement = place(anchorAt(centerX = 980f, topY = 500f, bottomY = 560f), 300f, 200f)
        assertEquals(VIEW_W - MARGIN - 150f, placement.arrowX, 0.01f)
        assertEquals(VIEW_W - MARGIN - 300f, placement.x, 0.01f)
    }
}
