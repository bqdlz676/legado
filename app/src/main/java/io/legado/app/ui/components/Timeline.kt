package io.legado.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Timeline 纪年时间轴：竖线 + 节点 + 政权对照行 + 左右箭头。
 *
 * 纪年页与人物 Sheet 共用同一组件，只换数据。当前节点暖金，其余古铜；
 * 左右箭头在边界处禁用。空数据 / 离线 / 加载均有兜底，不允许空白态。
 */
data class RegimeRow(
    val regime: String,
    val yearLabel: String,
)

data class TimelineNode(
    val yearLabel: String,
    val eraLabel: String,
    val regimes: List<RegimeRow> = emptyList(),
    val current: Boolean = false,
)

data class TimelineModel(
    val nodes: List<TimelineNode>,
    val index: Int = 0,
) {
    val canGoBack: Boolean get() = nodes.isNotEmpty() && index > 0
    val canGoForward: Boolean get() = nodes.isNotEmpty() && index < nodes.size - 1

    fun current(): TimelineNode? = nodes.getOrNull(index)

    fun goBack(): TimelineModel = if (canGoBack) copy(index = index - 1) else this

    fun goForward(): TimelineModel = if (canGoForward) copy(index = index + 1) else this

    /** 模型自检：返回不合规说明，空列表表示通过 */
    fun validate(): List<String> = buildList {
        if (nodes.isEmpty()) add("nodes 不可为空")
        if (index < 0 || index >= nodes.size) add("index 越界")
        nodes.forEachIndexed { i, node ->
            if (node.yearLabel.isBlank()) add("第 $i 个节点 yearLabel 不可为空")
        }
    }

    companion object {
        val Empty: TimelineModel = TimelineModel(emptyList(), 0)
    }
}

@Composable
fun InkTimeline(
    model: TimelineModel,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    offline: Boolean = false,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    when {
        loading -> Column(
            modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { InkLoadingDots() }

        model.nodes.isEmpty() -> InkEmptyState(
            message = if (offline) "当前离线，时间轴缓存不可用" else "暂无纪年数据",
            offline = offline,
            compact = true,
        )

        else -> TimelineBody(model, modifier, onPrev, onNext)
    }
}

@Composable
private fun TimelineBody(
    model: TimelineModel,
    modifier: Modifier,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val node = model.current() ?: return
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            // 竖线 + 节点列
            Column(
                modifier = Modifier.width(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(4.dp, 120.dp)) {
                    drawLine(
                        color = ComponentTokens.colorDivider,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 1.5f,
                    )
                }
                Box(
                    Modifier
                        .size(if (node.current) 12.dp else 9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (node.current) ComponentTokens.brandGold else ComponentTokens.brandCopper,
                        ),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.text.BasicText(
                    text = node.eraLabel,
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                androidx.compose.foundation.text.BasicText(
                    text = node.yearLabel,
                    style = TextStyle(
                        color = ComponentTokens.textSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                node.regimes.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ComponentTokens.brandCopper),
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicText(
                            text = row.regime,
                            style = TextStyle(
                                color = ComponentTokens.textSecondary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        androidx.compose.foundation.text.BasicText(
                            text = row.yearLabel,
                            style = TextStyle(
                                color = ComponentTokens.textTertiary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimelineArrow(forward = false, enabled = model.canGoBack, onClick = onPrev)
            androidx.compose.foundation.text.BasicText(
                text = "${model.index + 1} / ${model.nodes.size}",
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            TimelineArrow(forward = true, enabled = model.canGoForward, onClick = onNext)
        }
    }
}

@Composable
private fun TimelineArrow(
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val gesture = rememberInkGesture(enabled = enabled, onClick = onClick)
    val tint = if (enabled) ComponentTokens.brandGold else ComponentTokens.textTertiary
    Box(
        Modifier
            .then(gesture.gestureModifier)
            .size(36.dp)
            .clip(RoundedCornerShape(50))
            .background(ComponentTokens.colorDivider),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp, 14.dp)) {
            val path = Path().apply {
                if (forward) {
                    moveTo(size.width * 0.25f, size.height * 0.2f)
                    lineTo(size.width * 0.75f, size.height * 0.5f)
                    lineTo(size.width * 0.25f, size.height * 0.8f)
                } else {
                    moveTo(size.width * 0.75f, size.height * 0.2f)
                    lineTo(size.width * 0.25f, size.height * 0.5f)
                    lineTo(size.width * 0.75f, size.height * 0.8f)
                }
                close()
            }
            drawPath(path, color = tint)
        }
    }
}
