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

/**
 * 墨触手势结果：[interaction] 是当前七态快照，[gestureModifier] 绑定按压 / 悬停监听。
 */
internal class InkGesture(
    val interaction: InkInteraction,
    val gestureModifier: Modifier,
)

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
 * @param onClick 抬起且仍可交互时触发
 */
@Composable
internal fun rememberInkGesture(
    enabled: Boolean,
    loading: Boolean = false,
    empty: Boolean = false,
    offline: Boolean = false,
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

    val gestureModifier = Modifier.pointerInput(visual.eventsAllowed) {
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
                        if (visual.eventsAllowed) pressed = true
                    }
                    PointerEventType.Release -> {
                        if (pressed) {
                            pressed = false
                            if (visual.eventsAllowed) onClick()
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
