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

    /** 已进入翻页在途态的内容状态（追加 / 失败 / 收口分支的前置态）。 */
    private fun inFlight(hasMore: Boolean = true) =
        SearchReducer.reduce(contentState(hasMore), SearchIntent.LoadMoreStart)

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
            inFlight(),
            SearchIntent.LoadMoreAppend(incoming, hasMore = true),
        )

        val content = appended.loadState as SearchLoadState.Content
        assertEquals(listOf("1", "2"), content.results.map { it.id })
        // 多批次在途身份保留到 Finish：追加不回 Idle（D1-R1）。
        assertTrue(appended.paging is SearchPagingState.LoadingMore)
        assertEquals(content.results, appended.lastResults)
    }

    @Test
    fun `append keeps in flight and finish closes paging as single source`() {
        val appended = SearchReducer.reduce(
            inFlight(),
            SearchIntent.LoadMoreAppend(page2, hasMore = false),
        )

        // 追加批次不提前收口：仍在途，hasMore 按 OR 累积保留旧值；终态只由 Finish 落定（D1-R6）。
        assertTrue(appended.paging is SearchPagingState.LoadingMore)
        assertTrue(appended.hasMore)

        val finished = SearchReducer.reduce(appended, SearchIntent.LoadMoreFinish(hasMore = false))
        assertFalse(finished.hasMore)
        assertTrue(finished.paging is SearchPagingState.Ended)
        assertFalse(finished.canLoadMore)
    }

    @Test
    fun `load more failure keeps list and enters explicit retryable error state`() {
        val inFlight = inFlight()
        val failed = SearchReducer.reduce(inFlight, SearchIntent.LoadMoreFailure("boom"))

        val error = failed.paging as SearchPagingState.LoadMoreError
        assertEquals("boom", error.message)
        assertEquals(page1, (failed.loadState as SearchLoadState.Content).results)
        assertFalse("失败态不被滚动到底自动重发（D1-R3）", failed.canLoadMore)
        assertTrue("仅脚注显式重试入口可用", failed.canRetryLoadMore)
    }

    @Test
    fun `explicit retry from error re-enters flight at reducer gate`() {
        val failed = SearchReducer.reduce(inFlight(), SearchIntent.LoadMoreFailure("boom"))

        // 归约器闸门只放行失败态 → 在途这一条显式重试路径；自动到底的拦阻在编排器层锁定。
        val retry = SearchReducer.reduce(failed, SearchIntent.LoadMoreStart)
        assertTrue("脚注显式重试可重新进入在途", retry.paging is SearchPagingState.LoadingMore)
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

    // ────────────────────────── D1-R1 多批次收口回归（必补断言②） ──────────────────────────

    @Test
    fun `multi batch appends then final finish lands ended closing mark`() {
        // 构造同一页请求内两批成功回调，末次 Finish 带 hasMore=false：
        // 两批都落在在途态，收口标记最终必须落到 Ended / hasMore=false，不允许停在 Idle。
        var state = SearchReducer.reduce(contentState(), SearchIntent.LoadMoreStart)
        state = SearchReducer.reduce(state, SearchIntent.LoadMoreAppend(page1, hasMore = true))
        assertTrue("首批追加后仍在途", state.paging is SearchPagingState.LoadingMore)

        state = SearchReducer.reduce(state, SearchIntent.LoadMoreAppend(page2, hasMore = true))
        assertTrue("二批追加后仍在途，不误入首屏回填", state.paging is SearchPagingState.LoadingMore)
        assertEquals(
            listOf("1", "2"),
            (state.loadState as SearchLoadState.Content).results.map { it.id },
        )

        state = SearchReducer.reduce(state, SearchIntent.LoadMoreFinish(hasMore = false))
        assertFalse("末次收口标记落地：hasMore=false", state.hasMore)
        assertTrue("末次收口标记落地：paging=Ended", state.paging is SearchPagingState.Ended)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `append is rejected when not in flight even if content present`() {
        // 非在途态到达的追加（误路由首屏分支）不得被归约器收进翻页列表。
        val idle = contentState()
        val unchanged = SearchReducer.reduce(
            idle,
            SearchIntent.LoadMoreAppend(page2, hasMore = true),
        )
        assertEquals(idle, unchanged)
    }

    // ────────────────────────── D1-R5 贴底判定纯函数 ──────────────────────────

    @Test
    fun `bottom reached ignores footer item and short list visible footer`() {
        // 短列表：内容 2 条 + 1 脚注，脚注可见（下标 2），内容总数 2 → 不触发。
        assertFalse(
            SearchPagingPolicy.bottomReached(totalItemsCount = 3, lastVisibleIndex = 2, footerCount = 1),
        )
        // 长列表滚到末条内容（下标 = 内容总数 - 1），脚注尚未可见 → 触发。
        assertTrue(
            SearchPagingPolicy.bottomReached(totalItemsCount = 11, lastVisibleIndex = 9, footerCount = 1),
        )
        // 可见区还在中部，不触发。
        assertFalse(
            SearchPagingPolicy.bottomReached(totalItemsCount = 11, lastVisibleIndex = 4, footerCount = 1),
        )
        // 空列表不触发。
        assertFalse(SearchPagingPolicy.bottomReached(0, -1, footerCount = 1))
    }
}
