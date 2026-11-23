package io.legado.app.ui.main.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI 模型单测：各状态可见结果解析、阻塞态判定、字段枚举越界兜底。
 */
class SearchModelsTest {

    private val cached = listOf(SearchResultItem(id = "1", title = "春秋左传"))

    @Test
    fun `content exposes its own results`() {
        val state = SearchUiState(loadState = SearchLoadState.Content(cached))
        assertEquals(cached, state.visibleResults)
        assertFalse(state.isBlocking)
    }

    @Test
    fun `error and offline expose cached results and block clicks`() {
        val error = SearchUiState(loadState = SearchLoadState.Error("x", cached))
        assertEquals(cached, error.visibleResults)
        assertTrue(error.isBlocking)

        val offline = SearchUiState(loadState = SearchLoadState.Offline(cached))
        assertEquals(cached, offline.visibleResults)
        assertTrue(offline.isBlocking)
    }

    @Test
    fun `idle loading empty expose no results`() {
        assertTrue(
            SearchUiState(loadState = SearchLoadState.Idle).visibleResults.isEmpty(),
        )
        assertTrue(
            SearchUiState(loadState = SearchLoadState.Loading).visibleResults.isEmpty(),
        )
        assertTrue(
            SearchUiState(loadState = SearchLoadState.Empty("q")).visibleResults.isEmpty(),
        )
        assertFalse(
            SearchUiState(loadState = SearchLoadState.Empty("q")).isBlocking,
        )
    }

    @Test
    fun `field ordinal out of bounds falls back to book title`() {
        assertEquals(SearchField.BOOK_TITLE, SearchField.safeOf(null))
        assertEquals(SearchField.BOOK_TITLE, SearchField.safeOf(999))
        assertEquals(SearchField.CONTENT, SearchField.safeOf(2))
    }
}
