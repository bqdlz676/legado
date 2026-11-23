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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
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
 * - 结果为空时内部兜底引导文案，不允许空白态。
 */
@Composable
fun SearchResultList(
    results: List<SearchResultItem>,
    query: String,
    blocking: Boolean,
    onItemClick: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        SearchListFallback(modifier = modifier)
        return
    }

    LazyColumn(
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
