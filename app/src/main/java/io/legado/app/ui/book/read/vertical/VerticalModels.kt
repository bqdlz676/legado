package io.legado.app.ui.book.read.vertical

/**
 * 落文 · 竖排阅读引擎 —— 对外冻结数据模型。
 *
 * 坐标约定（传统中文竖排，从右向左）：
 * - 列：第 0 列在最右侧，列号自右向左递增；[Column.left] 为列左缘 x。
 * - 字：列内自上而下排列；[CharCell.x]/[CharCell.y] 为字形方框左缘/上缘，方框见方。
 * - 所有几何量均为 px，原点在屏幕左上。
 *
 * 注意：本文件中 [CharCell] / [Column] / [VerticalPage] / [CharHit] 的构造签名为冻结 API，
 * 下游阅读页 UI 直接调用，字段名与顺序不可改。
 */

/** 单字布局结果（分页后逐字缓存进 sparse array，供重排复用，对应规格“每列每字 x/y 缓存”）。 */
data class CharCell(
    /** 字形字符。 */
    val char: Char,
    /** 章节内字符流下标（按发出顺序 0 起，不含换行/空白占位）。 */
    val offset: Int,
    /** 字形方框左缘 x（px）。 */
    val x: Float,
    /** 字形方框上缘 y（px）。 */
    val y: Float,
    /** 方框宽（px，竖排取 1 em 见方）。 */
    val width: Float,
    /** 方框高（px）。 */
    val height: Float,
)

/** 一列（竖排栏），列内字自上而下。 */
data class Column(
    val cells: List<CharCell>,
    /** 列左缘 x（px）。 */
    val left: Float,
    /** 列首字上缘 y（px）。 */
    val top: Float,
)

/** 一页（含若干列，列从右向左排列）。 */
data class VerticalPage(
    val columns: List<Column>,
    /** 页序号（0 起）。 */
    val pageIndex: Int,
    /** 阅读进度 0f..1f（按本页末字 / 全章字数）。 */
    val progress: Float,
)

/** 逐字命中结果。 */
data class CharHit(
    /** 命中字符。 */
    val char: Char,
    /** 章节内字符流下标。 */
    val offset: Int,
    /** 命中字所在段落原文。 */
    val paragraphText: String,
    /** 命中字在段落内的下标。 */
    val pos: Int,
)

/** 翻页方向：左边缘 → 上翻（回上一页），右边缘 → 下翻（下一页）。 */
enum class PageTurn {
    /** 上翻页（向右侧已读方向回退）。 */
    PREV,

    /** 下翻页（向左侧未读方向前进）。 */
    NEXT,
}

// ---------------------------------------------------------------------------
// 以下为引擎内部模型，不对外冻结；渲染层如需侧排/标题信息，由适配层从内部数据映射。
// ---------------------------------------------------------------------------

/** 字符类别，供测量、侧排分流与渲染取用。 */
internal enum class CharKind {
    /** CJK 表意文字 / 假名等竖排正字。 */
    CJK,

    /** 拉丁字母。 */
    LATIN,

    /** 数字 / 年份 / 年代。 */
    DIGIT,

    /** 标点。 */
    PUNCT,

    /** 空白（不产生字形）。 */
    SPACE,

    /** 章节标题字。 */
    TITLE,
}

/** 引擎内部逐字布局记录（含侧排/列号等渲染信息，输出 [CharCell] 时剥离）。 */
internal data class LayoutCell(
    val char: Char,
    val offset: Int,
    val kind: CharKind,
    /** 全局列号：0 = 最右列。 */
    val columnIndex: Int,
    val x: Float,
    val y: Float,
    /** 字形方框边长（见方）。 */
    val size: Float,
    /** 内嵌拉丁/数字/年份是否旋转 90° 侧排。 */
    val rotated: Boolean,
) {
    val right: Float get() = x + size
    val bottom: Float get() = y + size

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= right && py >= y && py <= bottom
}

/** 段落范围：startOffset 为该段首字在字符流中的下标。 */
internal data class ParagraphRange(val startOffset: Int, val text: String)

/** 引擎内部一页数据。 */
internal data class PageData(
    val index: Int,
    val columns: List<ColumnData>,
    /** 本页首字下标（含）。 */
    val startOffset: Int,
    /** 本页末字下标（不含）。 */
    val endOffset: Int,
)

/** 引擎内部一列数据。 */
internal data class ColumnData(
    val left: Float,
    val top: Float,
    val cells: List<LayoutCell>,
)
