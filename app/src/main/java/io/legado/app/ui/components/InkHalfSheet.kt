package io.legado.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * U6 半屏 Sheet（切片-11 逐字落地，统一收口组件）。
 *
 * 视觉契约（§5.1）：
 * - 弹起时阅读页整体下沉 8dp + 暗化 40%（茶色毛玻璃遮罩，色取 [ComponentTokens.bgOverlay]
 *   即 rgba(26,20,16,.45)，模糊基准 12dp，见 [HalfSheetAnchors.MASK_BLUR_DP]）；
 * - Sheet 背景 = 纸白 [ComponentTokens.bgPage] + 金色 1px 边框，顶部圆角；
 * - 顶部 4dp 金色横杠拖拽指示器（**不是圆角条**），两端各一粒金点，Canvas 自绘；
 * - Sheet 最大高度 = 屏幕 × 60%；跟手下拉关闭，Sheet 下降的同时阅读页上升恢复亮度；
 * - 弹起 250ms ease-out（[ComponentTokens.cardEnterEasing]），遮罩 200ms
 *   cubic-bezier(0.4,0,0.6,1)（[ComponentTokens.maskEasing]）；禁线性缓动、禁菊花、
 *   禁 Material 默认组件与 elevation。
 *
 * 铁律（§5.4）：关闭后阅读位置、滚动位置、下划线状态一字不差——本组件只叠一层覆盖，
 * 不读写宿主滚动 / 选中 / 高亮状态；宿主内容槽原样透传。开合前后快照比对走
 * [ReaderSnapshot] + [isRestoredExactly]；若在场的是旧 [HalfSheetHostSnapshot]，
 * 先经 compat 单向适配（旧→新，不反向映射）成 [ReaderSnapshot] 再逐字段比对，JVM 单测可断言。
 *
 * 统一规范：不包裹 MaterialTheme、不取 Material colorScheme；全站不用 Material ripple，
 * 遮罩点击走墨触手势；颜色 / 容器参数来自 [ComponentTokens] / [ComponentPalette] /
 * [HalfSheetParams]；动画时长、模糊基准、拖拽阈值、横杠宽与点径仍由 [HalfSheetAnchors] 持有。
 *
 * @param dialog 独立窗口覆盖层模式：气泡详情等场景宿主页在自身窗口内，Sheet 需跨窗口
 *               铺满全屏而不被气泡小尺寸父 Box 裁剪；此模式下不渲染 [host] 槽，
 *               宿主页不被重排、不被重算 layout（§5.4）。
 * @param model 人物 / 字词分区模型；为 null 时走 [content] 自定义槽。
 * @param host 宿主阅读页槽位（内嵌模式）：随弹起下沉 8dp，其上覆茶玻遮罩。
 * @param source 出处标注脚注（如 Harvard CBDB #xxx / 《说文解字》版本）；空串不渲染。
 * @param onLinkSelected 分区内行点击回传该行跳转 query（同场人物 / 同名候选切换）。
 */
@Composable
fun InkHalfSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: ComponentVisualState = ComponentVisualState.DEFAULT,
    title: String = "",
    source: String = "",
    model: HalfSheetContent? = null,
    dialog: Boolean = false,
    host: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {},
    onLinkSelected: (String) -> Unit = {},
) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    val maskProgress = remember { Animatable(if (visible) 1f else 0f) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            // Sheet 弹起 250ms ease-out；遮罩独立走 200ms cubic-bezier(0.4,0,0.6,1)。
            progress.animateTo(
                1f,
                tween(HalfSheetAnchors.RISE_DURATION_MS, easing = ComponentTokens.cardEnterEasing),
            )
            maskProgress.animateTo(
                1f,
                tween(HalfSheetAnchors.MASK_DURATION_MS, easing = ComponentTokens.maskEasing),
            )
        } else {
            maskProgress.animateTo(
                0f,
                tween(HalfSheetAnchors.MASK_DURATION_MS, easing = ComponentTokens.maskEasing),
            )
            progress.animateTo(
                0f,
                tween(HalfSheetAnchors.RISE_DURATION_MS, easing = ComponentTokens.cardEnterEasing),
            )
        }
    }

    if (progress.value == 0f && !visible) return

    val sheet: @Composable () -> Unit = {
        HalfSheetRoot(
            modifier = modifier,
            state = state,
            title = title,
            source = source,
            model = model,
            host = host,
            content = content,
            onDismiss = onDismiss,
            onLinkSelected = onLinkSelected,
            progress = progress.value,
            maskProgress = maskProgress.value,
            dragOffsetPx = dragOffsetPx,
            onDragOffset = { dragOffsetPx = it },
            onResetDrag = {
                scope.launch {
                    val anim = Animatable(dragOffsetPx)
                    anim.animateTo(0f, ComponentTokens.sheetSpring()) { dragOffsetPx = value }
                }
            },
        )
    }

    if (dialog) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            sheet()
        }
    } else {
        sheet()
    }
}

@Composable
private fun HalfSheetRoot(
    modifier: Modifier,
    state: ComponentVisualState,
    title: String,
    source: String,
    model: HalfSheetContent?,
    host: @Composable () -> Unit,
    content: @Composable () -> Unit,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
    progress: Float,
    maskProgress: Float,
    dragOffsetPx: Float,
    onDragOffset: (Float) -> Unit,
    onResetDrag: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val sinkDp = with(LocalDensity.current) {
            // S-11 接线：下沉量收口 HalfSheetParams.HOST_SINK_DP（8dp）；进度缩放口径同旧 helper
            (HalfSheetParams.HOST_SINK_DP * progress.coerceIn(0f, 1f)).toDp()
        }

        // ── 宿主阅读页：随弹起下沉 8dp，上覆茶玻暗化 40%（内嵌模式；独立窗口模式不重排宿主） ──
        Box(
            Modifier
                .fillMaxSize()
                .offset(y = sinkDp),
        ) {
            host()
            // 茶色毛玻璃遮罩：bg-overlay rgba(26,20,16,.45)，模糊基准 12dp（§5.1）。
            // 边界同 InkFrost：RenderEffect 模糊的是图层自身内容，背景截获式 backdrop blur
            // 由窗口层（window blur behind）统一 mediate，此处不伪造 backdrop 采样。
            Box(
                Modifier
                    .fillMaxSize()
                    .blur(HalfSheetAnchors.MASK_BLUR_DP.dp)
                    .background(
                        ComponentTokens.bgOverlay.copy(
                            // S-11 接线：暗化上限收口 HalfSheetParams.DIM_ALPHA（0.4），随遮罩进度回落
                            alpha = HalfSheetParams.DIM_ALPHA * maskProgress.coerceIn(0f, 1f),
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

        // ── Sheet 本体：最大高度 = 屏高 × 60%，超出由内部竖滑滚动，不截断丢内容 ──
        // S-11 接线：最大高 = 屏高 × 中段 60%；三段 detent 单源在 ComponentPalette（中段）
        val maxSheetHeightPx = maxHeightPx * ComponentPalette.SHEET_DETENT_MID
        val maxSheetHeightDp = with(LocalDensity.current) { maxSheetHeightPx.toDp() }

        val draggableState = rememberDraggableState { delta ->
            // 跟手：上滑 delta<0 抬高，下滑 delta>0 压低；只允许向下拖出。
            if (delta > 0f) onDragOffset(dragOffsetPx + delta)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeightDp)
                .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                .clip(
                    RoundedCornerShape(
                        topStart = ComponentTokens.sheetRadius,
                        topEnd = ComponentTokens.sheetRadius,
                    ),
                )
                .background(ComponentTokens.bgPage)
                .border(
                    1.dp,
                    ComponentTokens.brandGold,
                    RoundedCornerShape(
                        topStart = ComponentTokens.sheetRadius,
                        topEnd = ComponentTokens.sheetRadius,
                    ),
                )
                .padding(bottom = 12.dp),
        ) {
            // 金杠拖拽指示器挂在 Header：下拉手势只走横杠，正文区交给竖滑滚动，二者不抢手势。
            HalfSheetGrabber(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val fraction = (maxSheetHeightPx - dragOffsetPx) / maxHeightPx
                            if (HalfSheetAnchors.shouldDismiss(fraction, velocity)) {
                                onDismiss()
                            } else {
                                onResetDrag()
                            }
                        },
                    )
                    .padding(top = 8.dp),
            )

            if (title.isNotBlank()) {
                BasicText(
                    text = title,
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // 正文区：竖滑滚动；禁用态整体压到 30% 透明度；悬停 / 点击泛金辉。
            val contentAlpha = if (state == ComponentVisualState.DISABLED) {
                ComponentTokens.disabledAlpha
            } else {
                1f
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { alpha = contentAlpha }
                    .then(
                        if (state == ComponentVisualState.HOVERED ||
                            state == ComponentVisualState.PRESSED
                        ) {
                            Modifier.background(ComponentTokens.colorGlow)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                when (state) {
                    ComponentVisualState.LOADING -> Column(Modifier.padding(16.dp)) {
                        HalfSheetLoadingDots()
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
                        HalfSheetSectionList(model, onLinkSelected = onLinkSelected)
                    } else {
                        content()
                    }
                }
            }

            // 出处脚注：每条数据必须带来源与版本（数据可信 = 命）；空串不渲染。
            if (source.isNotBlank()) {
                BasicText(
                    text = "出处 · $source",
                    style = TextStyle(
                        color = ComponentTokens.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
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
        // S-11 接线：横杠高收口 HalfSheetParams.GRABBER_BAR_HEIGHT_DP（4dp）
        HalfSheetParams.GRABBER_BAR_HEIGHT_DP.dp.toPx()
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
 * 加载态：三金点 600ms 依次亮灭（落文金点签名；不用菊花 / 不引 Material 进度条）。
 */
@Composable
private fun HalfSheetLoadingDots() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val anim = remember(index) { Animatable(0.25f) }
            LaunchedEffect(Unit) {
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 600
                            0.25f at 0
                            1f at 100 + index * 80
                            0.25f at 600
                        },
                        repeatMode = RepeatMode.Restart,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ComponentTokens.brandGold.copy(alpha = anim.value)),
            )
            if (index < 2) Spacer(Modifier.width(6.dp))
        }
    }
}

/**
 * 按 §5.2 / §5.3 固定顺序渲染分区列表：分区之间云灰细线分隔；
 * 首行加粗的分区（人物 ① 本段行为）首行走 SemiBold；带链接的行点击回传 query。
 */
@Composable
fun HalfSheetSectionList(
    model: HalfSheetContent,
    modifier: Modifier = Modifier,
    onLinkSelected: (String) -> Unit = {},
) {
    Column(modifier) {
        model.sections.forEachIndexed { index, section ->
            if (index > 0) HalfSheetDivider()
            Column(Modifier.padding(vertical = 10.dp)) {
                if (section.title.isNotBlank()) {
                    BasicText(
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
                    val link = section.links.getOrNull(lineIndex).orEmpty()
                    val rowModifier = if (link.isNotEmpty()) {
                        Modifier.then(
                            rememberInkGesture(enabled = true, onClick = { onLinkSelected(link) })
                                .gestureModifier,
                        )
                    } else {
                        Modifier
                    }
                    BasicText(
                        text = line,
                        style = TextStyle(
                            color = if (link.isNotEmpty()) ComponentTokens.colorLink
                            else ComponentTokens.textPrimary,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                            lineHeight = 26.sp,
                        ),
                        modifier = rowModifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
