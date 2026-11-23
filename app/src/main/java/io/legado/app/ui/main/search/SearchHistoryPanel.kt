package io.legado.app.ui.main.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 落文 · 搜索历史 / 热门面板（空闲态展示）
 *
 * - 历史词条可点选回填、可单条删除；右侧「清空历史」。
 * - 热门词条前置暖金小点；历史与热门同时为空时给引导兜底文案，不留白。
 * - 标签底 / 边线 Canvas 自绘，墨触无 ripple，颜色只取 [SearchTokens]。
 */
@Composable
fun SearchHistoryPanel(
    history: List<String>,
    hot: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty() && hot.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = SearchTokens.PAGE_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.text.BasicText(
                text = SearchTokens.HINT_GUIDE,
                style = TextStyle(
                    color = SearchTokens.TEXT_TERTIARY,
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                ),
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchTokens.PAGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (history.isNotEmpty()) {
            HistorySection(
                history = history,
                onPick = onPick,
                onRemove = onRemove,
                onClear = onClearHistory,
            )
        }
        if (hot.isNotEmpty()) {
            HotSection(hot = hot, onPick = onPick)
        }
    }
}

@Composable
private fun HistorySection(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(SearchTokens.HISTORY_TITLE)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .searchInkClickable(enabled = true, onClick = onClear)
                    .padding(4.dp),
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = SearchTokens.CLEAR_HISTORY_LABEL,
                    style = TextStyle(
                        color = SearchTokens.TEXT_TERTIARY,
                        fontFamily = FontFamily.Serif,
                        fontSize = 12.sp,
                    ),
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(SearchTokens.CHIP_GAP)) {
            history.forEach { key ->
                HistoryChip(
                    text = key,
                    onClick = { onPick(key) },
                    onRemove = { onRemove(key) },
                )
            }
        }
    }
}

@Composable
private fun HotSection(hot: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(SearchTokens.HOT_TITLE)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SearchTokens.CHIP_GAP)) {
            hot.forEach { key ->
                HotChip(text = key, onClick = { onPick(key) })
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = TextStyle(
            color = SearchTokens.TEXT_SECONDARY,
            fontFamily = FontFamily.Serif,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.06.sp,
        ),
    )
}

@Composable
private fun HistoryChip(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SearchTokens.BG_SURFACE, RoundedCornerShape(16.dp))
            .searchInkClickable(enabled = true, onClick = onClick)
            .padding(horizontal = SearchTokens.CHIP_PADDING_H, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(
                color = SearchTokens.TEXT_PRIMARY,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(14.dp)
                .searchInkClickable(enabled = true, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            ChipCross()
        }
    }
}

@Composable
private fun HotChip(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SearchTokens.BG_SURFACE, RoundedCornerShape(16.dp))
            .searchInkClickable(enabled = true, onClick = onClick)
            .padding(horizontal = SearchTokens.CHIP_PADDING_H, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(6.dp)) {
            drawCircle(color = SearchTokens.BRAND_GOLD, radius = size.minDimension / 2f)
        }
        Spacer(Modifier.width(6.dp))
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(
                color = SearchTokens.TEXT_SECONDARY,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
        )
    }
}

@Composable
private fun ChipCross() {
    Canvas(Modifier.size(12.dp)) {
        val stroke = 1.2.dp.toPx()
        val pad = 3.dp.toPx()
        drawLine(
            color = SearchTokens.TEXT_TERTIARY,
            start = Offset(pad, pad),
            end = Offset(size.width - pad, size.height - pad),
            strokeWidth = stroke,
        )
        drawLine(
            color = SearchTokens.TEXT_TERTIARY,
            start = Offset(size.width - pad, pad),
            end = Offset(pad, size.height - pad),
            strokeWidth = stroke,
        )
    }
}
