package io.legado.app.ui.book.read.vertical

/**
 * 落文 · 分页引擎（竖排模式）—— sp/dp 名义值 → px 的纯 JVM 换算结果。
 *
 * 为什么抽出来：`android.util.TypedValue.applyDimension` 与 `Resources.getSystem()`
 * 都是 Android 绑定，纯 JVM 单测跑不了；把“选号规则 + 比例乘法”收口在本层，
 * Android 工厂只负责取系统 density / scaledDensity 注入，换算规则可在 JVM 单测覆盖。
 *
 * 与 TypedValue 口径对齐：dp → px 乘 density；sp → px 乘 scaledDensity（字号缩放）。
 */
data class VerticalEnginePxSpec(
    /** 正文字号（px）。 */
    val bodySizePx: Float,
    /** 标题字号（px，正文 + [VerticalTokens.TITLE_SIZE_OFFSET_SP] sp）。 */
    val titleSizePx: Float,
    /** 注音/批注小字字号（px）。 */
    val annotationSizePx: Float,
    /** 标题列与正文列横向间距（px）。 */
    val titleGapPx: Float,
)

/**
 * 字号/间距选号纯逻辑：输入名义 sp/dp 与系统乘数，输出 px 数值；不引用任何 android.* 类。
 */
object VerticalPxResolver {

    /**
     * @param bodySp 正文字号 sp（默认规格锁定 18sp）。
     * @param density 显示密度，dp → px 乘数（取自 DisplayMetrics.density）。
     * @param fontScale 字号缩放，sp → px 乘数（取自 DisplayMetrics.scaledDensity）。
     * @param titleGapDp 标题列与正文列间距 dp（默认规格锁定 24dp）。
     * @param annotationSp 注音/批注小字 sp（默认规格锁定 10sp）。
     */
    fun resolve(
        bodySp: Float = VerticalTokens.BODY_SP,
        density: Float,
        fontScale: Float,
        titleGapDp: Float = VerticalTokens.TITLE_GAP_DP,
        annotationSp: Float = VerticalTokens.ANNOTATION_SP,
    ): VerticalEnginePxSpec {
        val bodyPx = bodySp * fontScale
        val titlePx = (bodySp + VerticalTokens.TITLE_SIZE_OFFSET_SP) * fontScale
        val annotationPx = annotationSp * fontScale
        val titleGapPx = titleGapDp * density
        return VerticalEnginePxSpec(
            bodySizePx = bodyPx,
            titleSizePx = titlePx,
            annotationSizePx = annotationPx,
            titleGapPx = titleGapPx,
        )
    }
}
