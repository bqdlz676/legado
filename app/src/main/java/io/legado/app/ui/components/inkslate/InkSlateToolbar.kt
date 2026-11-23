package io.legado.app.ui.components.inkslate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.rememberInkGesture

/**
 * U7 画板工具栏（切片-12 §8.2）。
 *
 * 顶部一行：画笔 · 直线 · 箭头 · 矩形 · 圆形 · 文字 · 橡皮，全部露出，无「⋯ 更多」；
 * 底部一行：六色圆点 · 线宽 · 撤销 · 重做 · 图层 · 导出，全部露出。
 * 反馈走墨触手势（无 Material ripple），颜色只走 token / [InkPreset]。
 */

private val ToolLabel: Map<InkTool, String> = mapOf(
    InkTool.BRUSH to "画笔",
    InkTool.LINE to "直线",
    InkTool.ARROW to "箭头",
    InkTool.RECT to "矩形",
    InkTool.CIRCLE to "圆形",
    InkTool.TEXT to "文字",
    InkTool.ERASER to "橡皮",
)

/** 线宽三档预设（dp 当量），在 [InkSlatePalette.WIDTH_MIN, WIDTH_MAX] 内。 */
private val widthPresets: List<Float> = listOf(2f, 4f, 8f)

@Composable
fun InkSlateTopBar(
    state: InkSlateState,
    onState: (InkSlateState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkTool.entries.forEach { tool ->
            val selected = state.tool == tool
            val gesture = rememberInkGesture(enabled = true) {
                onState(state.selectTool(tool))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(gesture.gestureModifier),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = ToolLabel[tool] ?: "",
                    style = TextStyle(
                        color = if (selected) ComponentTokens.brandGold
                        else ComponentTokens.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                if (selected) {
                    Canvas(
                        Modifier
                            .size(width = 20.dp, height = 2.dp)
                            .align(Alignment.BottomCenter),
                    ) {
                        drawLine(
                            color = ComponentTokens.brandGold,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InkSlateBottomBar(
    state: InkSlateState,
    onState: (InkSlateState) -> Unit,
    modifier: Modifier = Modifier,
    onExport: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 六色圆点
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InkPreset.bar.forEach { preset ->
                val selected = state.color == preset.argb
                val gesture = rememberInkGesture(enabled = true) {
                    onState(state.selectColor(preset))
                }
                Box(
                    Modifier
                        .size(if (selected) 22.dp else 18.dp)
                        .clip(CircleShape)
                        .background(Color(preset.argb))
                        .then(gesture.gestureModifier),
                )
            }
        }

        // 线宽三档
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            widthPresets.forEach { w ->
                val selected = kotlin.math.abs(state.width - w) < 0.01f
                val gesture = rememberInkGesture(enabled = true) {
                    onState(state.setWidth(w))
                }
                Box(
                    Modifier
                        .size(24.dp)
                        .then(gesture.gestureModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(width = 16.dp, height = 2.dp * w)) {
                        drawLine(
                            color = if (selected) ComponentTokens.brandGold
                            else ComponentTokens.textSecondary,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = w.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                }
            }
        }

        // 撤销 / 重做（不可用时压透明度兜底，不隐藏入口）
        BarAction(
            label = "撤销",
            enabled = state.canUndo,
            onClick = { onState(state.undo()) },
        )
        BarAction(
            label = "重做",
            enabled = state.canRedo,
            onClick = { onState(state.redo()) },
        )

        // 图层切换
        BarAction(
            label = if (state.layer == InkLayer.INK) "墨迹层" else "底图层",
            enabled = true,
            onClick = {
                val next = if (state.layer == InkLayer.INK) InkLayer.BASE else InkLayer.INK
                onState(state.setLayer(next))
            },
        )

        // 导出（PNG/SVG 由宿主层落盘，这里只发回调）
        BarAction(label = "导出", enabled = true, onClick = onExport)
    }
}

@Composable
private fun BarAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val gesture = rememberInkGesture(enabled = enabled, onClick = onClick)
    Box(
        Modifier
            .size(44.dp)
            .then(gesture.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) ComponentTokens.textPrimary
                else ComponentTokens.textTertiary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}
