package io.legado.app.ui.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.legado.app.ui.components.rememberInkGesture
import io.legado.app.ui.components.contract.ComponentVisualState

/**
 * 空状态卡变体（切片-25「十一、空状态卡」三分支逐字落地，口径已按规格原文纠正）。
 *
 * - [EmptyShelf]：暖金实心点 32dp 居中 + 「还没有书」竖排 18sp 墨色 + 12sp 云灰引导 + 暖金按钮「去发现页看看」圆角 20。
 * - [NotFound]：淡暖金金点 + 三行随机推荐书名；适用：检索无结果时给替代出口。
 * - [Offline]：朱砂断环（顶部 6° 断口，描边不闭合）+ 「本地已有 xx 本书可离线阅读」；适用：断网但本地有缓存。
 *
 * 外形 / 阴影 / 圆角 / 颗粒全部走 [CardSurface] 基座，色值只取 [CardTokens]；
 * 图标为 [Canvas] 自绘金点 / 断环，不引位图资源。
 *
 * B18 状态口径：legacy [CardState] 只承担阴影 / 抬升 / 骨架等视觉档位；「能不能点」不另立第二套布尔，
 * 由 [emptyCardVisualState] 统一映射到契约六态，事件许可取 [ComponentVisualState.eventsAllowed]。
 */
enum class EmptyKind { EmptyShelf, NotFound, Offline }

/**
 * B18：空态卡（变体 × legacy 基座态）→ 契约六态映射（纯函数，普通 JVM 单测可断言）。
 *
 * 优先级：禁用 > 离线 > 空变体 > 加载 > 默认。
 *  - [CardState.Disabled] → [ComponentVisualState.DISABLED]：整卡压淡且禁点；
 *  - [EmptyKind.Offline] → [ComponentVisualState.OFFLINE]：契约口径不压淡（contentAlpha=1f）、禁点；
 *  - [EmptyKind.NotFound] → [ComponentVisualState.EMPTY]：不压淡、允许事件；
 *  - [CardState.Loading] → [ComponentVisualState.LOADING]：不压淡、阻止事件；
 *  - 其余 → [ComponentVisualState.DEFAULT]。
 *
 * 〔0.35 裁决，B18 判据 5〕契约 OFFLINE.contentAlpha=1f 是整组件唯一透明度口径，枚举语义不得改动；
 * 旧实现把离线朱砂文字 / 断环另压 0.35f，属于与契约并存的第二套压淡来源，已移除。离线不可交互性
 * 只由事件闸表达，不再靠局部文字透明度暗示；「极淡」观感改由 1.5dp 细描边与小环几何承担。
 */
internal fun emptyCardVisualState(
    kind: EmptyKind,
    state: CardState,
): ComponentVisualState = when {
    state == CardState.Disabled -> ComponentVisualState.DISABLED
    kind == EmptyKind.Offline -> ComponentVisualState.OFFLINE
    kind == EmptyKind.NotFound -> ComponentVisualState.EMPTY
    state == CardState.Loading -> ComponentVisualState.LOADING
    else -> ComponentVisualState.DEFAULT
}

/**
 * 空状态卡：三变体共用同一居中竖向排版，金点 / 断环槽位 32dp，元素间距 16dp。
 *
 * @param kind 变体；内部 `when` 分派图标 / 文案 / 是否渲染行动按钮。
 * @param onAction 行动按钮回调；仅 [EmptyKind.EmptyShelf] 渲染按钮，其余变体传 null 即可。
 * @param state 基座五态；Loading 态由 [CardSurface] 自绘骨架灰块 + 暖金斜光带。
 * @param modifier 外部对齐 / 边距；卡片宽度由宿主约束，本组件内只铺内容槽。
 */
@Composable
fun EmptyStateCard(
    kind: EmptyKind,
    onAction: (() -> Unit)? = null,
    state: CardState = CardState.Default,
    modifier: Modifier = Modifier,
) {
    // B18：唯一事件许可来源——legacy 基座态 + 变体映射到契约六态，卡片内不再另立「能不能点」布尔。
    val visual = emptyCardVisualState(kind, state)
    CardSurface(
        state = state,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 16dp 内边距：规格指定空态四周留白，文案与图标不贴 16dp 圆角边缘；适用：三变体统一纸感呼吸。
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // 16dp 元素间距：规格指定图标 / 文案 / 按钮之间的竖向间隔；适用：居中堆叠的空态排版。
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (kind) {
                EmptyKind.EmptyShelf -> {
                    // 32dp 暖金实心点：规格「金点 32dp 居中」；适用：书架无书的主视觉标识。
                    GoldDot(fillColor = CardTokens.warmGold)
                    // 「还没有书」竖排 18sp 墨色：规格指定主文案；逐字正立堆叠，不旋转整体文本。
                    VerticalText(
                        text = "还没有书",
                        fontSize = CardTokens.text18,
                        color = CardTokens.ink,
                        fontWeight = FontWeight.Bold,
                    )
                    BasicText(
                        text = "从发现页挑一本开始阅读吧",
                        style = TextStyle(
                            color = CardTokens.cloudGray,
                            // 12sp：规格空书架引导语字号；适用：主标题之下的次级说明，规格未给文案，此为兜底。
                            fontSize = CardTokens.text12,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    // 暖金按钮「去发现页看看」圆角 20；适用：唯一可点的空态出口，回调由 [onAction] 注入。
                    EmptyActionButton(
                        label = "去发现页看看",
                        visual = visual,
                        onClick = { onAction?.invoke() },
                    )
                }

                EmptyKind.NotFound -> {
                    // 32dp 淡暖金金点：规格「金点淡暖金」；alpha 0.55 为实心暖金在纸白上的降淡档，
                    // 介于实心与纸白之间，表达「无结果但非错误」，不抢推荐书名层级；适用：未找到变体。
                    // F-73 待规格化：淡金 alpha 档位 0.55 无规格出处，为写码自拟降淡档（审计 表三#3，待重做后回填规格出处）
                    GoldDot(fillColor = CardTokens.warmGold.copy(alpha = 0.55f))
                    // 三行随机推荐书名：规格未给字号，对齐搜索结果卡书名档取 14sp 墨色；
                    // 候选池为组件兜底静态列表，宿主可在后续把真实推荐书单注入本槽位。
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        fallbackRecommendations.forEach { title ->
                            BasicText(
                                text = title,
                                style = TextStyle(
                                    color = CardTokens.ink,
                                    fontSize = CardTokens.text14,
                                    fontFamily = FontFamily.Serif,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                    }
                }

                EmptyKind.Offline -> {
                    // 32dp 朱砂断环：规格「极淡朱砂断环」；B18 裁决：不再另压 0.35f 透明度（契约 OFFLINE=1f 单源），
                    // 极淡观感由 1.5dp 细描边 + 小环几何承担；断口 6° 对齐 ic_state_offline 口径。
                    BrokenRingIcon()
                    BasicText(
                        text = "本地已有 xx 本书可离线阅读",
                        style = TextStyle(
                            // B18：离线文字不再局部压淡；整组件透明度统一读契约 OFFLINE.contentAlpha=1f。
                            color = CardTokens.cinnabar,
                            // 12sp：离线提示取次级字号档；「xx」为册数占位，由宿主数据注入替换。
                            fontSize = CardTokens.text12,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}

/** 未找到变体的兜底推荐书单；适用：宿主尚未接入真实推荐流时占位，三行刚好对应规格「三行随机推荐书名」。 */
// F-73 待规格化：兜底推荐书单来源规则无规格，当前为静态占位列表（审计 表三#3，待重做后回填规格出处）
private val fallbackRecommendations = listOf("诗经·周南", "史记·项羽本纪", "陶渊明集")

/**
 * 暖金行动按钮：圆角 20dp，纸白字压暖金底。
 * 为什么实心暖金而非描边：空态出口需一眼可点，实心比描边更有召唤感；
 * 圆角 20dp 沿用规格空态按钮口径，区别于卡片 16dp 大圆角。
 *
 * B18：点击一律走 [rememberInkGesture] 墨触闸，许可来自契约 [visual]；
 * 不可点时底 / 字切云灰档并按 [ComponentVisualState.contentAlpha] 压淡，
 * 不允许「看起来能点、点不动」的静默失效。
 */
@Composable
private fun EmptyActionButton(
    label: String,
    visual: ComponentVisualState,
    onClick: () -> Unit,
) {
    val eventsAllowed = visual.eventsAllowed
    val gesture = rememberInkGesture(enabled = eventsAllowed, onClick = onClick)
    val background = if (eventsAllowed) CardTokens.warmGold else CardTokens.cloudGray
    val foreground = if (eventsAllowed) CardTokens.cardPaper else CardTokens.ink
    Box(
        modifier = Modifier
            .then(gesture.gestureModifier)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .alpha(visual.contentAlpha)
            // 横向 16dp / 纵向 10dp 内边距：规格只给圆角未给按钮高，纵向 10dp 为兜底值，保证文字不贴暖金块边缘。
            // F-73 待规格化：按钮内边距 16×10dp 无规格出处，为写码自拟兜底值（审计 表三#3，待重做后回填规格出处）
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = foreground,
                // 12sp：按钮内文字取次级字号，避免与 18sp 竖排主标题争抢层级；适用：暖金块上的标签。
                fontSize = CardTokens.text12,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/**
 * 金点：32dp [Canvas] 实心圆，颜色由调用方按变体传入（实心暖金 / 淡暖金）。
 * 为什么用 Canvas 而非圆形 Box：规格图标统一走自绘口径，便于后续在点上叠加辉光而不改外层布局。
 */
@Composable
private fun GoldDot(fillColor: Color) {
    Canvas(modifier = Modifier.size(32.dp)) {
        drawCircle(
            color = fillColor,
            radius = size.minDimension / 2f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

/**
 * 极淡朱砂断环：32dp [Canvas] 描边圆环，顶部居中 6° 断口，不闭合、不填充。
 * 断口口径对齐 `ic_state_offline.xml`：缺口位于正上方（θ=270°）半宽 3°，
 * 即起角 273°、扫过 354°，余 6° 留空；stroke 1.5dp、圆头端点，朱砂全色，极淡观感由细描边几何承担。
 */
@Composable
private fun BrokenRingIcon() {
    Canvas(modifier = Modifier.size(32.dp)) {
        val stroke = Stroke(
            // 1.5dp 线宽：与 ic_state_offline 描边同口径；适用：32dp 小环轮廓不糊成块、也不过细断线。
            // F-73 待规格化：断环 stroke 宽度 1.5dp 无规格出处，为写码自拟（审计 表三#3，待重做后回填规格出处）
            width = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val w = size.width
        val h = size.height
        drawArc(
            color = CardTokens.cinnabar,
            // B18：断环不再另压 0.35f；6° 缺口几何（273°/354°）为写码自拟兜底（F-73 待规格化）。
            // 起角 273°：断口右缘；顺时针扫 354°，到 267° 左缘收口，正上方留 6° 缺口。
            startAngle = 273f,
            sweepAngle = 354f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.2f),
            size = Size(w * 0.6f, h * 0.6f),
            style = stroke,
        )
    }
}

/**
 * 竖排文字：逐字自上而下堆叠成一列（本文件私有实现，口径照 ClassicCard.VerticalText，不改 ClassicCard）。
 *
 * 为什么不用旋转整体文本：旋转会让汉字侧倒，竖排要求字字正立、从上到下阅读；
 * 逐字 Column 堆叠保留正立字形，也便于单列截断。适用：「还没有书」短主文案。
 *
 * @param text 原始字符串；按字符拆分逐字渲染。
 * @param fontSize 字号；适用：由调用方按规格档位传入 18sp。
 * @param color 文字色；适用：取基座语义色，不在此函数内定色。
 * @param fontWeight 字重；适用：主标题传 [FontWeight.Bold]，其余为常规。
 */
@Composable
private fun VerticalText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        text.forEach { ch ->
            BasicText(
                text = ch.toString(),
                style = TextStyle(
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    // 宋体族：东方印刷字口径；平台 Serif 兜底，未注入定制字体时不崩。
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    // 行高 = 字号 × 1.25 兜底；适用：竖排字距不过挤也不过松，规格未给具体行距。
                    lineHeight = fontSize * 1.25f,
                ),
            )
        }
    }
}
