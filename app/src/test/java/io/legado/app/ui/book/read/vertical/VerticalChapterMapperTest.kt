package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 落文 · 章节适配纯逻辑单测（交接清单 #4）。
 *
 * 覆盖 [VerticalChapterMapper] 的标题空白归一与正文透传；绑定 [TextChapter] 的
 * 扩展函数本身依赖 App 重类，无法在 JVM 内实例化，其 Android 路径【未验证】。
 */
class VerticalChapterMapperTest {

    @Test
    fun `null title maps to null`() {
        val input = VerticalChapterMapper.map(null, "正文甲乙丙")
        assertNull(input.title)
        assertEquals("正文甲乙丙", input.bodyText)
    }

    @Test
    fun `blank and whitespace-only title map to null`() {
        assertNull(VerticalChapterMapper.map("", "正文").title)
        assertNull(VerticalChapterMapper.map("   ", "正文").title)
        assertNull(VerticalChapterMapper.map("\t\n　", "正文").title)
    }

    @Test
    fun `non-blank title preserved verbatim without trim`() {
        val input = VerticalChapterMapper.map("  卷一 城濮之战 ", "正文")
        // 原扩展口径：isNotBlank 判定后原样返回，不 trim；空白字形由分页器发射时跳过。
        assertEquals("  卷一 城濮之战 ", input.title)
    }

    @Test
    fun `body text passes through unchanged including blank and multiline`() {
        assertEquals("", VerticalChapterMapper.map("卷一", "").bodyText)
        assertEquals("   ", VerticalChapterMapper.map("卷一", "   ").bodyText)
        val multi = "第一段甲乙丙。\n第二段丁戊己。"
        assertEquals(multi, VerticalChapterMapper.map(null, multi).bodyText)
    }

    @Test
    fun `mapper never returns null body`() {
        val input = VerticalChapterMapper.map("卷一", "")
        assertEquals("", input.bodyText)
    }
}
