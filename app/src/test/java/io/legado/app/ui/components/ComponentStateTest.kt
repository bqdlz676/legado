package io.legado.app.ui.components

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-04 七态快照归并闸门单测（纯 JVM，只用 org.junit + java.*，不引 Compose / Android 类型）。
 *
 * 对账口径：合法组合按优先级 禁用 > 离线 > 空数据 > 加载 > 点击 > 悬浮 > 默认 归并到唯一契约枚举；
 * 压淡 / 事件许可一律读契约枚举登记值；禁用 / 加载 / 离线快照不放行点击，空数据快照放行。
 * B1-件：旧七态枚举定义已删除，[ComponentVisualState] 为指向契约枚举的同名别名，事件口径读 eventsAllowed。
 */
class ComponentStateTest {

    @Test
    fun `默认快照解析为默认态`() {
        assertEquals(ComponentVisualState.DEFAULT, InkInteraction.Default.resolve())
    }

    @Test
    fun `优先级 禁用 高于 离线 高于 空数据 高于 加载 高于 点击 高于 悬浮`() {
        val interaction = InkInteraction(
            hovered = true,
            pressed = true,
            disabled = true,
            loading = true,
            empty = true,
            offline = true,
        )

        assertEquals(ComponentVisualState.DISABLED, interaction.resolve())
    }

    @Test
    fun `离线优先于空数据 空数据优先于加载 加载优先于按下`() {
        assertEquals(
            ComponentVisualState.OFFLINE,
            InkInteraction(offline = true, empty = true, loading = true, pressed = true).resolve(),
        )
        assertEquals(
            ComponentVisualState.EMPTY,
            InkInteraction(empty = true, loading = true, pressed = true).resolve(),
        )
        assertEquals(
            ComponentVisualState.LOADING,
            InkInteraction(loading = true, pressed = true).resolve(),
        )
    }

    @Test
    fun `按下优先于悬浮`() {
        assertEquals(
            ComponentVisualState.PRESSED,
            InkInteraction(hovered = true, pressed = true).resolve(),
        )
    }

    @Test
    fun `禁用 加载 离线快照不放行点击 空数据与默认快照放行`() {
        assertFalse(InkInteraction.Disabled.clickEnabled)
        assertFalse(InkInteraction.Loading.clickEnabled)
        assertFalse(InkInteraction.Offline.clickEnabled)
        assertTrue(InkInteraction.Empty.clickEnabled)
        assertTrue(InkInteraction.Default.clickEnabled)
    }

    @Test
    fun `按压与悬浮覆盖透明度取墨触两档 非可交互态不泛色`() {
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
    fun `禁用快照压淡到契约禁用档透明度 其余快照不压淡`() {
        assertEquals(ComponentVisualState.DISABLED.contentAlpha, InkInteraction.Disabled.contentAlpha, 1e-6f)
        assertEquals(1f, InkInteraction.Default.contentAlpha, 1e-6f)
        assertEquals(1f, InkInteraction.Loading.contentAlpha, 1e-6f)
    }

    @Test
    fun `契约枚举事件许可 可交互三态与空数据放行 禁用加载离线不放行`() {
        assertTrue(ComponentVisualState.DEFAULT.eventsAllowed)
        assertTrue(ComponentVisualState.HOVERED.eventsAllowed)
        assertTrue(ComponentVisualState.PRESSED.eventsAllowed)
        assertTrue(ComponentVisualState.EMPTY.eventsAllowed)

        assertFalse(ComponentVisualState.DISABLED.eventsAllowed)
        assertFalse(ComponentVisualState.LOADING.eventsAllowed)
        assertFalse(ComponentVisualState.OFFLINE.eventsAllowed)
    }
}
