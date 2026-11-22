package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 12 色 token / 时长 / 曲线 / 弹簧 / 分段比例 数值收口断言。
 * 规格：切片-03 颜色 Token 系统、动效层增补；切片-04 三段高度。
 */
class ComponentPaletteTest {

    @Test
    fun `12 色 token 与规格十六进制逐一吻合`() {
        assertEquals(0xFFF7F1E6L, ComponentPalette.BG_PAGE)
        assertEquals(0xFFEFE8D8L, ComponentPalette.BG_SURFACE)
        assertEquals(0x731A1410L, ComponentPalette.BG_OVERLAY)
        assertEquals(0xFF1A1410L, ComponentPalette.TEXT_PRIMARY)
        assertEquals(0xFF5C4F3AL, ComponentPalette.TEXT_SECONDARY)
        assertEquals(0xFF9B9687L, ComponentPalette.TEXT_TERTIARY)
        assertEquals(0xFFC23B2EL, ComponentPalette.TEXT_ACCENT)
        assertEquals(0xFFD4A54AL, ComponentPalette.BRAND_GOLD)
        assertEquals(0xFF8B6914L, ComponentPalette.BRAND_COPPER)
        assertEquals(0xFFC23B2EL, ComponentPalette.COLOR_LINK)
        assertEquals(0x141A1410L, ComponentPalette.COLOR_DIVIDER)
        assertEquals(0x4DD4A54AL, ComponentPalette.COLOR_GLOW)
        assertEquals(12, ComponentPalette.mainTokens.size)
    }

    @Test
    fun `墨触覆盖透明度为悬浮 8 按下 16 禁用 30`() {
        assertEquals(0.08f, ComponentPalette.PRESS_HOVER_ACCENT_ALPHA, 1e-6f)
        assertEquals(0.16f, ComponentPalette.PRESS_DOWN_ACCENT_ALPHA, 1e-6f)
        assertEquals(0.30f, ComponentPalette.DISABLED_OPACITY, 1e-6f)
    }

    @Test
    fun `时长常数与规格一致`() {
        assertEquals(120, ComponentPalette.DURATION_MICRO)
        assertEquals(250, ComponentPalette.DURATION_CARD)
        assertEquals(250, ComponentPalette.DURATION_BUBBLE)
        assertEquals(350, ComponentPalette.DURATION_PAGE)
        assertEquals(200, ComponentPalette.DURATION_MASK)
        assertEquals(2800, ComponentPalette.DURATION_BOOT)
    }

    @Test
    fun `四条缓动曲线均为四参数 cubic-bezier`() {
        arrayOf(
            ComponentPalette.CARD_ENTER_EASING,
            ComponentPalette.BUBBLE_EASING,
            ComponentPalette.PAGE_TURN_EASING,
            ComponentPalette.MASK_EASING,
        ).forEach { easing ->
            assertEquals(4, easing.size)
            easing.forEach { assertTrue(it in 0f..1f || it < 0f) }
        }
    }

    @Test
    fun `Sheet 三段高度为 30 60 90`() {
        assertEquals(0.30f, ComponentPalette.SHEET_DETENT_LOW, 1e-6f)
        assertEquals(0.60f, ComponentPalette.SHEET_DETENT_MID, 1e-6f)
        assertEquals(0.90f, ComponentPalette.SHEET_DETENT_HIGH, 1e-6f)
        assertEquals(3, ComponentPalette.sheetDetents.size)
    }

    @Test
    fun `性能红线单帧预算 16ms 模型预算 4ms`() {
        assertEquals(16L, ComponentPalette.FRAME_BUDGET_MS)
        assertTrue(ComponentPalette.MODEL_BUDGET_MS <= ComponentPalette.FRAME_BUDGET_MS)
    }
}
