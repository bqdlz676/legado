package io.legado.app.ui.components

import io.legado.app.ui.components.contract.ComponentVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B19 浮层离线分支动作裁决验收（纯 JVM，只用 org.junit + java.*，不依赖 Android / Compose）。
 *
 * 口径：
 *  - 宿主传入重试 / 刷新回调：裁决为 [SheetOfflineAction.Wired]，回调原样保留，
 *    每次点击触发一次（不去重）；
 *  - 宿主未传回调（null）：裁决为 [SheetOfflineAction.HintOnly]，纯提示兜底，
 *    不保留可点但空动作的入口（判据 3）；
 *  - 契约 OFFLINE 容器本体禁点语义保持不变（B17 分闸不回退，判据 4）。
 */
class SheetOfflineActionTest {

    @Test
    fun `宿主传入重试回调 裁决为 Wired 回调原样保留`() {
        val retry: () -> Unit = {}

        val action = resolveSheetOfflineAction(retry)

        assertTrue("有宿主回调时必须接成 Wired 动作入口", action is SheetOfflineAction.Wired)
        assertSame("回调必须原样接到动作入口，不得被空 lambda 包装吞掉", retry, (action as SheetOfflineAction.Wired).onAction)
    }

    @Test
    fun `点击重试 回调恰好触发一次`() {
        var invocations = 0
        val action = resolveSheetOfflineAction({ invocations++ }) as SheetOfflineAction.Wired

        action.onAction()

        assertEquals("一次点击必须恰好触发一次重试回调", 1, invocations)
    }

    @Test
    fun `重复点击 每次点击各触发一次 本单不去重`() {
        var invocations = 0
        val action = resolveSheetOfflineAction({ invocations++ }) as SheetOfflineAction.Wired

        action.onAction()
        action.onAction()

        assertEquals("双击 / 连点允许重复发起，每次点击各计一次回调", 2, invocations)
    }

    @Test
    fun `未传重试回调 回落纯提示无动作入口`() {
        val action = resolveSheetOfflineAction(null)

        assertTrue("无可接回调时必须回落 HintOnly 纯提示，不得挂空 lambda 按钮", action is SheetOfflineAction.HintOnly)
    }

    @Test
    fun `契约离线档事件许可仍为 false 容器禁点未回退`() {
        assertFalse("OFFLINE 容器本体仍按契约禁点，B17 分闸语义不得回退", ComponentVisualState.OFFLINE.eventsAllowed)
        assertEquals(1f, ComponentVisualState.OFFLINE.contentAlpha, 1e-6f)
    }
}
