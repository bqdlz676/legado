package io.legado.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.contract.ComponentVisualState

/**
 * InkButton 墨触按钮（组件基类，ListItem / Tab 沿用同一套墨触反馈）。
 *
 * - 全站不用 Material ripple；touchstart 泛朱砂：悬浮 8%、按下 16%，120ms 回落。
 * - 七态：默认 / 悬浮 / 点击 / 禁用（云灰 30%）/ 加载（三个金点依次亮灭）/ 空数据 / 离线。
 * - 状态取唯一契约模型 [ComponentVisualState]：宿主显式传入非默认态时以传入态为准，否则由
 *   手势快照按优先级解析；禁用 / 加载 / 离线由墨触闸阻止点击回调。
 * - 颜色只取 [ComponentTokens]，不包裹 MaterialTheme。
 */
enum class InkButtonVariant { PRIMARY, SECONDARY, GHOST }

/** 加载态：三个暖金点依次亮灭（落文金点签名，直径 8dp） */
@Composable
internal fun InkLoadingDots(
    modifier: Modifier = Modifier,
    dotColor: Color = ComponentTokens.brandGold,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val anim = remember(index) { Animatable(0.25f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = ComponentPalette.DURATION_CARD * 2
                            0.25f at 0
                            1f at ComponentPalette.DURATION_MICRO + index * 60
                            0.25f at ComponentPalette.DURATION_CARD * 2
                        },
                        repeatMode = RepeatMode.Restart,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor.copy(alpha = anim.value)),
            )
            if (index < 2) Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: InkButtonVariant = InkButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    offline: Boolean = false,
    empty: Boolean = false,
    standaloneInteractive: Boolean = false,
    state: ComponentVisualState = ComponentVisualState.DEFAULT,
    leading: @Composable (() -> Unit)? = null,
) {
    // 显式状态与既有布尔取并集：禁用 / 加载 / 离线不仅改外观，也要进墨触闸阻止回调。
    val effectiveDisabled = !enabled || state == ComponentVisualState.DISABLED
    val effectiveLoading = loading || state == ComponentVisualState.LOADING
    val effectiveOffline = offline || state == ComponentVisualState.OFFLINE
    val effectiveEmpty = empty || state == ComponentVisualState.EMPTY
    val gesture = rememberInkGesture(
        enabled = !effectiveDisabled,
        loading = effectiveLoading,
        empty = effectiveEmpty,
        offline = effectiveOffline,
        standaloneInteractive = standaloneInteractive,
        onClick = onClick,
    )
    val interaction = gesture.interaction
    // 宿主显式传入非默认态优先；缺省回落到手势快照解析态。两套来源最终都收口到同一契约枚举。
    val effectiveState = if (state != ComponentVisualState.DEFAULT) {
        state
    } else {
        interaction.resolve()
    }

    val overlayAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = interaction.accentOverlayAlpha,
        animationSpec = tween(
            durationMillis = ComponentPalette.DURATION_MICRO,
            easing = ComponentTokens.cardEnterEasing,
        ),
        label = "inkBtnOverlay",
    ).value

    val container: Color
    val content: Color
    val border: Color?
    when (variant) {
        InkButtonVariant.PRIMARY -> {
            container = ComponentTokens.textPrimary
            content = ComponentTokens.bgPage
            border = null
        }
        InkButtonVariant.SECONDARY -> {
            container = ComponentTokens.bgSurface
            content = ComponentTokens.textPrimary
            border = ComponentTokens.colorDivider
        }
        InkButtonVariant.GHOST -> {
            container = Color.Transparent
            content = ComponentTokens.textAccent
            border = null
        }
    }

    val resolvedLabel = when {
        effectiveState == ComponentVisualState.OFFLINE -> text.ifBlank { "离线 · 重试" }
        else -> text
    }
    val labelColor = when {
        effectiveState == ComponentVisualState.DISABLED -> ComponentTokens.textTertiary
        effectiveState == ComponentVisualState.EMPTY -> ComponentTokens.textTertiary
        effectiveState == ComponentVisualState.OFFLINE -> ComponentTokens.textSecondary
        else -> content
    }

    val shape = RoundedCornerShape(ComponentTokens.buttonRadius)

    Box(
        modifier = modifier
            .then(gesture.gestureModifier)
            .clip(shape)
            .background(container)
            .then(
                if (border != null) Modifier.border(1.dp, border, shape) else Modifier,
            )
            .alpha(effectiveState.contentAlpha)
            .heightIn(min = 44.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 朱砂泛色层：悬浮 / 按压覆盖在容器之上
        if (overlayAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(ComponentTokens.textAccent.copy(alpha = overlayAlpha)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            if (leading != null) Spacer(Modifier.width(8.dp))
            when {
                loading || effectiveState == ComponentVisualState.LOADING -> InkLoadingDots()
                else -> androidx.compose.foundation.text.BasicText(
                    text = resolvedLabel,
                    style = TextStyle(
                        color = labelColor,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

/**
 * 尾随 lambda 重载：分区 / 弹窗里把点击动作写在末尾（`InkButton(text, variant = GHOST) { … }`）。
 * 转发到主按钮；不渲染前置图标。主重载仍以第二个位置参数为 [onClick]，既有调用不受影响。
 */
@Composable
fun InkButton(
    text: String,
    variant: InkButtonVariant = InkButtonVariant.PRIMARY,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    offline: Boolean = false,
    empty: Boolean = false,
    standaloneInteractive: Boolean = false,
    state: ComponentVisualState = ComponentVisualState.DEFAULT,
    onClick: () -> Unit,
) = InkButton(
    text, onClick, modifier, variant, enabled, loading, offline, empty,
    standaloneInteractive, state, null,
)
