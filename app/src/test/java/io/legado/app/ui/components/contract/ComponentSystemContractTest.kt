package io.legado.app.ui.components.contract

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-04 组件系统 · 参数单源与六态契约验收（纯 JVM，只用 org.junit + java.*，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖本单断言：
 *  ① 默认态；② 悬停态；③ 按下态；④ 选中态；⑤ 禁用态；⑥ 加载中态（六态各一）；
 *  ⑦ 非法组合（禁用且按下 / 选中）归禁用，不抛异常；
 *  ⑧ 禁用态禁止事件；⑨ 按下态允许事件；
 *  ⑩ 最小点击区 ≥48dp；⑪ 圆角三档单调递减（按钮 > 卡片 > 浮层）。
 */
class ComponentSystemContractTest {

    @Test
    fun `default face when enabled and idle`() {
        val state = componentStateOf(enabled = true, pressed = false, selected = false)

        assertEquals(ComponentVisualState.DEFAULT, state)
        assertEquals(1f, state.contentAlpha, 1e-6f)
        assertTrue(state.eventsAllowed)
    }

    @Test
    fun `hovered face keeps full opacity and allows events`() {
        val state = ComponentVisualState.HOVERED

        assertEquals(1f, state.contentAlpha, 1e-6f)
        assertTrue(state.eventsAllowed)
    }

    @Test
    fun `pressed face allows events`() {
        val state = componentStateOf(enabled = true, pressed = true, selected = false)

        assertEquals(ComponentVisualState.PRESSED, state)
        assertTrue(state.eventsAllowed)
    }

    @Test
    fun `selected face keeps full opacity and allows events`() {
        val state = ComponentVisualState.SELECTED

        assertEquals(1f, state.contentAlpha, 1e-6f)
        assertTrue(state.eventsAllowed)
    }

    @Test
    fun `disabled face blocks events and dims opacity`() {
        val state = componentStateOf(enabled = false, pressed = false, selected = false)

        assertEquals(ComponentVisualState.DISABLED, state)
        assertFalse(state.eventsAllowed)
        assertEquals(ComponentPalette.DISABLED_OPACITY, state.contentAlpha, 1e-6f)
    }

    @Test
    fun `loading face blocks events`() {
        val state = ComponentVisualState.LOADING

        assertFalse(state.eventsAllowed)
        assertEquals(1f, state.contentAlpha, 1e-6f)
    }

    @Test
    fun `illegal combo disabled with pressed falls back to disabled`() {
        assertEquals(
            ComponentVisualState.DISABLED,
            componentStateOf(enabled = false, pressed = true, selected = false),
        )
        assertEquals(
            ComponentVisualState.DISABLED,
            componentStateOf(enabled = false, pressed = true, selected = true),
        )
        assertEquals(
            ComponentVisualState.DISABLED,
            componentStateOf(enabled = false, pressed = false, selected = true),
        )
    }

    @Test
    fun `disabled state forbids events`() {
        assertFalse(ComponentVisualState.DISABLED.eventsAllowed)
    }

    @Test
    fun `pressed state allows events`() {
        assertTrue(ComponentVisualState.PRESSED.eventsAllowed)
    }

    @Test
    fun `minimum touch target at least forty eight dp`() {
        assertTrue(
            "可点元素最小点击区不得低于 48dp 无障碍下限",
            ComponentSystemParams.MIN_TOUCH_TARGET_DP >= 48,
        )
    }

    @Test
    fun `radius three tiers monotonically decrease`() {
        assertTrue(
            "圆角三档须单调递减：按钮 > 卡片 > 浮层",
            ComponentSystemParams.RADIUS_BUTTON_DP >
                ComponentSystemParams.RADIUS_CARD_DP &&
                ComponentSystemParams.RADIUS_CARD_DP >
                ComponentSystemParams.RADIUS_OVERLAY_DP,
        )
    }
}
