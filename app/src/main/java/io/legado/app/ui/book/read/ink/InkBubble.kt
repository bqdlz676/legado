package io.legado.app.ui.book.read.ink

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.bubble.Action
import io.legado.app.help.bubble.BubbleContent
import io.legado.app.help.bubble.BubbleKind
import kotlin.math.roundToInt

/**
 * 字 / 词 / 典故 / 人名 / 地名 / 年号 气泡（契约第四节 Bubble + 规格 4.2）。
 *
 * 自绘：纸面绢帛底、朱砂注音、墨色释义、金线分隔、铜色动作 chips；
 * 尖角对准字心，上方越界由 [BubblePlacement] 翻到下方。
 * 未收录 / 空数据走模块⑥ 的 Missing 兜底文案，不允许空气泡。
 *
 * @param placement 已算好的定位（壳坐标 px）；外层 Box 已按 placement.x/y 偏移，
 *                  尖角本地横坐标 = placement.arrowX - placement.x。
 */
@Composable
fun InkBubble(
    content: BubbleContent,
    placement: BubblePlacement,
    onAction: (Action) -> Unit,
    maxWidthDp: Dp,
    modifier: Modifier = Modifier,
) {
    // 入场：scale 0.3 → 1.05 → 1.0，250ms，回弹过冲曲线
    val progress = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = InkTokens.DURATION_BUBBLE,
                easing = InkTokens.bubbleEasing,
            ),
        )
    }
    val scale = progress.value
    val alpha = scale.coerceIn(0f, 1f)
    val arrowLocalX = (placement.arrowX - placement.x).roundToInt()

    Column(
        modifier = modifier
            .widthIn(max = maxWidthDp)
            .scale(scale),
    ) {
        // 翻到下方时，尖角在气泡顶部
        if (!placement.above) {
            ArrowRow(above = false, localArrowX = arrowLocalX, tint = InkTokens.bgSurface)
        }

        Box {
            // 墨爆冲击环（0-100ms 墨色冲击，半径 60dp）
            Canvas(modifier = Modifier.matchParentSize()) {
                val ringRadius = 60.dp.toPx() * (1f - progress.value).coerceIn(0f, 1f)
                if (ringRadius > 0f) {
                    drawCircle(
                        color = InkTokens.textPrimary.copy(alpha = 0.12f * alpha),
                        radius = ringRadius,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .background(InkTokens.bgSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (content.phonetic?.isNotBlank() == true) {
                    BasicText(
                        text = content.phonetic,
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 10.sp,
                            color = InkTokens.textSecondary,
                        ),
                    )
                }

                BasicText(
                    text = content.headline.ifBlank { content.token },
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = InkTokens.textPrimary,
                    ),
                )

                // 金线分隔
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    drawLine(
                        color = InkTokens.brandGold.copy(alpha = 0.5f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                BasicText(
                    text = resolveBody(content),
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 13.sp,
                        color = InkTokens.textSecondary,
                        lineHeight = 19.sp,
                    ),
                )

                if (content.candidates.isNotEmpty()) {
                    BasicText(
                        text = "共 ${content.candidates.size} 个义项 ›",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 12.sp,
                            color = InkTokens.colorLink,
                        ),
                    )
                }

                if (content.actions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        content.actions.forEach { action ->
                            ActionChip(label = actionLabel(action)) { onAction(action) }
                        }
                    }
                }
            }
        }

        // 在原文上方时，尖角在气泡底部
        if (placement.above) {
            ArrowRow(above = true, localArrowX = arrowLocalX, tint = InkTokens.bgSurface)
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    InkIconButton(onClick = onClick) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                color = InkTokens.brandCopper,
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ArrowRow(
    above: Boolean,
    localArrowX: Int,
    tint: Color,
) {
    val arrowW = InkTokens.BUBBLE_ARROW_WIDTH
    val arrowH = InkTokens.BUBBLE_ARROW_HEIGHT
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(arrowH),
    ) {
        Canvas(
            modifier = Modifier
                .offset {
                    IntOffset(localArrowX - arrowW.roundToPx() / 2, 0)
                }
                .size(arrowW, arrowH),
        ) {
            val w = arrowW.toPx()
            val h = arrowH.toPx()
            val path = Path().apply {
                if (above) {
                    // 尖角朝下
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w / 2f, h)
                } else {
                    // 尖角朝上
                    moveTo(0f, h)
                    lineTo(w, h)
                    lineTo(w / 2f, 0f)
                }
                close()
            }
            drawPath(path, tint)
        }
    }
}

private fun actionLabel(action: Action): String = when (action) {
    is Action.OpenMap -> "地图"
    is Action.OpenPerson -> "人物"
    is Action.OpenTimeline -> "纪年"
    is Action.OpenRelation -> "关系"
    is Action.SearchWeb -> "联网"
    is Action.AddToNotebook -> "生字本"
}

private fun resolveBody(content: BubbleContent): String {
    if (content.kind == BubbleKind.MISSING) return content.body
    return content.body.ifBlank { content.detail }
}
