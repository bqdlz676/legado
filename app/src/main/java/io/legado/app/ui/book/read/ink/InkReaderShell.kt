package io.legado.app.ui.book.read.ink

import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.help.bubble.Action
import io.legado.app.help.bubble.BubbleCandidate
import io.legado.app.help.bubble.BubbleContent

/**
 * 阅读页 Compose 壳（U5-A）。
 *
 * 结构（自底向上）：底层 [AndroidView] 挂分页引擎自绘 View；其上是手势遮罩层、
 * 气泡层；最顶层是 60dp Compose 底栏。全部 Compose 自绘，不启用 Material 默认
 * ripple / elevation / shared-axis 转场。
 *
 * 手势分区铁律：
 *  - 左 / 右 1/5：点中字 → 先出气泡；点空白 → 翻页；滑动 → 回 View fling。
 *  - 中间 3/5：点 = 查字 / 气泡；长按 = 选中；滑 = 翻页。
 *  - 底栏 60dp：Compose 全吃。
 *  - [detectTapGestures] 只吃 tap / longPress，不注册任何 drag 检测器，
 *    滑动不消费，必须回 View；真机若 View 收不到滑动，切 [DispatchMode.ViewBridge]。
 *
 * @param engineView 分页引擎自绘 View，由宿主控制器创建传入。
 * @param host 壳 ↔ 宿主桥接实现。
 * @param bubbleContent 解析出的气泡内容；null 表示无气泡（含兜底 Missing 态）。
 * @param pageTick 翻页计数：变化即关闭气泡。
 * @param scrollTick 滚动计数：变化即关闭气泡。
 * @param dispatchMode 触摸派发模式；默认 ComposeOverlay，真机异常时切 ViewBridge。
 * @param onFallback 兜底入口回调（"web" 联网 / "notebook" 生字本）；保留位，
 *                   具体气泡内入口由 U5-B 的 InkBubble 按 allowWebSearch / allowNotebook 唤起。
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
    onCandidateSelect: (BubbleCandidate) -> Unit,
    modifier: Modifier = Modifier,
    onFallback: (String) -> Unit = {},
    dispatchMode: DispatchMode = DispatchMode.ComposeOverlay,
    state: ReaderShellState = rememberReaderShellState(dispatchMode),
    topSafePx: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val bottomBarPx = with(density) { InkTokens.BOTTOM_BAR_HEIGHT.toPx() }

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

    // 宿主气泡内容被外部关闭（返回键 / 翻页 / 进度跳转 / 章切换）时，同步清掉壳侧镜像状态，
    // 避免 state 残留 Visible 锚点；内容非空时不动，展示状态仍由宿主灌入。
    LaunchedEffect(bubbleContent) {
        if (bubbleContent == null) state.dismissBubble()
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
            .background(InkTokens.BG_PAGE),
    ) {
        val viewW = maxWidth.value
        val viewH = maxHeight.value

        // 1) 分页引擎自绘 View（底层，不动）
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
                                (engineView as? VerticalPageView)?.notifyInteraction()
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
                                (engineView as? VerticalPageView)?.notifyInteraction()
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

        // 3) 气泡层：定位 / 尖角 / 候选循环由 U5-B 的 InkBubble 按锚点自绘。
        val bubbleState = state.bubbleState
        if (bubbleState is BubbleState.Visible && bubbleContent != null) {
            InkBubble(
                content = bubbleContent,
                anchor = bubbleState.anchor,
                onAction = onBubbleAction,
                onCandidateSelected = onCandidateSelect,
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
