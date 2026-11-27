package io.legado.app.ui.book.read.ink

/**
 * 文字面上屏状态（B4-07，全链路第一个可运行里程碑 M1）。
 *
 * 状态真值唯一在 [io.legado.app.model.ReadBook]（R5）：本件只做「书 / 章 / 页是否就绪」
 * 的纯逻辑归类，不持有第二份章文本 / 页文本 / 进度镜像；页对象仍由控制器出页缝
 * [InkReadController.currentTextPage] 现取，本状态只记页序 / 页数口径供 Compose 文字面
 * 判定就绪态或空态文案。
 *
 * 四类边界兜底（不崩、不白屏、不静默吞异常）：
 *  - [NoBook]：书未打开 / 记录缺失；
 *  - [NoChapter]：书在但当前章未就绪（章节列表缺失 / 章加载中）；
 *  - [EmptyContent]：章已就绪但正文 / 段项为空；
 *  - [LayoutFailed]：章已加载但排版引擎未产出任何可用页。
 */
sealed interface InkTextPageState {

    /** 无书：[io.legado.app.model.ReadBook.book] 为空。 */
    data object NoBook : InkTextPageState

    /** 无章：书在但 [io.legado.app.model.ReadBook.curTextChapter] 为空。 */
    data object NoChapter : InkTextPageState

    /** 内容为空：章已就绪，但当前页正文为空白。 */
    data object EmptyContent : InkTextPageState

    /** 排版失败：章已加载，但可用页数为 0。 */
    data object LayoutFailed : InkTextPageState

    /**
     * 就绪：当前页可上屏。
     *
     * @property pageIndex 当前页序号（夹取在 0..pageCount-1 内，口径回源 ReadBook.durPageIndex）。
     * @property pageCount 当前章总页数。
     */
    data class Ready(val pageIndex: Int, val pageCount: Int) : InkTextPageState
}

/**
 * 文字面状态解析器（纯 JVM，无 Android 依赖，可离线单测）。
 *
 * 归类优先级自上而下：无书 → 无章 → 排版失败 → 内容为空 → 就绪；
 * 前序边界命中即返回，不再继续读后续输入，避免空章 / 空页把就绪态误判成有正文。
 */
object InkTextPageResolver {

    fun resolve(
        bookPresent: Boolean,
        chapterPresent: Boolean,
        pageCount: Int,
        currentIndex: Int,
        pageText: String?,
        layoutOk: Boolean,
    ): InkTextPageState {
        if (!bookPresent) return InkTextPageState.NoBook
        if (!chapterPresent) return InkTextPageState.NoChapter
        if (!layoutOk || pageCount <= 0) return InkTextPageState.LayoutFailed
        if (pageText.isNullOrBlank()) return InkTextPageState.EmptyContent
        val count = pageCount.coerceAtLeast(1)
        val index = currentIndex.coerceIn(0, count - 1)
        return InkTextPageState.Ready(pageIndex = index, pageCount = count)
    }
}
