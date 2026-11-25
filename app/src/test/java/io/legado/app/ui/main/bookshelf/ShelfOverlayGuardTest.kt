package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浮层开合守卫行为锁（验收 N3 收口 · 第一处：[ShelfSearch.hasOpenedOverlay] 守卫）。
 *
 * 背景：守卫原本埋在 `BookshelfScreen` 的 `LaunchedEffect(searchState.isOpen)` 内，
 * 纯 JVM 测试无法驱动 Compose 重组——推演「去掉守卫、首帧关闭也 detach」现有用例不会红。
 * 本文件把守卫判定收成纯函数 [ShelfSearch.overlayTransition] 后，在测试侧复刻画面层
 * 的派发动作（与 `BookshelfScreen` effect 同一 switch 口径），用调用记录桩断动作序列。
 *
 * 范式：
 * - 行为锁：构造「开合态 × 曾打开位」输入 → 调 [ShelfSearch.overlayTransition] →
 *   断言返回的守卫位与动作枚举；
 * - 调用记录桩：桩记录 attach 实参序列与 detach 次数，正向断「走到这支」，反向断
 *   「前位未命中时后位未被调用」（首帧关闭不得 detach）。
 *
 * 不做源码子串回证：断言对象是过渡结果枚举 / 桩调用序列，不读取画面源码文本。
 */
class ShelfOverlayGuardTest {

    /** 开合回调记录桩：记录每次挂接的实参与释放次数，替代看源码判断短路。 */
    private class CallbackRecorder {
        val attachedArgs = mutableListOf<Pair<String?, String?>>()
        var detachedCount = 0

        fun recordAttach(key: String?, scope: String?) {
            attachedArgs += key to scope
        }

        fun recordDetach() {
            detachedCount++
        }
    }

    /**
     * 复刻 `BookshelfScreen` 开合 effect 的派发口径：按过渡结果更新桩，不复制判定逻辑
     * （判定只在 [ShelfSearch.overlayTransition] 单源）。
     */
    private fun drive(
        transition: ShelfSearch.LifecycleTransition,
        recorder: CallbackRecorder,
    ) {
        when (transition.action) {
            ShelfSearch.LifecycleAction.Attach -> recorder.recordAttach(null, null)
            ShelfSearch.LifecycleAction.Detach -> recorder.recordDetach()
            ShelfSearch.LifecycleAction.Idle -> Unit
        }
    }

    // 1. 首帧关闭态（从未打开过）：动作必须是 Idle，不 attach、不 detach。
    //    反向可红：守卫被去掉（关闭分支无条件 detach）时，本条会在 detachCount 上变红。
    @Test
    fun `first frame closed without prior open stays idle and never detaches`() {
        val recorder = CallbackRecorder()

        val transition = ShelfSearch.overlayTransition(isOpen = false, hasOpened = false)
        drive(transition, recorder)

        assertFalse("首帧关闭守卫位必须保持 false", transition.hasOpened)
        assertEquals(ShelfSearch.LifecycleAction.Idle, transition.action)
        assertTrue("首帧关闭不得触发 attach", recorder.attachedArgs.isEmpty())
        assertEquals("首帧关闭不得触发 detach（冷启空挂接后立刻被取消的回归）", 0, recorder.detachedCount)
    }

    // 2. 首次打开：动作 Attach，守卫位置 true，attach 以 (null, null) 透传给 VM 幂等分支。
    @Test
    fun `first open attaches and marks overlay opened`() {
        val recorder = CallbackRecorder()

        val transition = ShelfSearch.overlayTransition(isOpen = true, hasOpened = false)
        drive(transition, recorder)

        assertTrue("打开后守卫位必须置 true", transition.hasOpened)
        assertEquals(ShelfSearch.LifecycleAction.Attach, transition.action)
        assertEquals(listOf<Pair<String?, String?>>(null to null), recorder.attachedArgs)
        assertEquals("打开分支不得触发 detach", 0, recorder.detachedCount)
    }

    // 3. 打开过后关闭：动作 Detach，守卫位复位 false，detach 恰好一次；attach 不重复。
    @Test
    fun `closing after being opened detaches once and clears guard`() {
        val recorder = CallbackRecorder()

        val transition = ShelfSearch.overlayTransition(isOpen = false, hasOpened = true)
        drive(transition, recorder)

        assertFalse("关闭后守卫位必须复位 false", transition.hasOpened)
        assertEquals(ShelfSearch.LifecycleAction.Detach, transition.action)
        assertEquals("曾打开后关闭必须触发一次 detach", 1, recorder.detachedCount)
        assertTrue("关闭分支不得触发 attach", recorder.attachedArgs.isEmpty())
    }

    // 4. 序列桩：打开 → 关闭 → 再次关闭（effect 重跑 / 重组二次观察关闭态）。守卫位复位后
    //    第二次关闭走 Idle，detach 全程只触发一次，attach 也不重复。
    @Test
    fun `open close then close again detaches only once`() {
        val recorder = CallbackRecorder()
        var hasOpened = false

        var transition = ShelfSearch.overlayTransition(isOpen = true, hasOpened)
        hasOpened = transition.hasOpened
        drive(transition, recorder)

        transition = ShelfSearch.overlayTransition(isOpen = false, hasOpened)
        hasOpened = transition.hasOpened
        drive(transition, recorder)

        transition = ShelfSearch.overlayTransition(isOpen = false, hasOpened)
        hasOpened = transition.hasOpened
        drive(transition, recorder)

        assertEquals("关闭序列内 detach 只允许一次", 1, recorder.detachedCount)
        assertEquals("挂接只允许一次", 1, recorder.attachedArgs.size)
        assertFalse("序列结束守卫位已复位", hasOpened)
    }

    // 5. 浮层已打开期间 effect 再次观察到打开（重复触发 / 重组重跑）：仍走 Attach 幂等分支，
    //    不得误判为关闭而 detach。
    @Test
    fun `repeat open effect while already open attaches without detach`() {
        val recorder = CallbackRecorder()

        val transition = ShelfSearch.overlayTransition(isOpen = true, hasOpened = true)
        drive(transition, recorder)

        assertTrue("重复打开守卫位保持 true", transition.hasOpened)
        assertEquals(ShelfSearch.LifecycleAction.Attach, transition.action)
        assertEquals("重复打开不得触发 detach", 0, recorder.detachedCount)
    }
}
