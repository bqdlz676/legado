package io.legado.app.ui.book.read.ink

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.provider.ChapterProvider

/**
 * 引擎文字面（B4-07，路线 C：引擎层原生 View + 特效层 Compose）。
 *
 * 图层口径（R6 自底向上）：纸面底色之下为本层；本层是 `AndroidView(ContentTextView)`，
 * 透明背景只画字，Compose 层不重绘文字。页对象由宿主出页缝 [ReaderShellHost.currentTextPage]
 * 从 ReadBook 唯一状态源现取，update 内交 [ContentTextView.setContent] 上屏；壳内不开
 * 第二份页文本 / 页序（R5）。
 *
 * 尺寸桥接（设计件 §三.3）：`layoutParams` 宽高填充满 Compose 约束；内边距取
 * [ChapterProvider] 引擎期望的 paddingTop / paddingBottom / paddingLeft / paddingRight，
 * 保证断行宽高与引擎口径一致。手势不在本层消费：View 不注册点击 / 触摸，交互全走 Compose
 * 遮罩层判定后转发。
 *
 * 空态兜底：[ReaderShellHost.textPageState] 归类为非就绪态时，本层叠一层明确空态文案，
 * 不崩、不白屏；四类边界的归类口径见 [InkTextPageState]。
 *
 * @param host 壳 ↔ 宿主桥接实现，出页 / 状态缝唯一入口。
 * @param pageTick 翻页计数：变化即重取出页缝刷新上屏页（与控制器 pageTick 同源）。
 */
@Composable
fun InkTextPageSurface(
    host: ReaderShellHost,
    pageTick: Int,
    modifier: Modifier = Modifier,
) {
    // 翻页 / 进章后按最新页序重取状态；页对象本身仍在 update 内出页缝现取，不在此缓存文本。
    val state = remember(host, pageTick) { host.textPageState() }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                ContentTextView(ctx, null).apply {
                    // 主视图：尺寸变化时由 View 回写 ChapterProvider 可视区口径（对齐旧壳 isMainView 口径）。
                    isMainView = true
                    // 文字面只绘制引擎页，不抢手势；点击 / 长按 / 滑动全由 Compose 遮罩层判定。
                    isClickable = false
                    // 透明背景：纸面底色由下层 Compose 纸面层承担，本层不画底。
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { view ->
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // 内边距对齐引擎排版期望值，断行宽高与 ChapterProvider 口径一致。
                view.setPadding(
                    ChapterProvider.paddingLeft,
                    ChapterProvider.paddingTop,
                    ChapterProvider.paddingRight,
                    ChapterProvider.paddingBottom,
                )
                host.currentTextPage()?.let(view::setContent)
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state !is InkTextPageState.Ready) {
            BasicText(
                text = stringResource(emptyMessageRes(state)),
                modifier = Modifier.align(Alignment.Center),
                style = TextStyle(
                    fontFamily = InkFonts.composeFamily,
                    fontSize = 14.sp,
                    color = InkTokens.TEXT_SECONDARY,
                ),
            )
        }
    }
}

/** 四类空态 → 文案资源映射；就绪态不调用本映射。 */
private fun emptyMessageRes(state: InkTextPageState): Int = when (state) {
    is InkTextPageState.NoBook -> R.string.book_not_exist
    is InkTextPageState.NoChapter -> R.string.content_empty
    is InkTextPageState.EmptyContent -> R.string.content_empty
    is InkTextPageState.LayoutFailed -> R.string.unknown_error
    is InkTextPageState.Ready -> R.string.data_loading
}
