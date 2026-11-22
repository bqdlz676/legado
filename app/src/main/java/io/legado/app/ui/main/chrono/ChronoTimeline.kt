package io.legado.app.ui.main.chrono

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 落文 · Timeline 组件（竖线 + 节点 + 政权对照行）。
 *
 * 纪年页与历史人物 Sheet 共用同一个组件，只换数据（[ChronoNode]）。
 *
 * 交互口径（轨迹原文）：左右滑动是「一整根轴向左 / 向右移动」，滑动本身不切朝代；
 * 切年只走左右箭头 / 吸附切换。拖拽偏移只作用于轴体平移，松手不弹回原位。
 */
@Composable
fun ChronoTimeline(
    nodes: List<ChronoNode>,
    modifier: Modifier = Modifier,
    onSelect: ((index: Int) -> Unit)? = null,
) {
    val density = LocalDensity.current
    // 轴体横向平移量（px）；左右滑动整根轴移动，不触发年份切换
    var axisPanPx by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = modifier
            .pointerInput(nodes.size) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    axisPanPx += dragAmount
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(nodes.size) { index ->
            val node = nodes[index]
            TimelineNodeRow(
                node = node,
                axisPanDp = with(density) { axisPanPx.toDp() },
                onClick = { onSelect?.invoke(index) },
            )
        }
    }
}

@Composable
private fun TimelineNodeRow(
    node: ChronoNode,
    axisPanDp: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        // 竖线 + 节点
        Column(
            modifier = Modifier
                .width(34.dp)
                .padding(top = 4.dp)
                .offset(x = axisPanDp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(if (node.selected) 12.dp else 9.dp)
                    .clip(CircleShape)
                    .background(
                        if (node.selected) ChronoTokens.brandGold else ChronoTokens.brandCopper
                    ),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .weight(1f)
                    .background(ChronoTokens.colorDivider),
            )
        }

        // 对照卡片（随轴体横向平移）
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp)
                .offset(x = axisPanDp),
        ) {
            NodeHeader(node)
            Spacer(Modifier.height(10.dp))
            if (node.state.rowsMissing) {
                FallbackRow(node.state.dayGanzhi)
            } else {
                node.state.rows.forEach { row -> ChronoRowItem(row) }
            }
        }
    }
}

@Composable
private fun NodeHeader(node: ChronoNode) {
    Column {
        BasicText(
            text = node.state.ganzhi,
            style = TextStyle(
                color = ChronoTokens.textPrimary,
                fontSize = 22.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        BasicText(
            text = node.state.yearLabel,
            style = TextStyle(color = ChronoTokens.textSecondary, fontSize = 13.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
        BasicText(
            text = node.state.agoText,
            style = TextStyle(color = ChronoTokens.textTertiary, fontSize = 12.sp),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ChronoRowItem(row: ChronoRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ChronoTokens.bgSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "${row.state} · ${row.title}",
            style = TextStyle(
                color = ChronoTokens.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        BasicText(
            text = "${row.nthText}年",
            style = TextStyle(
                color = ChronoTokens.brandGold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

@Composable
private fun FallbackRow(dayGanzhi: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChronoTokens.bgSurface)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = "此年诸侯世系未载",
            style = TextStyle(
                color = ChronoTokens.textSecondary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        BasicText(
            text = "仅存干支 · 当日 $dayGanzhi",
            style = TextStyle(color = ChronoTokens.textTertiary, fontSize = 12.sp),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 平移量取整到像素，避免亚像素抖动 */
internal fun panPxToDp(px: Float, density: Float): androidx.compose.ui.unit.Dp =
    androidx.compose.ui.unit.Dp(px.roundToInt() / density)
