package io.legado.app.ui.components.contract

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-04 组件系统 · 参数单源与状态契约验收（纯 JVM，只用 org.junit + java.*，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖本单断言：
 *  ① 默认态；② 悬停态；③ 按下态；④ 选中态；⑤ 禁用态；⑥ 加载中态；⑦ 空数据态；⑧ 离线态；
 *  ⑨ 非法组合（禁用且按下 / 选中）归禁用，不抛异常；
 *  ⑩ 禁用态禁止事件；⑪ 按下态允许事件；
 *  ⑫ 最小点击区 ≥48dp；⑬ 圆角三档单调递减（按钮 > 卡片 > 浮层）。
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
    fun `正常 空 离线三档各自透明度与事件许可独立登记`() {
        // 正常档：完整不透明，允许事件
        assertEquals(1f, ComponentVisualState.DEFAULT.contentAlpha, 1e-6f)
        assertTrue(ComponentVisualState.DEFAULT.eventsAllowed)

        // 空数据档：独立档位，不并入禁用；不压淡，仍可点（旧模型 empty 口径）
        assertEquals(1f, ComponentVisualState.EMPTY.contentAlpha, 1e-6f)
        assertTrue(ComponentVisualState.EMPTY.eventsAllowed)

        // 离线档：独立档位，不并入禁用；不压淡，但禁点（旧模型 offline 口径）
        assertEquals(1f, ComponentVisualState.OFFLINE.contentAlpha, 1e-6f)
        assertFalse(ComponentVisualState.OFFLINE.eventsAllowed)

        // 空 / 离线不得继承禁用档的 0.30 压淡系数
        assertFalse(ComponentVisualState.EMPTY.contentAlpha == ComponentPalette.DISABLED_OPACITY)
        assertFalse(ComponentVisualState.OFFLINE.contentAlpha == ComponentPalette.DISABLED_OPACITY)
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
