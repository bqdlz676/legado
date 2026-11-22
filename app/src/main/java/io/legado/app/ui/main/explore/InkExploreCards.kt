package io.legado.app.ui.main.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 发现页三种卡片变体
 *
 * 战役横卡（暖金边 + 进度条）/ 典籍竖卡（书名 + 朝代）/ 典故通栏卡（标题 + 典故数，毛玻璃底）。
 * 三种变体共用同一套 chrome 与六态：默认 / 悬浮 / 按压 / 禁用 / 加载 / 空数据（另加离线态）。
 *
 * 不引 MaterialTheme、不挂 Material ripple；文字用 foundation 的 BasicText 自绘，颜色只取 token。
 */

private val CardRadius = RoundedCornerShape(14.dp)
private val ClassicCardRadius = RoundedCornerShape(12.dp)

/** 标题宋体（契约：标题 思源宋体 SemiBold；字体子集由字体模块落地，此处用 Serif 兜底） */
private val TitleFont = FontFamily.Serif
private val BodyFont = FontFamily.Serif

/** 卡片底色按交互态切换：默认微黄面；悬浮叠暖金微光；按压叠朱砂 8%；禁用压透明 */
private fun cardSurface(interaction: InkCardInteraction): Brush = when {
    interaction.disabled -> Brush.linearGradient(
        listOf(InkExploreTokens.BgSurface.copy(alpha = 0.55f), InkExploreTokens.BgPage.copy(alpha = 0.55f))
    )
    interaction.pressed -> Brush.linearGradient(
        listOf(InkExploreTokens.BgSurface, InkExploreTokens.TextAccent.copy(alpha = 0.08f))
    )
    interaction.hovered -> Brush.linearGradient(
        listOf(InkExploreTokens.BgSurface, InkExploreTokens.ColorGlow)
    )
    else -> Brush.linearGradient(listOf(InkExploreTokens.BgSurface, InkExploreTokens.BgPage))
}

private fun cardBorder(interaction: InkCardInteraction): Color = when {
    interaction.disabled -> InkExploreTokens.ColorDivider
    interaction.pressed -> InkExploreTokens.TextAccent.copy(alpha = 0.55f)
    interaction.hovered -> InkExploreTokens.BrandGold
    else -> InkExploreTokens.BrandGold.copy(alpha = 0.35f)
}

/** 自绘文字：不消费 MaterialTheme，颜色 / 字号 / 字距全部显式传入 */
@Composable
private fun InkText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    color: Color,
    fontFamily: FontFamily = BodyFont,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            color = color,
            letterSpacing = letterSpacing,
            textAlign = textAlign ?: TextAlign.Start,
            lineHeight = lineHeight
        ),
        maxLines = maxLines
    )
}

/** 统一卡片外壳：圆角 + 暖金边 + 交互态底色 + 3D 前倾 + 墨触点击 */
@Composable
private fun InkExploreCard(
    interaction: InkCardInteraction,
    modifier: Modifier = Modifier,
    radius: RoundedCornerShape = CardRadius,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(radius)
            .background(cardSurface(interaction))
            .border(1.dp, cardBorder(interaction), radius)
            .tiltLayer(interaction)
            .inkClickable(interaction, onClick)
            .alpha(if (interaction.disabled) 0.45f else 1f)
    ) {
        content()
    }
}

// ————————————————————————————————————————————————
// 变体一：战役精选横卡（横滑吸附 + 暖金边 + 进度条）
// ————————————————————————————————————————————————

@Composable
fun BattleFeaturedCard(
    battle: BattleFeatured,
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val interaction = rememberInkCardInteraction(disabled = !enabled)
    val reveal = rememberRevealState(index)
    InkExploreCard(
        interaction = interaction,
        modifier = modifier.revealLayer(reveal),
        onClick = onClick
    ) {
        Column(Modifier.padding(16.dp, 14.dp, 14.dp, 12.dp)) {
            InkText(
                text = battle.title.ifBlank { "未命名战役" },
                fontSize = 16.sp,
                color = InkExploreTokens.TextPrimary,
                fontFamily = TitleFont,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(7.dp))
            InkText(
                text = battle.era,
                fontSize = 10.sp,
                letterSpacing = 0.08.sp,
                color = InkExploreTokens.BrandCopper
            )
            Spacer(Modifier.height(4.dp))
            InkText(
                text = listOf(battle.place, battle.note)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "战场题注待补" },
                fontSize = 11.sp,
                color = InkExploreTokens.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            ProgressRow(progress = battle.progress, notStartedLabel = "未推演")
            Spacer(Modifier.height(8.dp))
            InkText(
                text = if (battle.campaignKey.isNotEmpty()) "看沙盘 →" else "查看 →",
                fontSize = 10.5.sp,
                color = InkExploreTokens.ColorLink
            )
        }
    }
}

/** 进度条：金线底 + 朱砂填充；未开始显示占位 */
@Composable
private fun ProgressRow(progress: Float, notStartedLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(InkExploreTokens.BrandGold.copy(alpha = 0.25f))
        ) {
            if (progress in 0f..1f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(InkExploreTokens.TextAccent)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        InkText(
            text = if (progress in 0f..1f) "${(progress * 100).toInt()}%" else notStartedLabel,
            fontSize = 10.sp,
            color = InkExploreTokens.TextTertiary
        )
    }
}

// ————————————————————————————————————————————————
// 变体二：典籍竖排栅格卡（竖排书名 + 朝代，stagger 浮入 + 微前倾）
// ————————————————————————————————————————————————

@Composable
fun ClassicGridCard(
    classic: ClassicBook,
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val interaction = rememberInkCardInteraction(disabled = !enabled)
    val reveal = rememberRevealState(index)
    InkExploreCard(
        interaction = interaction,
        modifier = modifier.revealLayer(reveal),
        radius = ClassicCardRadius,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp, 14.dp, 8.dp, 10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InkText(
                text = verticalize(classic.title.ifBlank { "无名典籍" }),
                fontSize = 15.sp,
                letterSpacing = 0.06.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                color = InkExploreTokens.TextPrimary,
                fontFamily = TitleFont,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            InkText(
                text = listOf(classic.dynasty, classic.genre)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "朝代待考" },
                fontSize = 9.sp,
                letterSpacing = 0.1.sp,
                textAlign = TextAlign.Center,
                color = InkExploreTokens.BrandCopper.copy(alpha = 0.9f)
            )
        }
    }
}

/** 竖排：逐字换行；超 6 字取前五字加省略号 */
private fun verticalize(title: String): String {
    val chars = title.trim().toCharArray()
    return if (chars.size <= 6) chars.joinToString("\n")
    else chars.take(5).joinToString("\n") + "\n…"
}

// ————————————————————————————————————————————————
// 变体三：典故专题通栏卡（标题 + 典故数，毛玻璃底 + 暖金边框）
// ————————————————————————————————————————————————

/**
 * 毛玻璃底：上层叠半透明暖色渐变 + 顶部暖光，模拟磨砂。
 * [lowSpec] 对应低端机兜底（内存 <4GB 或核数 <4）：退化为半透明纯色，不做模糊叠层。
 * 真正的 backdrop RenderEffect 模糊需 API 31+ 且要截取背景，留待共享 Sheet 层统一实现。
 */
@Composable
fun TopicBannerCard(
    topic: AllusionTopic,
    index: Int,
    modifier: Modifier = Modifier,
    lowSpec: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val interaction = rememberInkCardInteraction(disabled = !enabled)
    val reveal = rememberRevealState(index)
    val frostBrush = if (lowSpec) {
        Brush.linearGradient(
            listOf(
                InkExploreTokens.BgSurface.copy(alpha = 0.92f),
                InkExploreTokens.BgPage.copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                InkExploreTokens.BgSurface.copy(alpha = 0.72f),
                InkExploreTokens.BgPage.copy(alpha = 0.62f),
                InkExploreTokens.BrandGold.copy(alpha = 0.08f)
            )
        )
    }
    Box(
        modifier = modifier
            .revealLayer(reveal)
            .clip(CardRadius)
            .background(frostBrush)
            .border(1.dp, cardBorder(interaction), CardRadius)
            .tiltLayer(interaction, baseTilt = 0f)
            .inkClickable(interaction, onClick)
            .alpha(if (interaction.disabled) 0.45f else 1f)
    ) {
        Column(Modifier.padding(InkExploreTokens.BannerPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkText(
                    text = topic.title.ifBlank { "未命名专题" },
                    fontSize = 14.sp,
                    color = InkExploreTokens.TextPrimary,
                    fontFamily = TitleFont,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                CountChip(topic.allusionCount)
            }
            Spacer(Modifier.height(5.dp))
            InkText(
                text = topic.preview.take(4).joinToString(" / ").ifEmpty { "典故整理中" },
                fontSize = 10.5.sp,
                letterSpacing = 0.04.sp,
                color = InkExploreTokens.TextTertiary
            )
        }
    }
}

@Composable
private fun CountChip(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(InkExploreTokens.BrandGold.copy(alpha = 0.16f))
            .padding(8.dp, 4.dp)
    ) {
        InkText(
            text = if (count > 0) "${count} 则典故" else "典故待校",
            fontSize = 10.sp,
            color = InkExploreTokens.BrandCopper
        )
    }
}

// ————————————————————————————————————————————————
// 加载骨架 / 空数据态
// ————————————————————————————————————————————————

/** 战役卡骨架（加载态） */
@Composable
fun BattleSkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CardRadius)
            .background(InkExploreTokens.BgSurface)
            .border(1.dp, InkExploreTokens.ColorDivider, CardRadius)
            .padding(16.dp, 14.dp, 14.dp, 12.dp)
    ) {
        Column {
            SkeletonBar(width = 120.dp, height = 16.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBar(width = 80.dp, height = 10.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBar(width = 160.dp, height = 11.dp)
            Spacer(Modifier.height(12.dp))
            SkeletonBar(width = 220.dp, height = 3.dp)
        }
    }
}

/** 典籍卡骨架（加载态） */
@Composable
fun ClassicSkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(ClassicCardRadius)
            .background(InkExploreTokens.BgSurface)
            .border(1.dp, InkExploreTokens.ColorDivider, ClassicCardRadius)
            .padding(8.dp, 14.dp, 8.dp, 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SkeletonBar(width = 28.dp, height = 15.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBar(width = 28.dp, height = 15.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBar(width = 44.dp, height = 9.dp)
        }
    }
}

/** 通栏卡骨架（加载态） */
@Composable
fun TopicSkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CardRadius)
            .background(InkExploreTokens.BgSurface)
            .border(1.dp, InkExploreTokens.ColorDivider, CardRadius)
            .padding(InkExploreTokens.BannerPadding)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBar(height = 14.dp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SkeletonBar(width = 56.dp, height = 18.dp)
            }
            Spacer(Modifier.height(8.dp))
            SkeletonBar(width = 220.dp, height = 10.dp)
        }
    }
}

@Composable
private fun SkeletonBar(
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified
) {
    Box(
        modifier
            .then(modifier)
            .then(if (width == Dp.Unspecified) Modifier else Modifier.width(width))
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(InkExploreTokens.ColorDivider)
    )
}

/**
 * 空数据 / 离线 / 错误兜底态：朱砂短竖 + 引导语 + 可选动作按钮。
 * 任何分区拿不到数据都走这里，不留空白、不崩溃。
 */
@Composable
fun ExploreEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .background(InkExploreTokens.TextAccent)
        )
        Spacer(Modifier.height(12.dp))
        InkText(
            text = title,
            fontSize = 15.sp,
            color = InkExploreTokens.TextSecondary,
            fontFamily = TitleFont,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        InkText(
            text = hint,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = InkExploreTokens.TextTertiary
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            val interaction = rememberInkCardInteraction()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, InkExploreTokens.BrandGold, RoundedCornerShape(10.dp))
                    .inkClickable(interaction, onAction)
                    .padding(16.dp, 8.dp)
            ) {
                InkText(
                    text = actionLabel,
                    fontSize = 12.sp,
                    color = InkExploreTokens.BrandCopper
                )
            }
        }
    }
}
