package io.legado.app.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 名物索引卡片（类别入口，切片-25「七、名物索引卡片」逐字落地）。
 *
 * 规格原文：类别名竖排 16sp 墨色 Bold + 右箭头；条目数 12sp 石青；云灰细线；
 * 代表条目预览 10sp 云灰横排（如 干将 · 莫邪 · 赤霄 · 湛卢 …）；高 72dp；点击进类别列表。
 *
 * 五态 / 阴影 / 圆角 / 颗粒一律走 [CardSurface]，色值全部取 [CardTokens]，本组件不另造
 * 色值、圆角或动效；本组件只补竖排类别名、条目数、云灰细线与代表条目预览横排。
 */

/**
 * 单张名物索引卡片（类别入口）。
 *
 * 为什么固定高 72dp：规格明确「高 72dp」，与条目数 / 预览行数解耦，列表滑动时行高一致；
 * 点击整卡进对应类别列表页，路由由宿主处理，本组件不做跳转。
 *
 * @param name 类别名；空名仍渲染卡片骨架，避免列表项塌缩。
 * @param count 条目数；负数 / 脏数据按 0 兜底，不出现「-3 条目」这类不可读文案。
 * @param previewItems 代表条目预览；空白条目先过滤，>4 项截断加「…」。
 * @param state 基座五态；Loading 态由 [CardSurface] 自绘骨架灰块 + 暖金斜光带。
 * @param icon 类别图标插槽；17 类图标已单独落地，调用方按需传入，null 时不占槽位。
 * @param modifier 外部对齐 / 宽度；高度由本卡按规格固定。
 * @param onClick 整卡点击回调；适用：跳转该类别索引列表页。
 */
@Composable
fun CategoryCard(
    name: String,
    count: Int,
    previewItems: List<String>,
    onClick: () -> Unit,
    state: CardState = CardState.Default,
    icon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 条目数兜底：负数 / 脏数据按 0 展示；适用：不出现「-3 条目」这类不可读文案。
    val safeCount = count.coerceAtLeast(0)

    // 代表条目预览：先剔除空白项；超过 4 个只取前 4，末尾补「…」表达还有后续条目。
    val visiblePreviews = previewItems.filter { it.isNotBlank() }
    val previewLine = if (visiblePreviews.size > 4) {
        visiblePreviews.take(4).joinToString(" · ") + " …"
    } else {
        visiblePreviews.joinToString(" · ")
    }

    CardSurface(
        state = state,
        modifier = modifier
            // 高 72dp：规格名物索引卡固定高度；适用：类别入口列表统一行高。
            .height(72.dp)
            // 整卡可点：点击进类别列表；按压态阴影由基座五态映射，不在此另写 ripple。
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // 内边距横向 14dp / 纵向 10dp：72dp 高内排竖排名 + 两行信息不贴边；规格未给，基座兜底值。
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 类别图标插槽：17 类图标由宿主单独落地；null 不渲染，也不预留 12dp 槽宽。
            if (icon != null) {
                icon()
                Spacer(Modifier.width(12.dp))
            }

            // 类别名竖排：16sp 墨色 Bold；逐字自上而下堆叠，与规格「竖排」口径一致。
            CategoryVerticalText(
                text = name,
                // 16sp：规格类别名字号；适用：卡内一级标题，与 CardTokens.text16 同值。
                fontSize = 16.sp,
                color = CardTokens.ink,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.width(10.dp))

            // 中部信息列：条目数 → 云灰细线 → 代表条目预览，占据竖排名右侧剩余宽度。
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 条目数 12sp 石青：规格次级标注档；适用：类别规模提示。
                BasicText(
                    text = "$safeCount 条目",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        // 12sp：规格条目数字号；与 CardTokens.text12 同值。
                        fontSize = 12.sp,
                        color = CardTokens.stoneBlue,
                    ),
                )

                // 云灰细线 1px：分隔条目数与预览行；色取云灰，细线不抢标题层级。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CardTokens.cloudGray),
                )

                // 代表条目预览 10sp 云灰横排：超长单行截断；适用：「干将 · 莫邪 · 赤霄 · 湛卢 …」口径。
                BasicText(
                    text = previewLine,
                    maxLines = 1,
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        // 10sp：规格预览字号；与 CardTokens.text10 同值。
                        fontSize = 10.sp,
                        color = CardTokens.cloudGray,
                    ),
                )
            }

            // 右箭头：与类别名同级墨色；适用：表达整卡可点进类别列表。
            BasicText(
                text = "→",
                style = TextStyle(
                    // 16sp：箭头跟随类别名档；不另起字号层级。
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CardTokens.ink,
                ),
            )
        }
    }
}

/**
 * 竖排文字：逐字自上而下堆叠成一列。
 *
 * 为什么不用整体旋转 90°：旋转会让汉字侧倒；规格要求竖排但字形正立，故逐字 Column 堆叠，
 * 与专题横条卡竖排标题同口径。适用：仅类别名竖排，横排标注不走本 helper。
 *
 * @param text 类别名；逐字渲染，空串时只留空列不崩。
 * @param fontSize 字号；适用：由调用方按规格档位传入，helper 内不自造字号。
 * @param color 文字色；适用：取基座语义色，不在此另造色值。
 * @param fontWeight 字重；适用：类别名传 [FontWeight.Bold]。
 */
@Composable
private fun CategoryVerticalText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        text.forEach { ch ->
            BasicText(
                text = ch.toString(),
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    color = color,
                    fontWeight = fontWeight,
                    fontSize = fontSize,
                    // 行高 = 字号 × 1.25f：竖排字字距不挤也不松；规格未给行距，取兜底值。
                    lineHeight = fontSize * 1.25f,
                ),
            )
        }
    }
}
