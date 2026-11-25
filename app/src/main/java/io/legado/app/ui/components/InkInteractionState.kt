package io.legado.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import io.legado.app.ui.components.compat.toVisualState
import io.legado.app.ui.components.contract.ComponentVisualState

/**
 * 墨触手势结果：[interaction] 是当前七态快照，[gestureModifier] 绑定按压 / 悬停监听。
 */
internal class InkGesture(
    val interaction: InkInteraction,
    val gestureModifier: Modifier,
)

/**
 * 分闸事件许可（纯函数，普通 JVM 单测可直接断言）。
 *
 * 背景（B17）：OFFLINE 容器本体禁点是契约语义——[ComponentVisualState.OFFLINE].eventsAllowed=false
 * 保持不变；但离线空态的「重试」是错误恢复入口，不能被容器闸一并短路。本函数把「容器本体闸」与
 * 「独立入口放行」拆开：
 *  - [standaloneInteractive] = true 表示本元素是独立操作入口（离线重试 / 恢复），在未禁用、未加载时
 *    独立于容器态闸放行自身点击；
 *  - 不改契约枚举值：契约值是全局语义，改它会让所有 OFFLINE 组件都变成可点；
 *  - 真正禁用（enabled=false）与加载中仍是硬闸，独立入口不凌驾于这两道闸之上。
 *
 * @param visual 契约解析出的可视态（提供容器本体事件许可）
 * @param standaloneInteractive 是否为独立操作入口（仅本元素自身点击放行）
 * @param enabled 是否真正可用（禁用硬闸）
 * @param loading 加载中硬闸
 */
internal fun resolveEventsAllowed(
    visual: ComponentVisualState,
    standaloneInteractive: Boolean,
    enabled: Boolean,
    loading: Boolean,
): Boolean {
    if (!enabled || loading) return false
    return visual.eventsAllowed || standaloneInteractive
}

/**
 * 墨触基类：全站可点元素共用的按压 / 悬停手势原语。
 *
 * 落文**不使用 Material ripple**：touchstart 元素短暂泛朱砂（悬浮 8% / 按下 16%），
 * 120ms 后按 [ComponentPalette.DURATION_MICRO] 回落。反馈色与透明度由
 * [InkInteraction.accentOverlayAlpha] 统一收口，Button / ListItem / Tab 都走这里。
 *
 * @param enabled 是否可用（与禁用态对应）
 * @param loading 加载中（阻止点击回调）
 * @param empty 空数据态
 * @param offline 离线态（阻止点击回调，走缓存兜底提示）
 * @param standaloneInteractive 是否独立操作入口（B17 分闸：离线重试入口放行自身点击，不影响容器本体闸）
 * @param onClick 抬起且仍可交互时触发
 */
@Composable
internal fun rememberInkGesture(
    enabled: Boolean,
    loading: Boolean = false,
    empty: Boolean = false,
    offline: Boolean = false,
    standaloneInteractive: Boolean = false,
    onClick: () -> Unit,
): InkGesture {
    var hovered by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    val interaction = InkInteraction(
        hovered = hovered,
        pressed = pressed,
        disabled = !enabled,
        loading = loading,
        empty = empty,
        offline = offline,
    )

    // S-04 接线：可点口径改读新契约 eventsAllowed（旧七态经 Compat 单向映射，旧模型不删）
    val visual = interaction.resolve().toVisualState()

    // B17 分闸：容器本体按契约闸；standaloneInteractive 的独立入口（离线重试）仅放行自身点击
    val eventsAllowed = resolveEventsAllowed(
        visual = visual,
        standaloneInteractive = standaloneInteractive,
        enabled = enabled,
        loading = loading,
    )

    val gestureModifier = Modifier.pointerInput(eventsAllowed) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Enter -> {
                        hovered = true
                    }
                    PointerEventType.Exit -> {
                        hovered = false
                        pressed = false
                    }
                    PointerEventType.Press -> {
                        if (eventsAllowed) pressed = true
                    }
                    PointerEventType.Release -> {
                        if (pressed) {
                            pressed = false
                            if (eventsAllowed) onClick()
                        }
                    }
                    else -> {
                        pressed = false
                    }
                }
            }
        }
    }

    return remember(interaction) { InkGesture(interaction, gestureModifier) }
}
