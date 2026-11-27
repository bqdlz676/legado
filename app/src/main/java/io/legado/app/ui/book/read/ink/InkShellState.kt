package io.legado.app.ui.book.read.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable

/**
 * 阅读壳内部状态：气泡显隐、底栏显隐、派发模式。
 *
 * 〔不变式〕
 * 1. [dispatchMode] 构造时固定，生命周期内不改写；派发模式只决定触摸事件落在 Compose 层
 *    还是引擎 View 桥接层，不参与开合判定。
 * 2. [bubbleState] 恒为 [BubbleState.Hidden] 或 [BubbleState.Visible] 之一；Visible 必带
 *    非空锚点 [BubbleAnchor] 与命中 [CharHit]，Hidden 不持命中。
 * 3. [barVisible] 为布尔态；底栏收起 / 展开只翻转该布尔，不连带气泡状态。
 * 4. 宿主解析结果通过外部灌入；锚点由壳在命中时自行记录，本状态不解析命中、不碰引擎。
 *
 * 〔气泡状态机〕
 * - Hidden → Visible：[showBubble] 记录命中锚点；
 * - Visible → Hidden：[dismissBubble] 点空白 / 翻页 / 滚动清气泡；
 * - Visible → Visible：重复 [showBubble] 以新命中覆盖锚点；
 * - Hidden → Hidden：冗余 [dismissBubble] 为安全空操作，累加 [redundantHiddenDismissCount]，
 *   不改状态、不抛异常。
 *
 * 〔底栏状态机〕
 * - 可见 ⇄ 收起：[toggleBar] 翻转；
 * - 任意态 → 可见：[showBar] 置可见，重复调用为幂等空操作；
 * - 非法 / 冗余处置均回退当前安全态，不抛异常。
 */
@Stable
class ReaderShellState(
    val dispatchMode: DispatchMode,
) {
    var bubbleState: BubbleState by mutableStateOf<BubbleState>(BubbleState.Hidden)
        private set

    var barVisible: Boolean by mutableStateOf(true)

    /** Hidden 态下冗余关闭气泡的累计次数；可观测计数，冗余调用不抛异常。 */
    var redundantHiddenDismissCount: Int = 0
        private set

    /** 记录命中并弹出气泡（锚点取自原字矩形）。 */
    fun showBubble(hit: CharHit) {
        bubbleState = BubbleState.Visible(BubbleAnchor(hit), hit)
    }

    /** 0ms 关闭气泡；Hidden 态重复关闭记一次冗余处置，不改状态。 */
    fun dismissBubble() {
        if (bubbleState is BubbleState.Hidden) {
            redundantHiddenDismissCount++
            return
        }
        bubbleState = BubbleState.Hidden
    }

    fun toggleBar() {
        barVisible = !barVisible
    }

    fun showBar() {
        barVisible = true
    }
}

/**
 * 组合层薄包装：记住当前派发模式下的壳状态；纯判定在 [ReaderShellState] 内，
 * 本函数只做 Compose 侧 remember 接线，不持 Activity / Context。
 */
@Composable
fun rememberReaderShellState(
    dispatchMode: DispatchMode = DispatchMode.ComposeOverlay,
): ReaderShellState = remember(dispatchMode) { ReaderShellState(dispatchMode) }
