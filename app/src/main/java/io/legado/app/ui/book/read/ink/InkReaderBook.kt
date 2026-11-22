package io.legado.app.ui.book.read.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.legado.app.help.bubble.Action

/**
 * 阅读页闭环入口（模块⑦对外 Compose 面）。
 *
 * 把 [InkReadController]（竖排引擎 / 气泡引擎 / 时空锚接线宿主）与 [InkReaderShell]
 * （手势遮罩 + 气泡层 + 底栏壳）组装到一起：
 *
 * 打开书 → [bindChapter] 锚定书 / 章与候选年代区间、竖排分页 →
 * 竖排逐字绘制 → 短按字 [hitTest] 命中 → [BubbleResolver.resolve] 出气泡；
 * 边缘空白翻页走引擎 prev / next；长按不出气泡。
 *
 * 宿主 Activity / Fragment 只需把当前书章标题与正文文本流传进来，并接好
 * 目录 / 朗读 / 更多 / 进度跳转 / 气泡动作回调即可；引擎细节不在此层外露。
 */
@Composable
fun InkReaderBook(
    bookId: String,
    chapterId: String,
    title: String?,
    body: String,
    onCatalog: () -> Unit,
    onTts: () -> Unit,
    onMenu: () -> Unit,
    onSeek: (Float) -> Unit,
    onBubbleAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = rememberInkReadController()

    // 书 / 章 / 正文变化时重锚时空总线并重排竖排引擎；空文本由引擎与渲染层兜底。
    LaunchedEffect(bookId, chapterId, title, body) {
        controller.bindChapter(bookId, chapterId, title, body)
    }

    InkReaderShell(
        engineView = controller.pageView,
        host = controller,
        bubbleContent = controller.bubbleContent,
        progress = controller.progress,
        pageTick = controller.pageTick,
        scrollTick = controller.scrollTick,
        onCatalog = onCatalog,
        onTts = onTts,
        onMenu = onMenu,
        onSeek = onSeek,
        onBubbleAction = onBubbleAction,
        modifier = modifier,
    )
}
