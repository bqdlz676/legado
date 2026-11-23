package io.legado.app.ui.main.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 搜索页兜底态（加载 / 空 / 失败 / 离线）
 *
 * - 加载：三个云灰小点依次亮灭 600ms 循环，不放菊花（切片-15）。
 * - 空 / 失败 / 离线：自绘图标 + 一行兜底文案 + 可选重试按钮；文案为空回落默认引导，不留白。
 * - 离线 / 失败态按钮为「重试」；墨触无 ripple，颜色只取 [SearchTokens]。
 */
@Composable
fun SearchLoadingDots(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Dot(index = index)
            if (index < 2) Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun Dot(index: Int) {
    val alpha = remember { Animatable(0.25f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 600
                    0.25f at (index * 150)
                    1f at (index * 150 + 150)
                    0.25f at (index * 150 + 300)
                },
                repeatMode = RepeatMode.Restart,
            ),
        )
    }
    Canvas(Modifier.size(8.dp)) {
        drawCircle(
            color = SearchTokens.TEXT_TERTIARY.copy(alpha = alpha.value),
            radius = size.minDimension / 2f,
        )
    }
}

/**
 * 兜底正文：图标 + 文案 + 可选操作按钮。
 *
 * @param message 兜底文案；空白回落 [SearchTokens.HINT_GUIDE]
 * @param offline 离线态：图标加古铜环，按钮默认「重试」
 * @param error 失败态：图标朱砂落款点
 * @param actionLabel 按钮文案；null 不渲染按钮
 */
@Composable
fun SearchFallbackBody(
    message: String,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    error: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val guide = message.ifBlank { SearchTokens.HINT_GUIDE }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchTokens.PAGE_PADDING, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FallbackIcon(offline = offline, error = error)
        Spacer(Modifier.height(16.dp))
        androidx.compose.foundation.text.BasicText(
            text = guide,
            style = TextStyle(
                color = SearchTokens.TEXT_TERTIARY,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
            ),
        )
        if (!actionLabel.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            RetryButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun FallbackIcon(offline: Boolean, error: Boolean) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                color = SearchTokens.BG_SURFACE,
                radius = size.minDimension / 2f,
            )
            drawCircle(
                color = SearchTokens.BRAND_GOLD,
                radius = 7f,
                center = Offset(cx, cy - 4f),
            )
            if (error) {
                drawCircle(
                    color = SearchTokens.TEXT_ACCENT,
                    radius = 3.5f,
                    center = Offset(cx + 10f, cy + 8f),
                )
            }
            if (offline) {
                drawCircle(
                    color = SearchTokens.BRAND_COPPER,
                    radius = 16f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f),
                )
            }
        }
    }
}

/** 重试按钮：绢帛底 + 朱砂字，墨触无 ripple。 */
@Composable
private fun RetryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SearchTokens.BG_SURFACE, RoundedCornerShape(16.dp))
            .searchInkClickable(enabled = true, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = SearchTokens.TEXT_ACCENT,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
        )
    }
}
