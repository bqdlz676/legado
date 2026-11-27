package io.legado.app.ui.book.read.ink

import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.entities.TextChapter

/**
 * 旧分页引擎 [DataSource] 适配桥（B4-07）。
 *
 * ContentTextView.CallBack.pageFactory 所需的 TextPageFactory 以 DataSource 为构造入参；
 * 本适配把 [ReadBook] 唯一状态源只读映射到 DataSource 口径：当前 / 前 / 后章、是否滚动、
 * 章边界判定、内容刷新回调。章 / 页 / 进度真值仍在 ReadBook，本类不另存正文镜像（R5）。
 *
 * @param onUpContent 内容刷新回调：交控制器从 ReadBook 回源当前章 / 页后重排上屏。
 */
internal class ReadBookTextDataSource(
    private val onUpContent: () -> Unit,
) : DataSource {

    override val currentChapter: TextChapter? get() = ReadBook.textChapter(0)

    override val nextChapter: TextChapter? get() = ReadBook.textChapter(1)

    override val prevChapter: TextChapter? get() = ReadBook.textChapter(-1)

    /** 新壳 C 阶段文字面为静态单页上屏，不走滚动翻页；滚动通道归后续翻页动画批次。 */
    override val isScroll: Boolean get() = false

    override fun hasNextChapter(): Boolean =
        ReadBook.durChapterIndex < ReadBook.simulatedChapterSize - 1

    override fun hasPrevChapter(): Boolean = ReadBook.durChapterIndex > 0

    override fun upContent(relativePosition: Int, resetPageOffset: Boolean) {
        onUpContent()
    }
}
