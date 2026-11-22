package io.legado.app.ui.book.read.ink

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

/**
 * InkButton 墨触基类（契约第四节）：不写 Material ripple；
 * 按压时泛朱砂 0→8% 覆盖层，120ms 回落。作为底栏按钮 / 列表项的统一基类。
 */
@Composable
fun InkIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    // 朱砂覆盖透明度：按压 0→8%，抬起落回 0，时长走微交互 120ms
    val pressAlpha = remember { Animatable(0f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            pressed = when (interaction) {
                is PressInteraction.Press -> true
                is PressInteraction.Release, is PressInteraction.Cancel -> false
            }
            val target = if (pressed) InkTokens.PRESS_ACCENT_ALPHA else 0f
            pressAlpha.animateTo(
                targetValue = target,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = InkTokens.DURATION_MICRO,
                ),
            )
        }
    }

    val overlay = InkTokens.textAccent.copy(alpha = pressAlpha.value)

    Box(
        modifier = modifier
            .background(overlay, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // 自绘墨触，禁用 ripple
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        androidx.compose.foundation.layout.Row(content = content)
    }
}
