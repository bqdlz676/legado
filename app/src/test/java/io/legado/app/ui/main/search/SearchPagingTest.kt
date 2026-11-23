package io.legado.app.ui.main.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻页（加载更多）归约与策略单测：纯 JVM，不依赖 Android / Compose。
 *
 * 覆盖：到底触发一次、在途重复滚动不重复请求、hasMore=false 不再请求、
 * 追加按 id 去重、失败保留列表且可重试、远端收口终态、去抖窗口、首屏回填清分页态。
 */
class SearchPagingTest {

    private val page1 = listOf(
        SearchResultItem(id = "1", title = "春秋左传", author = "左丘明"),
    )
    private val page2 = listOf(
        SearchResultItem(id = "2", title = "史记", snippet = "春秋时期"),
    )

    private fun contentState(hasMore: Boolean = true) = SearchUiState(
        query = "左传",
        loadState = SearchLoadState.Content(page1),
        lastResults = page1,
        hasMore = hasMore,
    )

    @Test
    fun `bottom reached on content with more results triggers one load`() {
        val state = contentState()
        assertTrue("有内容且远端还有更多时应允许翻页", state.canLoadMore)

        val started = SearchReducer.reduce(state, SearchIntent.LoadMoreStart)
        assertTrue(started.paging is SearchPagingState.LoadingMore)
        assertFalse("进入在途态后并发保护生效，不再允许第二个翻页", started.canLoadMore)
    }

    @Test
    fun `repeated scroll while in flight does not request another page`() {
        val inFlight = SearchReducer.reduce(contentState(), SearchIntent.LoadMoreStart)
        val again = SearchReducer.reduce(inFlight, SearchIntent.LoadMoreStart)

        // 第二次到底不二次翻转状态：仍在途，请求出口只有一个。
        assertTrue(again.paging is SearchPagingState.LoadingMore)
        assertFalse(again.canLoadMore)
        assertEquals(inFlight, again)
    }

    @Test
    fun `hasMore false blocks further bottom triggers`() {
        val ended = contentState(hasMore = false)
            .copy(paging = SearchPagingState.Ended)
        assertFalse("远端收口后不再允许翻页", ended.canLoadMore)

        val unchanged = SearchReducer.reduce(ended, SearchIntent.LoadMoreStart)
        assertEquals(ended, unchanged)
    }

    @Test
    fun `append merges new page keeping prior items and deduping by id`() {
        val incoming = page1 + page2
        val appended = SearchReducer.reduce(
            contentState(),
            SearchIntent.LoadMoreAppend(incoming, hasMore = true),
        )

        val content = appended.loadState as SearchLoadState.Content
        assertEquals(listOf("1", "2"), content.results.map { it.id })
        assertTrue("还有更多时回到空闲可翻页", appended.paging is SearchPagingState.Idle)
        assertEquals(content.results, appended.lastResults)
    }

    @Test
    fun `append with no more ends paging and stops further requests`() {
        val appended = SearchReducer.reduce(
            contentState(),
            SearchIntent.LoadMoreAppend(page2, hasMore = false),
        )

        assertFalse(appended.hasMore)
        assertTrue(appended.paging is SearchPagingState.Ended)
        assertFalse(appended.canLoadMore)
    }

    @Test
    fun `load more failure keeps list and enters retryable error state`() {
        val inFlight = SearchReducer.reduce(contentState(), SearchIntent.LoadMoreStart)
        val failed = SearchReducer.reduce(inFlight, SearchIntent.LoadMoreFailure("boom"))

        val error = failed.paging as SearchPagingState.LoadMoreError
        assertEquals("boom", error.message)
        assertEquals(page1, (failed.loadState as SearchLoadState.Content).results)
        assertTrue("分页失败保留列表且允许重试", failed.canLoadMore)
    }

    @Test
    fun `blank load more failure message falls back to default`() {
        val inFlight = SearchReducer.reduce(contentState(), SearchIntent.LoadMoreStart)
        val failed = SearchReducer.reduce(inFlight, SearchIntent.LoadMoreFailure(""))
        val error = failed.paging as SearchPagingState.LoadMoreError
        assertEquals(SearchTokens.ERROR_MESSAGE, error.message)
    }

    @Test
    fun `finish marks no more and closes paging`() {
        val inFlight = SearchReducer.reduce(contentState(), SearchIntent.LoadMoreStart)
        val finished = SearchReducer.reduce(inFlight, SearchIntent.LoadMoreFinish(hasMore = false))

        assertFalse(finished.hasMore)
        assertTrue(finished.paging is SearchPagingState.Ended)
    }

    @Test
    fun `finish with more keeps idle and reloadable`() {
        val inFlight = SearchReducer.reduce(contentState(), SearchIntent.LoadMoreStart)
        val finished = SearchReducer.reduce(inFlight, SearchIntent.LoadMoreFinish(hasMore = true))

        assertTrue(finished.hasMore)
        assertTrue(finished.paging is SearchPagingState.Idle)
        assertTrue(finished.canLoadMore)
    }

    @Test
    fun `append is ignored when not in content state`() {
        val empty = contentState().copy(loadState = SearchLoadState.Empty("左传"))
        val unchanged = SearchReducer.reduce(
            empty,
            SearchIntent.LoadMoreAppend(page2, hasMore = true),
        )
        assertEquals(empty, unchanged)
    }

    @Test
    fun `debounce window suppresses repeated bottom triggers`() {
        val now = 1_000_000L
        assertFalse(
            "超出窗口的上一次触发不被抑制",
            SearchPagingPolicy.withinDebounce(
                now - SearchTokens.LOAD_MORE_DEBOUNCE_MS - 1,
                now,
                SearchTokens.LOAD_MORE_DEBOUNCE_MS,
            ),
        )
        assertTrue(
            "窗口内的连发被抑制",
            SearchPagingPolicy.withinDebounce(
                now - SearchTokens.LOAD_MORE_DEBOUNCE_MS / 2,
                now,
                SearchTokens.LOAD_MORE_DEBOUNCE_MS,
            ),
        )
        assertFalse(
            "从未触发（时间戳 0）不做去抖",
            SearchPagingPolicy.withinDebounce(0L, now, SearchTokens.LOAD_MORE_DEBOUNCE_MS),
        )
    }

    @Test
    fun `first page results ready resets dirty paging flags`() {
        val dirty = contentState(hasMore = false)
            .copy(paging = SearchPagingState.Ended)
        val reset = SearchReducer.reduce(dirty, SearchIntent.ResultsReady(page1))

        assertTrue(reset.hasMore)
        assertTrue(reset.paging is SearchPagingState.Idle)
    }

    @Test
    fun `new submit clears previous paging state`() {
        val dirty = contentState(hasMore = false)
            .copy(paging = SearchPagingState.Ended)
        val submitted = SearchReducer.reduce(dirty, SearchIntent.Submit)

        assertTrue(submitted.loadState is SearchLoadState.Loading)
        assertTrue(submitted.hasMore)
        assertTrue(submitted.paging is SearchPagingState.Idle)
    }
}
