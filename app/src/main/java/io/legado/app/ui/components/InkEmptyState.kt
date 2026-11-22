package io.legado.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * EmptyState 空状态：图标（纸白圆 + 暖金点 + 朱砂落款式红点）+ 一行引导文字 + 可选操作按钮。
 *
 * 覆盖空数据与离线两态：离线时说明文案换成缓存兜底，操作按钮默认为「重试」。
 * 文案为空时回落到「暂无内容」，不允许空白态。
 */
@Composable
fun InkEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    compact: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val guide = message.ifBlank { if (offline) "当前离线，展示缓存内容" else "暂无内容" }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (compact) 12.dp else 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIcon(offline = offline, compact = compact)
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        androidx.compose.foundation.text.BasicText(
            text = guide,
            style = TextStyle(
                color = ComponentTokens.textTertiary,
                fontSize = if (compact) 13.sp else 14.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        val label = actionLabel ?: if (offline) "重试" else null
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            InkButton(
                text = label,
                onClick = onAction,
                variant = InkButtonVariant.SECONDARY,
                offline = offline,
            )
        }
    }
}

@Composable
private fun EmptyIcon(offline: Boolean, compact: Boolean) {
    val iconSize = if (compact) 44.dp else 64.dp
    Box(
        Modifier
            .size(iconSize)
            .clip(RoundedCornerShape(50))
            .background(ComponentTokens.bgPage),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(iconSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // 暖金点（落文视觉签名，8dp 等比缩放）
            drawCircle(
                color = ComponentTokens.brandGold,
                radius = if (compact) 5f else 7f,
                center = Offset(cx, cy - 4f),
            )
            // 朱砂落款红点
            drawCircle(
                color = ComponentTokens.textAccent,
                radius = if (compact) 2.5f else 3.5f,
                center = Offset(cx + 10f, cy + 8f),
            )
            if (offline) {
                // 离线：古铜小环
                drawCircle(
                    color = ComponentTokens.brandCopper,
                    radius = if (compact) 12f else 16f,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
                )
            }
        }
    }
}
