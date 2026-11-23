package io.legado.app.ui.book.read.ink

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.help.bubble.Action
import io.legado.app.help.bubble.BubbleCandidate
import io.legado.app.help.bubble.BubbleContent
import io.legado.app.help.bubble.BubbleKind
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 字 / 词 / 典故 / 人名 / 地名 / 年号 / 干支 / 朔望 统一气泡（切片-02 §3 各类型字段 +
 * 切片-04 §1 Bubble：撕边纸片 + 金边 + spring 注入 + 义项切换点；变体结构不同、容器统一）。
 *
 * 视觉口径：绢帛磨砂纸片（[frostedSurface]）+ 1px 暖金边 + 圆角；入场 250ms
 * `cubic-bezier(0.34,1.56,0.64,1)` overshoot 回弹；颜色只取 [InkTokens] 12 色，
 * 不套 MaterialTheme、不用默认 ripple / elevation，按压反馈走 [InkIconButton] 朱砂 8% 墨触。
 *
 * 定位口径（切片-02 §4 位置铁律）：气泡在原文上方 8dp、箭头对准原字水平中心，
 * 上方超界由 [BubblePlacer] 自动翻到下方；本组件在自身约束内测量尺寸后算出定位偏移，
 * 尖角按定位结果决定朝上 / 朝下与本地横坐标。不遮当前句；滚动 / 翻页 / 点空白关闭由
 * 宿主遮罩层负责，本组件是纯覆盖层，不触碰正文滚动状态，关闭后阅读位置 / 下划线一字不差。
 *
 * @param content  C2 冻结气泡内容协议（唯一数据结构，UI 不分别处理各 Provider 数据类）。
 * @param anchor   原字屏幕锚点（水平中心 + 所在句行边界），供定位与尖角对准。
 * @param onAction 动作分发回调；宿主缺省接 [InkActionDispatcher.dispatch]。
 * @param onCandidateSelected 候选切换回调；回传整条候选，重查用其 query（不带括号装饰），展示用 display。
 * @param onDismiss 点空白 / 关闭气泡回调；宿主据此清掉气泡状态，不留残余。
 * @param onFallback 兜底入口回调（"web" 联网检索 / "notebook" 生字本），非导航动作。
 */
@Composable
fun InkBubble(
    content: BubbleContent,
    anchor: BubbleAnchor,
    onAction: (Action) -> Unit,
    onCandidateSelected: (BubbleCandidate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onFallback: (String) -> Unit = {},
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val viewW = maxWidthPx
        val viewH = maxHeightPx
        var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

        val gapPx = with(density) { InkTokens.BUBBLE_GAP.toPx() }
        val bottomReservedPx = with(density) { InkTokens.BOTTOM_BAR_HEIGHT.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }

        // 测量出自家宽高后再定位：尖角对字心、上方超界翻下方、不遮当前句，全部收口在 [BubblePlacer]。
        val placement = remember(anchor, bubbleSize, viewW, viewH) {
            BubblePlacer.place(
                anchor = anchor,
                bubbleW = bubbleSize.width.toFloat().coerceAtLeast(1f),
                bubbleH = bubbleSize.height.toFloat().coerceAtLeast(1f),
                viewW = viewW,
                viewH = viewH,
                gapPx = gapPx,
                topSafePx = 0f,
                bottomReservedPx = bottomReservedPx,
                marginPx = marginPx,
            )
        }

        Box(
            modifier = Modifier.offset {
                IntOffset(placement.x.roundToInt(), placement.y.roundToInt())
            },
        ) {
            BubbleCard(
                content = content,
                placement = placement,
                onAction = onAction,
                onCandidateSelected = onCandidateSelected,
                onFallback = onFallback,
                modifier = Modifier.onSizeChanged { bubbleSize = it },
            )
        }
    }

    // 半屏详情 Sheet：独立窗口覆盖层，不滚动正文；关闭后阅读位置 / 下划线状态一字不差。
    // 整颗气泡的关闭由宿主手势遮罩在点空白 / 翻页 / 滚动时调 [onDismiss]；
    // 本组件不自行消费关闭回调，避免与宿主手势层重复判点。
}

@Composable
private fun BubbleCard(
    content: BubbleContent,
    placement: BubblePlacement,
    onAction: (Action) -> Unit,
    onCandidateSelected: (BubbleCandidate) -> Unit,
    onFallback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 入场：scale 0.3 → 1.0，250ms overshoot 回弹曲线（切片-03 缓动表「气泡弹入」）。
    val progress = remember { Animatable(0.3f) }
    LaunchedEffect(content.token) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = InkTokens.DURATION_BUBBLE,
                easing = InkTokens.bubbleEasing,
            ),
        )
    }
    val scale = progress.value

    // 候选循环状态：气泡内容（候选列表）切换后回到首个候选；空列表不出切换行。
    val cycle = remember(content) { CandidateCycle(content.candidates) }
    var sheetOpen by remember(content) { mutableStateOf(false) }

    val arrowLocalX = (placement.arrowX - placement.x).roundToInt()
    val accent = variantAccent(content.kind)

    Column(modifier = modifier.scale(scale)) {
        // 翻到下方时，尖角在气泡顶部（朝上指回原字）。
        if (!placement.above) {
            ArrowRow(above = false, localArrowX = arrowLocalX)
        }

        Box {
            Column(
                modifier = Modifier
                    .frostedSurface(RoundedCornerShape(12.dp))
                    .border(1.dp, InkTokens.BRAND_GOLD, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 注音栏：无读音数据时整行隐藏，不猜读音、不留空白占位行。
                if (!content.phonetic.isNullOrBlank()) {
                    BasicText(
                        text = content.phonetic.trim(),
                        style = TextStyle(
                            fontFamily = InkFonts.composeFamily,
                            fontSize = 10.sp,
                            color = InkTokens.TEXT_SECONDARY,
                        ),
                    )
                }

                // 标题行：加粗墨色 headline，右侧变体标签（字 / 词 / 人名 / 地名…）。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = content.headline.ifBlank { content.token },
                        style = TextStyle(
                            fontFamily = InkFonts.composeFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = InkTokens.TEXT_PRIMARY,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = content.kind,
                        style = TextStyle(
                            fontFamily = InkFonts.composeFamily,
                            fontSize = 10.sp,
                            color = accent,
                        ),
                    )
                }

                // 候选切换行（切片-02 §4：顶部一行「还有 N 种解释」，点击循环）。
                if (cycle.hasCandidates()) {
                    InkIconButton(onClick = {
                        val picked = cycle.next()
                        // 回传整条候选：宿主重查走 query（裸词），展示走 display（带括号装饰）。
                        if (picked != null) onCandidateSelected(picked)
                    }) {
                        BasicText(
                            text = candidateLabel(cycle),
                            style = TextStyle(
                                fontFamily = InkFonts.composeFamily,
                                fontSize = 12.sp,
                                color = InkTokens.COLOR_LINK,
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                // 金线分隔
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    drawLine(
                        color = InkTokens.BRAND_GOLD.copy(alpha = 0.5f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                // 气泡内 2~3 行解释；body 缺省回退 detail，不出空气泡。
                BasicText(
                    text = resolveBody(content),
                    style = TextStyle(
                        fontFamily = InkFonts.composeFamily,
                        fontSize = 13.sp,
                        color = InkTokens.TEXT_SECONDARY,
                        lineHeight = 19.sp,
                    ),
                )

                // 出处标注：每条数据必须带来源与版本（数据可信 = 命）。
                if (content.source.isNotBlank()) {
                    BasicText(
                        text = "出处 · ${content.source}",
                        style = TextStyle(
                            fontFamily = InkFonts.composeFamily,
                            fontSize = 10.sp,
                            color = InkTokens.TEXT_TERTIARY,
                        ),
                    )
                }

                // 动作 chips + 兜底入口；动作无可接通消费方时置灰不可点。
                val fallbackChips = buildList {
                    if (content.allowWebSearch) add("联网" to "web")
                    if (content.allowNotebook) add("生字本" to "notebook")
                }
                val hasDetail = content.detail.isNotBlank()

                if (content.actions.isNotEmpty() || fallbackChips.isNotEmpty() || hasDetail) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        content.actions.forEach { action ->
                            ActionChip(
                                label = actionLabel(action),
                                enabled = InkActionDispatcher.canDispatch(action),
                                onClick = { onAction(action) },
                            )
                        }
                        fallbackChips.forEach { (label, kind) ->
                            ActionChip(label = label, enabled = true) { onFallback(kind) }
                        }
                        if (hasDetail) {
                            ActionChip(label = "详情 ›", enabled = true) { sheetOpen = true }
                        }
                    }
                }
            }
        }

        // 在原文上方时，尖角在气泡底部（朝下指回原字）。
        if (placement.above) {
            ArrowRow(above = true, localArrowX = arrowLocalX)
        }
    }

    // 半屏详情 Sheet：纯覆盖层，不滚动正文；关闭后阅读位置 / 下划线状态一字不差。
    InkBubbleDetailSheet(
        content = content,
        visible = sheetOpen,
        onDismiss = { sheetOpen = false },
    )
}

/**
 * 半屏详情 Sheet（切片-11 §5：纸白绢帛底 + 1px 金框 + 4dp 金杠拖拽器 + 茶色遮罩，
 * 高度 ≤ 60% 屏；弹起 250ms ease-out，遮罩 200ms，跟手下拉关闭）。
 *
 * 用 [Dialog] 开独立窗口：Sheet 是全屏覆盖层，不能被气泡自身的小尺寸父 Box 裁剪；
 * 正文滚动状态完全不受本层影响，关闭后宿主页码 / 偏移 / 下划线原样保留。
 */
@Composable
private fun InkBubbleDetailSheet(
    content: BubbleContent,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    val maskAlpha = remember { Animatable(if (visible) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var dragOffsetPx by remember { mutableStateOf(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            // Sheet 弹起 250ms ease-out；遮罩 200ms 淡入（切片-03 时长 / 缓动表）。
            progress.animateTo(1f, tween(InkTokens.DURATION_SHEET, easing = InkTokens.cardEnterEasing))
            maskAlpha.animateTo(1f, tween(InkTokens.DURATION_MASK, easing = InkTokens.maskEasing))
        } else {
            maskAlpha.animateTo(0f, tween(InkTokens.DURATION_MASK, easing = InkTokens.maskEasing))
            progress.animateTo(0f, tween(InkTokens.DURATION_SHEET, easing = InkTokens.cardEnterEasing))
        }
    }

    if (progress.value == 0f && !visible) return

    val sheetState = rememberDraggableState { delta ->
        // 只允许向下拖；向上拖不跟手，避免把 Sheet 拉过头。
        if (delta > 0f) dragOffsetPx += delta
    }
    // Sheet 内部点击消费事件，不冒泡到背后遮罩触发关闭。
    val sheetInteraction = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visible) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        ) {
            // 茶色遮罩（暗化 40%），随 Sheet 升起进度联动。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(InkTokens.BG_OVERLAY.copy(alpha = maskAlpha.value * 0.45f)),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 详情占屏 ≤ 60%（切片-11 §5.1）；超出由内部滚动，不把正文挤出可视区。
                    .heightIn(max = maxHeight * SHEET_MAX_HEIGHT_FRACTION)
                    .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                    .clickable(
                        interactionSource = sheetInteraction,
                        indication = null,
                    ) {}
                    .draggable(
                        state = sheetState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            // 下拉超过阈值或快速下甩 → 关闭；否则回弹回原位。
                            val dismiss = dragOffsetPx > SHEET_DISMISS_PX || velocity > SHEET_FLING_VELOCITY
                            if (dismiss) {
                                onDismiss()
                            } else {
                                scope.launch { dragOffsetPx = 0f }
                            }
                        },
                    )
                    .frostedSurface(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(
                        1.dp,
                        InkTokens.BRAND_GOLD,
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 4dp 金色横杠拖拽指示器，两端各一粒金点（切片-11 §5.1）。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(InkTokens.BRAND_GOLD),
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .width(48.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(InkTokens.BRAND_GOLD),
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(InkTokens.BRAND_GOLD),
                    )
                }

                BasicText(
                    text = content.headline.ifBlank { content.token },
                    style = TextStyle(
                        fontFamily = InkFonts.composeFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = InkTokens.TEXT_PRIMARY,
                    ),
                )

                if (!content.phonetic.isNullOrBlank()) {
                    BasicText(
                        text = content.phonetic.trim(),
                        style = TextStyle(
                            fontFamily = InkFonts.composeFamily,
                            fontSize = 12.sp,
                            color = InkTokens.TEXT_SECONDARY,
                        ),
                    )
                }

                BasicText(
                    text = content.detail.ifBlank { content.body },
                    style = TextStyle(
                        fontFamily = InkFonts.composeFamily,
                        fontSize = 14.sp,
                        color = InkTokens.TEXT_PRIMARY,
                        lineHeight = 22.sp,
                    ),
                )

                if (content.source.isNotBlank()) {
                    BasicText(
                        text = "出处 · ${content.source}",
                        style = TextStyle(
                            fontFamily = InkFonts.composeFamily,
                            fontSize = 10.sp,
                            color = InkTokens.TEXT_TERTIARY,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    InkIconButton(onClick = onClick, enabled = enabled) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = InkFonts.composeFamily,
                fontSize = 12.sp,
                color = if (enabled) InkTokens.BRAND_COPPER else InkTokens.TEXT_TERTIARY,
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ArrowRow(above: Boolean, localArrowX: Int) {
    val arrowW = InkTokens.BUBBLE_ARROW_WIDTH
    val arrowH = InkTokens.BUBBLE_ARROW_HEIGHT
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(arrowH),
    ) {
        Canvas(
            modifier = Modifier
                .offset {
                    IntOffset(localArrowX - arrowW.roundToPx() / 2, 0)
                }
                .size(arrowW, arrowH),
        ) {
            val w = arrowW.toPx()
            val h = arrowH.toPx()
            val path = Path().apply {
                if (above) {
                    // 尖角朝下（气泡在原文上方）
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w / 2f, h)
                } else {
                    // 尖角朝上（气泡翻到原文下方）
                    moveTo(0f, h)
                    lineTo(w, h)
                    lineTo(w / 2f, 0f)
                }
                close()
            }
            drawPath(path, InkTokens.BG_SURFACE)
        }
    }
}

/** 变体强调色：人名朱砂、典故 / 词古铜、地名暖金、其余墨色。容器结构统一，只换强调色。 */
private fun variantAccent(kind: String): Color = when (kind) {
    BubbleKind.PERSON -> InkTokens.TEXT_ACCENT
    BubbleKind.ALLUSION, BubbleKind.WORD -> InkTokens.BRAND_COPPER
    BubbleKind.PLACE -> InkTokens.BRAND_GOLD
    else -> InkTokens.TEXT_PRIMARY
}

private fun actionLabel(action: Action): String = when (action) {
    is Action.OpenMap -> "地图"
    is Action.OpenPerson -> "人物"
    is Action.OpenTimeline -> "纪年"
    is Action.OpenRelation -> "关系"
}

private fun resolveBody(content: BubbleContent): String {
    if (content.kind == BubbleKind.MISSING) return content.body
    return content.body.ifBlank { content.detail }
}

/** Sheet 最大高度占屏比例（切片-11 §5.1：60%）。 */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.6f

/** Sheet 下拉关闭阈值（px）；与下甩速度阈值配合，防止小幅拖拽误关。 */
private const val SHEET_DISMISS_PX = 160f
private const val SHEET_FLING_VELOCITY = 600f
