package io.legado.app.ui.card

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 发现页「战役精选横卡」（切片-25「二、发现页 · 战役精选横卡」逐字落地）。
 *
 * 本文件只做第 2 类横卡：五态 / 阴影 / 圆角 / 颗粒 / 骨架光带全部复用 [CardBase] 与
 * [CardTokens]，不在此另造色值、圆角或动效；卡片自身只负责横排排版、暖金边框、毛玻璃底
 * 与横向 scroll-snap 吸附。
 */

/**
 * 战役横卡数据模型。
 *
 * @param title 战役名，标题横排 16sp 墨色 Bold；适用：卡内最高一级文字。
 * @param subtitle 时期 / 对阵，副标题 12sp 石青；适用：标题下的次级标注行。
 * @param description 一句题注，11sp 云灰；适用：一句话描述，超长单行截断。
 * @param watchedStage 已看阶段数；适用：进度条已读段与「n/总 阶段已看」文案。
 * @param totalStage 总阶段数（如 10）；适用：进度分母，≤0 时卡片按 0 进度兜底，不除零。
 */
data class BattleCardData(
    val title: String,
    val subtitle: String,
    val description: String,
    val watchedStage: Int,
    val totalStage: Int,
)

/**
 * 战役精选横滑列：LazyRow + [rememberSnapFlingBehavior] 实现 scroll-snap 吸附居中。
 *
 * 为什么用官方 snap fling：项目 BOM 2026.06.01 已提供该 API（发现页 bento 栅格同款用法），
 * 松手后把最近一张卡片吸到可视区居中；无需自写 animateScrollToItem 回中。
 *
 * @param battles 战役卡片数据；空列表由调用方给空态兜底，本子组件不渲染空白槽。
 * @param state 基座五态；Loading 态下 [CardBase] 自绘同尺寸骨架灰块 + 暖金斜光带 1.5s 扫过。
 * @param onBattleClick 卡片点击回调；按压态（CardState.Pressed）由交互状态机在外层驱动。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BattleCardRow(
    battles: List<BattleCardData>,
    modifier: Modifier = Modifier,
    state: CardState = CardState.Default,
    onBattleClick: (BattleCardData) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // 吸附甩动行为：与 LazyRow 状态同源，吸附目标即当前列表项；适用：横向一卡一吸。
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)

    LazyRow(
        state = listState,
        flingBehavior = snapFling,
        modifier = modifier,
        // 卡片间距取基座 token 12dp，不另造间距；两侧留同值边距让首尾卡也能吸附到居中位。
        contentPadding = PaddingValues(horizontal = CardTokens.cardGap),
        horizontalArrangement = Arrangement.spacedBy(CardTokens.cardGap),
    ) {
        items(battles, key = { it.title }) { battle ->
            BattleCard(
                data = battle,
                state = state,
                onClick = { onBattleClick(battle) },
            )
        }
    }
}

/**
 * 单张战役精选横卡。
 *
 * 尺寸：宽 = 屏宽 × 0.85，高 96dp（切片-25 原文尺寸）；圆角 / 阴影 / 颗粒走基座，
 * 本组件只在内容层追加规格指定的暖金 1px 边框与毛玻璃底。
 */
@Composable
fun BattleCard(
    data: BattleCardData,
    modifier: Modifier = Modifier,
    state: CardState = CardState.Default,
    onClick: () -> Unit = {},
) {
    // 屏宽取当前配置宽度；0.85f 为规格占比，横卡一屏露出约一张半，留出吸附与相邻卡提示。
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // 卡片外形圆角复用基座 16dp；shape 同时供底色 / 边框 / 裁剪复用，避免各造一个 Shape。
    val shape: Shape = RoundedCornerShape(CardTokens.cardRadius)

    CardBase(
        state = state,
        modifier = modifier
            // 宽=屏宽×0.85：规格横卡宽度；适用：发现页战役精选横滑项。
            .width((screenWidthDp * 0.85f).dp)
            // 高 96dp：规格横卡固定高度；适用：横排四行信息（标题 / 副标题 / 描述 / 进度）。
            .height(96.dp)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 毛玻璃底层：纸白 80% 半透明 + 8dp 模糊；低版本降级为纯半透明纸白（见下方注释）。
            Box(
                Modifier
                    .fillMaxSize()
                    .battleGlassBackdrop(shape)
            )
            // 边框独立叠在模糊层之上：避免模糊把暖金 1px 边线柔化虚掉。
            Box(
                Modifier
                    .fillMaxSize()
                    // 暖金 #D4A54A 1px 边框：规格原文指定战役横卡为少数带边框变体；色值取 token 暖金。
                    .border(1.dp, CardTokens.warmGold, shape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 内边距 14dp 横向 / 12dp 纵向：96dp 高内排四行不贴边；规格未给，基座兜底值。
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 标题行：标题横排 16sp 墨色 Bold + 右箭头；箭头与标题同色同级，表达可点进详情。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = data.title,
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            // 16sp：规格战役横卡标题字号；适用：卡内一级标题，与 CardTokens.text16 同值。
                            fontSize = 16.sp,
                            color = CardTokens.ink,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        text = "→",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CardTokens.ink,
                        ),
                    )
                }

                // 云灰细线 1px：标题与下方信息分隔；色取云灰，细线不抢标题层级。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CardTokens.cloudGray)
                )

                // 副标题 12sp 石青：时期 / 对阵等次级标注；适用：标题下第一行说明。
                BasicText(
                    text = data.subtitle,
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        // 12sp：规格副标题字号；与 CardTokens.text12 同值。
                        fontSize = 12.sp,
                        color = CardTokens.stoneBlue,
                    ),
                )

                // 一句话描述 11sp 云灰；适用：题注长句，单行截断不撑破 96dp 卡高。
                BasicText(
                    text = data.description,
                    maxLines = 1,
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        // 11sp：规格一句话描述字号；与 CardTokens.text11 同值。
                        fontSize = 11.sp,
                        color = CardTokens.cloudGray,
                    ),
                )

                // 进度行：朱砂已读段 + 云灰未读轨 + 「n/总 阶段已看」文案。
                val progress = if (data.totalStage <= 0) {
                    // 总阶段缺失按 0 进度兜底：不除零、不留空条；适用：年份 / 进度缺数据的边界情况。
                    0f
                } else {
                    (data.watchedStage.toFloat() / data.totalStage).coerceIn(0f, 1f)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            // 轨道高 3dp：规格未给进度条粗细，取基座兜底细线；适用：横卡底部进度轨。
                            .height(3.dp)
                            .background(CardTokens.cloudGray, shape)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                // 已读段取朱砂：token 语义即「已读进度」；适用：进度条完成部分。
                                .background(CardTokens.cinnabar, shape)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "${data.watchedStage}/${data.totalStage} 阶段已看",
                        style = TextStyle(
                            // 10sp：进度说明文字低于描述行；规格未给字号，取 token text10 辅助标注档。
                            fontSize = 10.sp,
                            color = CardTokens.cloudGray,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 战役横卡毛玻璃底层：纸白 80% + backdrop-blur 8dp。
 *
 * 模糊实现：API 31（S）及以上走 [Modifier.blur] RenderEffect 高斯模糊，半径 8.dp 对应规格 8px；
 * API 31 以下无 RenderEffect，降级为半透明纸白底，不伪造模糊采样（与 InkHalfSheet 同一口径）。
 * 注意：Compose 的 blur 模糊的是图层自身内容，真正的窗口级 backdrop 采样由窗口层统一 mediate，
 * 此处半透明纸白 0.8f 先承担「透出底层」的视觉职责。
 */
private fun Modifier.battleGlassBackdrop(shape: Shape): Modifier {
    val supportsRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return this
        // 纸白 80%：规格「纸白 80%」；0.8f 透明度让底层内容隐约透出，避免实心白块压过纸感。
        .background(CardTokens.cardPaper.copy(alpha = 0.8f), shape)
        .then(
            if (supportsRenderEffect) {
                // 8.dp 模糊半径：规格 backdrop-blur 8px；适用：API31+ 毛玻璃柔化底层。
                Modifier.blur(8.dp)
            } else {
                // 低版本降级：仅保留半透明纸白；适用：API < 31 机型，不崩、不伪造模糊。
                Modifier
            },
        )
}
