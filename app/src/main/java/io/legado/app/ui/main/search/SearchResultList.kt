package io.legado.app.ui.main.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 搜索结果列表（切片-13：纯文本列表，不配图；命中片段朱砂高亮）
 *
 * - 条目：书名（高亮）+ 作者 / 正文片段副行；条目间云灰细线分隔。
 * - 入场 stagger 渐显；墨触无 ripple；[blocking] 时（加载 / 离线 / 失败）不响应点击。
 * - 滚动到底触发 [onLoadMore]；底部脚注区分在途 / 失败可重试 / 已收口三态。
 * - 结果为空时内部兜底引导文案，不允许空白态。
 */
@Composable
fun SearchResultList(
    results: List<SearchResultItem>,
    query: String,
    blocking: Boolean,
    onItemClick: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier,
    paging: SearchPagingState = SearchPagingState.Idle,
    hasMore: Boolean = true,
    onLoadMore: () -> Unit = {},
    onRetryLoadMore: () -> Unit = {},
) {
    if (results.isEmpty()) {
        SearchListFallback(modifier = modifier)
        return
    }

    val listState = rememberLazyListState()

    // 到底监听：最后可见条目贴底即上报一次；是否真发请求由 ViewModel 守卫
    // （canLoadMore + 去抖 + 在途并发保护），此处连发不会穿透成重复请求。
    LaunchedEffect(listState, paging, hasMore) {
        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                val total = info.totalItemsCount
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                if (total > 0 && lastVisible >= total - 1) onLoadMore()
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SearchTokens.ROW_GAP),
    ) {
        itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
            val reveal = rememberSearchRevealState(index)
            SearchResultRow(
                item = item,
                query = query,
                blocking = blocking,
                onClick = { onItemClick(item) },
                modifier = Modifier.searchRevealLayer(reveal),
            )
        }
        item(key = "paging-footer") {
            PagingFooter(
                paging = paging,
                onRetry = onRetryLoadMore,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    item: SearchResultItem,
    query: String,
    blocking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .searchInkClickable(enabled = !blocking, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = SearchTokens.PAGE_PADDING),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = highlightedTitle(item.title, query),
            style = androidx.compose.ui.text.TextStyle(
                color = SearchTokens.TEXT_PRIMARY,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
            ),
        )

        val author = item.author.trim()
        val snippet = item.snippet.trim()
        if (author.isNotEmpty() || snippet.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.text.BasicText(
                text = listOf(author, snippet).filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = androidx.compose.ui.text.TextStyle(
                    color = SearchTokens.TEXT_SECONDARY,
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                ),
                maxLines = 2,
            )
        }

        Spacer(Modifier.height(10.dp))
        // 条目间云灰细线（自绘，不用 Material divider）。
        Canvas(Modifier.fillMaxWidth().height(SearchTokens.INPUT_BORDER_WIDTH)) {
            drawLine(
                color = SearchTokens.COLOR_DIVIDER,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = SearchTokens.INPUT_BORDER_WIDTH.toPx(),
            )
        }
    }
}

/** 书名高亮：命中片段朱砂，其余墨色；查询为空时整段墨色。 */
private fun highlightedTitle(title: String, query: String): AnnotatedString =
    buildAnnotatedString {
        SearchHighlight.split(title, query).forEach { seg ->
            val color: Color = if (seg.highlighted) {
                SearchTokens.TEXT_ACCENT
            } else {
                SearchTokens.TEXT_PRIMARY
            }
            withStyle(SpanStyle(color = color)) { append(seg.text) }
        }
    }

/** 列表底部分页脚注：在途 / 失败可重试 / 已收口三态可区分；空闲态不渲染。 */
@Composable
private fun PagingFooter(
    paging: SearchPagingState,
    onRetry: () -> Unit,
) {
    when (paging) {
        is SearchPagingState.LoadingMore -> PagingHintColumn(
            dots = true,
            text = SearchTokens.LOAD_MORE_HINT,
            color = SearchTokens.TEXT_SECONDARY,
        )

        is SearchPagingState.LoadMoreError -> PagingErrorColumn(
            message = paging.message.ifBlank { SearchTokens.ERROR_MESSAGE },
            onRetry = onRetry,
        )

        SearchPagingState.Ended -> PagingHintColumn(
            dots = false,
            text = SearchTokens.LOAD_MORE_END,
            color = SearchTokens.TEXT_TERTIARY,
        )

        SearchPagingState.Idle -> Unit
    }
}

/** 在途 / 收口脚注：可选三点加载 + 一行云灰或茶褐提示。 */
@Composable
private fun PagingHintColumn(dots: Boolean, text: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (dots) {
            SearchLoadingDots()
            Spacer(Modifier.height(8.dp))
        }
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                color = color,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
            ),
        )
    }
}

/** 失败脚注：朱砂提示 + 可点重试；墨触无 ripple，颜色只取 token。 */
@Composable
private fun PagingErrorColumn(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = message,
            style = androidx.compose.ui.text.TextStyle(
                color = SearchTokens.TEXT_ACCENT,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .searchInkClickable(enabled = true, onClick = onRetry)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.text.BasicText(
                text = SearchTokens.RETRY_LABEL,
                style = androidx.compose.ui.text.TextStyle(
                    color = SearchTokens.TEXT_ACCENT,
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

/** 列表空兜底：非空白引导文案（正常无结果态由整页 Empty 接管，这里只防数据穿空）。 */
@Composable
private fun SearchListFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = SearchTokens.PAGE_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = SearchTokens.HINT_GUIDE,
            style = androidx.compose.ui.text.TextStyle(
                color = SearchTokens.TEXT_TERTIARY,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
        )
    }
}
