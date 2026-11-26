package io.legado.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.contract.ComponentVisualState
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * BottomSheet 半屏 Sheet：磨砂半透底 + 金杠拖拽指示器 + 30% / 60% / 90% 三段高度，
 * spring 升起 250ms，跟手下拉关闭，背后茶玻遮罩联动，未过阈值关闭回弹。
 *
 * 七态由内容槽承载：默认 / 加载 / 空数据 / 离线；悬浮 / 点击 / 禁用体现在槽内按钮。
 * 平台真实 backdrop blur 由窗口层（Android window blur behind）mediate，本组件只给
 * 磨砂半透底色 + 金色 1px 顶边 + 细分隔线，不依赖 WebView。
 */
object SheetDetent {

    /** 三段高度占屏高比例 */
    val fractions: List<Float> = ComponentPalette.sheetDetents

    /** 最接近的分段比例 */
    fun nearest(fraction: Float): Float {
        val clamped = fraction.coerceIn(fractions.first(), fractions.last())
        return fractions.minBy { abs(it - clamped) }
    }

    /**
     * 是否关闭：拖出高度低于低段阈值，或下拉速度足够快。
     * @param fraction 当前高度占屏比例
     * @param velocityPxPerSec 垂直下拉速度（正值向下）
     */
    fun shouldDismiss(fraction: Float, velocityPxPerSec: Float): Boolean {
        val low = fractions.first()
        return fraction < low - ComponentPalette.SHEET_DISMISS_THRESHOLD ||
            (fraction < low && velocityPxPerSec > 600f)
    }

    /** 遮罩透明度与 Sheet 升起进度联动（0 落下 → 1 全升） */
    fun maskAlpha(progress: Float): Float = progress.coerceIn(0f, 1f)

    /** 入参 detent 合法性收口：非法值回落到中段 60% */
    fun normalize(detent: Float): Float = fractions.firstOrNull { abs(it - detent) < 0.01f } ?: fractions[1]
}

/**
 * B19 浮层离线分支动作裁决（纯函数 / 纯数据，普通 JVM 单测可断言，不依赖 Compose）。
 *
 * 背景：BottomSheet / HalfSheet 是通用浮层组件，自身不持有数据与网络请求，离线「重试」
 * 的真实发起函数在宿主侧。
 *  - [Wired]：宿主传入了重试 / 刷新回调，离线「重试」按钮接到该回调；点击经
 *    [InkEmptyState.onAction] → [InkButton.onClick] → `rememberInkGesture` 手势链路上溯到宿主发起函数；
 *  - [HintOnly]：宿主未传回调（null），组件内没有可接的重试入口；按 B19 判据 3 不得保留
 *    「可点但空动作」，回落到纯提示文案（不渲染操作按钮），不抛异常、不静默挂空 lambda。
 */
internal sealed interface SheetOfflineAction {
    /** 已接到宿主重试回调；[onAction] 即重试 / 刷新入口，不是空 lambda。 */
    data class Wired(val onAction: () -> Unit) : SheetOfflineAction

    /** 无可接回调：纯提示兜底，无操作按钮。 */
    data object HintOnly : SheetOfflineAction
}

/** 离线分支动作裁决：非空回调接 [SheetOfflineAction.Wired]，null 回落 [SheetOfflineAction.HintOnly]。 */
internal fun resolveSheetOfflineAction(onRetry: (() -> Unit)?): SheetOfflineAction =
    if (onRetry != null) SheetOfflineAction.Wired(onRetry) else SheetOfflineAction.HintOnly

@Composable
fun InkBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialDetent: Float = ComponentPalette.SHEET_DETENT_MID,
    state: ComponentVisualState = ComponentVisualState.DEFAULT,
    title: String = "",
    // B19：宿主重试 / 刷新入口；null 表示本浮层无可接回调，离线分支回落纯提示（判据 3）。
    onRetry: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var detent by remember { mutableStateOf(SheetDetent.normalize(initialDetent)) }
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(progress.value),
    ) {
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // 背后茶玻遮罩，点击关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    ComponentTokens.bgOverlay.copy(alpha = SheetDetent.maskAlpha(progress.value)),
                )
                .then(rememberInkGesture(enabled = true, onClick = onDismiss).gestureModifier),
        )

        val sheetHeightPx = (maxHeightPx * detent - dragOffsetPx).coerceIn(
            maxHeightPx * ComponentPalette.SHEET_DETENT_LOW * 0.6f,
            maxHeightPx,
        )
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
                .background(ComponentTokens.bgSurface.copy(alpha = 0.88f))
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
            // 金杠拖拽指示器
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(ComponentTokens.sheetGrabberWidth)
                        .height(ComponentTokens.sheetGrabberHeight)
                        .clip(RoundedCornerShape(50))
                        .background(ComponentTokens.brandGold),
                )
            }

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
                            if (SheetDetent.shouldDismiss(fraction, velocity)) {
                                onDismiss()
                            } else {
                                detent = SheetDetent.nearest(fraction)
                            }
                            val start = dragOffsetPx
                            scope.launch {
                                val anim = Animatable(start)
                                anim.animateTo(0f, ComponentTokens.sheetSpring()) {
                                    dragOffsetPx = value
                                }
                            }
                        },
                    )
                    .padding(horizontal = 16.dp)
                    .alpha(state.contentAlpha),
            ) {
                when (state) {
                    ComponentVisualState.LOADING -> Column(Modifier.padding(16.dp)) {
                        InkLoadingDots()
                    }
                    ComponentVisualState.EMPTY -> InkEmptyState(message = "暂无相关内容", compact = true)
                    ComponentVisualState.OFFLINE -> when (
                        val offlineAction = resolveSheetOfflineAction(onRetry)
                    ) {
                        // B19 判据 1/2：宿主重试回调原样接到空态动作入口，点击上溯到宿主发起函数。
                        is SheetOfflineAction.Wired -> InkEmptyState(
                            message = "当前离线，展示缓存内容",
                            compact = true,
                            offline = true,
                            onAction = offlineAction.onAction,
                        )
                        // B19 判据 3：无可接重试回调，动作标签置空 → 不渲染可点按钮，纯提示兜底。
                        SheetOfflineAction.HintOnly -> InkEmptyState(
                            message = "当前离线，展示缓存内容",
                            compact = true,
                            offline = true,
                            actionLabel = "",
                        )
                    }
                    else -> content()
                }
            }
        }
    }
}
