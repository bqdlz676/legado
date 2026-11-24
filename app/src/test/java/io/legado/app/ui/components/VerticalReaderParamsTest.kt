package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * S-09 竖排参数单测：纯 JVM，只用 org.junit 与 kotlin stdlib，
 * 不引用任何平台类型，不读资源（避免测试类 NoClassDefFoundError）。
 * 规格：docs/锚点/S-09-阅读页竖排.md 参数表；切片-09 第 7、12 行。
 */
class VerticalReaderParamsTest {

    private val delta = 1e-9

    @Test
    fun `屏宽1080dp时栏宽885点6dp`() {
        val metrics = computeVerticalColumnMetrics(screenWidthDp = 1080.0, fontSizeSp = 18.0)
        assertEquals(885.6, metrics.columnWidthDp, delta)
    }

    @Test
    fun `字号18sp时行距32点4sp`() {
        val metrics = computeVerticalColumnMetrics(screenWidthDp = 1080.0, fontSizeSp = 18.0)
        assertEquals(32.4, metrics.lineHeightSp, delta)
    }

    @Test
    fun `字号18sp时列间距 = 45sp`() {
        val metrics = computeVerticalColumnMetrics(screenWidthDp = 1080.0, fontSizeSp = 18.0)
        assertEquals(45.0, metrics.columnGapSp, delta)
    }

    @Test
    fun `字号为0或负数时回退安全默认值且不抛异常不产生NaN`() {
        // 字号 = 0：回退正文字号 18sp，行距 / 列间距取默认口径，数值不得为 NaN
        val zero = computeVerticalColumnMetrics(screenWidthDp = 1080.0, fontSizeSp = 0.0)
        assertEquals(
            VerticalReaderParams.BODY_FONT_SIZE_SP * VerticalReaderParams.LINE_HEIGHT_RATIO,
            zero.lineHeightSp,
            delta,
        )
        assertEquals(
            VerticalReaderParams.BODY_FONT_SIZE_SP * VerticalReaderParams.COLUMN_GAP_RATIO,
            zero.columnGapSp,
            delta,
        )
        assertFalse(zero.columnWidthDp.isNaN())
        assertFalse(zero.lineHeightSp.isNaN())
        assertFalse(zero.columnGapSp.isNaN())

        // 字号为负：与 0 走同一条兜底分支，结果一致
        val negative = computeVerticalColumnMetrics(screenWidthDp = 1080.0, fontSizeSp = -5.0)
        assertEquals(zero.lineHeightSp, negative.lineHeightSp, delta)
        assertEquals(zero.columnGapSp, negative.columnGapSp, delta)
        assertFalse(negative.lineHeightSp.isNaN())
        assertFalse(negative.columnGapSp.isNaN())

        // 屏宽 ≤ 0：栏宽记 0、每列字数记 0，同样不产生 NaN
        val noWidth = computeVerticalColumnMetrics(screenWidthDp = 0.0, fontSizeSp = 18.0)
        assertEquals(0.0, noWidth.columnWidthDp, delta)
        assertEquals(0, noWidth.charsPerColumn)
        assertFalse(noWidth.columnWidthDp.isNaN())
    }

    @Test
    fun `每列可容字数按栏宽除以字号向下取整`() {
        val metrics = computeVerticalColumnMetrics(screenWidthDp = 1080.0, fontSizeSp = 18.0)
        assertEquals(49, metrics.charsPerColumn)
    }

    @Test
    fun `进度线颜色只引用朱砂单源TEXT_ACCENT`() {
        assertEquals(ComponentPalette.TEXT_ACCENT, VerticalReaderParams.progressLineColor)
    }

    @Test
    fun `参数常量与锚点卡参数表逐一吻合`() {
        assertEquals(0.82, VerticalReaderParams.COLUMN_WIDTH_RATIO, delta)
        assertEquals(18.0, VerticalReaderParams.BODY_FONT_SIZE_SP, delta)
        assertEquals(1.8, VerticalReaderParams.LINE_HEIGHT_RATIO, delta)
        assertEquals(2.5, VerticalReaderParams.COLUMN_GAP_RATIO, delta)
        assertEquals(10.0, VerticalReaderParams.ANNOTATION_FONT_SIZE_SP, delta)
        assertEquals(2.0, VerticalReaderParams.TITLE_FONT_SIZE_DELTA_SP, delta)
        assertEquals(24.0, VerticalReaderParams.TITLE_BODY_GAP_DP, delta)
        assertEquals(1.0, VerticalReaderParams.PROGRESS_LINE_WIDTH_DP, delta)
    }
}
