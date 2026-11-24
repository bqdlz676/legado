package io.legado.app.ui.book.read.vertical

/**
 * 落文 · 分页引擎（竖排模式） —— 分页器。
 *
 * 排列规则（传统中文竖排，从右向左）：
 * - 列号 0 在最右；列内字自上而下；一列写满后向左开新列。
 * - 标题在最右侧若干列，字号更大；标题列与正文列之间留 [VerticalLayoutSpec.titleGapPx]。
 * - 换行只做段落切分（命中结果回填段落原文与段内下标）；段落续排进当前列，列满再向左开新列。
 * - 内嵌拉丁字母 / 数字 / 年份按 90° 侧排（[LayoutCell.rotated] 标记，渲染层旋转）。
 * - 分页按“每页列数”切分，列不跨页截断；末页允许不满。
 *
 * 性能口径（切片-09 五）：首次分页后逐字 x/y 写入 sparse array 等价缓存；
 * 同尺寸重排命中缓存，跳过测量与几何计算（×3 路径）。
 */
internal class VerticalPaginator(
    private val spec: VerticalLayoutSpec,
    private val metrics: VerticalTypefaceMetrics,
    private val cache: VerticalMeasureCache,
) {

    /** 分页结果。 */
    data class PaginationResult(
        val pages: List<PageData>,
        val paragraphs: List<ParagraphRange>,
        val totalCells: Int,
        /** 本次排版是否完全复用字位缓存（重排 ×3 路径）。 */
        val reusedFromCache: Boolean,
    )

    fun layout(text: String, title: String?, reuseCache: Boolean): PaginationResult {
        val expected = countCells(title, text)
        if (expected <= 0) {
            return PaginationResult(
                pages = listOf(PageData(0, emptyList(), 0, 0)),
                paragraphs = emptyList(),
                totalCells = 0,
                reusedFromCache = false,
            )
        }

        // 重排 ×3 路径：缓存字位齐全时按列号重建页结构，不重新测量、不重算几何。
        if (reuseCache && cache.coversRects(expected)) {
            val rebuilt = rebuildFromCache(expected)
            if (rebuilt != null) {
                return PaginationResult(
                    pages = paginate(rebuilt),
                    paragraphs = parseParagraphs(title, text),
                    totalCells = expected,
                    reusedFromCache = true,
                )
            }
        }

        val columns = ArrayList<ColumnBuilder>()
        val paragraphs = ArrayList<ParagraphRange>()
        var offset = 0
        var columnIndex = 0
        var runningRight = spec.textBlockRight
        var current: ColumnBuilder? = null

        fun openColumn(isTitle: Boolean, gapBefore: Float): ColumnBuilder {
            val thickness = if (isTitle) spec.titleThicknessPx else spec.bodyThicknessPx
            val right = runningRight - gapBefore
            val left = right - thickness
            val builder = ColumnBuilder(columnIndex, left, thickness, isTitle)
            runningRight = left - spec.columnGapPx
            columnIndex++
            columns.add(builder)
            return builder
        }

        fun ensureColumn(isTitle: Boolean): ColumnBuilder {
            val cur = current
            // 标题列 → 首列正文：留 24dp 标题间距；同类换列不留。
            val gap = if (cur != null && cur.isTitle && !isTitle) spec.titleGapPx else 0f
            return if (cur != null && cur.isTitle == isTitle) cur
            else openColumn(isTitle, gap).also { current = it }
        }

        fun emit(ch: Char, kind: CharKind, size: Float, isTitle: Boolean, rotated: Boolean) {
            val sizeKind = if (isTitle) CharKind.TITLE else kind
            val col = ensureColumn(isTitle)
            if (!col.canFit(size)) {
                current = openColumn(isTitle, 0f)
            }
            val target = current!!
            cache.advance(ch.code) {
                metrics.measureAdvance(ch.toString(), size, bold = isTitle)
            }
            val cell = LayoutCell(
                char = ch,
                offset = offset,
                kind = sizeKind,
                columnIndex = target.index,
                x = target.left,
                y = target.cursorY,
                size = size,
                rotated = rotated,
            )
            target.add(cell)
            cache.putRect(cell)
            offset++
        }

        // 1. 章节标题（最右列，竖排，字号更大、加粗）。
        val titleText = title?.trim().orEmpty()
        if (titleText.isNotEmpty()) {
            paragraphs.add(ParagraphRange(offset, titleText))
            for (ch in titleText) {
                if (ch.isWhitespace()) continue
                emit(ch, CharKind.TITLE, spec.titleSizePx, isTitle = true, rotated = false)
            }
        }

        // 2. 正文段落（换行分段；空白不产生字形）。
        for (raw in text.split('\n')) {
            val para = raw.trim()
            if (para.isEmpty()) continue
            val paraStart = offset
            paragraphs.add(ParagraphRange(paraStart, para))
            for (ch in para) {
                val kind = metrics.classify(ch)
                if (kind == CharKind.SPACE) continue
                val rotated = VerticalCharClassifier.isInlineScript(kind)
                emit(ch, kind, spec.bodySizePx, isTitle = false, rotated = rotated)
            }
        }

        val pageColumns = columns.map { it.toColumnData() }
        return PaginationResult(
            pages = paginate(pageColumns),
            paragraphs = paragraphs,
            totalCells = offset,
            reusedFromCache = false,
        )
    }

    /** 缓存字位齐全时重建列结构；缺字或数量不符返回 null，走冷排版。 */
    private fun rebuildFromCache(expected: Int): List<ColumnData>? {
        val byColumn = HashMap<Int, MutableList<LayoutCell>>()
        for (i in 0 until expected) {
            val cell = cache.rect(i) ?: return null
            byColumn.getOrPut(cell.columnIndex) { mutableListOf() }.add(cell)
        }
        return byColumn.toSortedMap().map { (_, cells) ->
            val sorted = cells.sortedBy { it.y }
            val first = sorted.first()
            ColumnData(
                left = first.x,
                top = first.y,
                thickness = first.size,
                cells = sorted,
            )
        }
    }

    private fun paginate(columns: List<ColumnData>): List<PageData> {
        if (columns.isEmpty()) {
            return listOf(PageData(0, emptyList(), 0, 0))
        }
        val perPage = spec.columnsPerPage.coerceAtLeast(1)
        val pages = ArrayList<PageData>()
        var start = 0
        var pageIndex = 0
        while (start < columns.size) {
            val end = minOf(start + perPage, columns.size)
            val slice = columns.subList(start, end)
            val cells = slice.flatMap { it.cells }
            val charStart = cells.firstOrNull()?.offset ?: 0
            val charEnd = cells.lastOrNull()?.let { it.offset + 1 } ?: 0
            pages.add(PageData(pageIndex, slice.toList(), charStart, charEnd))
            pageIndex++
            start = end
        }
        return pages
    }

    private fun parseParagraphs(title: String?, text: String): List<ParagraphRange> {
        val ranges = ArrayList<ParagraphRange>()
        var offset = 0
        val titleText = title?.trim().orEmpty()
        if (titleText.isNotEmpty()) {
            ranges.add(ParagraphRange(offset, titleText))
            offset += titleText.count { !it.isWhitespace() }
        }
        for (raw in text.split('\n')) {
            val para = raw.trim()
            if (para.isEmpty()) continue
            ranges.add(ParagraphRange(offset, para))
            offset += para.count { !it.isWhitespace() }
        }
        return ranges
    }

    private fun countCells(title: String?, text: String): Int {
        var n = title?.count { !it.isWhitespace() } ?: 0
        for (raw in text.split('\n')) {
            n += raw.trim().count { !it.isWhitespace() }
        }
        return n
    }

    private inner class ColumnBuilder(
        val index: Int,
        val left: Float,
        val thickness: Float,
        val isTitle: Boolean,
    ) {
        val cells = mutableListOf<LayoutCell>()
        var cursorY: Float = spec.contentTop

        fun canFit(size: Float): Boolean =
            cursorY + size <= spec.contentBottom + FIT_EPSILON

        fun add(cell: LayoutCell) {
            cells.add(cell)
            // 列内纵向 pitch：行距 1.8× 字号；标题按标题字号同比例推进。
            cursorY += cell.size * VerticalTokens.LINE_HEIGHT_RATIO
        }

        fun toColumnData(): ColumnData = ColumnData(
            left = left,
            top = spec.contentTop,
            thickness = thickness,
            cells = cells.toList(),
        )
    }

    private companion object {
        const val FIT_EPSILON = 0.5f
    }
}
