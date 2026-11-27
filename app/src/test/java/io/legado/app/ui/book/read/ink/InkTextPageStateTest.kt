package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文字面状态解析器单测（纯 JVM，离线可跑）。
 *
 * 覆盖三类可打假断言：
 *  - 页序推进：同一章多页按 ReadBook.durPageIndex 顺序逐页推进，就绪态页序号单调递增；
 *  - 当前页索引：范围内原样保留，越界向章内夹取；
 *  - 边界空态：无书 / 无章 / 排版失败 / 内容为空各自归类，且优先级自上而下不串态。
 */
class InkTextPageStateTest {

    @Test
    fun `三页章 页序从零逐页推进 序号单调递增到末页`() {
        val states = (0..2).map { index ->
            InkTextPageResolver.resolve(
                bookPresent = true,
                chapterPresent = true,
                pageCount = 3,
                currentIndex = index,
                pageText = "正文第${index}页",
                layoutOk = true,
            )
        }

        val ready = states.map { it as InkTextPageState.Ready }
        assertEquals(listOf(0, 1, 2), ready.map { it.pageIndex })
        assertTrue(ready.zipWithNext { a, b -> b.pageIndex > a.pageIndex }.all { it })
        assertEquals(3, ready.first().pageCount)
        assertEquals(2, ready.last().pageIndex)
    }

    @Test
    fun `范围内当前页索引原样保留`() {
        val state = InkTextPageResolver.resolve(
            bookPresent = true,
            chapterPresent = true,
            pageCount = 4,
            currentIndex = 2,
            pageText = "正文",
            layoutOk = true,
        )

        assertTrue(state is InkTextPageState.Ready)
        assertEquals(2, (state as InkTextPageState.Ready).pageIndex)
        assertEquals(4, state.pageCount)
    }

    @Test
    fun `索引超出末页夹取到末页 负索引夹取到首页`() {
        val high = InkTextPageResolver.resolve(
            bookPresent = true,
            chapterPresent = true,
            pageCount = 3,
            currentIndex = 9,
            pageText = "正文",
            layoutOk = true,
        )
        assertEquals(2, (high as InkTextPageState.Ready).pageIndex)

        val low = InkTextPageResolver.resolve(
            bookPresent = true,
            chapterPresent = true,
            pageCount = 3,
            currentIndex = -4,
            pageText = "正文",
            layoutOk = true,
        )
        assertEquals(0, (low as InkTextPageState.Ready).pageIndex)
    }

    @Test
    fun `无书归类为 NoBook 其余输入不再下判`() {
        val state = InkTextPageResolver.resolve(
            bookPresent = false,
            chapterPresent = true,
            pageCount = 3,
            currentIndex = 0,
            pageText = "正文",
            layoutOk = true,
        )
        assertTrue(state is InkTextPageState.NoBook)
    }

    @Test
    fun `书在但章未就绪归类为 NoChapter`() {
        val state = InkTextPageResolver.resolve(
            bookPresent = true,
            chapterPresent = false,
            pageCount = 0,
            currentIndex = 0,
            pageText = null,
            layoutOk = false,
        )
        assertTrue(state is InkTextPageState.NoChapter)
    }

    @Test
    fun `章已加载但可用页数为零归类为 LayoutFailed`() {
        val state = InkTextPageResolver.resolve(
            bookPresent = true,
            chapterPresent = true,
            pageCount = 0,
            currentIndex = 0,
            pageText = "正文",
            layoutOk = true,
        )
        assertTrue(state is InkTextPageState.LayoutFailed)

        val layoutNotOk = InkTextPageResolver.resolve(
            bookPresent = true,
            chapterPresent = true,
            pageCount = 2,
            currentIndex = 0,
            pageText = "正文",
            layoutOk = false,
        )
        assertTrue(layoutNotOk is InkTextPageState.LayoutFailed)
    }

    @Test
    fun `页正文空白归类为 EmptyContent 空串与纯空白与 null 均兜底`() {
        val blankCases = listOf("", "   ", null)
        blankCases.forEach { text ->
            val state = InkTextPageResolver.resolve(
                bookPresent = true,
                chapterPresent = true,
                pageCount = 2,
                currentIndex = 0,
                pageText = text,
                layoutOk = true,
            )
            assertTrue("空白输入应归 EmptyContent：$text", state is InkTextPageState.EmptyContent)
        }
    }

    @Test
    fun `优先级 NoBook 先于 NoChapter 先于 LayoutFailed 先于 EmptyContent`() {
        // 无书压过无章
        assertTrue(
            InkTextPageResolver.resolve(
                bookPresent = false, chapterPresent = false, pageCount = 0,
                currentIndex = 0, pageText = null, layoutOk = false,
            ) is InkTextPageState.NoBook,
        )
        // 无章压过排版失败
        assertTrue(
            InkTextPageResolver.resolve(
                bookPresent = true, chapterPresent = false, pageCount = 0,
                currentIndex = 0, pageText = null, layoutOk = false,
            ) is InkTextPageState.NoChapter,
        )
        // 排版失败压过内容为空
        assertTrue(
            InkTextPageResolver.resolve(
                bookPresent = true, chapterPresent = true, pageCount = 0,
                currentIndex = 0, pageText = "", layoutOk = true,
            ) is InkTextPageState.LayoutFailed,
        )
    }

    @Test
    fun `就绪态不落入空态分支`() {
        val state = InkTextPageResolver.resolve(
            bookPresent = true,
            chapterPresent = true,
            pageCount = 1,
            currentIndex = 0,
            pageText = "　　正文段落",
            layoutOk = true,
        )
        assertFalse(state is InkTextPageState.EmptyContent)
        assertTrue(state is InkTextPageState.Ready)
    }
}
