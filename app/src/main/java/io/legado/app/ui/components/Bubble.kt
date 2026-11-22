package io.legado.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * Bubble 气泡：字 / 词 / 典故 / 人名 / 地名 / 年号 六变体 + 加载 + 空数据 + 离线。
 *
 * 容器统一：撕边纸片观感（圆角 + 暖金 1px 边）+ spring 注入（scale 0.3 → 1.05 → 1.0）
 * + 义项切换点；箭头对准原字水平中心，默认在原文上方 8dp，上方超界翻到下方。
 * 数据协议对齐切片-02 [BubbleContent]：Provider 只产这一种结构，气泡 UI 不分别处理数据类。
 */
enum class BubbleKind { CHAR, WORD, ALLUSION, PERSON, PLACE, ERA, MISSING }

sealed class BubbleAction {
    data class OpenMap(val placeId: String) : BubbleAction()
    data class OpenPerson(val name: String) : BubbleAction()
    data class OpenTimeline(val year: Int) : BubbleAction()
    data class OpenRelation(val name: String) : BubbleAction()
}

data class BubbleContent(
    val kind: BubbleKind,
    val token: String,
    val phonetic: String? = null,
    val headline: String,
    val body: String,
    val detail: String = "",
    val actions: List<BubbleAction> = emptyList(),
    val candidates: List<String> = emptyList(),
    val source: String = "",
) {
    /** 多于一个候选时，顶部出现「还有 N 种解释」切换点 */
    val hasCandidates: Boolean get() = candidates.size > 1

    val isMissing: Boolean get() = kind == BubbleKind.MISSING

    /** 模型自检：返回不合规字段说明，空列表表示通过（供单测覆盖） */
    fun validate(): List<String> = buildList {
        if (token.isBlank()) add("token 不可为空")
        if (headline.isBlank() && !isMissing) add("headline 不可为空")
        if (body.isBlank() && !isMissing) add("body 不可为空")
    }

    companion object {
        /** 全空兜底（Missing）：允许「查网络 / 加入生字本」 */
        fun missing(token: String): BubbleContent = BubbleContent(
            kind = BubbleKind.MISSING,
            token = token,
            headline = "未查到释义",
            body = "此字 / 词暂无可用释义，可加入生字本或联网查证。",
        )
    }
}

@Composable
fun InkBubble(
    content: BubbleContent,
    modifier: Modifier = Modifier,
    arrowUp: Boolean = true,
    loading: Boolean = false,
    offline: Boolean = false,
    onAction: (BubbleAction) -> Unit = {},
    onCandidateSwitch: () -> Unit = {},
) {
    // spring 注入：0.3 → 1.05 → 1.0（overshoot 回弹）
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(loading) {
        scale.animateTo(
            targetValue = if (loading) 0.92f else 1f,
            animationSpec = ComponentTokens.bubbleSpring(),
        )
    }
    val alpha by animateFloatAsState(
        targetValue = if (loading) 0.6f else 1f,
        animationSpec = androidx.compose.animation.core.tween(
            ComponentPalette.DURATION_BUBBLE,
            easing = ComponentTokens.bubbleEasing,
        ),
        label = "bubbleAlpha",
    )

    val shape = RoundedCornerShape(ComponentTokens.bubbleRadius)
    val accent = when (content.kind) {
        BubbleKind.PERSON -> ComponentTokens.textAccent
        BubbleKind.ALLUSION -> ComponentTokens.brandCopper
        BubbleKind.PLACE -> ComponentTokens.stoneBlue
        BubbleKind.ERA -> ComponentTokens.brandGold
        else -> ComponentTokens.textPrimary
    }

    Column(modifier = modifier.alpha(alpha)) {
        if (arrowUp) BubbleArrow(Modifier.align(Alignment.CenterHorizontally))
        Box(
            Modifier
                .clip(shape)
                .background(ComponentTokens.bgSurface)
                .border(1.dp, ComponentTokens.brandGold.copy(alpha = 0.55f), shape)
                .padding(14.dp)
                .scale(scale.value),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    loading -> BubbleLoading()
                    content.isMissing -> BubbleMissing(content)
                    else -> BubbleBody(content, accent, offline, onAction, onCandidateSwitch)
                }
            }
        }
        if (!arrowUp) BubbleArrow(Modifier.align(Alignment.CenterHorizontally), flip = true)
    }
}

@Composable
private fun BubbleBody(
    content: BubbleContent,
    accent: Color,
    offline: Boolean,
    onAction: (BubbleAction) -> Unit,
    onCandidateSwitch: () -> Unit,
) {
    if (content.hasCandidates) {
        val candidateGesture = rememberInkGesture(enabled = true, onClick = onCandidateSwitch)
        androidx.compose.foundation.text.BasicText(
            text = "还有 ${content.candidates.size - 1} 种解释 · 点击切换",
            style = TextStyle(
                color = ComponentTokens.textTertiary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
            modifier = Modifier
                .then(candidateGesture.gestureModifier)
                .clip(RoundedCornerShape(8.dp))
                .background(ComponentTokens.colorDivider)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicText(
            text = content.token,
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 18.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        if (!content.phonetic.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText(
                text = content.phonetic,
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
    }

    androidx.compose.foundation.text.BasicText(
        text = content.headline,
        style = TextStyle(
            color = accent,
            fontSize = 14.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
        ),
    )
    androidx.compose.foundation.text.BasicText(
        text = content.body,
        style = TextStyle(
            color = ComponentTokens.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontFamily = FontFamily.Serif,
        ),
    )

    if (content.actions.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content.actions.forEach { action ->
                val label = when (action) {
                    is BubbleAction.OpenMap -> "地图"
                    is BubbleAction.OpenPerson -> "人物"
                    is BubbleAction.OpenTimeline -> "时间轴"
                    is BubbleAction.OpenRelation -> "关系"
                }
                BubbleActionChip(label) { onAction(action) }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (offline) {
            androidx.compose.foundation.text.BasicText(
                text = "离线缓存",
                style = TextStyle(
                    color = ComponentTokens.brandCopper,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (content.source.isNotBlank()) {
            androidx.compose.foundation.text.BasicText(
                text = content.source,
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}

@Composable
private fun BubbleActionChip(label: String, onClick: () -> Unit) {
    val gesture = rememberInkGesture(enabled = true, onClick = onClick)
    Box(
        Modifier
            .then(gesture.gestureModifier)
            .clip(RoundedCornerShape(ComponentPalette.BUTTON_RADIUS_DP.dp))
            .border(1.dp, ComponentTokens.colorDivider, RoundedCornerShape(ComponentPalette.BUTTON_RADIUS_DP.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = ComponentTokens.colorLink,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

@Composable
private fun BubbleMissing(content: BubbleContent) {
    androidx.compose.foundation.text.BasicText(
        text = content.headline,
        style = TextStyle(
            color = ComponentTokens.textTertiary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
        ),
    )
    androidx.compose.foundation.text.BasicText(
        text = content.body,
        style = TextStyle(
            color = ComponentTokens.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontFamily = FontFamily.Serif,
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BubbleActionChip("加入生字本") {}
        BubbleActionChip("联网查证") {}
    }
}

@Composable
private fun BubbleLoading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkLoadingDots()
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(
            text = "释义加载中",
            style = TextStyle(
                color = ComponentTokens.textTertiary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

@Composable
private fun BubbleArrow(modifier: Modifier, flip: Boolean = false) {
    Canvas(modifier.size(ComponentTokens.bubbleArrowWidth, ComponentTokens.bubbleArrowHeight)) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            if (!flip) {
                moveTo(0f, h)
                lineTo(w / 2f, 0f)
                lineTo(w, h)
            } else {
                moveTo(0f, 0f)
                lineTo(w / 2f, h)
                lineTo(w, 0f)
            }
            close()
        }
        drawPath(path, color = ComponentTokens.brandGold.copy(alpha = 0.55f))
    }
}
