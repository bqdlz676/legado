package io.legado.app.ui.components.inkslate

import io.legado.app.ui.components.inkslate.contract.InkDerivedColors
import io.legado.app.ui.components.inkslate.contract.InkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U7 InkSlate 模型：工具 / 色板 / 线宽、笔迹起落、撤销重做、橡皮命中、
 * 压感模拟、图层隔离、批注附注与自检收口。
 */
class InkSlateModelTest {

    private fun blank() = InkSlateState.Empty.setCanvasSize(300f, 400f)

    @Test
    fun `默认画笔 朱砂 4 号线宽`() {
        val state = blank()
        assertEquals(InkTool.BRUSH, state.tool)
        assertEquals(InkDerivedColors.resolvedColor(InkPalette.VERMILION), state.color)
        assertEquals(InkSlatePalette.WIDTH_DEFAULT, state.width, 1e-6f)
    }

    @Test
    fun `工具 颜色 线宽选择收口`() {
        val state = blank()
            .selectTool(InkTool.ARROW)
            .selectColor(InkPalette.INDIGO)
            .setWidth(20f)
        assertEquals(InkTool.ARROW, state.tool)
        assertEquals(InkDerivedColors.resolvedColor(InkPalette.INDIGO), state.color)
        assertEquals(InkSlatePalette.WIDTH_MAX, state.width, 1e-6f)

        val narrow = blank().setWidth(-3f)
        assertEquals(InkSlatePalette.WIDTH_MIN, narrow.width, 1e-6f)
    }

    @Test
    fun `起落笔后笔迹入列 重做栈清空`() {
        val state = blank()
            .beginStroke(10f, 20f, 0.8f)
            .extendStroke(30f, 40f, 0.6f)
            .finishStroke()
        assertEquals(1, state.strokes.size)
        assertNull(state.currentStroke)
        assertTrue(state.redoStack.isEmpty())
        assertEquals(2, state.strokes.first().points.size)
    }

    @Test
    fun `图形工具只保留首末两点`() {
        val state = blank()
            .selectTool(InkTool.RECT)
            .beginStroke(0f, 0f, 1f)
            .extendStroke(50f, 10f, 1f)
            .extendStroke(90f, 90f, 1f)
            .finishStroke()
        assertEquals(2, state.strokes.first().points.size)
    }

    @Test
    fun `撤销重做往返 边界不误触`() {
        val drawn = blank()
            .beginStroke(1f, 1f, 0.9f).extendStroke(2f, 2f, 0.9f).finishStroke()
            .beginStroke(3f, 3f, 0.9f).extendStroke(4f, 4f, 0.9f).finishStroke()
        assertEquals(2, drawn.strokes.size)
        assertTrue(drawn.canUndo)
        assertFalse(drawn.canRedo)

        val undone = drawn.undo()
        assertEquals(1, undone.strokes.size)
        assertEquals(1, undone.redoStack.size)
        assertTrue(undone.canRedo)

        val redone = undone.redo()
        assertEquals(2, redone.strokes.size)
        assertTrue(redone.redoStack.isEmpty())

        val emptyUndo = blank().undo()
        assertFalse(emptyUndo.canUndo)
        assertFalse(emptyUndo.canRedo)
    }

    @Test
    fun `进行中笔迹先收笔再撤销`() {
        val inFlight = blank().beginStroke(5f, 5f, 0.7f)
        val undone = inFlight.undo()
        assertTrue(undone.strokes.isEmpty())
        assertNull(undone.currentStroke)
    }

    @Test
    fun `橡皮擦除命中半径内笔迹 远处保留`() {
        val drawn = blank()
            .beginStroke(0f, 0f, 1f).finishStroke()
            .beginStroke(200f, 200f, 1f).finishStroke()
        assertEquals(2, drawn.strokes.size)

        val erased = drawn.eraseAt(2f, 2f)
        assertEquals(1, erased.strokes.size)
        assertEquals(200f, erased.strokes.first().points.first().x, 1e-6f)
    }

    @Test
    fun `压感模拟 慢速高 快速低 夹紧区间`() {
        assertEquals(InkPressure.MAX_PRESSURE, InkPressure.simulate(0f), 1e-6f)
        assertEquals(InkPressure.MIN_PRESSURE, InkPressure.simulate(100f), 1e-6f)
        val mid = InkPressure.simulate(12f, referenceVelocity = 24f)
        assertTrue(mid > InkPressure.MIN_PRESSURE && mid < InkPressure.MAX_PRESSURE)
    }

    @Test
    fun `图层隔离 清空墨迹层不碰底图层`() {
        val drawn = blank()
            .setLayer(InkLayer.INK)
            .beginStroke(10f, 10f, 1f).finishStroke()
            .setLayer(InkLayer.BASE)
            .beginStroke(20f, 20f, 1f).finishStroke()
        assertEquals(2, drawn.strokes.size)

        val cleared = drawn.clearLayer(InkLayer.INK)
        assertEquals(1, cleared.strokes.size)
        assertEquals(InkLayer.BASE, cleared.strokes.first().layer)
    }

    @Test
    fun `批注附注格式 章节名 页码 批注`() {
        val meta = AnnotationMeta(
            chapterTitle = "左传·隐公元年",
            pageLabel = "第 3 页",
            plainText = "初，郑武公娶于申……",
        )
        assertEquals("《左传·隐公元年》第 3 页批注", meta.footnote())
    }

    @Test
    fun `阅读上下文拼出名章页码附注`() {
        val meta = AnnotationMeta.fromReadContext(
            bookName = "左传",
            chapterName = "隐公元年",
            pageIndex = 2,
            plainText = "初，郑武公娶于申……",
        )
        requireNotNull(meta)
        assertEquals("《左传·隐公元年》第 3 页批注", meta.footnote())
        assertEquals("初，郑武公娶于申……", meta.plainText)
    }

    @Test
    fun `缺章名时附注只落书名 页码从一起算`() {
        val meta = AnnotationMeta.fromReadContext("史记", "", 0, "")
        requireNotNull(meta)
        assertEquals("《史记》第 1 页批注", meta.footnote())
    }

    @Test
    fun `书名为空回落自由画板 不带批注`() {
        assertNull(AnnotationMeta.fromReadContext("  ", "隐公元年", 2, "正文"))
    }

    @Test
    fun `文字笔迹挂文本后收笔`() {
        val state = blank()
            .selectTool(InkTool.TEXT)
            .beginStroke(30f, 30f, 1f)
            .commitText("寤生")
        assertEquals(1, state.strokes.size)
        assertEquals("寤生", state.strokes.first().text)
    }

    @Test
    fun `自检拦截越界线宽 非法色值 空文本笔迹`() {
        val badWidth = blank().copy(width = 99f)
        assertTrue(badWidth.validate().any { it.contains("线宽") })

        val badColor = blank().copy(color = 0xFF123456)
        assertTrue(badColor.validate().any { it.contains("六色预设") })

        val textNoContent = blank()
            .selectTool(InkTool.TEXT)
            .beginStroke(1f, 1f, 1f)
            .commitText("   ")
        assertTrue(textNoContent.validate().any { it.contains("文本") })
    }

    @Test
    fun `保留七工具六色板 砍掉项不出现`() {
        assertEquals(7, InkTool.entries.size)
        assertEquals(6, InkPalette.bar.size)
        val labels = InkPalette.bar.map { it.label }
        assertTrue(labels.containsAll(listOf("朱砂", "墨灰", "花青", "藤黄", "暖金", "石青")))
    }

    @Test
    fun `落点夹紧在画布范围内`() {
        val state = blank().beginStroke(-50f, 9999f, 1f)
        val p = state.currentStroke!!.points.first()
        assertEquals(0f, p.x, 1e-6f)
        assertEquals(400f, p.y, 1e-6f)
    }
}
