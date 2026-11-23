package io.legado.app.ui.components.inkslate

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U7 SVG 导出：图元标记、附注脚注、颜色转换与 XML 转义。
 */
class InkSlateSvgTest {

    private fun stateWithShapes(): InkSlateState {
        var state = InkSlateState.Empty.setCanvasSize(320f, 240f)
        state = state.selectTool(InkTool.LINE).beginStroke(10f, 10f, 1f).extendStroke(60f, 80f, 1f).finishStroke()
        state = state.selectTool(InkTool.RECT).beginStroke(20f, 20f, 1f).extendStroke(100f, 90f, 1f).finishStroke()
        state = state.selectTool(InkTool.CIRCLE).beginStroke(120f, 120f, 1f).extendStroke(200f, 180f, 1f).finishStroke()
        state = state.selectTool(InkTool.BRUSH).beginStroke(5f, 5f, 0.8f).extendStroke(15f, 25f, 0.6f).finishStroke()
        return state
    }

    @Test
    fun `四类图元各自生成对应 SVG 标签`() {
        val export = InkSlateSvg.build(stateWithShapes())
        assertTrue(export.svg.contains("<line "))
        assertTrue(export.svg.contains("<rect "))
        assertTrue(export.svg.contains("<circle "))
        assertTrue(export.svg.contains("<polyline "))
        assertEquals(4, export.strokeCount)
    }

    @Test
    fun `批注附注写入 SVG 底部文本`() {
        val meta = AnnotationMeta("左传·隐公元年", "第 3 页", "正文")
        val state = stateWithShapes().bindAnnotation(meta)
        val export = InkSlateSvg.build(state)
        assertEquals("《左传·隐公元年》第 3 页批注", export.footnote)
        assertTrue(export.svg.contains("《左传·隐公元年》第 3 页批注"))
    }

    @Test
    fun `无批注时脚注为空 不写文本节点`() {
        val export = InkSlateSvg.build(stateWithShapes())
        assertTrue(export.footnote == null)
        assertFalse(export.svg.contains("批注"))
    }

    @Test
    fun `ARGB 转六位 RGB 十六进制`() {
        assertEquals("#c23b2e", InkSlateSvg.toSvgColor(ComponentPalette.TEXT_ACCENT))
        assertEquals("#d4a54a", InkSlateSvg.toSvgColor(ComponentPalette.BRAND_GOLD))
    }

    @Test
    fun `文本特殊字符 XML 转义`() {
        val state = InkSlateState.Empty
            .setCanvasSize(100f, 100f)
            .selectTool(InkTool.TEXT)
            .beginStroke(10f, 10f, 1f)
            .commitText("甲<乙>&\"丙\"")
        val export = InkSlateSvg.build(state)
        assertTrue(export.svg.contains("&lt;"))
        assertTrue(export.svg.contains("&amp;"))
        assertTrue(export.svg.contains("&quot;"))
    }

    @Test
    fun `箭头生成头部折线`() {
        val state = InkSlateState.Empty
            .setCanvasSize(200f, 200f)
            .selectTool(InkTool.ARROW)
            .beginStroke(0f, 0f, 1f)
            .extendStroke(100f, 100f, 1f)
            .finishStroke()
        val export = InkSlateSvg.build(state)
        assertTrue(export.svg.contains("<line "))
        assertTrue(export.svg.contains("<polyline "))
    }
}
