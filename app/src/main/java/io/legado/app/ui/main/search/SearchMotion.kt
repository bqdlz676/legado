package io.legado.app.ui.main.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 落文 · 搜索页动效件（切片-15 铁律）
 *
 * - 结果 stagger 渐显：逐条浮起，曲线 [SearchTokens.CARD_REVEAL]，顺位错开
 *   [SearchTokens.STAGGER_STEP_MS]（最多错 [SearchTokens.STAGGER_MAX_INDEX] 条）。
 * - 聚焦 spring：输入框浮起回弹，曲线 [SearchTokens.FOCUS_SPRING]。
 * - 不挂 Material ripple：clickable 传 indication = null，泛色由条目自绘。
 */

/** 条目浮入进度：0 = 尚未入场，1 = 落定。 */
data class SearchRevealState(
    val alpha: Float,
    val translationDp: Float,
)

@Composable
fun rememberSearchRevealState(index: Int, enabled: Boolean = true): SearchRevealState {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(index, enabled) {
        if (!enabled) return@LaunchedEffect
        val stagger = (index.coerceAtMost(SearchTokens.STAGGER_MAX_INDEX) *
            SearchTokens.STAGGER_STEP_MS).toLong()
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SearchTokens.DURATION_CARD.toInt(),
                delayMillis = stagger.toInt(),
                easing = SearchTokens.CARD_REVEAL,
            ),
        )
    }
    val p = progress.value
    return SearchRevealState(alpha = p, translationDp = (1f - p) * 22f)
}

/** 浮入层：透明度 + 上浮位移（dp）。 */
fun Modifier.searchRevealLayer(state: SearchRevealState): Modifier = composed {
    this.graphicsLayer {
        alpha = state.alpha
        translationY = state.translationDp.dp.toPx()
    }
}

/**
 * 墨触点击：无 ripple。禁用态不响应点击；按压泛色由调用方按 token 自绘。
 */
fun Modifier.searchInkClickable(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this.clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}
