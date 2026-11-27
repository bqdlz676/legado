package io.legado.app.ui.book.read.ink

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.bubble.Action
import io.legado.app.help.bubble.BubbleCandidate
import io.legado.app.help.bubble.BubbleContent
import io.legado.app.help.bubble.BubbleKind
import io.legado.app.ui.components.HalfSheetFallback
import io.legado.app.ui.components.HalfSheetMapper
import io.legado.app.ui.components.InkHalfSheet
import kotlin.math.roundToInt

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
        // BoxWithConstraintsScope 只暴露 maxWidth/maxHeight（Dp）；定位锚点与 BubblePlacer 全按 px 口径，
        // 故经 density 转 px。旧版臆造的 maxWidthPx/maxHeightPx 在此作用域不存在，会编译失败。
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
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
                            fontFamily = InkFonts.composeSansFamily,
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
                            fontFamily = InkFonts.composeTitleFamily,
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
                        text = sourceAnnotated(content.source),
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

    // 半屏详情 Sheet：复用统一 [InkHalfSheet]，按 kind 分派人物 / 字词两套固定顺序；
    // 纯覆盖层，不滚动正文；关闭后阅读位置 / 下划线状态一字不差（切片-11 §5.4）。
    val halfSheetModel = remember(content) { HalfSheetMapper.build(content) }
    InkHalfSheet(
        visible = sheetOpen,
        onDismiss = { sheetOpen = false },
        dialog = true,
        title = content.headline.ifBlank { content.token },
        source = content.source,
        model = halfSheetModel,
        onLinkSelected = { query ->
            // 分区行点击：同名候选回传裸 query，交宿主按候选重查切换。
            content.candidates.firstOrNull { it.query == query }
                ?.let { onCandidateSelected(it) }
        },
        content = { BubbleRawDetail(content = content) },
    )
}

/**
 * 自定义内容槽：地名 / 纪年 / 干支 / 朔望 / 未识等不在 §5.2 / §5.3 两套固定顺序内的
 * 气泡，原样展示拼音 + detail（缺省回退 body）；不另造分区模型，出处脚注由 [InkHalfSheet] 统一渲染。
 */
@Composable
private fun BubbleRawDetail(content: BubbleContent) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!content.phonetic.isNullOrBlank()) {
            BasicText(
                text = content.phonetic.trim(),
                style = TextStyle(
                    fontFamily = InkFonts.composeSansFamily,
                    fontSize = 12.sp,
                    color = InkTokens.TEXT_SECONDARY,
                ),
            )
        }
        val lines = content.detail.ifBlank { content.body }
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) {
            BasicText(
                text = HalfSheetFallback.MISSING_TEXT,
                style = TextStyle(
                    fontFamily = InkFonts.composeFamily,
                    fontSize = 14.sp,
                    color = InkTokens.TEXT_TERTIARY,
                ),
            )
        } else {
            lines.forEach { line ->
                BasicText(
                    text = line,
                    style = TextStyle(
                        fontFamily = InkFonts.composeFamily,
                        fontSize = 14.sp,
                        color = InkTokens.TEXT_PRIMARY,
                        lineHeight = 22.sp,
                    ),
                )
            }
        }
    }
}

/** 出处串：`#编号`（CBDB Person ID 等）走等宽角色；无 `#` 时整段原样返回，不出空白态。 */
private fun sourceAnnotated(source: String): AnnotatedString = buildAnnotatedString {
    append("出处 · ")
    val hash = source.indexOf('#')
    if (hash < 0) {
        append(source)
        return@buildAnnotatedString
    }
    append(source.substring(0, hash))
    withStyle(SpanStyle(fontFamily = InkFonts.composeMonoFamily)) {
        append(source.substring(hash))
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
    // 正文 / 详情双空时落无载占位文案，不出空气泡解释行（C3 不空屏兜底）。
    if (content.kind == BubbleKind.MISSING) {
        return content.body.ifBlank { HalfSheetFallback.MISSING_TEXT }
    }
    return content.body.ifBlank { content.detail }.ifBlank { HalfSheetFallback.MISSING_TEXT }
}
