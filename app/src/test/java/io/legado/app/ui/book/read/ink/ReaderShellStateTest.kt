package io.legado.app.ui.book.read.ink

import androidx.compose.ui.geometry.Rect
import io.legado.app.ui.book.read.vertical.CharHit as VerticalCharHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 阅读壳状态机单测：气泡显隐、点空白关闭、翻页 / 滚动清气泡、底栏显隐。
 */
class ReaderShellStateTest {

    private fun newHit(): CharHit {
        val engineHit = VerticalCharHit(char = '字', offset = 0, paragraphText = "段落原文", pos = 0)
        return CharHit(anchor = Rect(480f, 300f, 520f, 340f), engineHit = engineHit)
    }

    @Test
    fun `初始为隐藏气泡、底栏可见`() {
        val state = ReaderShellState(DispatchMode.ComposeOverlay)
        assertTrue(state.bubbleState is BubbleState.Hidden)
        assertTrue(state.barVisible)
    }

    @Test
    fun `showBubble 记录锚点进入可见态`() {
        val state = ReaderShellState(DispatchMode.ComposeOverlay)
        val hit = newHit()
        state.showBubble(hit)
        val visible = state.bubbleState as? BubbleState.Visible
        assertEquals(hit, visible?.hit)
        assertEquals(500f, visible?.anchor?.centerX ?: 0f, 0.01f)
    }

    @Test
    fun `dismissBubble 立即回到隐藏态（点空白与翻页清气泡）`() {
        val state = ReaderShellState(DispatchMode.ComposeOverlay)
        state.showBubble(newHit())
        state.dismissBubble()
        assertTrue(state.bubbleState is BubbleState.Hidden)
        assertNull((state.bubbleState as? BubbleState.Visible)?.hit)
    }

    @Test
    fun `底栏收起与展开`() {
        val state = ReaderShellState(DispatchMode.ComposeOverlay)
        state.toggleBar()
        assertFalse(state.barVisible)
        state.showBar()
        assertTrue(state.barVisible)
    }
}
