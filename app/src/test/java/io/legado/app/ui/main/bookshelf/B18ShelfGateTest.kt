package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.components.ComponentPalette
import io.legado.app.ui.components.resolveEventsAllowed
import io.legado.app.ui.components.compat.toVisualState
import io.legado.app.ui.components.contract.ComponentVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B18 六态收敛 · 书架闸门与单源断言（纯 JVM，只用 org.junit + java.io.File，
 * 不依赖 Android / Compose 运行时）。
 *
 * 覆盖锚点卡七条判据中书架侧部分：
 *  ① 禁用压淡单源（CardTokens 转发 ComponentPalette，代码行不再出现 0.30f 字面量）；
 *  ② 书架按钮一律走墨触闸（shelfGateOf 快照 → resolveEventsAllowed 闸门布尔）；
 *  ③ componentStateOf 已删除，main 源码无残留调用；
 *  ⑥ 契约枚举语义未改（DISABLED/LOADING/EMPTY/OFFLINE 的 contentAlpha 与 eventsAllowed）。
 */
class B18ShelfGateTest {

    // ─────────────────────────── 判据 1：压淡单源 ───────────────────────────

    @Test
    fun `禁用压淡系数 卡片域转发组件库单源`() {
        assertEquals(0.30f, ComponentPalette.DISABLED_OPACITY, 1e-6f)
        val cardTokens = File(sourceRoot, "io/legado/app/ui/card/CardTokens.kt").readText()
        assertTrue(
            "CardTokens.disabledAlpha 必须转发 ComponentPalette.DISABLED_OPACITY",
            cardTokens.lines().any { it.contains("disabledAlpha") && it.contains("ComponentPalette.DISABLED_OPACITY") },
        )
    }

    @Test
    fun `卡片域代码行不再内联禁用压淡三零点三零 f 字面量`() {
        val cardTokens = File(sourceRoot, "io/legado/app/ui/card/CardTokens.kt").readText()
        val codeLines = cardTokens.lines().filter { !isCommentLine(it) }
        assertTrue(
            "CardTokens 代码行不得另写 0.30f 禁用压淡字面量：$codeLines",
            codeLines.none { it.contains("0.30f") },
        )
    }

    // ─────────────────────────── 判据 2：书架闸门布尔 ───────────────────────────

    @Test
    fun `首帧加载与刷新中 闸门硬拦`() {
        listOf(ShelfState.INIT_LOADING, ShelfState.REFRESHING).forEach { state ->
            val gate = shelfGateOf(state, recoveryEntry = false)
            assertFalse("${state} 必须 enabled=false", gate.enabled)
            assertTrue("${state} 必须挂 loading 硬闸", gate.loading)
            assertFalse(gate.standaloneInteractive)
            val allowed = resolveEventsAllowed(
                visual = ComponentVisualState.LOADING,
                standaloneInteractive = gate.standaloneInteractive,
                enabled = gate.enabled,
                loading = gate.loading,
            )
            assertFalse("${state} 事件必须被拦", allowed)
        }
    }

    @Test
    fun `禁用态 闸门硬拦 不放行独立入口`() {
        val gate = shelfGateOf(ShelfState.DISABLED, recoveryEntry = false)
        assertFalse(gate.enabled)
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.DISABLED,
            standaloneInteractive = gate.standaloneInteractive,
            enabled = gate.enabled,
            loading = gate.loading,
        )
        assertFalse("DISABLED 硬闸优先，独立入口也不得放行", allowed)
    }

    @Test
    fun `离线态 容器本体禁点 不压淡`() {
        val gate = shelfGateOf(ShelfState.OFFLINE, recoveryEntry = false)
        assertTrue(gate.enabled)
        assertTrue(gate.offline)
        assertFalse(gate.standaloneInteractive)
        val visual = ComponentVisualState.OFFLINE
        val allowed = resolveEventsAllowed(
            visual = visual,
            standaloneInteractive = gate.standaloneInteractive,
            enabled = gate.enabled,
            loading = gate.loading,
        )
        assertFalse("OFFLINE 容器本体仍按契约禁点", allowed)
        assertEquals(1f, visual.contentAlpha, 1e-6f)
    }

    @Test
    fun `空数据态 沿用旧口径可点`() {
        val gate = shelfGateOf(ShelfState.EMPTY, recoveryEntry = false)
        assertTrue(gate.empty)
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.EMPTY,
            standaloneInteractive = gate.standaloneInteractive,
            enabled = gate.enabled,
            loading = gate.loading,
        )
        assertTrue("EMPTY 态契约放行，书架导入入口可点", allowed)
    }

    @Test
    fun `错误态重试 作为独立恢复入口放行自身点击`() {
        val gate = shelfGateOf(ShelfState.ERROR, recoveryEntry = true)
        assertTrue(gate.enabled)
        assertTrue(gate.standaloneInteractive)
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.OFFLINE,
            standaloneInteractive = gate.standaloneInteractive,
            enabled = gate.enabled,
            loading = gate.loading,
        )
        assertTrue("B17 分闸：错误恢复入口独立放行", allowed)
    }

    @Test
    fun `常态书架 按钮放行`() {
        val gate = shelfGateOf(ShelfState.CONTENT, recoveryEntry = false)
        assertTrue(gate.enabled)
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.DEFAULT,
            standaloneInteractive = gate.standaloneInteractive,
            enabled = gate.enabled,
            loading = gate.loading,
        )
        assertTrue(allowed)
    }

    @Test
    fun `书架按钮渲染点 接墨触闸而非裸手势`() {
        val screen = File(sourceRoot, "io/legado/app/ui/main/bookshelf/BookshelfScreen.kt").readText()
        assertTrue("ShelfInkButton 必须经 rememberInkGesture 取 gestureModifier",
            screen.contains("rememberInkGesture("))
        assertTrue("ShelfInkButton 必须读 resolveEventsAllowed 闸门布尔",
            screen.contains("resolveEventsAllowed("))
    }

    // ─────────────────────────── 判据 3：componentStateOf 已删除 ───────────────────────────

    @Test
    fun `三布尔解析函数 componentStateOf 不留渲染残留`() {
        val contract = File(sourceRoot, "io/legado/app/ui/components/contract/ComponentSystemContract.kt").readText()
        assertFalse("contract 文件不得再声明 fun componentStateOf",
            contract.lines().any { it.trimStart().startsWith("fun componentStateOf") })

        val uiRoot = File(sourceRoot, "io/legado/app/ui")
        val residual = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readLines().any { line ->
                    !isCommentLine(line) && line.substringBefore("//").contains("componentStateOf(")
                }
            }
            .map { uiRoot.toPath().relativize(it.toPath()).toString() }
            .toList()
        assertTrue("main 源码不得残留 componentStateOf 调用：$residual", residual.isEmpty())
    }

    // ─────────────────────────── 判据 6：契约枚举语义未改 ───────────────────────────

    @Test
    fun `契约六态 透明度与事件许可与冻结口径一致`() {
        assertEquals(ComponentPalette.DISABLED_OPACITY, ComponentVisualState.DISABLED.contentAlpha, 1e-6f)
        assertFalse(ComponentVisualState.DISABLED.eventsAllowed)

        assertEquals(1f, ComponentVisualState.LOADING.contentAlpha, 1e-6f)
        assertFalse(ComponentVisualState.LOADING.eventsAllowed)

        assertEquals(1f, ComponentVisualState.EMPTY.contentAlpha, 1e-6f)
        assertTrue(ComponentVisualState.EMPTY.eventsAllowed)

        assertEquals(1f, ComponentVisualState.OFFLINE.contentAlpha, 1e-6f)
        assertFalse(ComponentVisualState.OFFLINE.eventsAllowed)
    }

    @Test
    fun `旧七态到契约单向映射 离线空态档位不串`() {
        assertEquals(ComponentVisualState.OFFLINE,
            io.legado.app.ui.components.ComponentVisualState.OFFLINE.toVisualState())
        assertEquals(ComponentVisualState.EMPTY,
            io.legado.app.ui.components.ComponentVisualState.EMPTY.toVisualState())
    }

    private fun isCommentLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
    }

    private val sourceRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }
}
