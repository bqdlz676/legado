package io.legado.app.ui.book.read.page

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.PopupWindow
import io.legado.app.R
import io.legado.app.data.entities.BookHighlight
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.isPdf
import io.legado.app.help.chrono.Chronology
import io.legado.app.help.ChineseDict
import io.legado.app.help.AllusionDict
import io.legado.app.help.EnDict
import io.legado.app.ui.main.CharDetailActivity
import io.legado.app.help.HighlightMatcher
import io.legado.app.help.HighlightRuleMatcher
import io.legado.app.help.HighlightStyle
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.isForBook
import io.legado.app.ui.association.OpenUrlConfirmActivity
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.ButtonColumn
import io.legado.app.ui.book.read.page.entities.column.TextHtmlColumn
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.ReviewColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import java.util.IdentityHashMap
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * 阅读内容视图
 */
class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var charBubble: PopupWindow? = null
    private val allusionCache = IdentityHashMap<TextPage, Map<TextLine, List<AllusionDict.Hit>>>()
    private val allusionPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80567A46.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.3f * resources.displayMetrics.density
        }
    }
    var selectAble = AppConfig.textSelectAble
    val selectedPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.btn_bg_press_2)
            style = Paint.Style.FILL
        }
    }
    private var callBack: CallBack
    private val visibleRect = ChapterProvider.visibleRect
    val selectStart = TextPos(0, -1, -1)
    private val selectEnd = TextPos(0, -1, -1)
    var textPage: TextPage = TextPage()
        private set
    var isMainView = false
    var longScreenshot = false
    var reverseStartCursor = false
    var reverseEndCursor = false

    //滚动参数
    private val pageFactory get() = callBack.pageFactory
    private val pageDelegate get() = callBack.pageDelegate
    private var pageOffset = 0
    private var autoPager: AutoPager? = null
    private var isScroll = false
    private val renderRunnable by lazy { Runnable { preRenderPage() } }
    private var lastClickTime = 0L
    private var doubleClick = false
    private data class HighlightTapTarget(val id: Long, val start: Int, val end: Int, val manual: Boolean)
    private var highlightTapTarget: HighlightTapTarget? = null
    private var highlightTapPage: TextPage? = null
    private var highlightTapTime = 0L
    private var highlightTapX = 0f
    private var highlightTapY = 0f
    private val highlightDoubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
    internal fun cancelHighlightTap() { highlightTapTarget = null; highlightTapPage = null }
    private val pdfZoom: PdfZoom?
        get() = (parent?.parent?.parent as? ReadView)?.pdfZoom
    private var pdfRenderer: PdfZoomRenderer? = null
    internal val pdfRenderedPixelCount: Int get() = pdfRenderer?.renderedPixelCount ?: 0
    internal val pdfRenderCount: Int get() = pdfRenderer?.renderCount ?: 0
    internal val pdfRenderedPages: List<Int> get() = pdfRenderer?.renderedPages.orEmpty()

    internal fun closePdfRenderer() {
        pdfRenderer?.close()
        pdfRenderer = null
    }

    override fun onDetachedFromWindow() {
        cancelHighlightTap()
        closePdfRenderer()
        super.onDetachedFromWindow()
    }

    //绘制图片的paint
    val imagePaint by lazy {
        Paint().apply {
            isAntiAlias = AppConfig.useAntiAlias
        }
    }

    init {
        callBack = activity as CallBack
    }

    /**
     * 设置内容
     */
    fun setContent(textPage: TextPage) {
        cancelHighlightTap()
        this.textPage = textPage
        upHighlight()
        // 非滑动翻页动画需要同步重绘，不然翻页可能会出现闪烁
        if (isScroll) {
            postInvalidate()
        } else {
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isMainView) return
        ChapterProvider.upViewSize(w, h)
        textPage.format()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        autoPager?.onDraw(canvas)
        if (longScreenshot) {
            canvas.translate(0f, scrollY.toFloat())
        }
        check(!visibleRect.isEmpty) { "visibleRect 为空" }
        // Capsule backgrounds may extend into either page margin. Keep the shared
        // visibleRect unchanged: PDF zoom and reading positions still use its bounds.
        canvas.clipRect(0f, visibleRect.top, visibleRect.right, visibleRect.bottom)
        val zoom = pdfZoom?.takeIf { it.isEnabled() && !longScreenshot }
        if (zoom != null) {
            zoom.setBounds(visibleRect)
            val saved = canvas.save()
            zoom.transform(canvas)
            drawPage(canvas)
            canvas.restoreToCount(saved)
            if (isMainView && zoom.scale > 1f && ReadBook.book?.isPdf == true) {
                val pages = mutableListOf(textPage to relativeOffset(0))
                if (callBack.isScroll && pageFactory.hasNext()) {
                    pages.add(relativePage(1) to relativeOffset(1))
                    if (pageFactory.hasNextPlus()) pages.add(relativePage(2) to relativeOffset(2))
                }
                if (!zoom.isInteracting) {
                    val renderer = pdfRenderer ?: PdfZoomRenderer(this).also { pdfRenderer = it }
                    ReadBook.book?.let { renderer.draw(canvas, it, zoom, pages) }
                }
            } else if (pdfRenderer != null) {
                closePdfRenderer()
            }
        } else {
            closePdfRenderer()
            drawPage(canvas)
        }
    }

    /**
     * 绘制页面
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = relativeOffset(0)
        textPage.draw(this, canvas, relativeOffset)
        drawAllusions(canvas, textPage, relativeOffset)
        if (!callBack.isScroll) return
        //滚动翻页
        if (!pageFactory.hasNext()) return
        val textPage1 = relativePage(1)
        relativeOffset += textPage.height
        textPage1.draw(this, canvas, relativeOffset)
        drawAllusions(canvas, textPage1, relativeOffset)
        if (!pageFactory.hasNextPlus()) return
        relativeOffset += textPage1.height
        if (relativeOffset < ChapterProvider.visibleHeight) {
            val textPage2 = relativePage(2)
            textPage2.draw(this, canvas, relativeOffset)
            drawAllusions(canvas, textPage2, relativeOffset)
        }
    }

    /** 正文中命中的典故词，在字下画一道细线 */
    private fun drawAllusions(canvas: Canvas, page: TextPage, offset: Float) {
        if (page.lines.isEmpty()) return
        val hits = allusionCache[page] ?: run {
            if (allusionCache.size > 8) allusionCache.clear()
            val map = HashMap<TextLine, List<AllusionDict.Hit>>()
            for (line in page.lines) {
                val t = line.text
                if (t.length < 2) continue
                val h = AllusionDict.match(context, t)
                if (h.isNotEmpty()) map[line] = h
            }
            allusionCache[page] = map
            map
        }
        if (hits.isEmpty()) return
        val dy = 2.5f * resources.displayMetrics.density
        for ((line, list) in hits) {
            val y = line.lineBottom + offset + dy
            for (h in list) {
                val c1 = line.getColumn(h.start) as? TextColumn ?: continue
                val c2 = line.getColumn(h.end - 1) as? TextColumn ?: continue
                canvas.drawLine(c1.start, y, c2.end, y, allusionPaint)
            }
        }
    }

    /** 从行文本里取出包含 pos 的英文单词 */
    private fun englishWordAt(text: String, pos: Int): String? {
        if (pos !in text.indices) return null
        fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == '-'
        if (!isWordChar(text[pos])) return null
        var s = pos
        while (s > 0 && isWordChar(text[s - 1])) s--
        var e = pos
        while (e + 1 < text.length && isWordChar(text[e + 1])) e++
        return text.substring(s, e + 1).trim('-', '\'').takeIf { it.isNotEmpty() }
    }

    private fun columnIndexOf(line: TextLine, column: TextBaseColumn): Int {
        val n = line.getColumnsCount()
        for (i in 0 until n) {
            if (line.getColumn(i) === column) return i
        }
        return -1
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
        autoPager?.computeOffset()
    }

    /**
     * 滚动事件
     * pageOffset 向上滚动 减小 向下滚动 增大
     * pageOffset 范围 0 ~ -textPage.height 大于0为上一页，小于-textPage.height为下一页
     * 以内容显示区域顶端为界，pageOffset的绝对值为textPage上方的高度
     * pageOffset + textPage.height 为 textPage 下方的高度
     */
    fun scroll(mOffset: Int) {
        if (pageFactory.isRefreshingResources) return
        cancelHighlightTap()
        val previousOffset = pageOffset
        val previousPage = textPage
        pageOffset += mOffset
        if (longScreenshot) {
            scrollY += -mOffset
        }
        if (!pageFactory.hasPrev() && pageOffset > 0) {
            pageOffset = 0
            pageDelegate?.abortAnim()
        } else if (!pageFactory.hasNext()
            && pageOffset < 0
            && pageOffset + textPage.height < ChapterProvider.visibleHeight
        ) {
            val offset = (ChapterProvider.visibleHeight - textPage.height).toInt()
            pageOffset = min(0, offset)
            pageDelegate?.abortAnim()
        } else if (pageOffset > 0) {
            if (pageFactory.moveToPrev(true)) {
                pageOffset -= textPage.height.toInt()
            } else {
                pageOffset = 0
                pageDelegate?.abortAnim()
            }
        } else if (pageOffset < -textPage.height) {
            val height = textPage.height
            if (pageFactory.moveToNext(upContent = true)) {
                pageOffset += height.toInt()
            } else {
                pageOffset = -height.toInt()
                pageDelegate?.abortAnim()
            }
        }
        val moved = if (textPage === previousPage) pageOffset - previousOffset else mOffset
        if (!longScreenshot && moved != 0) callBack.onReadScroll(moved)
        postInvalidateOnAnimation()
    }

    fun submitRenderTask() {
        renderThread.submit(renderRunnable)
    }

    private fun preRenderPage() {
        val view = this
        var invalidate = false
        pageFactory.run {
            if (hasPrev() && prevPage.render(view)) {
                invalidate = true
            }
            if (curPage.render(view)) {
                invalidate = true
            }
            if (hasNext() && nextPage.render(view) && callBack.isScroll) {
                invalidate = true
            }
            if (hasNextPlus() && nextPlusPage.render(view) && callBack.isScroll
                && relativeOffset(2) < ChapterProvider.visibleHeight
            ) {
                invalidate = true
            }
            if (invalidate) {
                postInvalidate()
                pageDelegate?.postInvalidate()
            }
        }
    }

    /**
     * 重置滚动位置
     */
    fun resetPageOffset() {
        pageOffset = 0
    }

    fun isAtChapterTop(): Boolean {
        return textPage.index == 0 && pageOffset == 0
    }

    fun restorePageOffset(chapterPos: Int) {
        val line = textPage.lines.firstOrNull { it.chapterPosition == chapterPos }
            ?: textPage.lines.firstOrNull {
                chapterPos in it.chapterPosition..<it.chapterPosition + it.charSize
            }
            ?: return
        if (line === textPage.lines.firstOrNull()) return
        scroll((ChapterProvider.paddingTop - line.lineTop).toInt())
    }

    /**
     * 长按
     */
    fun longPress(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        cancelHighlightTap()
        val highlightActionTrigger = AppConfig.highlightActionTrigger
        touch(x, y) { relativeOffset, textPos, textPage, textLine, column ->
            when (column) {
                is ImageColumn -> callBack.onImageLongPress(x, y, column.src)
                is TextColumn -> {
                    if (highlightActionTrigger == "longPress" && column.highlightStyle != null &&
                        notifyHighlightClick(column, textPos, textPage, textLine, relativeOffset)
                    ) return@touch
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                }
                is TextHtmlColumn -> {
                    if (
                        column.highlightStyle != null &&
                        (highlightActionTrigger == "longPress" ||
                            (column.linkUrl != null && highlightActionTrigger != "doubleTap")) &&
                        notifyHighlightClick(column, textPos, textPage, textLine, relativeOffset)
                    ) return@touch
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                }
            }
        }
    }

    /**
     * 单击
     * @return true:已处理, false:未处理
     */
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun click(x: Float, y: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        val debounceClick = currentTime - lastClickTime < 300L //300毫秒防抖和双击
        lastClickTime = currentTime
        doubleClick = if (debounceClick) {
            !doubleClick
        } else {
            false
        }
        val highlightActionTrigger = AppConfig.highlightActionTrigger
        val previousHighlightTarget = highlightTapTarget
        val previousHighlightPage = highlightTapPage
        cancelHighlightTap()
        fun highlightTap(
            column: TextBaseColumn,
            textPos: TextPos,
            page: TextPage,
            line: TextLine,
            relativeOffset: Float,
        ): Boolean {
            if (highlightActionTrigger == "doubleTap") {
                val target = highlightAt(column, textPos, page)?.let {
                    HighlightTapTarget(it.time, it.chapterPos, it.chapterPosEnd, true)
                } ?: highlightRuleAt(column, textPos, page)?.let {
                    HighlightTapTarget(it.ruleId, it.start, it.end, false)
                } ?: return false
                val now = SystemClock.uptimeMillis()
                val dx = x - highlightTapX
                val dy = y - highlightTapY
                val isDoubleTap = previousHighlightPage === page && previousHighlightTarget == target &&
                    now - highlightTapTime <= ViewConfiguration.getDoubleTapTimeout() &&
                    dx * dx + dy * dy <= highlightDoubleTapSlop * highlightDoubleTapSlop
                if (!isDoubleTap) {
                    highlightTapTarget = target
                    highlightTapPage = page
                    highlightTapTime = now
                    highlightTapX = x
                    highlightTapY = y
                    // Consume the first tap so it cannot turn the page before the second.
                    return true
                }
            }
            return notifyHighlightClick(column, textPos, page, line, relativeOffset)
        }
        var handled = false
        touch(x, y) { relativeOffset, textPos, textPage, textLine, column ->
            when (column) {
                is ButtonColumn -> {
                    context.toastOnUi("Button Pressed!")
                    handled = true
                }

                is ReviewColumn -> {
                    if (!debounceClick) {
                        callBack.onReviewClick(
                            resolveReviewId(textLine),
                            column.count,
                            textPage.chapterIndex
                        )
                    }
                    handled = true
                }

                is ImageColumn -> when (AppConfig.clickImgWay) {
                    "1" -> { //预览图片
                        activity?.showDialogFragment(PhotoDialog(column.src, isBook = true))
                        handled = true
                    }
                    "2" -> { //兼容处理
                        if (!debounceClick) {
                            if (ReadBook.book?.isOnLineTxt == true) {
                                val click = column.click
                                val src = column.src
                                if (!click.isNullOrBlank()) {
                                    callBack.clickImg(click, src)
                                    handled = true
                                } else {
                                    handled = callBack.oldClickImg(src)
                                }
                            }
                        }
                    }
                    "3" -> { //关闭
                        handled = false
                    }
                    "4" -> { //双击
                        if (doubleClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        } else {
                            handled = true
                        }
                    }
                    else -> { //默认点击
                        if (!debounceClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        }
                    }
                }
                is TextHtmlColumn -> {
                    val linkUrl = column.linkUrl
                    if (linkUrl != null) {
                        activity?.startActivity<OpenUrlConfirmActivity> {
                            putExtra("uri", linkUrl)
                        }
                        handled = true
                    } else if (highlightActionTrigger != "longPress" && column.highlightStyle != null) {
                        handled = highlightTap(
                            column,
                            textPos,
                            textPage,
                            textLine,
                            relativeOffset
                        )
                    }
                }

                is TextColumn -> {
                    if (highlightActionTrigger != "longPress" &&
                        column.highlightStyle != null
                    ) {
                        handled = highlightTap(
                            column,
                            textPos,
                            textPage,
                            textLine,
                            relativeOffset
                        )
                    } else {
                        val ch = column.charData
                        if (ch.length == 1 && ch[0].code in 0x4E00..0x9FFF) {
                            val colIdx = columnIndexOf(textLine, column)
                            val lineText = textLine.text
                            val chronoHit = if (colIdx >= 0) {
                                chronoHitAt(lineText, colIdx)
                            } else null
                            if (chronoHit != null) {
                                showBubble(
                                    chronoHit.first, "", chronoHit.second,
                                    x, y, relativeOffset, textLine,
                                    chronoHit.first.length >= 5, false
                                )
                                handled = true
                            } else {
                                val hit = if (colIdx >= 0) {
                                    AllusionDict.hitAt(context, lineText, colIdx)
                                } else null
                                if (hit != null) {
                                    showAllusionBubble(hit.entry, x, y, relativeOffset, textLine)
                                } else {
                                    val word = if (colIdx >= 0) {
                                        ChineseDict.wordAt(context, lineText, colIdx)
                                    } else null
                                    if (word != null && word.word.length >= 2) {
                                        showBubble(
                                            word.word, "", word.meaning,
                                            x, y, relativeOffset, textLine,
                                            word.word.length >= 3, false
                                        )
                                    } else {
                                        showCharBubble(ch, x, y, relativeOffset, textLine)
                                    }
                                }
                                handled = true
                            }
                        } else if (ch.length == 1 && (ch[0] in 'a'..'z' || ch[0] in 'A'..'Z')) {
                            val colIdx = columnIndexOf(textLine, column)
                            if (colIdx >= 0) {
                                val word = englishWordAt(textLine.text, colIdx)
                                if (word != null) {
                                    val entry = EnDict.lookup(context, word)
                                    showBubble(
                                        word,
                                        entry?.phonetic.orEmpty(),
                                        entry?.translation ?: "词库未收录",
                                        x, y, relativeOffset, textLine,
                                        word.length >= 9, false
                                    )
                                    handled = true
                                }
                            }
                        }
                    }
                }
            }
        }
        return handled
    }

    /**
     * 选择文字
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touchRough(x, y) { _, textPos, _, _, column ->
            if (column is TextBaseColumn) {
                column.selected = true
                select(textPos)
            }
        }
    }

    /**
     * 开始选择符移动
     */
    fun selectStartMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (selectStart.compare(textPos) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectEnd) <= 0) {
                selectStartMoveIndex(textPos)
            } else {
                touchRough(x - 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectEnd) > 0) {
                        reverseStartCursor = true
                        reverseEndCursor = false
                        selectEnd.columnIndex++
                        selectStartMoveIndex(selectEnd)
                        selectEndMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 结束选择符移动
     */
    fun selectEndMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (textPos.compare(selectEnd) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectStart) >= 0) {
                selectEndMoveIndex(textPos)
            } else {
                touchRough(x + 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectStart) < 0) {
                        reverseEndCursor = true
                        reverseStartCursor = false
                        selectStart.columnIndex--
                        selectEndMoveIndex(selectStart)
                        selectStartMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * @param touched 回调
     */
    private fun touch(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        if (!visibleRect.contains(x, y)) return
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                if (textLine.isTouch(x, y, relativeOffset)) {
                    val reviewIndex = textLine.columns.indexOfFirst {
                        it is ReviewColumn && it.isTouch(x, y, relativeOffset)
                    }
                    if (reviewIndex >= 0) {
                        touched.invoke(
                            relativeOffset,
                            TextPos(relativePos, lineIndex, reviewIndex),
                            textPage,
                            textLine,
                            textLine.columns[reviewIndex]
                        )
                        return
                    }
                    for ((charIndex, textColumn) in textLine.columns.withIndex()) {
                        if (textColumn !is ReviewColumn && textColumn.isTouch(x)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    return
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * 文本选择专用
     * @param touched 回调
     */
    private fun touchRough(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for (lineIndex in textPage.lines.indices) {
                val textLine = textPage.getLine(lineIndex)
                if (textLine.isTouchY(y, relativeOffset)) {
                    if (textPage.doublePage) {
                        val halfWidth = width / 2
                        if (textLine.isLeftLine && x > halfWidth) {
                            continue
                        }
                        if (!textLine.isLeftLine && x < halfWidth) {
                            continue
                        }
                    }
                    val columns = textLine.columns
                    for (charIndex in columns.indices) {
                        val textColumn = columns[charIndex]
                        if (textColumn.isTouch(x)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    val isLast = columns.first().start < x
                    val charIndex = if (isLast) columns.lastIndex + 1 else -1
                    val textColumn = if (isLast) columns.last() else columns.first()
                    touched.invoke(
                        relativeOffset,
                        TextPos(relativePos, lineIndex, charIndex),
                        textPage, textLine, textColumn
                    )
                    return
                }
            }
        }
    }

    fun getCurVisiblePage(): TextPage {
        val visiblePage = TextPage()
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    visiblePage.addLine(visibleLine)
                }
            }
        }
        return visiblePage
    }

    fun getReadPosition(): Pair<Int, TextLine>? {
        if (textPage.isMsgPage) return null
        val offset = relativeOffset(0)
        val line = textPage.lines.firstOrNull { it.isVisible(offset) }
            ?: textPage.lines.lastOrNull()
            ?: return null
        return textPage.chapterIndex to line
    }

    fun getReadAloudPos(): Pair<Int, TextLine>? {
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    return textPage.chapterIndex to visibleLine
                }
            }
        }
        return null
    }

    /**
     * 选择开始文字
     */
    fun selectStartMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectStart.relativePagePos = relativePagePos
        selectStart.lineIndex = lineIndex
        selectStart.columnIndex = max(0, charIndex)
        val textLine = relativePage(relativePagePos).getLine(lineIndex)
        val textColumn = textLine.getColumn(charIndex)
        upSelectedStart(
            if (charIndex < textLine.columns.size) textColumn.start else textColumn.end,
            textLine.lineBottom + relativeOffset(relativePagePos),
            textLine.lineTop + relativeOffset(relativePagePos)
        )
        upSelectChars()
    }

    fun selectStartMoveIndex(textPos: TextPos) = textPos.run {
        selectStartMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    /**
     * 选择结束文字
     */
    fun selectEndMoveIndex(
        relativePage: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectEnd.relativePagePos = relativePage
        selectEnd.lineIndex = lineIndex
        val textLine = relativePage(relativePage).getLine(lineIndex)
        selectEnd.columnIndex = min(charIndex, textLine.columns.lastIndex)
        val textColumn = textLine.getColumn(charIndex)
        upSelectedEnd(
            if (charIndex > -1) textColumn.end else textColumn.start,
            textLine.lineBottom + relativeOffset(relativePage)
        )
        upSelectChars()
    }

    fun selectEndMoveIndex(textPos: TextPos) = textPos.run {
        selectEndMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    private fun upSelectChars() {
        if (!selectStart.isSelected() && !selectEnd.isSelected()) {
            return
        }
        val last = if (callBack.isScroll) 2 else 0
        val textPos = TextPos(0, 0, 0)
        for (relativePos in 0..last) {
            textPos.relativePagePos = relativePos
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                textPos.lineIndex = lineIndex
                for ((charIndex, column) in textLine.columns.withIndex()) {
                    textPos.columnIndex = charIndex
                    if (column is TextBaseColumn) {
                        val compareStart = textPos.compare(selectStart)
                        val compareEnd = textPos.compare(selectEnd)
                        column.selected = compareStart >= 0 && compareEnd <= 0
                        column.isSearchResult =
                            column.selected && callBack.isSelectingSearchResult
                        if (column.isSearchResult) {
                            textPage.searchResult.add(column)
                        }
                    }
                }
            }
        }
        postInvalidate()
    }

    private fun upSelectedStart(x: Float, y: Float, top: Float) {
        callBack.run {
            upSelectedStart(x + imgBgPaddingStart, y + headerHeight, top + headerHeight)
        }
    }

    private fun upSelectedEnd(x: Float, y: Float) {
        callBack.run {
            upSelectedEnd(x + imgBgPaddingStart, y + headerHeight)
        }
    }

    fun resetReverseCursor() {
        reverseStartCursor = false
        reverseEndCursor = false
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        val last = if (callBack.isScroll) 2 else 0
        for (relativePos in 0..last) {
            val textPage = relativePage(relativePos)
            textPage.lines.forEach { textLine ->
                textLine.columns.forEach {
                    if (it is TextBaseColumn) {
                        it.selected = false
                        if (clearSearchResult) {
                            it.isSearchResult = false
                            textPage.searchResult.remove(it)
                        }
                    }
                }
            }
        }
        selectStart.reset()
        selectEnd.reset()
        postInvalidate()
        callBack.onCancelSelect()
    }

    fun upHighlight() {
        val last = if (isScroll) 2 else 0
        for (relativePos in 0..last) {
            val page = relativePage(relativePos)
            if (page.lines.isEmpty() || page.isMsgPage) continue
            val chapter = page.getTextChapter()
            if (chapter.isTransient || !chapter.isForBook(ReadBook.book)) {
                page.lines.forEach { line ->
                    line.columns.forEach { column ->
                        if (column is TextBaseColumn) column.highlightStyle = null
                    }
                }
                continue
            }
            val pageBase = chapter.getReadLength(page.index)
            val pageLength = page.lines.sumOf {
                it.charSize + if (it.isParagraphEnd) 1 else 0
            }
            val pageEnd = pageBase + pageLength
            val chapterRanges = ReadBook.highlightRangesOfChapter(chapter)
            if (!ReadBook.upHighlightSpacing(chapter, chapterRanges)) continue
            val ranges = chapterRanges.filter { it.start < pageEnd && it.end > pageBase }
            val lineSpecs = page.lines.map { line ->
                HighlightMatcher.LineSpec(
                    charSize = line.charSize,
                    columnCharLengths = line.columns.map { it.positionLength },
                    isParagraphEnd = line.isParagraphEnd,
                    isTitle = line.isTitle,
                    paragraphIndentColumns = line.columns.map { (it as? TextBaseColumn)?.isParagraphIndent == true }
                )
            }
            val styles = HighlightMatcher.resolve(
                pageBase,
                lineSpecs,
                ranges
            )
            page.lines.forEachIndexed { lineIndex, line ->
                line.columns.forEachIndexed { columnIndex, column ->
                    if (column is TextBaseColumn) {
                        column.highlightStyle = styles[lineIndex][columnIndex]
                    }
                }
            }
        }
        postInvalidate()
    }

    fun getSelectedText(): String {
        val textPos = TextPos(0, 0, 0)
        val builder = StringBuilder()
        for (relativePos in selectStart.relativePagePos..selectEnd.relativePagePos) {
            val textPage = relativePage(relativePos)
            textPos.relativePagePos = relativePos
            textPage.lines.forEachIndexed { lineIndex, textLine ->
                textPos.lineIndex = lineIndex
                val lastTextColumnIndex = textLine.columns.indexOfLast {
                    it is TextBaseColumn
                }
                textLine.columns.forEachIndexed { charIndex, column ->
                    textPos.columnIndex = charIndex
                    val compareStart = textPos.compare(selectStart)
                    val compareEnd = textPos.compare(selectEnd)
                    if (column is TextBaseColumn) {
                        when {
                            compareStart == -1 -> if (
                                selectStart.columnIndex == textLine.columns.size
                                && charIndex == lastTextColumnIndex
                            ) {
                                builder.append("\n")
                            }

                            compareEnd == 1 -> if (selectEnd.columnIndex == -1 && charIndex == 0) {
                                builder.append("\n")
                            }

                            compareStart >= 0 && compareEnd <= 0 -> {
                                builder.append(column.charData)
                                if (
                                    textLine.isParagraphEnd
                                    && charIndex == lastTextColumnIndex
                                    && compareEnd != 0
                                ) {
                                    builder.append("\n")
                                }
                            }
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    private fun dismissCharBubble() {
        charBubble?.takeIf { it.isShowing }?.dismiss()
        charBubble = null
    }

    private fun showCharBubble(
        ch: String,
        x: Float,
        y: Float,
        relativeOffset: Float,
        textLine: TextLine,
    ) {
        val info = ChineseDict.getChar(context, ch)
        showBubble(
            ch,
            info?.pinyin?.takeIf { it.isNotBlank() } ?: charPinyin(context, ch).orEmpty(),
            info?.meaning.orEmpty(),
            x, y, relativeOffset, textLine, false, true
        )
    }

    private fun showAllusionBubble(
        entry: AllusionDict.Entry,
        x: Float,
        y: Float,
        relativeOffset: Float,
        textLine: TextLine,
    ) {
        showBubble(
            entry.word,
            entry.pinyin,
            entry.meaning + "\n〔出处〕" + entry.source,
            x, y, relativeOffset, textLine, entry.word.length >= 3, false
        )
    }


    /**
     * 行内年代识别：
     * 点到「己巳」这类干支日，结合同行年月算出公历；
     * 点到「贞观三年」这类年号纪年，直接换公元与干支年。
     */
    private fun chronoHitAt(lineText: String, pos: Int): Pair<String, String>? {
        if (lineText.isEmpty()) return null

        // 干支日：点到的两字，或左侧起算的两字
        val cands = ArrayList<String>(2)
        if (pos >= 0 && pos + 2 <= lineText.length) {
            cands.add(lineText.substring(pos, pos + 2))
        }
        if (pos >= 1 && pos + 1 <= lineText.length) {
            cands.add(lineText.substring(pos - 1, pos + 1))
        }
        for (c in cands) {
            if (Chronology.ganzhiIndex(c) == null) continue
            val year = yearInLine(lineText)
            val month = monthInLine(lineText)
            return if (year != null && month != null) {
                val days = Chronology.resolveDays(year, month, c)
                if (days.isEmpty()) {
                    c to "本年${month}月无${c}日"
                } else {
                    val ds = days.joinToString(" / ") { "${it.dayOfMonth}日" }
                    c to "公元${year}年${month}月${ds}（${Chronology.yearGanzhi(year)}年）· 距今${Chronology.yearsAgo(year)}年"
                }
            } else {
                c to "干支日；同行无年月，无法落到具体公历"
            }
        }

        // 年号纪年
        val re = KOTLIN_YEAR_RE
        for (m in re.findAll(lineText)) {
            if (pos < m.range.first || pos > m.range.last) continue
            val era = m.groupValues[1]
            val e = Chronology.eraOf(era) ?: continue
            val n = chnNumber(m.groupValues[2]) ?: continue
            val y = e.startYear + n - 1
            return "${era}${m.groupValues[2]}年" to "${Chronology.yearLabel(y)}（${e.dynasty}）"
        }
        return null
    }

    private fun yearInLine(lineText: String): Int? {
        for (m in KOTLIN_YEAR_RE.findAll(lineText)) {
            val e = Chronology.eraOf(m.groupValues[1]) ?: continue
            val n = chnNumber(m.groupValues[2]) ?: continue
            return e.startYear + n - 1
        }
        return null
    }

    private fun monthInLine(lineText: String): Int? {
        KOTLIN_MONTH_RE.find(lineText)?.let { m ->
            chnNumber(m.groupValues[1])?.takeIf { it in 1..12 }?.let { return it }
        }
        return when {
            lineText.contains("正月") -> 1
            lineText.contains("冬月") -> 11
            lineText.contains("腊月") -> 12
            else -> null
        }
    }

    /** 一~九百九十九够用，古籍纪年不会更大 */
    private fun chnNumber(raw: String): Int? {
        if (raw.isEmpty()) return null
        var total = 0
        var section = 0
        var num = 0
        for (c in raw) {
            when (c) {
                '一', '壹' -> num = 1
                '二', '贰', '兩', '两' -> num = 2
                '三', '叁' -> num = 3
                '四', '肆' -> num = 4
                '五', '伍' -> num = 5
                '六', '陆' -> num = 6
                '七', '柒' -> num = 7
                '八', '捌' -> num = 8
                '九', '玖' -> num = 9
                '十', '拾' -> {
                    section += if (num == 0) 10 else num * 10
                    num = 0
                }
                '百', '佰' -> {
                    section += if (num == 0) 100 else num * 100
                    num = 0
                }
                '千', '仟' -> {
                    section += if (num == 0) 1000 else num * 1000
                    num = 0
                }
                else -> return null
            }
        }
        total = section + num
        return total.takeIf { it > 0 }
    }

    private fun showBubble(
        ch: String,
        pinyinText: String,
        meaningText: String,
        x: Float,
        y: Float,
        relativeOffset: Float,
        textLine: TextLine,
        titleSmall: Boolean,
        detail: Boolean,
    ) {
        if (charBubble?.isShowing == true) {
            dismissCharBubble()
            return
        }
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val screenX = loc[0] + x
        val lineTopY = loc[1] + relativeOffset + textLine.lineTop
        val lineBottomY = lineTopY + textLine.height
        val bubble = BubbleView(context).apply {
            char = ch
            pinyin = pinyinText
            meaning = meaningText
            this.titleSmall = titleSmall
            showDetail = detail
            onDetail = {
                dismissCharBubble()
                val intent = Intent(context, CharDetailActivity::class.java)
                    .putExtra("char", ch)
                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val w = bubble.measuredWidth
        val h = bubble.measuredHeight
        val frame = Rect()
        getWindowVisibleDisplayFrame(frame)
        var top = (lineBottomY + 2f).toInt()
        var arrowUp = true
        if (top + h > frame.bottom - 4) {
            top = (lineTopY - h - 2f).toInt()
            arrowUp = false
        }
        if (top < frame.top + 4) top = frame.top + 4
        bubble.arrowUp = arrowUp
        var left = (screenX - w / 2f).toInt()
        left = min(max(left, frame.left + 4), frame.right - w - 4)
        bubble.arrowX = screenX - left
        val pw = PopupWindow(
            bubble,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(0x00000000))
            isOutsideTouchable = true
            isFocusable = false
        }
        pw.showAtLocation(this, Gravity.NO_GRAVITY, left, top)
        charBubble = pw
    }

    private class BubbleView(context: Context) : View(context) {
        var char: String = ""
        var pinyin: String = ""
        var meaning: String = ""
        var titleSmall: Boolean = false
        var showDetail: Boolean = false
        var onDetail: (() -> Unit)? = null
        var arrowUp: Boolean = true
        var arrowX: Float = -1f
        private val dm = resources.displayMetrics
        private val pad = 14f * dm.density
        private val arrowH = 9f * dm.density
        private val arrowW = 8f * dm.density
        private val radius = 12f * dm.density
        private val maxBoxW = 236f * dm.density
        private val gap = 4f * dm.density
        private val detH = 22f * dm.density
        private var meaningLayout: StaticLayout? = null

        private val detPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A6A3E.toInt()
            textSize = 12f * dm.scaledDensity
            textAlign = Paint.Align.RIGHT
        }

        init {
            isClickable = true
        }

        private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF24201A.toInt()
            textSize = 26f * dm.scaledDensity
            textAlign = Paint.Align.CENTER
        }
        private val pyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6B6459.toInt()
            textSize = 13f * dm.scaledDensity
            textAlign = Paint.Align.CENTER
        }
        private val meanPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3F382E.toInt()
            textSize = 12f * dm.scaledDensity
            textAlign = Paint.Align.LEFT
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFBF6EA.toInt()
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCFC6B4.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * dm.density
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            charPaint.textSize = when {
                char.length >= 11 -> 13f * dm.scaledDensity
                titleSmall -> 18f * dm.scaledDensity
                else -> 26f * dm.scaledDensity
            }
            val fm = charPaint.fontMetrics
            val hasPy = pinyin.isNotEmpty()
            val innerMax = (maxBoxW - pad * 2).toInt().coerceAtLeast(1)
            val ml = if (meaning.isNotEmpty()) {
                StaticLayout.Builder
                    .obtain(meaning, 0, meaning.length, meanPaint, innerMax)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .setMaxLines(5)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            } else null
            meaningLayout = ml
            val tw = max(
                charPaint.measureText(char),
                max(
                    if (hasPy) pyPaint.measureText(pinyin) else 0f,
                    ml?.width?.toFloat() ?: 0f
                )
            )
            val boxW = (tw + pad * 2).coerceAtMost(maxBoxW)
            val th = fm.descent - fm.ascent
            val ph = if (hasPy) {
                pyPaint.fontMetrics.let { it.descent - it.ascent } + 3f * dm.density
            } else 0f
            val mh = if (ml != null) ml.height + gap * 2f else 0f
            val dh = if (showDetail) detH else 0f
            setMeasuredDimension(
                boxW.toInt(),
                (th + ph + mh + pad * 2 + arrowH + dh).toInt()
            )
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val rectTop = if (arrowUp) arrowH else 0f
            val rectBottom = if (arrowUp) h else h - arrowH
            val path = Path()
            path.addRoundRect(0f, rectTop, w, rectBottom, radius, radius, Path.Direction.CW)
            val minX = radius + arrowW
            val maxX = w - radius - arrowW
            val tx = if (maxX > minX) (arrowX.takeIf { it >= 0f } ?: (w / 2f)).coerceIn(minX, maxX)
            else w / 2f
            if (arrowUp) {
                path.moveTo(tx - arrowW, rectTop + 0.5f)
                path.lineTo(tx, 0f)
                path.lineTo(tx + arrowW, rectTop + 0.5f)
            } else {
                path.moveTo(tx - arrowW, rectBottom - 0.5f)
                path.lineTo(tx, h)
                path.lineTo(tx + arrowW, rectBottom - 0.5f)
            }
            path.close()
            canvas.drawPath(path, bgPaint)
            canvas.drawPath(path, borderPaint)
            val fm = charPaint.fontMetrics
            val charBase = rectTop + pad - fm.ascent
            canvas.drawText(char, w / 2f, charBase, charPaint)
            var bottom = charBase + fm.descent
            if (pinyin.isNotEmpty()) {
                val pfm = pyPaint.fontMetrics
                val pyBase = bottom + 3f * dm.density - pfm.ascent
                canvas.drawText(pinyin, w / 2f, pyBase, pyPaint)
                bottom = pyBase + pfm.descent
            }
            meaningLayout?.let { ml ->
                canvas.save()
                canvas.translate(pad, bottom + gap * 2f)
                ml.draw(canvas)
                canvas.restore()
            }
            if (showDetail) {
                val bottomPad = if (arrowUp) pad else pad + arrowH
                canvas.drawText(
                    "详情 ›",
                    w - pad,
                    h - bottomPad - 3f * dm.density,
                    detPaint
                )
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!showDetail) return super.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                val bottomPad = if (arrowUp) pad else pad + arrowH
                if (event.y > height - bottomPad - detH) {
                    onDetail?.invoke()
                }
            }
            return true
        }
    }

    fun createBookmark(): Bookmark? {
        val page = relativePage(selectStart.relativePagePos)
        page.getTextChapter().let { chapter ->
            ReadBook.book?.let { book ->
                return book.createBookMark().apply {
                    chapterIndex = page.chapterIndex
                    chapterPos = chapter.getReadLength(page.index) +
                            page.getPosByLineColumn(selectStart.lineIndex, selectStart.columnIndex)
                    chapterName = chapter.title
                    bookText = getSelectedText()
                }
            }
        }
        return null
    }

    fun createHighlight(style: HighlightStyle): BookHighlight? {
        val book = ReadBook.book ?: return null
        val startPage = relativePage(selectStart.relativePagePos)
        val endPage = relativePage(selectEnd.relativePagePos)
        if (startPage.chapterIndex != endPage.chapterIndex) return null
        val startLine = startPage.getLine(selectStart.lineIndex)
        val endLine = endPage.getLine(selectEnd.lineIndex)
        if (startLine.isTitle || endLine.isTitle) return null
        val chapter = startPage.getTextChapter()
        if (!chapter.isForBook(book)) return null
        val titleLength = chapter.layoutTitleLength.takeIf { it >= 0 } ?: return null
        val endLength = highlightSelectionEndLength(selectEnd.columnIndex) {
            endLine.getColumn(selectEnd.columnIndex).positionLength
        }
        val rawStart = chapter.getReadLength(startPage.index) +
                startPage.getPosByLineColumn(selectStart.lineIndex, selectStart.columnIndex)
        val rawEnd = chapter.getReadLength(endPage.index) +
                endPage.getPosByLineColumn(selectEnd.lineIndex, selectEnd.columnIndex) + endLength
        val bodyStart = (rawStart - titleLength).coerceAtLeast(0)
        val bodyEnd = (rawEnd - titleLength).coerceAtLeast(0)
        if (bodyEnd <= bodyStart) return null
        val bookText = getSelectedText()
        if (!bookText.hasHighlightableText()) return null
        ReadBook.anchoredHighlightsOfChapter(chapter, titleLength)
            .lastOrNull { (_, anchor) -> anchor.start == bodyStart && anchor.end == bodyEnd }
            ?.first?.let { return it }
        return BookHighlight(
            bookUrl = book.bookUrl,
            chapterUrl = chapter.chapter.url,
            bookName = book.name,
            bookAuthor = book.author,
            chapterIndex = startPage.chapterIndex,
            chapterPos = bodyStart + titleLength,
            chapterPosEnd = bodyEnd + titleLength,
            layoutTitleLength = titleLength,
            chapterName = chapter.title,
            bookText = bookText
        ).apply { applyStyle(style) }
    }

    private fun notifyHighlightClick(
        column: TextBaseColumn,
        textPos: TextPos,
        page: TextPage,
        line: TextLine,
        relativeOffset: Float
    ): Boolean {
        if (AppConfig.highlightActionTrigger == "off") return false
        val x = column.start + callBack.imgBgPaddingStart
        val y = line.lineTop + relativeOffset + callBack.headerHeight
        highlightAt(column, textPos, page)?.let {
            callBack.onHighlightClick(it, x, y)
            return true
        }
        highlightRuleAt(column, textPos, page)?.let {
            callBack.onHighlightRuleClick(it.ruleId, x, y)
            return true
        }
        return false
    }

    private fun highlightAt(
        column: TextBaseColumn,
        textPos: TextPos,
        page: TextPage
    ): BookHighlight? {
        val book = ReadBook.book ?: return null
        val chapter = page.getTextChapter()
        if (!chapter.isForBook(book)) return null
        if (page.getLine(textPos.lineIndex).isTitle) return null
        val titleLength = chapter.layoutTitleLength.takeIf { it >= 0 } ?: return null
        val rawColumnStart = chapter.getReadLength(page.index) +
                page.getPosByLineColumn(textPos.lineIndex, textPos.columnIndex)
        val columnStart = (rawColumnStart - titleLength).coerceAtLeast(0)
        val columnEnd = (rawColumnStart + column.positionLength - titleLength).coerceAtLeast(0)
        return ReadBook.anchoredHighlightsOfChapter(chapter, titleLength)
            .lastOrNull { (_, anchor) ->
                highlightRangeIntersects(columnStart, columnEnd, anchor.start, anchor.end)
            }
            ?.first
    }

    private fun highlightRuleAt(
        column: TextBaseColumn,
        textPos: TextPos,
        page: TextPage
    ): HighlightRuleMatcher.RuleMatch? {
        val book = ReadBook.book ?: return null
        val chapter = page.getTextChapter()
        if (!chapter.isForBook(book)) return null
        val line = page.getLine(textPos.lineIndex)
        val columnStart = chapter.getReadLength(page.index) +
                page.getPosByLineColumn(textPos.lineIndex, textPos.columnIndex)
        val columnEnd = columnStart + column.positionLength
        return highlightRuleAtColumn(
            ReadBook.ruleMatchesOfChapter(chapter),
            columnStart,
            columnEnd,
            line.isTitle
        )
    }

    private fun relativeOffset(relativePos: Int): Float {
        return when (relativePos) {
            0 -> pageOffset.toFloat()
            1 -> pageOffset + textPage.height
            else -> pageOffset + textPage.height + pageFactory.nextPage.height
        }
    }

    fun relativePage(relativePos: Int): TextPage {
        return when (relativePos) {
            0 -> textPage
            1 -> pageFactory.nextPage
            else -> pageFactory.nextPlusPage
        }
    }

    fun setAutoPager(autoPager: AutoPager?) {
        this.autoPager = autoPager
    }

    fun setIsScroll(value: Boolean) {
        isScroll = value
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return callBack.isScroll && pageFactory.hasNext()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longScreenshot = true
                scrollY = 0
            }

            MotionEvent.ACTION_UP -> {
                longScreenshot = false
                scrollY = 0
            }
        }
        return callBack.onLongScreenshotTouchEvent(event)
    }

    companion object {
        /** 年号+年，如「贞观三年」 */
        private val KOTLIN_YEAR_RE = Regex("([\u4e00-\u9fff]{2,4})([一二三四五六七八九十百]+)年")
        /** 月份，如「二月」「十二月」 */
        private val KOTLIN_MONTH_RE = Regex("([一二三四五六七八九十]+)月")

        private val renderThread by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "TextPageRender")
            }
        }
        private val cursorWidth = 24.dpToPx()

        @Volatile
        private var pinyinMap: Map<String, String>? = null

        fun charPinyin(context: Context, ch: String): String? {
            val map = pinyinMap ?: synchronized(this) {
                val loaded = HashMap<String, String>(24000)
                runCatching {
                    context.applicationContext.assets.open("char_pinyin.txt")
                        .bufferedReader()
                        .useLines { lines ->
                            lines.forEach { line ->
                                val tab = line.indexOf('\t')
                                if (tab > 0) {
                                    loaded[line.substring(0, tab)] = line.substring(tab + 1)
                                }
                            }
                        }
                }
                pinyinMap = loaded
                loaded
            }
            return map[ch]
        }

        @Volatile
        private var dictMap: Map<String, String>? = null

        fun preloadDict(context: Context) {
            charMeaning(context, "\u0000")
        }

        fun charMeaning(context: Context, ch: String): String? {
            val map = dictMap ?: synchronized(this) {
                val loaded = HashMap<String, String>(18000)
                runCatching {
                    context.applicationContext.assets.open("char_dict.txt")
                        .bufferedReader()
                        .useLines { lines ->
                            lines.forEach { line ->
                                val a = line.indexOf('\t')
                                val b = line.indexOf('\t', a + 1)
                                if (a > 0 && b > a) {
                                    loaded[line.substring(0, a)] = line.substring(b + 1)
                                }
                            }
                        }
                }
                dictMap = loaded
                loaded
            }
            return map[ch]
        }
    }

    interface CallBack {
        val headerHeight: Int
        val imgBgPaddingStart: Int
        val pageFactory: TextPageFactory
        val pageDelegate: PageDelegate?
        val isScroll: Boolean
        fun onReadScroll(offset: Int)
        var isSelectingSearchResult: Boolean
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onImageLongPress(x: Float, y: Float, src: String)
        fun onCancelSelect()
        fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean
        fun oldClickImg(src: String): Boolean
        fun clickImg(click: String, src: String)
        fun onReviewClick(paragraphNum: Int, count: Int, chapterIndex: Int)
        fun onHighlightClick(highlight: BookHighlight, x: Float, y: Float)
        fun onHighlightRuleClick(ruleId: Long, x: Float, y: Float)
    }

    private fun resolveReviewId(textLine: TextLine): Int {
        if (textLine.isTitle) return -1
        val reviewId = textLine.paragraphNum - textLine.reviewTitleOffset
        return if (reviewId > 0) reviewId else textLine.paragraphNum
    }
}

internal fun highlightRangeIntersects(
    columnStart: Int,
    columnEnd: Int,
    rangeStart: Int,
    rangeEnd: Int
): Boolean = columnStart < columnEnd && rangeStart < rangeEnd &&
        columnStart < rangeEnd && columnEnd > rangeStart

internal fun highlightRuleAtColumn(
    matches: List<HighlightRuleMatcher.RuleMatch>,
    columnStart: Int,
    columnEnd: Int,
    isTitle: Boolean
): HighlightRuleMatcher.RuleMatch? = matches.lastOrNull {
    (if (isTitle) it.applyToTitle else it.applyToBody) &&
            highlightRangeIntersects(columnStart, columnEnd, it.start, it.end)
}

internal inline fun highlightSelectionEndLength(
    columnIndex: Int,
    columnLength: () -> Int
): Int = if (columnIndex < 0) 0 else columnLength()

internal fun String.hasHighlightableText(): Boolean = any { it != '\r' && it != '\n' }
