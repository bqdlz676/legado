package io.legado.app.ui.book.read.vertical

import kotlin.math.abs

/**
 * 落文 · 竖排阅读引擎门面（冻结 API）。
 *
 * 下游阅读页 UI 通过本类取分页结果、逐字命中与翻页命中；测量/缓存/分页细节全部内聚在本包，
 * 不依赖 Legado 横排引擎的任何类（只保留其文本解析/分章结果作为输入）。
 *
 * 冻结签名（不可改）：
 * - [layout] `(text: String, viewWidthPx: Int, viewHeightPx: Int): VerticalPage`
 * - [hitTest] `(x: Float, y: Float): CharHit?`
 * - [invalidateCache] `()`
 *
 * 翻页命中（切片-09 一、3.3 / 手势分区）：左 1/5 上翻，右 1/5 下翻，中间 3/5 为正文区。
 * 兜底铁律：先命中字则先出气泡/选中，边缘翻页不得抢边缘的字——UI 层先调 [hitTest]，
 * 命中非空时不消费 [pageTurnAt]。
 */
class VerticalLayoutEngine(
    private val bodySizePx: Float,
    private val titleSizePx: Float = bodySizePx + 2f,
    private val metrics: VerticalTypefaceMetrics = DefaultTypefaceMetrics(),
    /** 标题列与正文列横向间距（px）；Android 工厂按 24dp 换算传入。 */
    private val titleGapPx: Float = VerticalTokens.TITLE_GAP_DP,
) {
    // 字位缓存为引擎内部实现细节，不暴露进公开构造签名。
    private val cache = VerticalMeasureCache()
    private var spec: VerticalLayoutSpec? = null
    private var pages: List<PageData> = emptyList()
    private var paragraphs: List<ParagraphRange> = emptyList()
    private var currentIndex: Int = 0
    private var totalCells: Int = 0
    private var lastText: String? = null
    private var lastTitle: String? = null
    private var lastSpecKey: Long = 0L

    /** 最近一次排版是否复用字位缓存（重排 ×3 路径，bench/测试取用）。 */
    var lastLayoutReusedCache: Boolean = false
        private set

    val canPrev: Boolean get() = currentIndex > 0
    val canNext: Boolean get() = pages.isNotEmpty() && currentIndex < pages.lastIndex

    /** 当前页（无页时返回 null）。 */
    val currentPage: VerticalPage?
        get() = pages.getOrNull(currentIndex)?.toVerticalPage(totalCells)

    /**
     * 排版入口（冻结签名）。把整段文本分页，返回当前页；列从右向左、列内自上而下。
     */
    fun layout(text: String, viewWidthPx: Int, viewHeightPx: Int): VerticalPage =
        layout(text, viewWidthPx, viewHeightPx, title = null)

    /**
     * 排版入口（带章节标题）。标题置最右列、字号更大；冻结三参方法委托本方法。
     */
    fun layout(text: String, viewWidthPx: Int, viewHeightPx: Int, title: String?): VerticalPage {
        val layoutSpec = VerticalLayoutSpec.create(
            viewWidthPx = viewWidthPx.toFloat(),
            viewHeightPx = viewHeightPx.toFloat(),
            bodySizePx = bodySizePx,
            titleSizePx = titleSizePx,
            titleGapPx = titleGapPx,
        )
        spec = layoutSpec

        val specKey = specFingerprint(viewWidthPx, viewHeightPx)
        val sameContent = text == lastText && title == lastTitle && pages.isNotEmpty()
        val reuseCache = sameContent && specKey == lastSpecKey

        val paginator = VerticalPaginator(layoutSpec, metrics, cache)
        val result = paginator.layout(text, title, reuseCache = reuseCache)

        pages = result.pages
        paragraphs = result.paragraphs
        totalCells = result.totalCells
        lastText = text
        lastTitle = title
        lastSpecKey = specKey
        lastLayoutReusedCache = result.reusedFromCache

        currentIndex = if (sameContent) {
            currentIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        } else {
            0
        }
        return currentPage ?: emptyPage()
    }

    /** 逐字命中；未命中返回 null。命中优先于边缘翻页区。 */
    fun hitTest(x: Float, y: Float): CharHit? {
        val page = pages.getOrNull(currentIndex) ?: return null
        val thickness = spec?.columnThicknessPx ?: return null

        val column = page.columns.firstOrNull { x >= it.left && x <= it.left + thickness }
            ?: return null

        // 1. 方框精确命中。
        column.cells.firstOrNull { it.contains(x, y) }?.let { return toHit(it) }

        // 2. 纵向最近字回退（横向必须落在该列厚度内，容差半字距），供点字出气泡。
        val pitch = spec?.charPitchPx ?: return null
        val tolerance = pitch * 0.5f
        val nearest = column.cells.minByOrNull { abs(it.y + it.size / 2f - y) } ?: return null
        if (abs(nearest.y + nearest.size / 2f - y) <= tolerance) {
            return toHit(nearest)
        }
        return null
    }

    /**
     * 翻页命中分区。左 1/5 → [PageTurn.PREV]（上翻），右 1/5 → [PageTurn.NEXT]（下翻），
     * 中间 3/5 返回 null。字命中优先：UI 应先调 [hitTest]，非空时不调用本方法。
     */
    fun pageTurnAt(x: Float): PageTurn? {
        val s = spec ?: return null
        return when {
            x <= s.centerLeftPx -> PageTurn.PREV
            x >= s.centerRightPx -> PageTurn.NEXT
            else -> null
        }
    }

    /** 上翻一页；已是首页返回 null。 */
    fun prev(): VerticalPage? {
        if (!canPrev) return null
        currentIndex--
        return currentPage
    }

    /** 下翻一页；已是末页返回 null。 */
    fun next(): VerticalPage? {
        if (!canNext) return null
        currentIndex++
        return currentPage
    }

    /** 跳转到指定页（越界收敛）；返回目标页。 */
    fun jumpTo(index: Int): VerticalPage? {
        if (pages.isEmpty()) return null
        currentIndex = index.coerceIn(0, pages.lastIndex)
        return currentPage
    }

    /** 清全部缓存与页数据（章节退出/回收/字号字体变化时调用）。 */
    fun invalidateCache() {
        cache.clearAll()
        pages = emptyList()
        paragraphs = emptyList()
        currentIndex = 0
        totalCells = 0
        lastText = null
        lastTitle = null
        lastSpecKey = 0L
        lastLayoutReusedCache = false
    }

    /** 测试/bench 取用：当前页内部字位（含侧排标记），不对外冻结。 */
    internal fun debugCurrentCells(): List<LayoutCell> =
        pages.getOrNull(currentIndex)?.columns?.flatMap { it.cells }.orEmpty()

    private fun toHit(cell: LayoutCell): CharHit {
        val paragraph = paragraphs.lastOrNull { cell.offset >= it.startOffset }
        val pos = if (paragraph != null) cell.offset - paragraph.startOffset else 0
        return CharHit(
            char = cell.char,
            offset = cell.offset,
            paragraphText = paragraph?.text.orEmpty(),
            pos = pos,
        )
    }

    private fun emptyPage(): VerticalPage = VerticalPage(
        columns = emptyList(),
        pageIndex = 0,
        progress = 0f,
    )

    private fun specFingerprint(viewWidthPx: Int, viewHeightPx: Int): Long {
        // 尺寸/字号任一变化都视为版面变化，字位缓存不再直接复用。
        val w = viewWidthPx.toLong() and 0xFFFFL
        val h = viewHeightPx.toLong() and 0xFFFFL
        val body = bodySizePx.toLong() and 0xFFL
        val title = titleSizePx.toLong() and 0xFFL
        return (w shl 40) or (h shl 24) or (body shl 8) or title
    }

    private fun PageData.toVerticalPage(total: Int): VerticalPage {
        val columns = columns.map { col ->
            Column(
                cells = col.cells.map { cell ->
                    CharCell(
                        char = cell.char,
                        offset = cell.offset,
                        x = cell.x,
                        y = cell.y,
                        width = cell.size,
                        height = cell.size,
                    )
                },
                left = col.left,
                top = col.top,
            )
        }
        val progress = if (total <= 0) 0f else (endOffset.toFloat() / total).coerceIn(0f, 1f)
        return VerticalPage(columns = columns, pageIndex = index, progress = progress)
    }
}
