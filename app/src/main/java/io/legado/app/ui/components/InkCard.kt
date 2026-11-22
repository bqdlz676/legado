package io.legado.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * InkCard 绢帛卡片。
 *
 * 三变体对应发现页：战役横卡（暖金边 + 毛玻璃）/ 典籍竖卡 / 专题通栏卡（毛玻璃底）。
 * 七态：默认 / 悬浮（暖金阴影增强）/ 点击（朱砂阴影）/ 禁用 / 加载（骨架 + 金点）/
 * 空数据（内嵌 EmptyState）/ 离线（缓存降级角标）。删除态：碎墨粒消散（alpha + scale 退场）。
 */
enum class InkCardVariant { ELEVATED, GLASS, OUTLINE }

@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    variant: InkCardVariant = InkCardVariant.ELEVATED,
    enabled: Boolean = true,
    loading: Boolean = false,
    empty: Boolean = false,
    offline: Boolean = false,
    deleting: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val clickable = onClick != null
    val gesture = rememberInkGesture(
        enabled = enabled && clickable,
        loading = loading,
        empty = empty,
        offline = offline,
    ) { onClick?.invoke() }
    val interaction = gesture.interaction
    val state = interaction.resolve()

    val overlayAlpha = animateFloatAsState(
        targetValue = interaction.accentOverlayAlpha,
        animationSpec = tween(ComponentPalette.DURATION_MICRO, easing = ComponentTokens.cardEnterEasing),
        label = "inkCardOverlay",
    ).value

    val dissolve = animateFloatAsState(
        targetValue = if (deleting) 0f else 1f,
        animationSpec = tween(ComponentPalette.DURATION_CARD, easing = ComponentTokens.cardEnterEasing),
        label = "inkCardDissolve",
    ).value

    val shape = RoundedCornerShape(ComponentTokens.cardRadius)

    val container = when (variant) {
        InkCardVariant.GLASS -> ComponentTokens.bgSurface.copy(alpha = 0.72f)
        InkCardVariant.OUTLINE -> ComponentTokens.bgPage
        InkCardVariant.ELEVATED -> ComponentTokens.bgSurface
    }
    val borderColor = when {
        variant == InkCardVariant.ELEVATED && state == ComponentVisualState.HOVERED ->
            ComponentTokens.brandGold
        variant == InkCardVariant.OUTLINE -> ComponentTokens.colorDivider
        else -> ComponentTokens.colorDivider
    }

    Box(
        modifier = modifier
            .then(gesture.gestureModifier)
            .clip(shape)
            .background(container)
            .border(1.dp, borderColor, shape)
            .alpha(interaction.contentAlpha * dissolve)
            .scale(0.96f + 0.04f * dissolve)
            .padding(16.dp),
    ) {
        if (overlayAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(ComponentTokens.textAccent.copy(alpha = overlayAlpha)),
            )
        }

        when {
            loading -> CardLoadingSkeleton()
            empty -> InkEmptyState(message = "暂无内容", compact = true)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (title.isNotBlank()) {
                    androidx.compose.foundation.text.BasicText(
                        text = title,
                        style = TextStyle(
                            color = ComponentTokens.textPrimary,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                if (subtitle.isNotBlank()) {
                    androidx.compose.foundation.text.BasicText(
                        text = subtitle,
                        style = TextStyle(
                            color = ComponentTokens.textSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif,
                        ),
                    )
                }
                content?.invoke()
                if (offline) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ComponentTokens.brandCopper),
                        )
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.foundation.text.BasicText(
                            text = "离线缓存 · 联网后更新",
                            style = TextStyle(
                                color = ComponentTokens.textTertiary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .height(16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ComponentTokens.colorDivider),
        )
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ComponentTokens.colorDivider),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLoadingDots()
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText(
                text = "加载中",
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}
