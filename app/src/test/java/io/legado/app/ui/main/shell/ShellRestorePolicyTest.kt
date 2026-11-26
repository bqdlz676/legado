package io.legado.app.ui.main.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B2 收尾（R7 / O1 / O8）契约单测：纯 JVM 策略断言 + 壳 / 宿主源码静态断言。
 *
 * 为什么双轨：策略函数在 JVM 内直接断言「首 composition 不覆盖恢复态、显式请求才切槽」；
 * 源码静态断言锁住宿主接线顺序与令牌取用，防止下次重构退回偏好槽直覆 / 裸 easing / 内联覆盖色。
 * 纯 JVM，不触 Compose 运行时与 Android 框架。
 */
class ShellRestorePolicyTest {

    private val javaRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    private fun readMain(rel: String): String = File(javaRoot, rel).readText()

    @Test
    fun `host request does not override saved state on first composition`() {
        // 代次 0 = 首 composition（冷启动 / 进程重建）：有保存态不覆盖，无保存态走 initialTab 兜底。
        assertFalse(ShellRestorePolicy.shouldApplyHostRequest(0))
        // 活进程内显式请求（newIntent 检索改道）代次自增后才允许切槽。
        assertTrue(ShellRestorePolicy.shouldApplyHostRequest(1))
        assertTrue(ShellRestorePolicy.shouldApplyHostRequest(2))
    }

    @Test
    fun `illegal restore slot falls back to bookshelf without crash`() {
        assertEquals(ShellTab.BOOKSHELF, ShellRestorePolicy.sanitizeTab(null))
        assertEquals(ShellTab.EXPLORE, ShellRestorePolicy.sanitizeTab(ShellTab.EXPLORE))
        assertEquals(ShellTab.SETTINGS, ShellRestorePolicy.sanitizeTab(ShellTab.SETTINGS))
    }

    @Test
    fun `host wires restore-first order and keeps preference default slot fallback`() {
        val host = readMain("io/legado/app/ui/main/MainActivity.kt")
        // 旧口径：首 composition 即按偏好默认槽覆盖 rememberSaveable 恢复态——不得回归。
        assertFalse(host.contains("LaunchedEffect(requestedTab)"))
        // 新口径：代次闸 + 策略门；冷启动默认槽仍由 initialTab 兜底传入壳态。
        assertTrue(host.contains("LaunchedEffect(shellRequestGeneration)"))
        assertTrue(host.contains("ShellRestorePolicy.shouldApplyHostRequest(shellRequestGeneration)"))
        assertTrue(host.contains("rememberMainShellState(initialTab = requestedTab)"))
    }

    @Test
    fun `bottom bar tints icons and labels from gold copper tokens`() {
        val bar = readMain("io/legado/app/ui/main/shell/ShellBottomBar.kt")
        assertTrue(bar.contains("ComponentTokens.brandGold"))
        assertTrue(bar.contains("ComponentTokens.brandCopper"))
        // 旧覆盖色（深褐 textSecondary）不得再作未选中 tint；选中朱砂 textAccent 仅留给按压覆盖 / 角标。
        assertFalse(bar.contains("ComponentTokens.textSecondary"))
        assertFalse(
            Regex("contentColor\\s*=\\s*if\\s*\\(selected\\)\\s*ComponentTokens\\.textAccent")
                .containsMatchIn(bar),
        )
    }

    @Test
    fun `shell transition fade easing reads token instead of bare easing`() {
        val shell = readMain("io/legado/app/ui/main/shell/MainShell.kt")
        assertTrue(shell.contains("easing = ComponentTokens.pageTurnEasing"))
        // 旧口径：fade tween 未传 easing 走 Compose 默认曲线——不得回归；壳内不写裸贝塞尔。
        assertFalse(shell.contains("fadeIn(tween(duration))"))
        assertFalse(shell.contains("fadeOut(tween(duration))"))
        assertFalse(shell.contains("CubicBezierEasing("))
    }
}
