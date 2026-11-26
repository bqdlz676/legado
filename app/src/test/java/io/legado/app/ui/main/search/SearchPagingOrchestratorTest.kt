package io.legado.app.ui.main.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻页编排器 [SearchPagingOrchestrator] 单测：纯 JVM，状态流 / 检索出口均注入桩。
 *
 * 覆盖验收判据 5 必补断言：
 * - ① 新检索命令作废旧翻页命令序号（跨词复位等价闸门；模型层页码游标复位由 SearchPageCursorTest 锁定，Q-179）；
 * - ③ 翻页协程体命令序号 / 在途态复核（D1-R2 跨词竞态回归）；
 * - ④ 请求计数桩：同页在途模型检索出口只被记录一次，替换「状态相等推断不重复」的弱断言；
 * - R3 失败态自动到底被拦、仅显式重试放行；R4 归约拒收不落去抖账。
 *
 * 范式标注见 [docs/修复-搜索翻页收口.md] §新增断言。
 */
class SearchPagingOrchestratorTest {

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

    private fun inFlight() =
        SearchReducer.reduce(contentState(), SearchIntent.LoadMoreStart)

    private class Harness(initial: SearchUiState) {
        val state = MutableStateFlow(initial)
        val gate = SearchCommandGate()
        val orchestrator = SearchPagingOrchestrator(gate)
        val searchCalls = mutableListOf<Long>()

        val dispatch: (SearchIntent) -> Unit = { intent ->
            state.update { SearchReducer.reduce(it, intent) }
        }
        val onSearch: (Long) -> Unit = { command -> searchCalls.add(command) }
    }

    // ────────────────────────── ④ 请求计数桩（调用记录桩范式） ──────────────────────────

    @Test
    fun `one in-flight page records a single search decision`() {
        val harness = Harness(contentState())

        val first = harness.orchestrator.startAuto(
            snapshot = { harness.state.value },
            now = 1_000L,
            dispatch = harness.dispatch,
            onSearch = harness.onSearch,
        )
        assertEquals(SearchPagingOrchestrator.Outcome.Started, first)
        assertEquals("在途第一页检索出口调用一次", 1, harness.searchCalls.size)

        // 在途态第二次到底：守卫拦截，检索出口不得再被记录。
        val second = harness.orchestrator.startAuto(
            snapshot = { harness.state.value },
            now = 1_001L,
            dispatch = harness.dispatch,
            onSearch = harness.onSearch,
        )
        assertEquals(SearchPagingOrchestrator.Outcome.BlockedGuard, second)
        assertEquals("在途重复触发不产生第二次检索调用", 1, harness.searchCalls.size)
    }

    @Test
    fun `rapid second trigger after settled idle is suppressed by debounce`() {
        val harness = Harness(contentState())

        harness.orchestrator.startAuto(
            snapshot = { harness.state.value },
            now = 1_000L,
            dispatch = harness.dispatch,
            onSearch = harness.onSearch,
        )
        // 模拟本页已收口回到空闲可翻页态。
        harness.state.value = contentState()

        val second = harness.orchestrator.startAuto(
            snapshot = { harness.state.value },
            now = 1_000L + SearchTokens.LOAD_MORE_DEBOUNCE_MS / 2,
            dispatch = harness.dispatch,
            onSearch = harness.onSearch,
        )
        assertEquals(SearchPagingOrchestrator.Outcome.BlockedDebounce, second)
        assertEquals(1, harness.searchCalls.size)
    }

    // ────────────────────────── R3 失败态：自动拦阻 / 显式重试放行 ──────────────────────────

    @Test
    fun `error state blocks auto trigger but explicit retry records one search`() {
        val failed = SearchReducer.reduce(inFlight(), SearchIntent.LoadMoreFailure("boom"))
        val harness = Harness(failed)

        val auto = harness.orchestrator.startAuto(
            snapshot = { harness.state.value },
            now = 2_000L,
            dispatch = harness.dispatch,
            onSearch = harness.onSearch,
        )
        assertEquals("失败态滚动到底不自动重发", SearchPagingOrchestrator.Outcome.BlockedGuard, auto)
        assertTrue("自动触发未调用检索出口", harness.searchCalls.isEmpty())

        val retry = harness.orchestrator.startRetry(
            snapshot = { harness.state.value },
            now = 2_000L,
            dispatch = harness.dispatch,
            onSearch = harness.onSearch,
        )
        assertEquals("脚注显式重试放行", SearchPagingOrchestrator.Outcome.Started, retry)
        assertEquals(1, harness.searchCalls.size)
    }

    // ────────────────────────── R4 归约拒收：不落去抖账、不调检索出口 ──────────────────────────

    @Test
    fun `rejected by reducer records no search and keeps debounce window free`() {
        val state = MutableStateFlow(contentState())
        val gate = SearchCommandGate()
        val orchestrator = SearchPagingOrchestrator(gate)
        val calls = mutableListOf<Long>()
        // 模拟归约器二次闸门拒收：dispatch 不把状态翻进在途态。
        val rejectingDispatch: (SearchIntent) -> Unit = { }
        val realDispatch: (SearchIntent) -> Unit = { intent ->
            state.update { SearchReducer.reduce(it, intent) }
        }

        val rejected = orchestrator.startAuto(
            snapshot = { state.value },
            now = 3_000L,
            dispatch = rejectingDispatch,
            onSearch = { calls.add(it) },
        )
        assertEquals(SearchPagingOrchestrator.Outcome.RejectedByReducer, rejected)
        assertTrue("拒收不调用检索出口", calls.isEmpty())

        // 拒收未落去抖账：同一时刻再走真实归约器触发，不应被去抖压住。
        val retry = orchestrator.startAuto(
            snapshot = { state.value },
            now = 3_000L,
            dispatch = realDispatch,
            onSearch = { calls.add(it) },
        )
        assertEquals("拒收后窗口内合法触发不被误压", SearchPagingOrchestrator.Outcome.Started, retry)
        assertEquals(1, calls.size)
    }

    // ────────────────────────── ③ 命令序号 / 在途态复核（D1-R2） ──────────────────────────

    @Test
    fun `stale load-more command fails body eligibility after newer search`() {
        val gate = SearchCommandGate()
        val orchestrator = SearchPagingOrchestrator(gate)
        val command = gate.next()
        val flightState = inFlight()

        assertTrue("在途命令初始复核通过", orchestrator.bodyStillEligible(command, flightState))

        // 新检索取下一个命令序号，旧翻页命令作废。
        gate.next()
        assertFalse("旧翻页命令序号已作废", orchestrator.isCurrent(command))
        assertFalse(
            "作废命令的翻页体不得继续",
            orchestrator.bodyStillEligible(command, flightState),
        )
    }

    @Test
    fun `body eligibility fails when paging no longer in flight`() {
        val gate = SearchCommandGate()
        val orchestrator = SearchPagingOrchestrator(gate)
        val command = gate.next()

        val settled = contentState()
        assertFalse(
            "状态已离开在途态时翻页体复核不通过",
            orchestrator.bodyStillEligible(command, settled),
        )
    }

    // ────────────────────────── ① 跨词命令作废（等价闸门） ──────────────────────────

    @Test
    fun `new search clears load-more debounce window`() {
        val harness = Harness(contentState())

        harness.orchestrator.startAuto(
            snapshot = { harness.state.value },
            now = 4_000L,
            dispatch = harness.dispatch,
            onSearch = harness.onSearch,
        )
        harness.state.value = contentState()

        // 新检索清掉翻页去抖账：同一时刻再触发不被窗口压住。
        harness.orchestrator.onNewSearch()
        val afterNewSearch = harness.orchestrator.startAuto(
            snapshot = { harness.state.value },
            now = 4_000L,
            dispatch = harness.dispatch,
            onSearch = harness.onSearch,
        )
        assertEquals(SearchPagingOrchestrator.Outcome.Started, afterNewSearch)
    }

    @Test
    fun `new search command invalidates prior load-more token`() {
        val gate = SearchCommandGate()
        val loadMoreCommand = gate.next()
        val newSearchCommand = gate.next()

        assertNotEquals(loadMoreCommand, newSearchCommand)
        assertFalse("旧翻页命令被新检索作废", gate.isCurrent(loadMoreCommand))
        assertTrue("新检索命令为当前有效命令", gate.isCurrent(newSearchCommand))
    }
}
