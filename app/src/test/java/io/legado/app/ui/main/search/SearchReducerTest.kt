package io.legado.app.ui.main.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 归约器单测：覆盖空闲 / 提交 / 回填 / 空结果 / 失败 / 离线 / 重试 / 历史增删清，
 * 以及空查询、无结果、无网三类边界兜底不崩溃、可重试。
 */
class SearchReducerTest {

    private val sample = listOf(
        SearchResultItem(id = "1", title = "春秋左传", author = "左丘明"),
        SearchResultItem(id = "2", title = "史记", snippet = "春秋时期"),
    )

    @Test
    fun `initial state is idle`() {
        val state = SearchUiState()
        assertTrue(state.loadState is SearchLoadState.Idle)
        assertTrue(state.history.isEmpty())
        assertTrue(state.hot.isEmpty())
    }

    @Test
    fun `blank query change falls back to idle`() {
        val state = SearchReducer.reduce(
            SearchUiState(query = "左传", loadState = SearchLoadState.Loading),
            SearchIntent.QueryChange("   "),
        )
        assertEquals("   ", state.query)
        assertTrue(state.loadState is SearchLoadState.Idle)
    }

    @Test
    fun `non blank query change keeps text and does not force loading`() {
        val state = SearchReducer.reduce(
            SearchUiState(),
            SearchIntent.QueryChange("春秋"),
        )
        assertEquals("春秋", state.query)
        assertTrue(state.loadState is SearchLoadState.Idle)
    }

    @Test
    fun `focus gained and lost toggle flag`() {
        val focused = SearchReducer.reduce(SearchUiState(), SearchIntent.FocusGained)
        assertTrue(focused.focused)
        val blurred = SearchReducer.reduce(focused, SearchIntent.FocusLost)
        assertFalse(blurred.focused)
    }

    @Test
    fun `blank submit stays idle and writes no history`() {
        val state = SearchReducer.reduce(
            SearchUiState(query = "  "),
            SearchIntent.Submit,
        )
        assertTrue(state.loadState is SearchLoadState.Idle)
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `submit non blank enters loading and records history`() {
        val state = SearchReducer.reduce(
            SearchUiState(query = " 左传 "),
            SearchIntent.Submit,
        )
        assertTrue(state.loadState is SearchLoadState.Loading)
        assertEquals(listOf("左传"), state.history)
    }

    @Test
    fun `clear query returns to idle`() {
        val state = SearchReducer.reduce(
            SearchUiState(query = "左传", loadState = SearchLoadState.Content(sample)),
            SearchIntent.ClearQuery,
        )
        assertEquals("", state.query)
        assertTrue(state.loadState is SearchLoadState.Idle)
    }

    @Test
    fun `pick history submits and moves keyword to front`() {
        val state = SearchReducer.reduce(
            SearchUiState(history = listOf("史记", "春秋")),
            SearchIntent.PickHistory("春秋"),
        )
        assertEquals("春秋", state.query)
        assertEquals(listOf("春秋", "史记"), state.history)
        assertTrue(state.loadState is SearchLoadState.Loading)
    }

    @Test
    fun `blank pick history leaves state unchanged`() {
        val initial = SearchUiState(history = listOf("史记"))
        val state = SearchReducer.reduce(initial, SearchIntent.PickHistory("  "))
        assertEquals(initial, state)
    }

    @Test
    fun `remove and clear history`() {
        val base = SearchUiState(history = listOf("左传", "史记"))
        val removed = SearchReducer.reduce(base, SearchIntent.RemoveHistory("史记"))
        assertEquals(listOf("左传"), removed.history)
        val cleared = SearchReducer.reduce(removed, SearchIntent.ClearHistory)
        assertTrue(cleared.history.isEmpty())
    }

    @Test
    fun `results ready stores content and last results`() {
        val state = SearchReducer.reduce(
            SearchUiState(query = "左传"),
            SearchIntent.ResultsReady(sample),
        )
        val content = state.loadState as SearchLoadState.Content
        assertEquals(sample, content.results)
        assertEquals(sample, state.lastResults)
    }

    @Test
    fun `empty results become empty state and keep previous cache`() {
        val base = SearchUiState(
            query = "左传",
            lastResults = sample,
            loadState = SearchLoadState.Content(sample),
        )
        val state = SearchReducer.reduce(base, SearchIntent.ResultsReady(emptyList()))
        val empty = state.loadState as SearchLoadState.Empty
        assertEquals("左传", empty.query)
        assertEquals(sample, state.lastResults)
    }

    @Test
    fun `failure keeps cache for retry fallback`() {
        val base = SearchUiState(lastResults = sample)
        val state = SearchReducer.reduce(base, SearchIntent.Failure("boom"))
        val error = state.loadState as SearchLoadState.Error
        assertEquals("boom", error.message)
        assertEquals(sample, error.cached)
    }

    @Test
    fun `blank failure message falls back to default`() {
        val state = SearchReducer.reduce(SearchUiState(), SearchIntent.Failure(""))
        val error = state.loadState as SearchLoadState.Error
        assertEquals(SearchTokens.ERROR_MESSAGE, error.message)
    }

    @Test
    fun `offline keeps cached results`() {
        val base = SearchUiState(lastResults = sample)
        val state = SearchReducer.reduce(base, SearchIntent.Offline)
        val offline = state.loadState as SearchLoadState.Offline
        assertEquals(sample, offline.cached)
    }

    @Test
    fun `retry with query re-enters loading`() {
        val state = SearchReducer.reduce(
            SearchUiState(query = "左传", loadState = SearchLoadState.Offline(sample)),
            SearchIntent.Retry,
        )
        assertTrue(state.loadState is SearchLoadState.Loading)
    }

    @Test
    fun `retry with blank query falls back to idle`() {
        val state = SearchReducer.reduce(
            SearchUiState(query = "  ", loadState = SearchLoadState.Error("x")),
            SearchIntent.Retry,
        )
        assertTrue(state.loadState is SearchLoadState.Idle)
    }

    @Test
    fun `loading intent switches to loading`() {
        val state = SearchReducer.reduce(SearchUiState(), SearchIntent.Loading)
        assertTrue(state.loadState is SearchLoadState.Loading)
    }
}
