package io.legado.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * InkProgress 进度线变体（切片-03 设计系统四层表逐字：
 * Progress | 朱砂竖线（书架/阅读页边）/ 蚕丝金线（时间轴）两种）。
 *
 * 颜色只取 [ComponentTokens]：朱砂取 `textAccent`，蚕丝金取 `brandGold`；
 * 轨道取 `colorDivider`，不在组件内联写 hex。进度为确定值，超出 [0,1] 自动夹取。
 */
enum class InkProgressVariant {
    /**
     * 朱砂竖线 · 适用条件：书架 / 阅读页边的阅读进度，沿页面竖向贴着边栏走；
     * 强调「读到哪里」，用朱砂 `textAccent`。不用于时间轴脉络。
     */
    READER_VERTICAL,

    /**
     * 蚕丝金线 · 适用条件：时间轴节点之间的脉络进度，细而克制，用蚕丝金 `brandGold`；
     * 不用于阅读页边的强进度指示。
     */
    TIMELINE_GOLD,
}

/**
 * 墨色线性进度线（竖轴）。
 *
 * @param variant 进度变体，见 [InkProgressVariant] 各档 KDoc 适用条件
 * @param progress 确定进度，自动夹取到 [0f, 1f]
 * @param thickness 线宽，阅读竖线默认 2dp，时间轴金线可调用方收细到 1dp
 * @param modifier 布局修饰；竖线由调用方给足高度
 */
@Composable
fun InkProgress(
    variant: InkProgressVariant,
    progress: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 2.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val activeColor = when (variant) {
        InkProgressVariant.READER_VERTICAL -> ComponentTokens.textAccent
        InkProgressVariant.TIMELINE_GOLD -> ComponentTokens.brandGold
    }
    val trackColor = ComponentTokens.colorDivider
    Canvas(modifier = modifier.fillMaxHeight()) {
        val stroke = thickness.toPx()
        val x = size.width / 2f
        // 轨道：整条竖轴先铺云灰底
        drawLine(
            color = trackColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = stroke,
        )
        if (clamped > 0f) {
            // 已完成段：自上而下按进度占比填主动色
            drawLine(
                color = activeColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height * clamped),
                strokeWidth = stroke,
            )
        }
    }
}
