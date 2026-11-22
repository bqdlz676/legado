package io.legado.app.ui.book.read.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable

/**
 * 阅读壳内部状态：气泡显隐、底栏显隐、派发模式。
 * 宿主解析结果通过 [bubbleContent] 外部灌入；锚点由壳在命中时自行记录。
 */
@Stable
class ReaderShellState(
    val dispatchMode: DispatchMode,
) {
    var bubbleState: BubbleState by mutableStateOf<BubbleState>(BubbleState.Hidden)
        private set

    var barVisible: Boolean by mutableStateOf(true)

    /** 记录命中并弹出气泡（锚点取自原字矩形）。 */
    fun showBubble(hit: CharHit) {
        bubbleState = BubbleState.Visible(BubbleAnchor(hit), hit)
    }

    /** 0ms 关闭气泡。 */
    fun dismissBubble() {
        bubbleState = BubbleState.Hidden
    }

    fun toggleBar() {
        barVisible = !barVisible
    }

    fun showBar() {
        barVisible = true
    }
}

@Composable
fun rememberReaderShellState(
    dispatchMode: DispatchMode = DispatchMode.ComposeOverlay,
): ReaderShellState = remember(dispatchMode) { ReaderShellState(dispatchMode) }
