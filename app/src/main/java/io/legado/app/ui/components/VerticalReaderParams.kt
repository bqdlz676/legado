package io.legado.app.ui.components

import kotlin.math.floor

/**
 * S-09 阅读页竖排 · 排版参数单源（平台无关层，纯数值）
 *
 * 〔为什么参数集中在一处〕
 * 竖排栏宽比例、正文字号、行距系数、列间距系数、注音字号、章节标题增量、
 * 标题与正文间距、进度线宽与进度线颜色，全部逐字来自
 * `docs/落文-规格切片/切片-09-阅读页竖排.md` 第 7、12 行与
 * `docs/锚点/S-09-阅读页竖排.md` 参数表。集中单点定义是为了：
 *  1. 将来的渲染层（Composable / 绘制代码）只允许引用本表，不得在页面里另写
 *     系数、尺寸或色值，避免排版引擎、阅读页 UI、单测三处口径漂移；
 *  2. 单测可对确定输入断言确定输出（如屏宽 1080dp → 栏宽 885.6）；
 *  3. 后续调参只改这一处，改动可被 grep 回证与单测同时兜住。
 *
 * 〔为什么用纯函数〕
 * [computeVerticalColumnMetrics] 只接收 Double，返回纯数值数据类，
 * 不调用任何 Android API（dp/sp 换算、Paint、Canvas、Resources），也不依赖
 * Compose 运行时或资源加载。因此在普通 JVM 单测里即可直接运行断言，
 * 复现不了“测试类 NoClassDefFoundError”那类 Android 依赖事故；
 * sp/dp ↔ px 的单位换算留给将来的渲染层在接入时自行完成。
 *
 * 〔颜色口径〕
 * 已读进度线颜色只取既有单源 [ComponentPalette.TEXT_ACCENT]（朱砂），
 * 本文件不写任何十六进制色值（锚点卡判据 2）。
 */
object VerticalReaderParams {

    // ── 切片-09 原文参数（锚点卡参数表；单位不许自创） ──

    /** 栏宽比例：列宽 = 屏幕宽 × 0.82（左侧 18% 留给注音 / 批注小字） */
    const val COLUMN_WIDTH_RATIO: Double = 0.82

    /** 正文字号 sp（原文“字号：18sp”） */
    const val BODY_FONT_SIZE_SP: Double = 18.0

    /** 行距系数：行距 = 字号 × 1.8 */
    const val LINE_HEIGHT_RATIO: Double = 1.8

    /** 列间距系数：列间距 = 字号 × 2.5 */
    const val COLUMN_GAP_RATIO: Double = 2.5

    /** 注音小字字号 sp（无衬线；原文“注音小字无衬线 10sp”） */
    const val ANNOTATION_FONT_SIZE_SP: Double = 10.0

    /** 章节标题相对正文字号增量 sp（原文“比正文大 2sp 加粗”） */
    const val TITLE_FONT_SIZE_DELTA_SP: Double = 2.0

    /** 章节标题与正文间距 dp（原文“与正文间距 24dp”） */
    const val TITLE_BODY_GAP_DP: Double = 24.0

    /** 阅读进度线宽 dp（原文“页面最右边缘一条竖线 1dp 宽”） */
    const val PROGRESS_LINE_WIDTH_DP: Double = 1.0

    /**
     * 已读进度线颜色：朱砂。只引用既有单源 [ComponentPalette.TEXT_ACCENT]，
     * 本文件不内联任何 hex。
     */
    val progressLineColor: Long get() = ComponentPalette.TEXT_ACCENT
}

/**
 * 竖排单列排版度量（纯数值，平台无关）。
 *
 * @property columnWidthDp 栏宽 dp = 屏宽 dp × [VerticalReaderParams.COLUMN_WIDTH_RATIO]
 * @property lineHeightSp 行高 sp = 字号 sp × [VerticalReaderParams.LINE_HEIGHT_RATIO]
 * @property columnGapSp 列间距 sp = 字号 sp × [VerticalReaderParams.COLUMN_GAP_RATIO]
 * @property charsPerColumn 每列可容字数（估算：栏宽 ÷ 单字 em 宽即字号，向下取整；
 *   竖排真实字数须由渲染层按列高与字形测量修正）
 */
data class VerticalColumnMetrics(
    val columnWidthDp: Double,
    val lineHeightSp: Double,
    val columnGapSp: Double,
    val charsPerColumn: Int,
)

/**
 * 由屏宽 dp 与字号 sp 纯计算竖排栏宽 / 行高 / 列间距 / 每列可容字数。
 *
 * 边界兜底：字号 ≤ 0 时回退到 [VerticalReaderParams.BODY_FONT_SIZE_SP]，
 * 屏宽 ≤ 0 时栏宽记 0；任何输入都不抛异常、不产生 NaN / Infinity。
 * 本函数只使用 Double / Int，不含任何 Android 或 Compose 类型。
 */
fun computeVerticalColumnMetrics(
    screenWidthDp: Double,
    fontSizeSp: Double,
): VerticalColumnMetrics {
    val safeFontSize = if (fontSizeSp > 0.0) fontSizeSp else VerticalReaderParams.BODY_FONT_SIZE_SP
    val safeWidth = if (screenWidthDp > 0.0) screenWidthDp else 0.0

    val columnWidthDp = safeWidth * VerticalReaderParams.COLUMN_WIDTH_RATIO
    val lineHeightSp = safeFontSize * VerticalReaderParams.LINE_HEIGHT_RATIO
    val columnGapSp = safeFontSize * VerticalReaderParams.COLUMN_GAP_RATIO

    val charsPerColumn = if (columnWidthDp > 0.0 && safeFontSize > 0.0) {
        floor(columnWidthDp / safeFontSize).toInt()
    } else {
        0
    }

    return VerticalColumnMetrics(
        columnWidthDp = columnWidthDp,
        lineHeightSp = lineHeightSp,
        columnGapSp = columnGapSp,
        charsPerColumn = charsPerColumn,
    )
}
