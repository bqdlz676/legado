package io.legado.app.ui.main.search

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 搜索输入框（切片-13）
 *
 * - 居中一个输入框，**无搜索按钮**；输入框下方一行云灰 12sp 引导字。
 * - 聚焦时输入框浮起（soft shadow + 暖金辉光），底线云灰 1px。
 * - 整框 Canvas 自绘：绢帛底、底线、放大镜、清除叉；不引 MaterialTheme，不用 Material ripple。
 * - 颜色只取 [SearchTokens]。
 */
@Composable
fun SearchInputBar(
    query: String,
    focused: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lift by animateDpAsState(
        targetValue = if (focused) SearchTokens.INPUT_FOCUS_LIFT else 0.dp,
        animationSpec = tween(
            durationMillis = SearchTokens.DURATION_MICRO.toInt(),
            easing = SearchTokens.FOCUS_SPRING,
        ),
        label = "searchInputLift",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SearchTokens.INPUT_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .graphicsLayer { translationY = -lift.toPx() },
        ) {
            InputCanvas(focused = focused, modifier = Modifier.fillMaxSize())

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                // 放大镜由背景 Canvas 绘制在左侧 22dp 处，文本左对齐留出空位。
                Spacer(Modifier.width(44.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = SearchTokens.TEXT_PRIMARY,
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSubmit() },
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        SearchTokens.TEXT_ACCENT,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onFocusChange(it.isFocused) },
                )
                if (query.isNotBlank()) {
                    ClearAffordance(onClear)
                }
                Spacer(Modifier.width(SearchTokens.PAGE_PADDING))
            }
        }

        Spacer(Modifier.height(8.dp))

        // 切片-13：输入框下方一行字（云灰 12sp）。
        androidx.compose.foundation.text.BasicText(
            text = SearchTokens.HINT_GUIDE,
            style = TextStyle(
                color = SearchTokens.TEXT_TERTIARY,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                letterSpacing = 0.04.sp,
            ),
        )
    }
}

/** 输入框自绘层：绢帛底 + 聚焦辉光 + 云灰底线 + 放大镜。 */
@Composable
private fun InputCanvas(focused: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = 12.dp.toPx()

        // 绢帛底面
        drawRoundRect(
            color = SearchTokens.BG_SURFACE,
            cornerRadius = CornerRadius(radius, radius),
        )

        // 聚焦暖金辉光（soft shadow 聚焦时输入框浮起）
        if (focused) {
            drawRoundRect(
                color = SearchTokens.COLOR_GLOW,
                cornerRadius = CornerRadius(radius, radius),
            )
        }

        // 云灰 1px 底线（subtle border）
        val lineWidth = SearchTokens.INPUT_BORDER_WIDTH.toPx()
        drawLine(
            color = if (focused) SearchTokens.TEXT_TERTIARY else SearchTokens.COLOR_DIVIDER,
            start = Offset(0f, h - lineWidth / 2f),
            end = Offset(w, h - lineWidth / 2f),
            strokeWidth = lineWidth,
        )

        // 放大镜：暖金圆环 + 斜柄
        val cx = 22.dp.toPx()
        val cy = h / 2f
        val ring = 7.dp.toPx()
        drawCircle(
            color = SearchTokens.BRAND_COPPER,
            radius = ring,
            center = Offset(cx, cy),
            style = Stroke(width = 1.4.dp.toPx()),
        )
        val handle = Offset(cx + ring * 0.72f, cy + ring * 0.72f)
        drawLine(
            color = SearchTokens.BRAND_COPPER,
            start = handle,
            end = Offset(handle.x + 5.dp.toPx(), handle.y + 5.dp.toPx()),
            strokeWidth = 1.4.dp.toPx(),
        )
    }
}

/** 清除叉：自绘两笔斜线，墨触无 ripple。 */
@Composable
private fun ClearAffordance(onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .searchInkClickable(enabled = true, onClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = 1.3.dp.toPx()
            val pad = 4.dp.toPx()
            drawLine(
                color = SearchTokens.TEXT_TERTIARY,
                start = Offset(pad, pad),
                end = Offset(size.width - pad, size.height - pad),
                strokeWidth = stroke,
            )
            drawLine(
                color = SearchTokens.TEXT_TERTIARY,
                start = Offset(size.width - pad, pad),
                end = Offset(pad, size.height - pad),
                strokeWidth = stroke,
            )
        }
    }
}
