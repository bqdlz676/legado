package io.legado.app.ui.book.read.vertical

import io.legado.app.ui.components.VerticalReaderParams

/**
 * 落文 · 竖排阅读引擎 —— 排版常量（口径逐字来自切片-09 一、3.1 / 五）。
 *
 * 为什么常量单独收口：比例与 sp/dp 名义值一旦散落在分页器、命中、工厂各处，
 * 规格微调时就会出现“同一行距两套数”的漂移；这里是唯一出处，其余类只读不改。
 *
 * 规格原文要点：
 * - 列宽（正文版心）= 屏幕宽 × 0.82，左侧 18% 留给注音/批注小字
 * - 正文 18sp，行距 = 字号 × 1.8，列间距 = 字号 × 2.5
 * - 正文 Noto Serif SC（思源宋体），注音小字无衬线 10sp
 * - 章节标题：右侧竖排，比正文大 2sp 加粗，与正文间距 24dp
 * - 翻页命中：左 1/5 上翻，右 1/5 下翻
 *
 * 本对象只持有无量纲比例与 sp/dp 名义值；sp/dp → px 的换算在 Android 工厂层完成，
 * 核心排版层保持纯 JVM 可单测，不引用 android.util。
 */
object VerticalTokens {

    // ── S-09 排版参数：本层不再自写字面值，统一一层转发到单源
    // [VerticalReaderParams]。为什么统一：避免排版引擎、阅读页 UI、单测三处口径漂移；
    // 旧常量名全部保留，既有调用方不改，仅做 Double→Float 适配，渲染流程与计算口径不变。 ──

    /** 正文默认字号 sp（转发自 [VerticalReaderParams.BODY_FONT_SIZE_SP]）。 */
    val BODY_SP: Float = VerticalReaderParams.BODY_FONT_SIZE_SP.toFloat()

    /** 注音/批注小字字号 sp，无衬线（转发自 [VerticalReaderParams.ANNOTATION_FONT_SIZE_SP]）。 */
    val ANNOTATION_SP: Float = VerticalReaderParams.ANNOTATION_FONT_SIZE_SP.toFloat()

    /** 行距 = 字号 × 行距系数；竖排中该比例落在列内纵向字距 pitch 上。 */
    val LINE_HEIGHT_RATIO: Float = VerticalReaderParams.LINE_HEIGHT_RATIO.toFloat()

    /** 列间距 = 字号 × 列距系数。 */
    val COLUMN_GAP_RATIO: Float = VerticalReaderParams.COLUMN_GAP_RATIO.toFloat()

    /** 正文版心宽度占屏宽比例（转发自 [VerticalReaderParams.COLUMN_WIDTH_RATIO]）。 */
    val TEXT_BLOCK_RATIO: Float = VerticalReaderParams.COLUMN_WIDTH_RATIO.toFloat()

    /** 左侧注音/批注栏比例 = 1 − 版心比例（同由单源栏宽比例派生，不另立系数）。 */
    val NOTE_BAND_RATIO: Float = (1.0 - VerticalReaderParams.COLUMN_WIDTH_RATIO).toFloat()

    /** 标题比正文大的字号增量 sp（转发自 [VerticalReaderParams.TITLE_FONT_SIZE_DELTA_SP]）。 */
    val TITLE_SIZE_OFFSET_SP: Float = VerticalReaderParams.TITLE_FONT_SIZE_DELTA_SP.toFloat()

    /** 标题列与正文列之间的横向间距 dp（转发自 [VerticalReaderParams.TITLE_BODY_GAP_DP]）。 */
    val TITLE_GAP_DP: Float = VerticalReaderParams.TITLE_BODY_GAP_DP.toFloat()

    /** 进度线宽 dp（转发自 [VerticalReaderParams.PROGRESS_LINE_WIDTH_DP]；引擎只产出 0f..1f 进度比例，不画线）。 */
    val PROGRESS_LINE_DP: Float = VerticalReaderParams.PROGRESS_LINE_WIDTH_DP.toFloat()

    /** 左右边缘翻页手势区各占屏宽 1/5，中间 3/5 为正文点字区。 */
    const val EDGE_ZONE_RATIO = 1f / 5f

    /** 翻页掀角时长 400ms（动画层取用；引擎不消费）。 */
    const val PAGE_PEEK_DURATION_MS = 400

    /** 翻页背面墨色透印 alpha 15%（动画层取用；引擎不消费）。 */
    const val BACK_INK_ALPHA = 0.15f
}
