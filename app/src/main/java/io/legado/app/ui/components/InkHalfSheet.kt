package io.legado.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * U6 半屏 Sheet（切片-11 逐字落地）。
 *
 * 视觉契约：
 * - Sheet 背景 = 纸白 [ComponentTokens.bgPage] + 金色 1px 边框，顶部圆角；
 * - 顶部 4dp 金色横杠拖拽指示器（**不是圆角条**），两端各一粒金点，Canvas 自绘；
 * - 茶色毛玻璃遮罩，暗化上限 40%；弹起时阅读页整体下沉 8dp；
 * - Sheet 最大高度 = 屏幕 × 60%；跟手下拉关闭，Sheet 下降的同时阅读页上升恢复亮度；
 * - 未过关闭阈值回弹到 60% 锚点（spring）。
 *
 * 铁律（§5.4）：关闭后阅读位置、滚动位置、下划线状态一字不差——本组件只包裹宿主槽位，
 * 不读写宿主滚动 / 选中状态。
 *
 * 统一规范：不包裹 MaterialTheme、不取 Material colorScheme；全站不用 Material ripple，
 * 遮罩点击走墨触手势；颜色 / 尺寸 / 曲线全部来自 [ComponentTokens] / [HalfSheetAnchors]。
 *
 * 与既有 [InkBottomSheet]（30/60/90 三段 detent）共存：本组件是切片-11 的 60% 上限
 * 单锚点半屏形态，互不覆盖。
 */
@Composable
fun InkHalfSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: ComponentVisualState = ComponentVisualState.DEFAULT,
    title: String = "",
    model: HalfSheetContent? = null,
    host: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(1f, ComponentTokens.sheetSpring())
        } else {
            progress.animateTo(
                0f,
                tween(ComponentPalette.DURATION_MASK, easing = ComponentTokens.maskEasing),
            )
        }
    }

    if (progress.value == 0f && !visible) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val sinkDp = with(LocalDensity.current) {
            HalfSheetAnchors.hostSinkDp(progress.value).toDp()
        }

        // ── 宿主阅读页：随弹起下沉 8dp，上覆茶玻暗化 40% ──
        Box(
            Modifier
                .fillMaxSize()
                .offset(y = sinkDp),
        ) {
            host()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        ComponentTokens.bgOverlay.copy(
                            alpha = HalfSheetAnchors.maskAlpha(progress.value),
                        ),
                    ),
            )
        }

        // ── 遮罩层：点击关闭（墨触手势，无 ripple） ──
        Box(
            Modifier
                .fillMaxSize()
                .then(rememberInkGesture(enabled = true, onClick = onDismiss).gestureModifier),
        )

        // ── Sheet 本体 ──
        val restingHeightPx = maxHeightPx * HalfSheetAnchors.MAX_HEIGHT_FRACTION
        val sheetHeightPx = (restingHeightPx - dragOffsetPx)
            .coerceIn(maxHeightPx * 0.12f, restingHeightPx)
        val sheetHeightDp = with(LocalDensity.current) { sheetHeightPx.toDp() }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .clip(
                    RoundedCornerShape(
                        topStart = ComponentTokens.sheetRadius,
                        topEnd = ComponentTokens.sheetRadius,
                    ),
                )
                .background(ComponentTokens.bgPage)
                .border(
                    1.dp,
                    ComponentTokens.brandGold.copy(alpha = 0.45f),
                    RoundedCornerShape(
                        topStart = ComponentTokens.sheetRadius,
                        topEnd = ComponentTokens.sheetRadius,
                    ),
                )
                .safeDrawingPadding(),
        ) {
            // 金杠拖拽指示器：直边横杠 + 两端金点（Canvas 自绘，非圆角条）
            HalfSheetGrabber(Modifier.fillMaxWidth().padding(top = 8.dp))

            if (title.isNotBlank()) {
                androidx.compose.foundation.text.BasicText(
                    text = title,
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            val draggableState = rememberDraggableState { delta ->
                // 跟手：上滑 delta<0 抬高，下滑 delta>0 压低
                dragOffsetPx = (dragOffsetPx - delta).coerceAtLeast(0f)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val fraction = sheetHeightPx / maxHeightPx
                            if (HalfSheetAnchors.shouldDismiss(fraction, velocity)) {
                                onDismiss()
                            } else {
                                scope.launch {
                                    val anim = Animatable(dragOffsetPx)
                                    anim.animateTo(0f, ComponentTokens.sheetSpring()) {
                                        dragOffsetPx = value
                                    }
                                }
                            }
                        },
                    )
                    .padding(horizontal = 16.dp),
            ) {
                when (state) {
                    ComponentVisualState.LOADING -> Column(Modifier.padding(16.dp)) {
                        InkLoadingDots()
                    }
                    ComponentVisualState.EMPTY -> InkEmptyState(
                        message = "暂无相关内容",
                        compact = true,
                    )
                    ComponentVisualState.OFFLINE -> InkEmptyState(
                        message = "当前离线，展示缓存内容",
                        compact = true,
                        offline = true,
                    )
                    else -> if (model != null) {
                        HalfSheetSectionList(model)
                    } else {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 金杠拖拽指示器：4dp 高直边金色横杠，左右两端各一粒金点。
 * 全部 Canvas 自绘，不使用圆角 Shape，不引 MaterialTheme。
 */
@Composable
private fun HalfSheetGrabber(modifier: Modifier = Modifier) {
    val barWidth = with(LocalDensity.current) {
        HalfSheetAnchors.GRABBER_BAR_WIDTH_DP.dp.toPx()
    }
    val barHeight = with(LocalDensity.current) {
        HalfSheetAnchors.GRABBER_BAR_HEIGHT_DP.dp.toPx()
    }
    val dotRadius = with(LocalDensity.current) {
        HalfSheetAnchors.GRABBER_DOT_RADIUS_DP.dp.toPx()
    }

    Canvas(modifier.height(20.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val barLeft = cx - barWidth / 2f
        drawRect(
            color = ComponentTokens.brandGold,
            topLeft = Offset(barLeft, cy - barHeight / 2f),
            size = Size(barWidth, barHeight),
        )
        drawCircle(
            color = ComponentTokens.brandGold,
            radius = dotRadius,
            center = Offset(barLeft, cy),
        )
        drawCircle(
            color = ComponentTokens.brandGold,
            radius = dotRadius,
            center = Offset(barLeft + barWidth, cy),
        )
    }
}

/** 云灰 1px 分区分隔线（Canvas 自绘）。 */
@Composable
fun HalfSheetDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = ComponentTokens.colorDivider,
            start = Offset(0f, 0.5f),
            end = Offset(size.width, 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/**
 * 按 §5.2 / §5.3 固定顺序渲染分区列表：分区之间云灰细线分隔；
 * 首行加粗的分区（人物 ① 本段行为）首行走 SemiBold。
 */
@Composable
fun HalfSheetSectionList(model: HalfSheetContent, modifier: Modifier = Modifier) {
    Column(modifier) {
        model.sections.forEachIndexed { index, section ->
            if (index > 0) HalfSheetDivider()
            Column(Modifier.padding(vertical = 10.dp)) {
                if (section.title.isNotBlank()) {
                    androidx.compose.foundation.text.BasicText(
                        text = section.title,
                        style = TextStyle(
                            color = ComponentTokens.stoneBlue,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                        ),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                section.lines.forEachIndexed { lineIndex, line ->
                    val bold = section.boldFirst && lineIndex == 0
                    androidx.compose.foundation.text.BasicText(
                        text = line,
                        style = TextStyle(
                            color = ComponentTokens.textPrimary,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                            lineHeight = 26.sp,
                        ),
                    )
                }
            }
        }
    }
}
