package io.legado.app.ui.book.read.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 阅读底栏（契约第五节：底栏 60dp 用 Compose 全吃）。
 *
 * 纸面 [InkTokens.bgSurface] 底 + 顶部细分隔线；进度 = 蚕丝金线 + 朱砂圆点，
 * 点按轨道跳转。按钮统一走 [InkIconButton] 墨触，不写 ripple。
 * 收起态缩成 4dp 金线。
 */
@Composable
fun InkBottomBar(
    visible: Boolean,
    progress: Float,
    onCatalog: () -> Unit,
    onTts: () -> Unit,
    onMenu: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)

    if (!visible) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(InkTokens.brandGold),
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(InkTokens.BOTTOM_BAR_HEIGHT)
            .background(InkTokens.bgSurface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BarAction("目录", onClick = onCatalog)

            ProgressTrack(
                progress = clamped,
                onSeek = onSeek,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )

            BarAction("朗读", onClick = onTts)
            BarAction("更多", onClick = onMenu)
        }

        // 顶部分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(InkTokens.colorDivider)
                .align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun BarAction(label: String, onClick: () -> Unit) {
    InkIconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 10.dp)) {
        Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
            BasicText(
                text = label,
                style = TextStyle(
                    fontFamily = InkFonts.composeFamily,
                    fontSize = 13.sp,
                    color = InkTokens.textSecondary,
                ),
            )
        }
    }
}

@Composable
private fun ProgressTrack(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var trackWidth by remember { mutableStateOf(1f) }
    val dotRadius = 5.dp

    Box(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = if (trackWidth > 0f) {
                        (offset.x / trackWidth).coerceIn(0f, 1f)
                    } else 0f
                    onSeek(fraction)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            onDraw = {
                trackWidth = size.width
                val cy = size.height / 2f
                val lineH = 2.dp.toPx()
                // 蚕丝金线轨道
                drawLine(
                    color = InkTokens.brandGold,
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = lineH,
                )
                // 已读段铜色加深
                drawLine(
                    color = InkTokens.brandCopper,
                    start = Offset(0f, cy),
                    end = Offset(size.width * progress, cy),
                    strokeWidth = lineH,
                )
                // 朱砂圆点浮线上 2dp
                val cx = (trackWidth * progress)
                    .coerceIn(dotRadius.toPx(), trackWidth - dotRadius.toPx())
                drawCircle(
                    color = InkTokens.textAccent,
                    radius = dotRadius.toPx(),
                    center = Offset(cx, cy),
                )
            },
        )
    }
}
