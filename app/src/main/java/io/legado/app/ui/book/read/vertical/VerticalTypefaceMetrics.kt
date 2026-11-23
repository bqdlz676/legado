package io.legado.app.ui.book.read.vertical

/**
 * 落文 · 竖排阅读引擎 —— 字形测量抽象与字符分类。
 *
 * 为什么把测量抽成接口：核心排版层要在纯 JVM 单测里跑，不能碰 android.graphics；
 * Android 层用 TextPaint 实现 [VerticalTypefaceMetrics]，单测注入伪测量实现即可。
 */

/** 字形测量抽象。 */
interface VerticalTypefaceMetrics {
    /** 测量单字（或内嵌小字串）在指定字号下的 advance（px）；bold 为标题加粗标记。 */
    fun measureAdvance(text: String, sizePx: Float, bold: Boolean): Float

    /** 字符分类。 */
    fun classify(ch: Char): CharKind
}

/** 字符分类兜底规则（与字体无关，单测与 Android 实现共用）。 */
object VerticalCharClassifier {

    private fun isLatin(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z'

    private fun isDigit(ch: Char): Boolean = ch in '0'..'9'

    fun classify(ch: Char): CharKind = when {
        ch.isWhitespace() -> CharKind.SPACE
        isDigit(ch) -> CharKind.DIGIT
        isLatin(ch) -> CharKind.LATIN
        !ch.isLetterOrDigit() -> CharKind.PUNCT
        else -> CharKind.CJK
    }

    /** 是否属于需要 90° 侧排的内嵌脚本（拉丁/数字/年份）。 */
    fun isInlineScript(kind: CharKind): Boolean = kind == CharKind.LATIN || kind == CharKind.DIGIT
}

/**
 * JVM 默认伪测量：字身按 1 em 见方，advance 取字号。
 * 仅用于单测与无 Android 环境兜底；真机走 TextPaint 实现。
 */
class DefaultTypefaceMetrics : VerticalTypefaceMetrics {

    override fun measureAdvance(text: String, sizePx: Float, bold: Boolean): Float = sizePx

    override fun classify(ch: Char): CharKind = VerticalCharClassifier.classify(ch)
}
