package io.legado.app.ui.main.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · U8 搜索页（切片-13，Compose 自绘）
 *
 * 规格原文：居中一个输入框，无搜索按钮；输入框下方一行云灰 12sp 引导字；
 * 输入时下方渐显结果列表，匹配文字片段朱砂高亮，纯文本列表不配图。
 *
 * 边界兜底（一律非空白态、不崩溃、可重试）：
 * - 查询为空 → 历史 + 热门面板；
 * - 无结果 → 空态引导文案；
 * - 网络断开 → 缓存结果 + 离线文案 + 重试；
 * - 检索失败 → 缓存结果 + 失败文案 + 重试。
 *
 * 整页不引 MaterialTheme，颜色只取 [SearchTokens]；不使用 Material ripple。
 *
 * @param state 整页状态（归约器产出）
 * @param onQueryChange 输入文本变化
 * @param onSubmit 提交检索（回车 / 词条点选）
 * @param onFocusChange 输入框焦点变化
 * @param onClearQuery 清空输入框
 * @param onPickHistory 点选历史 / 热门词条
 * @param onRemoveHistory 删除单条历史
 * @param onClearHistory 清空历史
 * @param onItemClick 点开一条结果
 * @param onRetry 离线 / 失败态重试
 * @param onBack 返回入口（可选；无返回入口时不渲染箭头）
 */
@Composable
fun InkSearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onClearQuery: () -> Unit,
    onPickHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onItemClick: (SearchResultItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SearchTokens.BG_PAGE),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SearchTokens.PAGE_PADDING),
        ) {
            HeaderBar(onBack)

            SearchInputBar(
                query = state.query,
                focused = state.focused,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onFocusChange = onFocusChange,
                onClear = onClearQuery,
            )

            Spacer(Modifier.height(12.dp))

            Crossfade(
                targetState = state.loadState,
                animationSpec = tween(
                    durationMillis = SearchTokens.DURATION_PAGE.toInt(),
                    easing = SearchTokens.PAGE_TURN,
                ),
                label = "searchStateCrossfade",
                modifier = Modifier.weight(1f),
            ) { target ->
                when (target) {
                    is SearchLoadState.Idle -> IdleBody(
                        state = state,
                        onPick = onPickHistory,
                        onRemove = onRemoveHistory,
                        onClearHistory = onClearHistory,
                    )

                    is SearchLoadState.Loading -> LoadingBody()

                    is SearchLoadState.Content -> ResultBody(
                        results = target.results,
                        query = state.query,
                        blocking = false,
                        onItemClick = onItemClick,
                    )

                    is SearchLoadState.Empty -> SearchFallbackBody(
                        message = SearchTokens.EMPTY_MESSAGE,
                    )

                    is SearchLoadState.Error -> FallbackWithCache(
                        message = target.message.ifBlank { SearchTokens.ERROR_MESSAGE },
                        offline = false,
                        cached = target.cached,
                        onRetry = onRetry,
                        onItemClick = onItemClick,
                    )

                    is SearchLoadState.Offline -> FallbackWithCache(
                        message = SearchTokens.OFFLINE_MESSAGE,
                        offline = true,
                        cached = target.cached,
                        onRetry = onRetry,
                        onItemClick = onItemClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .searchInkClickable(enabled = true, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(18.dp)) {
                    val stroke = 1.6.dp.toPx()
                    val midY = size.height / 2f
                    drawLine(
                        color = SearchTokens.TEXT_PRIMARY,
                        start = Offset(size.width * 0.72f, midY - size.height * 0.28f),
                        end = Offset(size.width * 0.28f, midY),
                        strokeWidth = stroke,
                    )
                    drawLine(
                        color = SearchTokens.TEXT_PRIMARY,
                        start = Offset(size.width * 0.28f, midY),
                        end = Offset(size.width * 0.72f, midY + size.height * 0.28f),
                        strokeWidth = stroke,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .background(SearchTokens.TEXT_ACCENT),
            )
            Spacer(Modifier.width(12.dp))
        }
        androidx.compose.foundation.text.BasicText(
            text = "检 索",
            style = TextStyle(
                color = SearchTokens.TEXT_PRIMARY,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            ),
        )
    }
}

@Composable
private fun IdleBody(
    state: SearchUiState,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SearchHistoryPanel(
            history = state.history,
            hot = state.hot,
            onPick = onPick,
            onRemove = onRemove,
            onClearHistory = onClearHistory,
        )
    }
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SearchLoadingDots()
    }
}

@Composable
private fun ResultBody(
    results: List<SearchResultItem>,
    query: String,
    blocking: Boolean,
    onItemClick: (SearchResultItem) -> Unit,
) {
    SearchResultList(
        results = results,
        query = query,
        blocking = blocking,
        onItemClick = onItemClick,
        modifier = Modifier.fillMaxSize(),
    )
}

/** 离线 / 失败：兜底文案 + 重试；有缓存结果时在下方列出（不可点击进入详情）。 */
@Composable
private fun FallbackWithCache(
    message: String,
    offline: Boolean,
    cached: List<SearchResultItem>,
    onRetry: () -> Unit,
    onItemClick: (SearchResultItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchFallbackBody(
            message = message,
            offline = offline,
            error = !offline,
            actionLabel = SearchTokens.RETRY_LABEL,
            onAction = onRetry,
        )
        if (cached.isNotEmpty()) {
            ResultBody(
                results = cached,
                query = "",
                blocking = true,
                onItemClick = onItemClick,
            )
        }
    }
}
