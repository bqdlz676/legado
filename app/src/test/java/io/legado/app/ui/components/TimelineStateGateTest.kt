package io.legado.app.ui.components

import io.legado.app.ui.components.contract.ComponentVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Timeline 状态闸门收口单测（纯 JVM，只用 org.junit + java.*，不引 Compose / Android 类型）。
 *
 * 锁住 N1 两条：
 *  ① DISABLED 时箭头不可点——闸门经契约 [ComponentVisualState.eventsAllowed] 裁决，
 *     不再只读模型 `canGoBack` / `canGoForward`；
 *  ② DISABLED 压淡读契约 [ComponentVisualState.contentAlpha]，整行 alpha 来自枚举登记值。
 *
 * 另一路是源码静态反证：直接读 Timeline.kt 文本，证明箭头闸与压淡在源码逻辑链上真的接到契约字段，
 * 而非注释声称。改坏任一路径，对应断言变红。
 */
class TimelineStateGateTest {

    // ───────────────────────── 纯函数：箭头可点闸 ─────────────────────────

    @Test
    fun `禁用态箭头不可点 即便模型边界允许前进后退`() {
        assertFalse(timelineArrowAllowed(ComponentVisualState.DISABLED, canNavigate = true))
        assertFalse(timelineArrowAllowed(ComponentVisualState.DISABLED, canNavigate = false))
    }

    @Test
    fun `离线与加载态容器本体箭头不放行`() {
        assertFalse(timelineArrowAllowed(ComponentVisualState.OFFLINE, canNavigate = true))
        assertFalse(timelineArrowAllowed(ComponentVisualState.LOADING, canNavigate = true))
    }

    @Test
    fun `默认与空数据态按模型边界放行箭头`() {
        assertTrue(timelineArrowAllowed(ComponentVisualState.DEFAULT, canNavigate = true))
        assertTrue(timelineArrowAllowed(ComponentVisualState.EMPTY, canNavigate = true))
        // 模型边界不成立（首节点 / 末节点）时，即便契约放行也不可点
        assertFalse(timelineArrowAllowed(ComponentVisualState.DEFAULT, canNavigate = false))
        assertFalse(timelineArrowAllowed(ComponentVisualState.EMPTY, canNavigate = false))
    }

    // ───────────────────────── 纯函数：契约压淡口径 ─────────────────────────

    @Test
    fun `禁用态压淡到契约禁用档透明度 离线空数据加载不压淡`() {
        assertEquals(
            ComponentPalette.DISABLED_OPACITY,
            ComponentVisualState.DISABLED.contentAlpha,
            1e-6f,
        )
        assertEquals(1f, ComponentVisualState.OFFLINE.contentAlpha, 1e-6f)
        assertEquals(1f, ComponentVisualState.EMPTY.contentAlpha, 1e-6f)
        assertEquals(1f, ComponentVisualState.LOADING.contentAlpha, 1e-6f)
        assertEquals(1f, ComponentVisualState.DEFAULT.contentAlpha, 1e-6f)
    }

    // ───────────────────────── 源码静态反证 ─────────────────────────

    private val timelineSource: String by lazy {
        File(sourceRoot, "io/legado/app/ui/components/Timeline.kt").readText()
    }

    @Test
    fun `箭头闸门读契约 eventsAllowed 并叠加模型边界`() {
        assertTrue(
            "回退箭头必须经 timelineArrowAllowed 闸（契约 eventsAllowed && canGoBack）",
            timelineSource.contains("timelineArrowAllowed(state, model.canGoBack)"),
        )
        assertTrue(
            "前进箭头必须经 timelineArrowAllowed 闸（契约 eventsAllowed && canGoForward）",
            timelineSource.contains("timelineArrowAllowed(state, model.canGoForward)"),
        )
        assertTrue(
            "闸函数必须直读契约 eventsAllowed，不许另写平行可点布尔",
            timelineSource.contains("state.eventsAllowed && canNavigate"),
        )
        // 旧缺口反证：箭头不得再只读模型边界布尔
        assertFalse(
            "箭头不得裸用 enabled = model.canGoBack",
            timelineSource.contains("enabled = model.canGoBack"),
        )
        assertFalse(
            "箭头不得裸用 enabled = model.canGoForward",
            timelineSource.contains("enabled = model.canGoForward"),
        )
    }

    @Test
    fun `整行压淡读契约 contentAlpha`() {
        assertTrue(
            "TimelineBody 根节点必须 .alpha(state.contentAlpha) 直读契约压淡系数",
            timelineSource.contains(".alpha(state.contentAlpha)"),
        )
    }

    @Test
    fun `快照层平行可点与压淡口径未回流到 Timeline`() {
        assertFalse(timelineSource.contains("clickEnabled"))
        assertFalse(timelineSource.contains("InkInteraction.Disabled.contentAlpha"))
    }

    private val sourceRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }
}
