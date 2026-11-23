package io.legado.app.ui.main.search

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索页模型层 bench 断言：各阶段 p95 必须落在对应预算内。
 * 注意：本 bench 只覆盖纯模型 / 状态解析耗时；Compose 重组、真实绘制与触摸派发
 * 需真机或模拟器实测，未在本测试中验证。
 */
class SearchBenchTest {

    @Test
    fun `模型层各阶段 p95 在预算内（含空态或无网兜底路径）`() {
        val report = SearchBenchmark.run()
        println(report.summary())
        assertTrue(
            "存在超出预算的阶段：\n${report.summary()}",
            report.withinBudget,
        )
        report.phases.forEach {
            assertTrue(
                "${it.name} p95=${it.p95Ms}ms 超出预算 ${it.budgetMs}ms",
                it.p95Ms <= it.budgetMs,
            )
        }
    }

    @Test
    fun `兜底路径不抛异常且返回安全空结果`() {
        // 空候选 + 空查询 + 离线归约，全部必须安全回落，不抛异常。
        assertTrue(SearchMatcher.rank(emptyList(), "春秋").isEmpty())
        assertTrue(SearchMatcher.rank(emptyList(), "").isEmpty())

        var state = SearchUiState(lastResults = emptyList())
        state = SearchReducer.reduce(state, SearchIntent.ResultsReady(emptyList()))
        assertTrue(state.loadState is SearchLoadState.Empty)

        state = SearchReducer.reduce(state, SearchIntent.Offline)
        val offline = state.loadState as SearchLoadState.Offline
        assertTrue(offline.cached.isEmpty())

        state = SearchReducer.reduce(state, SearchIntent.Retry)
        // 空查询重试按契约回落空闲态（与 SearchReducerTest 一致），不崩、安全空结果。
        assertTrue(state.loadState is SearchLoadState.Idle)
    }
}
