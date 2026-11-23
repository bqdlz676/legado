package io.legado.app.ui.main.search

/**
 * 落文 · 搜索页状态归约（纯 JVM，可单测）
 *
 * UI 不直接改状态，全部经 [SearchIntent] 进入 [SearchReducer.reduce] 产出新 [SearchUiState]。
 * 边界兜底：空查询不触发检索、不写历史；失败 / 离线保留 [SearchUiState.lastResults] 作缓存；
 * 重试时空查询回落空闲态。归约器不碰网络、不抛异常。
 */
sealed interface SearchIntent {

    /** 输入框文本变化（原文透传，归一化在归约器内做）。 */
    data class QueryChange(val text: String) : SearchIntent

    /** 输入框获得焦点（聚焦 spring 浮起）。 */
    object FocusGained : SearchIntent

    /** 输入框失去焦点。 */
    object FocusLost : SearchIntent

    /** 提交检索（无搜索按钮，回车 / 点历史词条等同提交）。 */
    object Submit : SearchIntent

    /** 清空输入框。 */
    object ClearQuery : SearchIntent

    /** 点选一条历史词条。 */
    data class PickHistory(val key: String) : SearchIntent

    /** 删除单条历史。 */
    data class RemoveHistory(val key: String) : SearchIntent

    /** 清空全部历史。 */
    object ClearHistory : SearchIntent

    /** 外部进入检索中。 */
    object Loading : SearchIntent

    /** 检索结果回填（空列表由归约器判为无结果态）。 */
    data class ResultsReady(val results: List<SearchResultItem>) : SearchIntent

    /** 检索失败，带可重试入口。 */
    data class Failure(val message: String) : SearchIntent

    /** 网络断开，保留缓存兜底。 */
    object Offline : SearchIntent

    /** 点击重试。 */
    object Retry : SearchIntent
}

object SearchReducer {

    fun reduce(state: SearchUiState, intent: SearchIntent): SearchUiState = when (intent) {
        is SearchIntent.QueryChange -> {
            val normalized = SearchMatcher.normalize(intent.text)
            if (normalized.isEmpty()) {
                // 查询为空：回落空闲面板（历史 + 热门 + 引导小字），不留白。
                state.copy(query = intent.text, loadState = SearchLoadState.Idle)
            } else {
                state.copy(query = intent.text)
            }
        }

        is SearchIntent.FocusGained -> state.copy(focused = true)

        is SearchIntent.FocusLost -> state.copy(focused = false)

        is SearchIntent.Submit -> {
            val key = SearchMatcher.normalize(state.query)
            if (key.isEmpty()) {
                // 空提交不检索、不写历史，回落空闲态。
                state.copy(loadState = SearchLoadState.Idle)
            } else {
                state.copy(
                    history = SearchHistory.add(state.history, key),
                    loadState = SearchLoadState.Loading,
                )
            }
        }

        is SearchIntent.ClearQuery -> state.copy(
            query = "",
            loadState = SearchLoadState.Idle,
        )

        is SearchIntent.PickHistory -> {
            val key = SearchMatcher.normalize(intent.key)
            if (key.isEmpty()) state
            else state.copy(
                query = key,
                history = SearchHistory.add(state.history, key),
                loadState = SearchLoadState.Loading,
            )
        }

        is SearchIntent.RemoveHistory ->
            state.copy(history = SearchHistory.remove(state.history, intent.key))

        is SearchIntent.ClearHistory ->
            state.copy(history = SearchHistory.clear())

        is SearchIntent.Loading -> state.copy(loadState = SearchLoadState.Loading)

        is SearchIntent.ResultsReady -> {
            val results = intent.results
            if (results.isEmpty()) {
                // 无结果：保留上一次缓存，空态给可重试引导。
                state.copy(
                    loadState = SearchLoadState.Empty(SearchMatcher.normalize(state.query)),
                )
            } else {
                state.copy(
                    loadState = SearchLoadState.Content(results),
                    lastResults = results,
                )
            }
        }

        is SearchIntent.Failure -> state.copy(
            loadState = SearchLoadState.Error(
                message = intent.message.ifBlank { SearchTokens.ERROR_MESSAGE },
                cached = state.lastResults,
            ),
        )

        is SearchIntent.Offline -> state.copy(
            loadState = SearchLoadState.Offline(cached = state.lastResults),
        )

        is SearchIntent.Retry -> {
            val key = SearchMatcher.normalize(state.query)
            if (key.isEmpty()) {
                state.copy(loadState = SearchLoadState.Idle)
            } else {
                state.copy(loadState = SearchLoadState.Loading)
            }
        }
    }
}
