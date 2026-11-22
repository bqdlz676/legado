package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Toast 三色映射与空消息兜底。
 */
class ToastPaletteTest {

    @Test
    fun `成功暖金 失败朱砂 提示石青`() {
        assertEquals(ComponentPalette.BRAND_GOLD, ToastPalette.colorOf(ToastKind.SUCCESS))
        assertEquals(ComponentPalette.TEXT_ACCENT, ToastPalette.colorOf(ToastKind.FAILURE))
        assertEquals(ComponentPalette.STONE_BLUE, ToastPalette.colorOf(ToastKind.INFO))
    }

    @Test
    fun `空文本 Toast 判定无效 不渲染`() {
        assertFalse(ToastMessage.success(1, "  ").isValid)
        assertTrue(ToastMessage.failure(2, "加载失败").isValid)
        assertTrue(ToastMessage.info(3, "当前离线", "重试").isValid)
    }

    @Test
    fun `工厂方法携带类型与动作`() {
        val toast = ToastMessage.info(9, "离线", "重试")
        assertEquals(ToastKind.INFO, toast.kind)
        assertEquals("重试", toast.actionLabel)
        assertEquals(9, toast.id)
    }
}
