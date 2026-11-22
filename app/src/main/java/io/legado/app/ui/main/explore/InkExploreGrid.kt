package io.legado.app.ui.main.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 发现页 bento 分区栅格
 *
 * 三区节奏不同：战役精选横滑吸附（LazyRow + SnapFlingBehavior）；
 * 典籍竖排三列栅格（逐行 stagger 浮入）；典故专题通栏列（毛玻璃）。
 * 每个分区独立兜底空态，整页没有数据缺口。
 */

private val BattleCardWidth = 262.dp
private val ClassicCardHeight = 112.dp

// ————————————————————————————————————————————————
// 分区标题：朱砂短竖 + 宋体标题 + 条数
// ————————————————————————————————————————————————

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(InkExploreTokens.TextAccent)
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = InkExploreTokens.TextPrimary
            )
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = "$count",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 11.sp,
                    color = InkExploreTokens.TextTertiary
                )
            )
        }
    }
}

// ————————————————————————————————————————————————
// 分区一：战役精选（横滑 + 滚动吸附 + 暖金边 + 进度条）
// ————————————————————————————————————————————————

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BattleSection(
    battles: List<BattleFeatured>,
    modifier: Modifier = Modifier,
    onBattleClick: (BattleFeatured) -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader("战役精选", battles.size)
        Spacer(Modifier.height(12.dp))
        if (battles.isEmpty()) {
            SectionEmptyHint("暂无战役精选，沙盘正在整理")
            return@Column
        }
        val listState = rememberLazyListState()
        val snap = rememberSnapFlingBehavior(lazyListState = listState)
        LazyRow(
            state = listState,
            flingBehavior = snap,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = InkExploreTokens.PagePadding
            ),
            horizontalArrangement = Arrangement.spacedBy(InkExploreTokens.CardGap)
        ) {
            itemsIndexed(battles) { index, battle ->
                BattleFeaturedCard(
                    battle = battle,
                    index = index,
                    modifier = Modifier.width(BattleCardWidth),
                    onClick = { onBattleClick(battle) }
                )
            }
        }
    }
}

// ————————————————————————————————————————————————
// 分区二：典籍竖排栅格（stagger 逐行浮入 + 微前倾）
// ————————————————————————————————————————————————

@Composable
fun ClassicSection(
    classics: List<ClassicBook>,
    modifier: Modifier = Modifier,
    onClassicClick: (ClassicBook) -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader("典籍", classics.size)
        Spacer(Modifier.height(12.dp))
        if (classics.isEmpty()) {
            SectionEmptyHint("典籍目录正在汇校")
            return@Column
        }
        classics.chunked(3).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkExploreTokens.PagePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEachIndexed { colIndex, book ->
                    val globalIndex = rowIndex * 3 + colIndex
                    ClassicGridCard(
                        classic = book,
                        index = globalIndex,
                        modifier = Modifier
                            .weight(1f)
                            .height(ClassicCardHeight),
                        onClick = { onClassicClick(book) }
                    )
                }
                // 行末不足三张时补空 weight，保持栅格对齐
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ————————————————————————————————————————————————
// 分区三：典故专题通栏（毛玻璃底 + 暖金边框 + 典故数）
// ————————————————————————————————————————————————

@Composable
fun TopicSection(
    topics: List<AllusionTopic>,
    modifier: Modifier = Modifier,
    lowSpec: Boolean = false,
    onTopicClick: (AllusionTopic) -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader("典故专题", topics.size)
        Spacer(Modifier.height(12.dp))
        if (topics.isEmpty()) {
            SectionEmptyHint("典故专题待汇编")
            return@Column
        }
        Column(
            modifier = Modifier.padding(horizontal = InkExploreTokens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(InkExploreTokens.CardGap)
        ) {
            topics.forEachIndexed { index, topic ->
                TopicBannerCard(
                    topic = topic,
                    index = index,
                    lowSpec = lowSpec,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTopicClick(topic) }
                )
            }
        }
    }
}

// ————————————————————————————————————————————————
// 分区内空态：一行暖金短划 + 辅助文案（不空白、不崩溃）
// ————————————————————————————————————————————————

@Composable
private fun SectionEmptyHint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkExploreTokens.PagePadding, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(14.dp)
                .height(1.dp)
                .background(InkExploreTokens.BrandGold.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 11.sp,
                color = InkExploreTokens.TextTertiary
            )
        )
    }
}
