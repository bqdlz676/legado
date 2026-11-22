package io.legado.app.ui.book.read.ink

import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.help.bubble.Action
import io.legado.app.help.bubble.BubbleContent
import kotlin.math.roundToInt

/**
 * 阅读页 Compose 壳（模块⑦）。
 *
 * 结构（契约第六节）：底层 [AndroidView] 挂排版引擎 View（一行不改），
 * 其上是手势遮罩层、气泡层，最顶层是 60dp Compose 底栏。
 *
 * 手势分区铁律：
 *  - 左 / 右 1/5：点中字 → 先出气泡；点空白 → 翻页；滑动 → 回 View fling。
 *  - 中间 3/5：点 = 查字 / 气泡；长按 = 选中；滑 = 翻页。
 *  - 底栏 60dp：Compose 全吃。
 *  - [detectTapGestures] 只吃 tap / longPress，不注册任何 drag 检测器，
 *    滑动不消费，必须回 View；真机若 View 收不到滑动，切 [DispatchMode.ViewBridge]。
 *
 * @param engineView 排版引擎 View（ReadView / ContentTextView 层级），由宿主创建传入。
 * @param host 壳 ↔ 宿主桥接实现。
 * @param bubbleContent core 解析出的气泡内容；null 表示无气泡（含兜底 Missing 态）。
 * @param pageTick 翻页计数：变化即关闭气泡。
 * @param scrollTick 滚动计数：变化即关闭气泡。
 * @param dispatchMode 触摸派发模式；默认 ComposeOverlay，真机异常时切 ViewBridge。
 */
@Composable
fun InkReaderShell(
    engineView: View,
    host: ReaderShellHost,
    bubbleContent: BubbleContent?,
    progress: Float,
    pageTick: Int,
    scrollTick: Int,
    onCatalog: () -> Unit,
    onTts: () -> Unit,
    onMenu: () -> Unit,
    onSeek: (Float) -> Unit,
    onBubbleAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    dispatchMode: DispatchMode = DispatchMode.ComposeOverlay,
    state: ReaderShellState = rememberReaderShellState(dispatchMode),
    topSafePx: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val bottomBarPx = with(density) { InkTokens.BOTTOM_BAR_HEIGHT.toPx() }
    val gapPx = with(density) { InkTokens.BUBBLE_GAP.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val topSafePxVal = with(density) { topSafePx.toPx() }

    // 壳侧包装：命中先记录锚点（气泡定位用），再交宿主解析 / 关闭。
    val shellHost = remember(host, state) {
        object : ReaderShellHost by host {
            override fun requestBubble(hit: CharHit) {
                state.showBubble(hit)
                host.requestBubble(hit)
            }

            override fun onCenterTap(x: Float, y: Float, hit: CharHit?) {
                if (hit != null) state.showBubble(hit) else state.dismissBubble()
                host.onCenterTap(x, y, hit)
            }

            override fun dismissBubble() {
                state.dismissBubble()
                host.dismissBubble()
            }
        }
    }

    // 翻页 / 滚动立刻关闭气泡（0ms）
    LaunchedEffect(pageTick, scrollTick) {
        state.dismissBubble()
    }

    // ViewBridge 降级通道：挂 / 摘引擎 View 监听
    val bridge = remember { ViewGestureBridge(shellHost, bottomBarPx) }
    LaunchedEffect(engineView, dispatchMode) {
        if (dispatchMode == DispatchMode.ViewBridge) {
            bridge.attach(engineView)
        } else {
            bridge.detach(engineView)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(InkTokens.bgPage),
    ) {
        val viewW = maxWidth.value
        val viewH = maxHeight.value

        // 1) 排版引擎 View（底层，不动）
        AndroidView(
            factory = { engineView },
            modifier = Modifier.fillMaxSize(),
        )

        // 2) 手势遮罩层（仅 ComposeOverlay 模式吃点 / 长按；滑动不消费）
        if (dispatchMode == DispatchMode.ComposeOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(shellHost, bottomBarPx) {
                        detectTapGestures(
                            onTap = { offset ->
                                val zone = ZoneClassifier.classify(
                                    x = offset.x,
                                    y = offset.y,
                                    width = viewW,
                                    height = viewH,
                                    bottomBarPx = bottomBarPx,
                                )
                                GestureRouter.onTap(shellHost, zone, offset.x, offset.y)
                            },
                            onLongPress = { offset ->
                                val zone = ZoneClassifier.classify(
                                    x = offset.x,
                                    y = offset.y,
                                    width = viewW,
                                    height = viewH,
                                    bottomBarPx = bottomBarPx,
                                )
                                GestureRouter.onLongPress(shellHost, zone, offset.x, offset.y)
                            },
                        )
                    },
            )
        }

        // 3) 气泡层
        val bubbleState = state.bubbleState
        if (bubbleState is BubbleState.Visible && bubbleContent != null) {
            BubbleOverlay(
                anchor = bubbleState.anchor,
                content = bubbleContent,
                viewW = viewW,
                viewH = viewH,
                gapPx = gapPx,
                topSafePx = topSafePxVal,
                bottomReservedPx = bottomBarPx,
                marginPx = marginPx,
                onAction = onBubbleAction,
                onDismiss = { shellHost.dismissBubble() },
            )
        }

        // 4) 底栏（最顶层，Compose 全吃）
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            InkBottomBar(
                visible = state.barVisible,
                progress = progress,
                onCatalog = onCatalog,
                onTts = onTts,
                onMenu = onMenu,
                onSeek = onSeek,
            )
        }
    }
}

@Composable
private fun BubbleOverlay(
    anchor: BubbleAnchor,
    content: BubbleContent,
    viewW: Float,
    viewH: Float,
    gapPx: Float,
    topSafePx: Float,
    bottomReservedPx: Float,
    marginPx: Float,
    onAction: (Action) -> Unit,
    onDismiss: () -> Unit,
) {
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

    val placement = remember(anchor, bubbleSize, viewW, viewH) {
        BubblePlacement.place(
            anchor = anchor,
            bubbleW = bubbleSize.width.toFloat().coerceAtLeast(1f),
            bubbleH = bubbleSize.height.toFloat().coerceAtLeast(1f),
            viewW = viewW,
            viewH = viewH,
            gapPx = gapPx,
            topSafePx = topSafePx,
            bottomReservedPx = bottomReservedPx,
            marginPx = marginPx,
        )
    }

    val maxWidthDp = with(LocalDensity.current) { (viewW * InkTokens.BUBBLE_MAX_WIDTH_RATIO).toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(placement.x.roundToInt(), placement.y.roundToInt())
            }
            // 点气泡空白处关闭；chips 自身 clickable 消费事件，不会冒泡到这里
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
    ) {
        InkBubble(
            content = content,
            placement = placement,
            onAction = onAction,
            maxWidthDp = maxWidthDp,
            modifier = Modifier.onSizeChanged { bubbleSize = it },
        )
    }
}
