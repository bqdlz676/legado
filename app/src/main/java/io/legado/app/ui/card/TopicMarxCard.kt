package io.legado.app.ui.card

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 发现页主题卡两类（切片-25 五「马克思/党史卡片」）。
 *
 * [Marx] 马克思人物卡 / [Cpc] 党史卡：规格明确两卡**同尺寸、同布局**，唯一可见差异是边框色
 * （暖金 vs 朱砂），因此共用 [TopicHeroCard]，不在两个 kind 间复制布局代码。
 */
enum class HeroKind { Marx, Cpc }

// 规格例外（验收判据 2：原文指定的边框变体色允许在卡片内落字面量）：马克思卡边框暖金 #D4A54A，同口径 CardTokens.warmGold。
private val MarxBorder = Color(0xFFD4A54A)

// 党史卡边框朱砂 #C23B2E，同口径 CardTokens.cinnabar；两 kind 唯一差异即边框色与随边框走的时间线色，其余布局逐字一致。
private val CpcBorder = Color(0xFFC23B2E)

/** 标题 / 正文宋体兜底：字体子集由字体模块落地，此处统一 Serif，与包内其他卡片口径一致。 */
private val HeroFont = FontFamily.Serif

/**
 * 发现页 · 马克思/党史主题卡（切片-25 五，逐字落地）。
 *
 * 竖排标题 18sp 暖金 Bold；副标题 12sp 石青；年份时间线（Canvas 横线 + 两端年份文本）；
 * 边框 1px；底色毛玻璃 + 深墨 15% 透底。[kind] 只切换边框色，尺寸 / 布局 / 字号完全一致。
 * 阴影 / 圆角 / 五态 / 颗粒全部复用 [CardBase]，本组件不复制阴影代码。
 *
 * @param kind 马克思卡（暖金边）或党史卡（朱砂边）。
 * @param title 竖排主标题；超长按 [verticalTitle] 逐字换行截断。
 * @param subtitle 副标题，石青次级标注。
 * @param yearStart 时间线左端年份（马克思示例 1818）。
 * @param yearEnd 时间线右端年份（马克思示例 1883）。
 * @param modifier 外部给定尺寸 / 对齐；「同尺寸」由调用方修饰符保证，两 kind 共用本组件天然一致。
 * @param state 交互五态，透传基座 [CardBase]。
 */
@Composable
fun TopicHeroCard(
    kind: HeroKind,
    title: String,
    subtitle: String,
    yearStart: String,
    yearEnd: String,
    modifier: Modifier = Modifier,
    state: CardState = CardState.Default,
) {
    // 边框色按 kind 解析：Marx 暖金、Cpc 朱砂；时间线横线同取此色，保证两类卡片视觉语义一致。
    val borderColor = when (kind) {
        HeroKind.Marx -> MarxBorder
        HeroKind.Cpc -> CpcBorder
    }

    CardBase(
        state = state,
        modifier = modifier
            // 1px 边框：基座默认 0 stroke，本卡属规格原文指定边框变体，在外壳层补边线；shape 复用基座圆角 token。
            .border(1.dp, borderColor, RoundedCornerShape(CardTokens.cardRadius)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 深墨 15% 透底：规格五「底色毛玻璃 + 深墨 15% 透底」；压在基座纸白颗粒之上，形成暗调磨砂面。
                .background(CardTokens.ink.copy(alpha = 0.15f))
                .heroFrostBlur(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 内边距 12dp：取基座卡片间距 token，保证文字不贴 1px 边框。
                    .padding(CardTokens.cardGap),
            ) {
                // 竖排标题：18sp 暖金 Bold（规格五）；竖排由逐字换行实现，字体 Serif 兜底。
                BasicText(
                    text = verticalTitle(title),
                    style = TextStyle(
                        fontFamily = HeroFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = CardTokens.warmGold,
                    ),
                )
                // 副标题：12sp 石青（规格五次级标注口径同基座 text12 / stoneBlue）。
                BasicText(
                    text = subtitle,
                    style = TextStyle(
                        fontFamily = HeroFont,
                        fontSize = 12.sp,
                        color = CardTokens.stoneBlue,
                    ),
                )
                // 弹性占位：把时间线压到卡片底部；标题 / 副标题在上，年份轴线在下，两类 kind 共用同一节奏。
                Spacer(Modifier.weight(1f))
                TimelineRow(yearStart = yearStart, yearEnd = yearEnd, lineColor = borderColor)
            }
        }
    }
}

/**
 * 时间线示意行：左端年份 —— Canvas 横线 —— 右端年份（规格五「如 1818 ——— 1883」）。
 * 横线用 [Canvas] 自绘，不用分割线组件，便于两端对齐年份基线；[lineColor] 随 kind 边框色走。
 */
@Composable
private fun TimelineRow(
    yearStart: String,
    yearEnd: String,
    lineColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左端年份：10sp 云灰（规格未给年份字号，取基座最小辅助标注档 text10 / cloudGray）。
        BasicText(
            text = yearStart,
            style = TextStyle(
                fontFamily = HeroFont,
                fontSize = 10.sp,
                color = CardTokens.cloudGray,
            ),
        )
        Canvas(
            modifier = Modifier
                // 横线占满两年份之间的剩余宽度；上下留边距避免与年份文字贴齐。
                .weight(1f)
                .padding(horizontal = CardTokens.cardGap / 2)
                // 线高 1dp：与边框同档细线，规格未指定线粗，按发丝线口径兜底。
                .height(1.dp),
        ) {
            // 横线：贯穿两端年份之间，strokeWidth 取 1dp；颜色随 kind 边框色，0.6 透明度避免抢标题。
            drawLine(
                color = lineColor.copy(alpha = 0.6f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
            )
        }
        // 右端年份：字号 / 颜色同左端，保证轴线两端标注对称。
        BasicText(
            text = yearEnd,
            style = TextStyle(
                fontFamily = HeroFont,
                fontSize = 10.sp,
                color = CardTokens.cloudGray,
            ),
        )
    }
}

/**
 * 竖排标题：逐字以换行连接；超过 6 字截断前五字加省略号（口径同典籍竖卡，规格未另给上限）。
 */
private fun verticalTitle(title: String): String {
    val chars = title.trim().toCharArray()
    return if (chars.size <= 6) chars.joinToString("\n")
    else chars.take(5).joinToString("\n") + "\n…"
}

/**
 * API 31+ 层模糊磨砂（Compose [BlurEffect]）；旧版 / 低端机不挂模糊层，由深墨 15% 半透明底兜底，
 * 不拿纯透明冒充毛玻璃。模糊半径 18dp，口径同发现页其他磨砂卡。
 */
private fun Modifier.heroFrostBlur(): Modifier = this.then(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            val radius = 18.dp.toPx()
            renderEffect = BlurEffect(
                radiusX = radius,
                radiusY = radius,
                edgeTreatment = TileMode.Clamp,
            )
        }
    } else {
        Modifier
    },
)
