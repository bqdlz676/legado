package io.legado.app.ui.book.read.vertical

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.spToPx

/**
 * Android 字形测量实现：包装 [TextPaint]，供竖排分页器测量字宽。
 *
 * 字体：正文 Noto Serif SC（思源宋体）。[typeface] 由上层按字体加载结果注入；
 * 未注入时回退系统默认 serif，不在此处硬编码字体文件路径。
 */
class TextPaintVerticalMetrics(
    private val typeface: Typeface? = null,
) : VerticalTypefaceMetrics {

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface ?: Typeface.SERIF
    }

    override fun measureAdvance(text: String, sizePx: Float, bold: Boolean): Float {
        paint.textSize = sizePx
        paint.isFakeBoldText = bold
        return paint.measureText(text)
    }

    override fun classify(ch: Char): CharKind = VerticalCharClassifier.classify(ch)
}

/**
 * 竖排引擎 Android 工厂：sp/dp → px 在此换算后构造 [VerticalLayoutEngine]，
 * 核心排版层只吃 px 数值，保持 JVM 可单测。
 */
object VerticalLayoutEngineFactory {

    /**
     * @param bodySp 正文字号，默认 18sp（规格锁定值）。
     * @param typeface Noto Serif SC 字体实例，上层加载字体资产后注入。
     */
    fun create(
        viewWidthPx: Int,
        viewHeightPx: Int,
        bodySp: Float = VerticalTokens.BODY_SP,
        typeface: Typeface? = null,
    ): VerticalLayoutEngine {
        val bodyPx = bodySp.spToPx()
        val titlePx = (bodySp + VerticalTokens.TITLE_SIZE_OFFSET_SP).spToPx()
        val titleGapPx = VerticalTokens.TITLE_GAP_DP.dpToPx()
        val metrics = TextPaintVerticalMetrics(typeface)
        return VerticalLayoutEngine(
            bodySizePx = bodyPx,
            titleSizePx = titlePx,
            metrics = metrics,
            titleGapPx = titleGapPx,
        )
    }

    /** 注音小字字号（px），供左侧 18% 批注栏取用。 */
    fun annotationSizePx(): Float = VerticalTokens.ANNOTATION_SP.spToPx()
}
