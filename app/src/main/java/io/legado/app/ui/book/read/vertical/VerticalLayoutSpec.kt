package io.legado.app.ui.book.read.vertical

/**
 * 落文 · 竖排阅读引擎 —— 版心规格（px）。所有几何量在此一次性算好，分页/命中/渲染只读不改。
 *
 * 规格口径（切片-09 一、3.1）：
 * - 列宽（正文版心）= 屏宽 × 0.82；左侧 18% 为注音/批注小字栏。
 * - 字号 18sp；行距 = 字号 × 1.8（列内纵向 pitch）；列间距 = 字号 × 2.5。
 * - 标题比正文大 2sp 加粗；标题列与正文列间距 24dp。
 *
 * 兜底说明（规格未写死、由实现收口）：
 * - 单列厚度取 1 em（一个字身宽）；标题列厚度取标题字号 1 em。
 * - 竖排“行距 1.8×”落在列内字距上；字形方框取 1 em 见方。
 * - sp/dp → px 由 Android 工厂层完成，本类只持有 px 数值，保持 JVM 可测。
 */
data class VerticalLayoutSpec(
    val viewWidthPx: Float,
    val viewHeightPx: Float,
    val bodySizePx: Float,
    /** 标题字号（正文 + 2sp 换算值）。 */
    val titleSizePx: Float,
    /** 左侧注音/批注栏宽。 */
    val noteBandWidthPx: Float,
    /** 正文版心宽（右侧 82%）。 */
    val textBlockWidthPx: Float,
    /** 正文单列厚度（1 em）。 */
    val bodyThicknessPx: Float,
    /** 标题单列厚度（标题字号 1 em）。 */
    val titleThicknessPx: Float,
    /** 列间距。 */
    val columnGapPx: Float,
    /** 列内纵向字距 pitch。 */
    val charPitchPx: Float,
    /** 标题列与正文列之间的横向间距。 */
    val titleGapPx: Float,
    val topPaddingPx: Float,
    val bottomPaddingPx: Float,
) {
    /** 正文版心左边界（竖排从右向左排，右边界贴屏右）。 */
    val textBlockLeft: Float get() = noteBandWidthPx

    /** 正文版心右边界。 */
    val textBlockRight: Float get() = viewWidthPx

    val contentTop: Float get() = topPaddingPx

    val contentBottom: Float get() = viewHeightPx - bottomPaddingPx

    val contentHeight: Float get() = contentBottom - contentTop

    /** 每页可容纳的正文列数（版心宽 ÷ 单列厚度+列间距，向下取整，至少 1）。 */
    val columnsPerPage: Int
        get() {
            val slot = bodyThicknessPx + columnGapPx
            if (slot <= 0f) return 1
            val count = ((textBlockRight - textBlockLeft) + columnGapPx) / slot
            return count.toInt().coerceAtLeast(1)
        }

    /** 每列可容纳的字数（版心高 ÷ 字距 pitch，向下取整，至少 1）。 */
    val rowsPerColumn: Int
        get() {
            if (charPitchPx <= 0f) return 1
            val count = (contentHeight + FIT_EPSILON) / charPitchPx
            return count.toInt().coerceAtLeast(1)
        }

    /** 左右边缘翻页手势区宽度（屏宽 1/5）。 */
    val edgeZonePx: Float get() = viewWidthPx * VerticalTokens.EDGE_ZONE_RATIO

    val centerLeftPx: Float get() = edgeZonePx

    val centerRightPx: Float get() = viewWidthPx - edgeZonePx

    init {
        require(viewWidthPx > 0f && viewHeightPx > 0f) {
            "版面尺寸必须为正: $viewWidthPx x $viewHeightPx"
        }
        require(bodySizePx > 0f) { "字号必须为正: $bodySizePx" }
    }

    companion object {
        private const val FIT_EPSILON = 0.5f

        /**
         * 由 px 数值构造规格。sp/dp → px 由 Android 层完成后传入。
         *
         * @param bodySizePx 正文字号（px）。
         * @param titleSizePx 标题字号（px），默认正文 + 2sp 量级。
         * @param titleGapPx 标题列与正文列横向间距（px）。
         */
        fun create(
            viewWidthPx: Float,
            viewHeightPx: Float,
            bodySizePx: Float,
            titleSizePx: Float = bodySizePx + VerticalTokens.TITLE_SIZE_OFFSET_SP,
            titleGapPx: Float = VerticalTokens.TITLE_GAP_DP,
            topPaddingPx: Float = bodySizePx,
            bottomPaddingPx: Float = bodySizePx,
        ): VerticalLayoutSpec {
            val noteBand = viewWidthPx * VerticalTokens.NOTE_BAND_RATIO
            val textBlock = viewWidthPx * VerticalTokens.TEXT_BLOCK_RATIO
            return VerticalLayoutSpec(
                viewWidthPx = viewWidthPx,
                viewHeightPx = viewHeightPx,
                bodySizePx = bodySizePx,
                titleSizePx = titleSizePx,
                noteBandWidthPx = noteBand,
                textBlockWidthPx = textBlock,
                bodyThicknessPx = bodySizePx,
                titleThicknessPx = titleSizePx,
                columnGapPx = bodySizePx * VerticalTokens.COLUMN_GAP_RATIO,
                charPitchPx = bodySizePx * VerticalTokens.LINE_HEIGHT_RATIO,
                titleGapPx = titleGapPx,
                topPaddingPx = topPaddingPx,
                bottomPaddingPx = bottomPaddingPx,
            )
        }
    }
}
