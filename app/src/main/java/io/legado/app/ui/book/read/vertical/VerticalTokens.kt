package io.legado.app.ui.book.read.vertical

/**
 * 落文 · 竖排阅读引擎 —— 排版常量（全部来自规格切片-09，原文逐字口径）。
 *
 * 规格原文要点：
 * - 列宽（正文版心）= 屏幕宽 × 0.82，左侧 18% 留给注音/批注小字
 * - 字号 18sp，行距 = 字号 × 1.8，列间距 = 字号 × 2.5
 * - 正文 Noto Serif SC（思源宋体），注音小字无衬线 10sp
 * - 章节标题：右侧竖排，比正文大 2sp 加粗，与正文间距 24dp
 * - 翻页命中：左 1/5 上翻，右 1/5 下翻
 *
 * 本对象只持有无量纲比例与 sp/dp 名义值；sp/dp → px 的换算在 Android 工厂层完成，
 * 核心排版层保持 JVM 可单测，不引用 android.util。
 */
object VerticalTokens {

    /** 正文默认字号 18sp。 */
    const val BODY_SP = 18f

    /** 注音/批注小字字号 10sp（无衬线）。 */
    const val ANNOTATION_SP = 10f

    /** 行距 = 字号 × 1.8（竖排中落在列内纵向字距 pitch 上）。 */
    const val LINE_HEIGHT_RATIO = 1.8f

    /** 列间距 = 字号 × 2.5。 */
    const val COLUMN_GAP_RATIO = 2.5f

    /** 正文版心宽度占屏宽比例（右侧 82%）。 */
    const val TEXT_BLOCK_RATIO = 0.82f

    /** 左侧注音/批注栏占屏宽比例（18%）。 */
    const val NOTE_BAND_RATIO = 0.18f

    /** 标题比正文大 2sp。 */
    const val TITLE_SIZE_OFFSET_SP = 2f

    /** 标题与正文间距 24dp。 */
    const val TITLE_GAP_DP = 24f

    /** 进度线宽 1dp（渲染层取用，引擎只产出进度比例）。 */
    const val PROGRESS_LINE_DP = 1f

    /** 左右边缘翻页手势区占屏宽比例：各 1/5，中间 3/5。 */
    const val EDGE_ZONE_RATIO = 1f / 5f

    /** 翻页掀角时长 400ms（动画层取用，引擎不消费）。 */
    const val PAGE_PEEK_DURATION_MS = 400

    /** 翻页背面墨色透印 alpha 15%（动画层取用）。 */
    const val BACK_INK_ALPHA = 0.15f
}
