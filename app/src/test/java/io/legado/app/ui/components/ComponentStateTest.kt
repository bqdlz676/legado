package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 七态优先级、点击可用性、墨触覆盖透明度与禁用透明度断言。
 */
class ComponentStateTest {

    @Test
    fun `默认态解析为 DEFAULT`() {
        assertEquals(ComponentVisualState.DEFAULT, InkInteraction.Default.resolve())
    }

    @Test
    fun `状态优先级 禁用 高于 离线 高于 空 高于 加载 高于 按压 高于 悬浮`() {
        val interaction = InkInteraction(
            hovered = true,
            pressed = true,
            disabled = true,
            loading = true,
            empty = true,
            offline = true,
        )
        assertEquals(ComponentVisualState.DISABLED, interaction.resolve())

        assertEquals(
            ComponentVisualState.OFFLINE,
            interaction.copy(disabled = false).resolve(),
        )
        assertEquals(
            ComponentVisualState.EMPTY,
            interaction.copy(disabled = false, offline = false).resolve(),
        )
        assertEquals(
            ComponentVisualState.LOADING,
            interaction.copy(disabled = false, offline = false, empty = false).resolve(),
        )
        assertEquals(
            ComponentVisualState.PRESSED,
            interaction.copy(
                disabled = false, offline = false, empty = false, loading = false,
            ).resolve(),
        )
        assertEquals(
            ComponentVisualState.HOVERED,
            interaction.copy(
                disabled = false, offline = false, empty = false, loading = false,
                pressed = false,
            ).resolve(),
        )
    }

    @Test
    fun `禁用 加载 离线均不可点击`() {
        assertFalse(InkInteraction.Disabled.clickEnabled)
        assertFalse(InkInteraction.Loading.clickEnabled)
        assertFalse(InkInteraction.Offline.clickEnabled)
        assertTrue(InkInteraction.Empty.clickEnabled)
        assertTrue(InkInteraction.Default.clickEnabled)
    }

    @Test
    fun `悬浮泛朱砂 8 按压泛 16 非可交互态不泛色`() {
        assertEquals(
            ComponentPalette.PRESS_HOVER_ACCENT_ALPHA,
            InkInteraction(hovered = true).accentOverlayAlpha,
            1e-6f,
        )
        assertEquals(
            ComponentPalette.PRESS_DOWN_ACCENT_ALPHA,
            InkInteraction(pressed = true).accentOverlayAlpha,
            1e-6f,
        )
        assertEquals(0f, InkInteraction.Disabled.accentOverlayAlpha, 1e-6f)
        assertEquals(0f, InkInteraction.Loading.accentOverlayAlpha, 1e-6f)
        assertEquals(0f, InkInteraction.Offline.accentOverlayAlpha, 1e-6f)
    }

    @Test
    fun `禁用整体透明度 30 其余不压透明度`() {
        assertEquals(ComponentPalette.DISABLED_OPACITY, InkInteraction.Disabled.contentAlpha, 1e-6f)
        assertEquals(1f, InkInteraction.Default.contentAlpha, 1e-6f)
        assertEquals(1f, InkInteraction.Loading.contentAlpha, 1e-6f)
    }

    @Test
    fun `可交互态仅 默认 悬浮 按压`() {
        assertTrue(ComponentVisualState.DEFAULT.isInteractive)
        assertTrue(ComponentVisualState.HOVERED.isInteractive)
        assertTrue(ComponentVisualState.PRESSED.isInteractive)
        assertFalse(ComponentVisualState.DISABLED.isInteractive)
        assertFalse(ComponentVisualState.LOADING.isInteractive)
        assertFalse(ComponentVisualState.EMPTY.isInteractive)
        assertFalse(ComponentVisualState.OFFLINE.isInteractive)
    }
}
