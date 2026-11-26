package io.legado.app.model.webBook

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 页码游标 [SearchPageCursor] 单测（Q-179 / 验收 N1）：按已成功取页数推进。
 * 纯 JVM，不依赖 Android / 协程 / DAO；行为锁口径见 `docs/修复-失败重试跳页.md`。
 *
 * 三条行为锁：
 * - 首查从第 1 页起，成功收口后前进 1 页；
 * - 同 id 失败重试再取同一页，重试成功后才前进；
 * - 换词 / 新检索复位回到第 1 页。
 */
class SearchPageCursorTest {

    @Test
    fun `first search starts at page one and advances only after success`() {
        val cursor = SearchPageCursor()

        assertEquals("首查待取第 1 页", 1, cursor.currentPage())

        cursor.markSuccess()

        assertEquals("首查成功收口后前进到第 2 页", 2, cursor.currentPage())
    }

    @Test
    fun `retry after failure refetches the same page without advancing`() {
        val cursor = SearchPageCursor()
        cursor.markSuccess()
        assertEquals("第 1 页成功后待取第 2 页", 2, cursor.currentPage())

        // 第 2 页失败：不调 markSuccess；重试必须再取第 2 页，不得跳到第 3 页。
        val retryPage = cursor.currentPage()
        assertEquals("失败重试仍取失败的第 2 页", 2, retryPage)

        cursor.markSuccess()

        assertEquals("重试成功收口后才前进到第 3 页", 3, cursor.currentPage())
    }

    @Test
    fun `new search resets cursor back to page one`() {
        val cursor = SearchPageCursor()
        cursor.markSuccess()
        cursor.markSuccess()
        assertEquals("两次成功后待取第 3 页", 3, cursor.currentPage())

        cursor.reset()

        assertEquals("换词 / 新检索复位到第 1 页", 1, cursor.currentPage())
    }
}
