package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U6 半屏 Sheet 锚点模型：60% 高度上限、40% 暗化、8dp 下沉、关闭阈值与回弹。
 */
class HalfSheetAnchorsTest {

    @Test
    fun `高度夹紧到屏幕 60 上限`() {
        assertEquals(0.60f, HalfSheetAnchors.clampHeight(0.80f), 1e-6f)
        assertEquals(0.30f, HalfSheetAnchors.clampHeight(0.30f), 1e-6f)
        assertEquals(0f, HalfSheetAnchors.clampHeight(-0.20f), 1e-6f)
    }

    @Test
    fun `遮罩暗化上限 40 随升起进度联动`() {
        assertEquals(0f, HalfSheetAnchors.maskAlpha(0f), 1e-6f)
        assertEquals(0.40f, HalfSheetAnchors.maskAlpha(1f), 1e-6f)
        assertEquals(0.20f, HalfSheetAnchors.maskAlpha(0.5f), 1e-6f)
        assertEquals(0.40f, HalfSheetAnchors.maskAlpha(1.4f), 1e-6f)
        assertEquals(0f, HalfSheetAnchors.maskAlpha(-0.3f), 1e-6f)
    }

    @Test
    fun `阅读页下沉 8dp 随进度联动`() {
        assertEquals(0f, HalfSheetAnchors.hostSinkDp(0f), 1e-6f)
        assertEquals(8f, HalfSheetAnchors.hostSinkDp(1f), 1e-6f)
        assertEquals(4f, HalfSheetAnchors.hostSinkDp(0.5f), 1e-6f)
    }

    @Test
    fun `拖过低阈值关闭 慢速未过阈值回弹`() {
        assertTrue(HalfSheetAnchors.shouldDismiss(0.10f, 0f))
        assertTrue(HalfSheetAnchors.shouldDismiss(0.30f, 800f))
        assertFalse(HalfSheetAnchors.shouldDismiss(0.50f, 100f))
        // 中段高度 + 慢速松手：未过低阈值，回弹到 60% 锚点。
        assertFalse(HalfSheetAnchors.shouldDismiss(0.40f, 120f))
    }

    @Test
    fun `唯一停留锚点为 60 最大高`() {
        assertEquals(0.60f, HalfSheetAnchors.restingFraction(), 1e-6f)
    }
}
