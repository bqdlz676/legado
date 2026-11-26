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
 * 锁住 V3 两条：
 *  ① 事件闸并集口径——显式传 state 时按契约枚举 [ComponentVisualState.eventsAllowed] 裁决；
 *     只传 offline 布尔而不显式传 state 时，该布尔也并入闸门，与 InkButton 并集口径对称；
 *  ② 压淡只读契约 [ComponentVisualState.contentAlpha]：离线 / 加载 / 空态不套禁用 0.30，
 *     EMPTY 不被误伤。
 *
 * 另一路是源码静态反证：直接读 Timeline.kt 文本，证明箭头闸、Body 事件闸与压淡在源码逻辑链上
 * 真的接到纯函数与契约字段，而非注释声称。改坏任一路径，对应断言变红。
 */
class TimelineStateGateTest {

    // ───────────────────────── 纯函数：state 入口 × 契约五态事件闸 ─────────────────────────

    @Test
    fun `state 入口 禁用离线加载三态 Body 事件不放行`() {
        assertFalse(timelineBodyEventsAllowed(ComponentVisualState.DISABLED, offline = false))
        assertFalse(timelineBodyEventsAllowed(ComponentVisualState.OFFLINE, offline = false))
        assertFalse(timelineBodyEventsAllowed(ComponentVisualState.LOADING, offline = false))
    }

    @Test
    fun `state 入口 默认为空数据态 Body 事件按契约放行`() {
        assertTrue(timelineBodyEventsAllowed(ComponentVisualState.DEFAULT, offline = false))
        assertTrue(timelineBodyEventsAllowed(ComponentVisualState.EMPTY, offline = false))
    }

    // ───────────────────────── 纯函数：offline 布尔入口 × 并集闸 ─────────────────────────

    @Test
    fun `布尔入口 仅传 offline 为真视同离线禁点 与显式 OFFLINE 口径一致`() {
        // 显式 state=OFFLINE 入口
        assertFalse(timelineBodyEventsAllowed(ComponentVisualState.OFFLINE, offline = false))
        // 只传 offline 布尔、state 留 DEFAULT 的入口：布尔必须并入闸门
        assertFalse(timelineBodyEventsAllowed(ComponentVisualState.DEFAULT, offline = true))
        // 两条入口放行档一致：DEFAULT 且 offline=false 仍放行
        assertTrue(timelineBodyEventsAllowed(ComponentVisualState.DEFAULT, offline = false))
    }

    @Test
    fun `布尔入口 offline 为假不额外禁点`() {
        assertTrue(timelineBodyEventsAllowed(ComponentVisualState.EMPTY, offline = false))
        assertTrue(timelineBodyEventsAllowed(ComponentVisualState.HOVERED, offline = false))
        assertTrue(timelineBodyEventsAllowed(ComponentVisualState.PRESSED, offline = false))
        assertTrue(timelineBodyEventsAllowed(ComponentVisualState.SELECTED, offline = false))
    }

    // ───────────────────────── 纯函数：箭头闸（Body 闸 ∧ 模型边界）─────────────────────────

    @Test
    fun `箭头闸 禁用态即便模型边界允许也不可点`() {
        assertFalse(timelineArrowAllowed(ComponentVisualState.DISABLED, offline = false, canNavigate = true))
        assertFalse(timelineArrowAllowed(ComponentVisualState.DISABLED, offline = false, canNavigate = false))
    }

    @Test
    fun `箭头闸 离线与加载态两条入口均不放行`() {
        // state 入口
        assertFalse(timelineArrowAllowed(ComponentVisualState.OFFLINE, offline = false, canNavigate = true))
        assertFalse(timelineArrowAllowed(ComponentVisualState.LOADING, offline = false, canNavigate = true))
        // 布尔入口
        assertFalse(timelineArrowAllowed(ComponentVisualState.DEFAULT, offline = true, canNavigate = true))
    }

    @Test
    fun `箭头闸 默认与空数据态按模型边界放行`() {
        assertTrue(timelineArrowAllowed(ComponentVisualState.DEFAULT, offline = false, canNavigate = true))
        assertTrue(timelineArrowAllowed(ComponentVisualState.EMPTY, offline = false, canNavigate = true))
        // 模型边界不成立（首节点 / 末节点）时，即便契约放行也不可点
        assertFalse(timelineArrowAllowed(ComponentVisualState.DEFAULT, offline = false, canNavigate = false))
        assertFalse(timelineArrowAllowed(ComponentVisualState.EMPTY, offline = false, canNavigate = false))
    }

    // ───────────────────────── 纯函数：契约压淡矩阵 ─────────────────────────

    @Test
    fun `压淡矩阵 仅禁用档落契约禁用透明度 离线加载空数据默认均不压淡`() {
        assertEquals(
            ComponentPalette.DISABLED_OPACITY,
            timelineContentAlpha(ComponentVisualState.DISABLED, offline = false),
            1e-6f,
        )
        assertEquals(1f, timelineContentAlpha(ComponentVisualState.OFFLINE, offline = false), 1e-6f)
        assertEquals(1f, timelineContentAlpha(ComponentVisualState.LOADING, offline = false), 1e-6f)
        assertEquals(1f, timelineContentAlpha(ComponentVisualState.EMPTY, offline = false), 1e-6f)
        assertEquals(1f, timelineContentAlpha(ComponentVisualState.DEFAULT, offline = false), 1e-6f)
    }

    @Test
    fun `布尔入口 offline 为真不触发压淡 空态不做压淡误伤`() {
        // 只传 offline 布尔：视同 OFFLINE 禁点，但契约 OFFLINE contentAlpha=1f，不得套禁用 0.30
        assertEquals(1f, timelineContentAlpha(ComponentVisualState.DEFAULT, offline = true), 1e-6f)
        // 离线布尔前后透明度不变（布尔不参与压淡系数推导）
        assertEquals(
            timelineContentAlpha(ComponentVisualState.DEFAULT, offline = false),
            timelineContentAlpha(ComponentVisualState.DEFAULT, offline = true),
            1e-6f,
        )
        // EMPTY 既放行也不压淡，双向都不误伤
        assertTrue(timelineBodyEventsAllowed(ComponentVisualState.EMPTY, offline = false))
        assertEquals(1f, timelineContentAlpha(ComponentVisualState.EMPTY, offline = false), 1e-6f)
    }

    // ───────────────────────── 源码静态反证 ─────────────────────────

    private val timelineSource: String by lazy {
        File(sourceRoot, "io/legado/app/ui/components/Timeline.kt").readText()
    }

    @Test
    fun `箭头闸门读并集事件闸并叠加模型边界`() {
        assertTrue(
            "回退箭头必须经 timelineArrowAllowed 闸（state + offline 并集 ∧ canGoBack）",
            timelineSource.contains("timelineArrowAllowed(state, offline, model.canGoBack)"),
        )
        assertTrue(
            "前进箭头必须经 timelineArrowAllowed 闸（state + offline 并集 ∧ canGoForward）",
            timelineSource.contains("timelineArrowAllowed(state, offline, model.canGoForward)"),
        )
        assertTrue(
            "Body 事件闸必须直读契约 eventsAllowed 并入 offline 布尔，不许另写平行可点布尔",
            timelineSource.contains("state.eventsAllowed && !offline"),
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
    fun `整行压淡读契约 contentAlpha 经纯函数转发`() {
        assertTrue(
            "TimelineBody 根节点必须 .alpha(timelineContentAlpha(state, offline)) 直读契约压淡系数",
            timelineSource.contains(".alpha(timelineContentAlpha(state, offline))"),
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
