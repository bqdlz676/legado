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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.contract.ComponentVisualState

/**
 * Timeline 纪年时间轴：竖线 + 节点 + 政权对照行 + 左右箭头。
 *
 * 纪年页与人物 Sheet 共用同一组件，只换数据。当前节点暖金，其余古铜；
 * 左右箭头在边界处禁用。空数据 / 离线 / 加载均有兜底，不允许空白态。
 *
 * 状态取唯一契约模型 [ComponentVisualState]：箭头闸门与整行压淡一律读契约
 * [ComponentVisualState.eventsAllowed] / [ComponentVisualState.contentAlpha]，
 * 不再只看模型边界布尔。显式 state 与 offline 布尔取并集（与 InkButton 同口径）：
 * 只传 offline 布尔而不显式传 state 时，该布尔也并入事件闸。DISABLED 箭头不可点且整行按
 * 契约 0.30 压淡；OFFLINE / LOADING 容器本体不放行箭头；EMPTY 走空态兜底且仍可点、不压淡。
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

/**
 * Body 事件闸（纯函数，普通 JVM 单测可直接断言）：契约事件许可与离线布尔取并集。
 *
 * 并集口径与 [InkButton] 一致：显式传 [state] 时按契约枚举 [ComponentVisualState.eventsAllowed]
 * 裁决；只传 `offline` 布尔而不显式传 state 时，该布尔也并入闸门——offline=true 视同 OFFLINE
 * 禁点。DISABLED / OFFLINE / LOADING 契约登记 `eventsAllowed=false`，容器本体吞掉回调；
 * EMPTY / DEFAULT / HOVERED / PRESSED / SELECTED 放行。不另写一套可点布尔，避免与契约口径漂移。
 */
internal fun timelineBodyEventsAllowed(state: ComponentVisualState, offline: Boolean): Boolean =
    state.eventsAllowed && !offline

/**
 * 箭头可点闸（纯函数，普通 JVM 单测可直接断言）：Body 事件闸再叠加模型边界
 * （首节点不可回、末节点不可进），两者同时成立才放行。
 */
internal fun timelineArrowAllowed(
    state: ComponentVisualState,
    offline: Boolean,
    canNavigate: Boolean,
): Boolean = timelineBodyEventsAllowed(state, offline) && canNavigate

/**
 * Body 压淡系数（纯函数，普通 JVM 单测可直接断言）：只读契约 [ComponentVisualState.contentAlpha]。
 *
 * 离线布尔不另造压淡系数：契约 OFFLINE / LOADING / EMPTY 的 contentAlpha 均为 1f，离线 / 加载 /
 * 空态不得套 DISABLED 的 0.30；压淡误伤只可能来自契约 DISABLED 档。
 */
internal fun timelineContentAlpha(state: ComponentVisualState, offline: Boolean): Float =
    state.contentAlpha

@Composable
fun InkTimeline(
    model: TimelineModel,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    offline: Boolean = false,
    state: ComponentVisualState = ComponentVisualState.DEFAULT,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val effectiveLoading = loading || state == ComponentVisualState.LOADING
    val effectiveOffline = offline || state == ComponentVisualState.OFFLINE
    val effectiveEmpty = model.nodes.isEmpty() || state == ComponentVisualState.EMPTY

    when {
        effectiveLoading -> Column(
            modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { InkLoadingDots() }

        effectiveEmpty -> InkEmptyState(
            message = if (effectiveOffline) "当前离线，时间轴缓存不可用" else "暂无纪年数据",
            offline = effectiveOffline,
            state = state,
            compact = true,
        )

        else -> TimelineBody(model, modifier, state, effectiveOffline, onPrev, onNext)
    }
}

@Composable
private fun TimelineBody(
    model: TimelineModel,
    modifier: Modifier,
    state: ComponentVisualState,
    offline: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val node = model.current() ?: return
    // 整行压淡直读契约 contentAlpha：DISABLED 落 0.30，其余态 1f；alpha 是静态透明度，
    // 不依赖动画时长，低端机 reduce-motion 下压淡仍然成立。离线布尔不压淡，见 timelineContentAlpha。
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp).alpha(timelineContentAlpha(state, offline)),
    ) {
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
            TimelineArrow(
                forward = false,
                enabled = timelineArrowAllowed(state, offline, model.canGoBack),
                onClick = onPrev,
            )
            androidx.compose.foundation.text.BasicText(
                text = "${model.index + 1} / ${model.nodes.size}",
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            TimelineArrow(
                forward = true,
                enabled = timelineArrowAllowed(state, offline, model.canGoForward),
                onClick = onNext,
            )
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
