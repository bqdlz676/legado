package io.legado.app.ui.main.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 落文 · 发现页动效件
 *
 * - 浮入：stagger 逐张浮起，曲线 CardReveal，时长 CardMs，顺位错开 45ms（最多错 8 张）。
 * - 前倾：按下绕 Y 轴微倾 4.5°、缩到 .985，抬起回弹；倾角尺度与全站卡片保持同调性。
 * - 不挂 Material ripple：clickable 传 indication = null，泛色由卡片自己画。
 */

/** 卡片浮入进度：0 = 尚未入场，1 = 落定 */
data class RevealState(
    val alpha: Float,
    val translationDp: Float
)

@Composable
fun rememberRevealState(index: Int, enabled: Boolean = true): RevealState {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(index, enabled) {
        if (!enabled) return@LaunchedEffect
        val stagger = (index.coerceAtMost(8) * 45).toLong()
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = InkExploreTokens.CardMs.toInt(),
                delayMillis = stagger.toInt(),
                easing = InkExploreTokens.CardReveal
            )
        )
    }
    val p = progress.value
    return RevealState(alpha = p, translationDp = (1f - p) * 26f)
}

/** 卡片交互态：按压 / 悬浮 / 禁用（禁用由调用方传入） */
class InkCardInteraction(
    val pressed: Boolean,
    val hovered: Boolean,
    val disabled: Boolean,
    val source: MutableInteractionSource
)

@Composable
fun rememberInkCardInteraction(disabled: Boolean = false): InkCardInteraction {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    return InkCardInteraction(
        pressed = pressed,
        hovered = hovered && !disabled,
        disabled = disabled,
        source = source
    )
}

/**
 * 墨触点击：无 ripple。禁用态不响应点击，视觉透明度由卡片自行处理。
 */
fun Modifier.inkClickable(
    interaction: InkCardInteraction,
    onClick: (() -> Unit)?
): Modifier = composed {
    this.clickable(
        interactionSource = interaction.source,
        indication = null,
        enabled = !interaction.disabled && onClick != null,
        onClick = { onClick?.invoke() }
    )
}

/**
 * 卡片 3D 前倾层：空闲微前倾 [baseTilt] 度，按下追加 4.5° 并缩到 .985。
 * cameraDistance 在 Compose 层由 graphicsLayer 按默认相机处理，倾角保持小角度避免畸变。
 */
fun Modifier.tiltLayer(
    interaction: InkCardInteraction,
    baseTilt: Float = 2.5f
): Modifier = composed {
    val rotationY by animateFloatAsState(
        targetValue = if (interaction.pressed) baseTilt + 4.5f else baseTilt,
        animationSpec = tween(
            durationMillis = if (interaction.pressed) InkExploreTokens.MicroMs.toInt()
            else InkExploreTokens.CardMs.toInt(),
            easing = if (interaction.pressed) InkExploreTokens.PageTurn
            else InkExploreTokens.BubbleSpring
        ),
        label = "tiltRotationY"
    )
    val scale by animateFloatAsState(
        targetValue = if (interaction.pressed) 0.985f else 1f,
        animationSpec = tween(
            durationMillis = InkExploreTokens.MicroMs.toInt(),
            easing = InkExploreTokens.PageTurn
        ),
        label = "tiltScale"
    )
    this.graphicsLayer {
        this.rotationY = rotationY
        scaleX = scale
        scaleY = scale
        cameraDistance = 12 * density
    }
}

/** 浮入层：透明度 + 上浮位移（dp） */
fun Modifier.revealLayer(state: RevealState): Modifier = composed {
    this.graphicsLayer {
        alpha = state.alpha
        translationY = state.translationDp.dp.toPx()
    }
}
