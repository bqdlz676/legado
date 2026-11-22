package io.legado.app.ui.main.explore

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 发现页入口（可复用 Composable）
 *
 * 主控 merge 时：在 Fragment/Activity 里用 ComposeView 挂本函数，把 ViewModel 映射成
 * [ExploreLoadState] 传入；点击回调接回原有页面（沙盘 / 搜索 / 典故 Sheet）。
 *
 * 整页不引 MaterialTheme：纸白底、自绘标题、token 上色；状态切换走 PageMs 平滑过渡。
 *
 * @param state    整页加载态（加载 / 内容 / 空 / 错误 / 离线）
 * @param lowSpec  低端机兜底：毛玻璃退化为半透明纯色（内存 <4GB 或核数 <4 时由外部判定传入）
 */
@Composable
fun InkExploreScreen(
    state: ExploreLoadState,
    modifier: Modifier = Modifier,
    lowSpec: Boolean = false,
    onBattleClick: (BattleFeatured) -> Unit = {},
    onClassicClick: (ClassicBook) -> Unit = {},
    onTopicClick: (AllusionTopic) -> Unit = {},
    onRetry: () -> Unit = {},
    onSearchClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(InkExploreTokens.BgPage)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HeaderBar(onSearchClick)
            Crossfade(
                targetState = state,
                animationSpec = tween(
                    durationMillis = InkExploreTokens.PageMs.toInt(),
                    easing = InkExploreTokens.PageTurn
                ),
                label = "exploreStateCrossfade"
            ) { target ->
                when (target) {
                    ExploreLoadState.Loading -> LoadingBody()

                    is ExploreLoadState.Content -> {
                        if (target.battles.isEmpty() &&
                            target.classics.isEmpty() &&
                            target.topics.isEmpty()
                        ) {
                            FullEmptyBody(onRetry)
                        } else {
                            ContentBody(
                                state = target,
                                lowSpec = lowSpec,
                                onBattleClick = onBattleClick,
                                onClassicClick = onClassicClick,
                                onTopicClick = onTopicClick
                            )
                        }
                    }

                    ExploreLoadState.Empty -> FullEmptyBody(onRetry)

                    is ExploreLoadState.Error -> ExploreEmptyState(
                        title = "加载失败",
                        hint = target.message.ifEmpty { "数据暂时取不到，可重试一次" },
                        actionLabel = "重试",
                        onAction = onRetry
                    )

                    ExploreLoadState.Offline -> ExploreEmptyState(
                        title = "网络断开",
                        hint = "已为你保留本地典藏，联网后更新专题与典故",
                        actionLabel = "重试",
                        onAction = onRetry
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 页首：朱砂短竖 + 「发 现」宋体标题，右侧检索入口（可选） */
@Composable
private fun HeaderBar(onSearchClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkExploreTokens.PagePadding, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(22.dp)
                .background(InkExploreTokens.TextAccent)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                text = "发 现",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = InkExploreTokens.TextPrimary
                )
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = "策展 · 战役 / 典籍 / 典故",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 10.sp,
                    letterSpacing = 0.12.sp,
                    color = InkExploreTokens.TextTertiary
                )
            )
        }
        if (onSearchClick != null) {
            val interaction = rememberInkCardInteraction()
            Box(
                modifier = Modifier
                    .inkClickable(interaction, onSearchClick)
                    .padding(8.dp, 4.dp)
            ) {
                BasicText(
                    text = "检索 →",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 12.sp,
                        color = InkExploreTokens.BrandCopper
                    )
                )
            }
        }
    }
}

@Composable
private fun ContentBody(
    state: ExploreLoadState.Content,
    lowSpec: Boolean,
    onBattleClick: (BattleFeatured) -> Unit,
    onClassicClick: (ClassicBook) -> Unit,
    onTopicClick: (AllusionTopic) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BattleSection(battles = state.battles, onBattleClick = onBattleClick)
        ClassicSection(classics = state.classics, onClassicClick = onClassicClick)
        TopicSection(
            topics = state.topics,
            lowSpec = lowSpec,
            onTopicClick = onTopicClick
        )
    }
}

/** 加载骨架：三套卡片各出占位，节奏与正式栅格一致 */
@Composable
private fun LoadingBody() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            SectionHeaderSkeleton()
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = InkExploreTokens.PagePadding
                ),
                horizontalArrangement = Arrangement.spacedBy(InkExploreTokens.CardGap)
            ) {
                items(3) {
                    BattleSkeletonCard(modifier = Modifier.width(262.dp))
                }
            }
        }
        Column {
            SectionHeaderSkeleton()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = InkExploreTokens.PagePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    ClassicSkeletonCard(modifier = Modifier.weight(1f).height(112.dp))
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = InkExploreTokens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(InkExploreTokens.CardGap)
        ) {
            repeat(2) { TopicSkeletonCard(modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun SectionHeaderSkeleton() {
    Row(
        modifier = Modifier.padding(horizontal = InkExploreTokens.PagePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(InkExploreTokens.ColorDivider)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(72.dp)
                .height(15.dp)
                .background(InkExploreTokens.ColorDivider)
        )
    }
}

@Composable
private fun FullEmptyBody(onRetry: () -> Unit) {
    ExploreEmptyState(
        title = "发现页暂无内容",
        hint = "典籍与典故正在汇编，稍后再来翻看",
        actionLabel = "刷新",
        onAction = onRetry
    )
}
