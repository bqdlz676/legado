package io.legado.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * InkDivider 分割线变体（切片-03 设计系统四层表逐字：Divider | 云灰细线 / 金线 / 朱砂线 三种）。
 *
 * 颜色只取 [ComponentTokens]：云灰走 `colorDivider`，金线走 `brandGold`，朱砂线走 `textAccent`；
 * 不在组件内联写 hex。三种变体只承担「视觉分隔强弱」，不参与事件许可判定。
 */
enum class InkDividerVariant {
    /**
     * 云灰细线 · 适用条件：正文内普通分组、列表项之间、表单字段上下的常规分隔；
     * 强度最低，不打断阅读节奏，缺省变体。
     */
    CLOUD_LINE,

    /**
     * 金线 · 适用条件：卷首 / 章节起讫、卡片与正文之间的强调分隔；需要把上下两段内容
     * 在视觉上郑重分开时使用，强度高于云灰线，低于朱砂线。
     */
    GOLD_LINE,

    /**
     * 朱砂线 · 适用条件：落款式强调分隔，如批注边界、危险 / 关键动作分区、归档与待办之间；
     * 强度最高，只在需要标出「此处是边界」时使用，不做常规内分隔。
     */
    CINNABAR_LINE,
}

/**
 * 墨色分割线。
 *
 * @param variant 分割变体，见 [InkDividerVariant] 各档 KDoc 适用条件
 * @param thickness 线宽，云灰细线默认 1dp；金线 / 朱砂线可由调用方加重到 1.5dp
 * @param modifier 布局修饰
 */
@Composable
fun InkDivider(
    variant: InkDividerVariant = InkDividerVariant.CLOUD_LINE,
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
) {
    val color = when (variant) {
        InkDividerVariant.CLOUD_LINE -> ComponentTokens.colorDivider
        InkDividerVariant.GOLD_LINE -> ComponentTokens.brandGold
        InkDividerVariant.CINNABAR_LINE -> ComponentTokens.textAccent
    }
    Canvas(modifier = modifier.fillMaxWidth()) {
        val stroke = thickness.toPx()
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
        )
    }
}
