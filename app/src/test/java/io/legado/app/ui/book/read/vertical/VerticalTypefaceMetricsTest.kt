package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 落文 · JVM 默认伪测量单测：[DefaultTypefaceMetrics] 字身 1 em 见方，
 * advance 取字号；分类委托 [VerticalCharClassifier]。真机 TextPaint 实现为 Android 绑定，【未验证】。
 */
class VerticalTypefaceMetricsTest {

    private val metrics = DefaultTypefaceMetrics()

    @Test
    fun `advance returns body size regardless of bold`() {
        assertEquals(36f, metrics.measureAdvance("汉", 36f, bold = false), 1e-6f)
        assertEquals(36f, metrics.measureAdvance("汉", 36f, bold = true), 1e-6f)
        assertEquals(40f, metrics.measureAdvance("卷", 40f, bold = true), 1e-6f)
    }

    @Test
    fun `empty string still reports one em advance without crash`() {
        assertEquals(36f, metrics.measureAdvance("", 36f, bold = false), 1e-6f)
    }

    @Test
    fun `classify delegates to shared classifier`() {
        assertSame(CharKind.CJK, metrics.classify('汉'))
        assertSame(CharKind.LATIN, metrics.classify('x'))
        assertSame(CharKind.DIGIT, metrics.classify('7'))
        assertSame(CharKind.PUNCT, metrics.classify('。'))
        assertSame(CharKind.SPACE, metrics.classify(' '))
    }
}
