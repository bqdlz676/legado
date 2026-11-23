package io.legado.app.ui.book.read.vertical

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint

/**
 * Android 字形测量实现：包装 [TextPaint]，供竖排分页器测量字宽。
 *
 * 字体：正文 Noto Serif SC（思源宋体）。[typeface] 由上层按字体加载结果注入；
 * 字体资产未打包前回退系统默认 serif，不在此处硬编码字体文件路径，避免丢资源即崩。
 *
 * 测试边界（交接清单 #4）：本类包装 TextPaint / Typeface，依赖 android.graphics，
 * **无法在纯 JVM 单测内实例化，测量路径【未验证】**；分类规则复用纯 JVM 的
 * [VerticalCharClassifier]，已由 JVM 单测覆盖。
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
 * 竖排引擎 Android 工厂：sp/dp → px 在此取系统密度后交给纯 JVM 解析器
 * [VerticalPxResolver]，核心排版层只吃 px 数值，保持 JVM 可单测。
 *
 * 测试边界（交接清单 #4）：本对象读取 [Resources] 系统密度并构造 [TextPaintVerticalMetrics]，
 * 属 Android 绑定，**无法在纯 JVM 单测内验证，【未验证】**；选号与比例乘法在
 * [VerticalPxResolver] 中有 JVM 单测覆盖。公开构造签名冻结，下游 U5 阅读页壳按此对接。
 */
object VerticalLayoutEngineFactory {

    /**
     * @param bodySp 正文字号，默认 18sp（规格锁定值）。
     * @param typeface Noto Serif SC 字体实例，上层加载字体资产后注入；null 走系统 serif 兜底。
     */
    fun create(
        viewWidthPx: Int,
        viewHeightPx: Int,
        bodySp: Float = VerticalTokens.BODY_SP,
        typeface: Typeface? = null,
    ): VerticalLayoutEngine {
        val metrics = Resources.getSystem().displayMetrics
        val spec = VerticalPxResolver.resolve(
            bodySp = bodySp,
            density = metrics.density,
            fontScale = metrics.scaledDensity,
        )
        val paintMetrics = TextPaintVerticalMetrics(typeface)
        return VerticalLayoutEngine(
            bodySizePx = spec.bodySizePx,
            titleSizePx = spec.titleSizePx,
            metrics = paintMetrics,
            titleGapPx = spec.titleGapPx,
        )
    }

    /** 注音小字字号（px），供左侧 18% 批注栏取用；无衬线由渲染层选字体。 */
    fun annotationSizePx(): Float {
        val metrics = Resources.getSystem().displayMetrics
        return VerticalPxResolver.resolve(
            density = metrics.density,
            fontScale = metrics.scaledDensity,
        ).annotationSizePx
    }
}
