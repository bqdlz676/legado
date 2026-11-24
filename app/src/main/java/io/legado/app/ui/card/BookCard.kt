package io.legado.app.ui.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 书架页 · 书籍卡片（切片-25「一、书架页 · 书籍卡片」逐字落地）。
 *
 * 纯展示组件：外形 / 阴影 / 圆角 / 颗粒全部走 [CardSurface] 基座，色值只取 [CardTokens]，
 * 不另造 token。对外只暴露三个交互回调：[onClick] 点书进阅读、[onLongPressMenu] 长按唤出菜单、
 * [onSwipeDelete] 右滑删除。碎墨粒子物理不在本卡实现，仅留 200ms 缩放散开占位。
 */

/** 长按菜单三动作（规格：置顶 / 导出批注 / 删除，竖排三行）；菜单 UI 由宿主半屏 Sheet 渲染，本卡只回传枚举。 */
enum class BookCardMenuAction {

    /** 置顶到书架顶部。 */
    Pin,

    /** 导出本书批注。 */
    ExportAnnotation,

    /** 从书架删除。 */
    Delete,
}

@Composable
fun BookCard(
    title: String,
    author: String,
    wordCount: String,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongPressMenu: () -> Unit = {},
    onSwipeDelete: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // 宽=屏宽×0.28：书架竖排书脊卡的列宽占比；适用：窄列内只排竖排书名 + 作者 / 字数两行标注。
    val cardWidthDp = configuration.screenWidthDp * 0.28f
    // 高=屏高×0.42：卡高占屏幕比例；适用：容纳书名 / 作者 / 字数三段竖排与右侧整高进度线。
    val cardHeightDp = configuration.screenHeightDp * 0.42f

    // 已读比例兜底夹紧：越界输入不允许画出进度线外；适用：缓存旧数据或异常进度值。
    val readProgress = progress.coerceIn(0f, 1f)

    var hovered by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var dragDx by remember { mutableStateOf(0f) }

    // 前倾角度：悬浮 5°、点击加深到 8°；适用：graphicsLayer.rotationX 俯仰，模拟书脊前倾立体感。
    val tiltTarget = when {
        pressed -> 8f
        hovered -> 5f
        else -> 0f
    }
    val tilt by animateFloatAsState(
        targetValue = tiltTarget,
        animationSpec = spring(
            dampingRatio = CardTokens.cardSpringDamping,
            stiffness = CardTokens.cardSpringStiffness,
        ),
        label = "bookCardTilt",
    )

    // 删除占位动画：触发后 200ms 缩放 + 透明度散开；碎墨 8-12 粒粒子层待特效模块接入。
    val dismiss = remember { Animatable(1f) }
    LaunchedEffect(dismissing) {
        if (dismissing) {
            dismiss.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 200))
        }
    }

    // 基座五态映射：按下走 Pressed 朱砂暗影，悬停走 Hover 暖金加深，其余常态 Default。
    val cardState = when {
        pressed -> CardState.Pressed
        hovered -> CardState.Hover
        else -> CardState.Default
    }

    // 右滑删除阈值 40dp：超过该位移才判为删除手势，避免普通横向滚动误触；适用：触屏书架列表。
    val swipeThresholdPx = with(density) { 40.dp.toPx() }

    CardSurface(
        state = cardState,
        modifier = modifier
            .width(cardWidthDp.dp)
            .height(cardHeightDp.dp)
            .graphicsLayer(
                // cameraDistance 取 density×12px 基准：距离过近俯仰会畸变，故沿用基座投影口径；角度由 tilt 驱动。
                cameraDistance = density.density * 12f,
                rotationX = tilt,
                scaleX = dismiss.value,
                scaleY = dismiss.value,
                alpha = dismiss.value,
            )
            // 指针进入 / 离开：鼠标悬停置 hovered；触屏按下由 onPress 覆盖。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                            else -> Unit
                        }
                    }
                }
            }
            // 点按 / 长按：onTap 进阅读，onLongPress 唤出三行菜单；press 区间维持 Pressed 态。
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongPressMenu() },
                )
            }
            // 右滑删除：累计横向位移，超过阈值触发 onSwipeDelete 并播 200ms 散开占位。
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragDx = 0f },
                    onHorizontalDrag = { _, dx -> dragDx += dx },
                    onDragEnd = {
                        if (dragDx > swipeThresholdPx) {
                            dismissing = true
                            onSwipeDelete()
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 内边距 8dp：竖排文字与金点不贴圆角边缘；适用：窄书脊卡四周留气口。
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 竖排文字列：书名 → 作者 → 字数，逐字纵向堆叠。
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BookVerticalText(
                        text = title,
                        fontSize = 22.sp,
                        color = CardTokens.ink,
                        fontWeight = FontWeight.Bold,
                    )
                    BookVerticalText(
                        text = author,
                        fontSize = 11.sp,
                        color = CardTokens.stoneBlue,
                        fontWeight = FontWeight.Normal,
                    )
                    BookVerticalText(
                        text = wordCount,
                        fontSize = 10.sp,
                        color = CardTokens.cloudGray,
                        fontWeight = FontWeight.Normal,
                    )
                }

                // 云灰细线 1px 分隔：文字列与进度线之间的竖向隔断；Compose 无 1px 修饰符，沿用全站细线 1.dp 口径。
                Spacer(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(CardTokens.cloudGray),
                )

                // 右侧朱砂进度竖线 2dp：底层未读云灰，顶层已读朱砂按比例自顶部填充。
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(CardTokens.cloudGray),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(readProgress)
                            .background(CardTokens.cinnabar),
                    )
                }
            }

            // 右下落文金点 6dp：卡片签名标记，暖金实心圆；适用：全部书架书脊卡右下角统一识别。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(CardTokens.warmGold),
            )
        }
    }
}

/**
 * 竖排文字：逐字拆成单列 BasicText 纵向堆叠。
 * 为什么不用横排自动换行：规格要求书名 / 作者 / 字数均竖排，CJK 逐字下排即传统书脊排版；
 * 适用：仅本卡三段竖排文字，其他横排卡片不要复用。
 */
@Composable
private fun BookVerticalText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        text.forEach { ch ->
            BasicText(
                text = ch.toString(),
                style = TextStyle(
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    // 宋体衬线：与阅读页东方排印口径一致；适用：书籍标题与标注文字。
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}
