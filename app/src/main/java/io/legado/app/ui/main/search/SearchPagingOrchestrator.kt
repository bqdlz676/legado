package io.legado.app.ui.main.search

/**
 * 落文 · 翻页（加载更多）编排协作者（纯 JVM，不碰 Android / 网络 / DAO）。
 *
 * 职责：持有翻页命令闸门与去抖时间戳，决定一次到底触发是否放行、是否落去抖账、
 * 是否调用检索出口。[InkSearchViewModel] 只把状态快照、[SearchIntent] 分发与检索
 * 回调接进来；本类不读 `viewModelScope`，便于单测注入桩记录调用序列（验收判据 5 ④）。
 *
 * 收口窗口口径（D1-R1/R6）：一次翻页请求内模型可能按书源多批回调 [onSearchSuccess]，
 * 在途身份保留到 [SearchIntent.LoadMoreFinish]；本协作者只负责「发不发这一页」，
 * 终态收口由归约器在 Finish 时落定。
 */
internal class SearchPagingOrchestrator(
    private val gate: SearchCommandGate,
) {

    /** 上一次翻页触发时间戳（ms）；0 = 从未触发。归编排器持有，归约器拒收时不落账（D1-R4）。 */
    private var lastLoadMoreAt = 0L

    /** 一次触发的编排结果：Started 已放行并调用检索出口；其余为各层闸门拦截。 */
    sealed interface Outcome {
        /** 守卫通过、归约器接收进在途，检索出口已被调用一次。 */
        object Started : Outcome

        /** 状态守卫不满足（无内容 / 已收口 / 在途 / 失败态自动触发）。 */
        object BlockedGuard : Outcome

        /** 去抖窗口内连发，本次被抑制。 */
        object BlockedDebounce : Outcome

        /** 归约器二次闸门拒收；未落去抖账、未占命令。 */
        object RejectedByReducer : Outcome
    }

    /** 滚动到底自动触发；失败态不自动重试（D1-R3）。 */
    fun startAuto(
        snapshot: () -> SearchUiState,
        now: Long,
        dispatch: (SearchIntent) -> Unit,
        onSearch: (command: Long) -> Unit,
    ): Outcome = start(snapshot, now, dispatch, onSearch, explicitRetry = false)

    /** 脚注显式重试；仅分页错态可入，绕开自动拦阻但仍过归约器闸门（D1-R3）。 */
    fun startRetry(
        snapshot: () -> SearchUiState,
        now: Long,
        dispatch: (SearchIntent) -> Unit,
        onSearch: (command: Long) -> Unit,
    ): Outcome {
        if (!SearchPagingPolicy.retryAllowed(snapshot())) return Outcome.BlockedGuard
        lastLoadMoreAt = 0L
        return start(snapshot, now, dispatch, onSearch, explicitRetry = true)
    }

    private fun start(
        snapshot: () -> SearchUiState,
        now: Long,
        dispatch: (SearchIntent) -> Unit,
        onSearch: (command: Long) -> Unit,
        explicitRetry: Boolean,
    ): Outcome {
        val state = snapshot()
        val guardOk = if (explicitRetry) {
            state.paging is SearchPagingState.LoadMoreError
        } else {
            SearchPagingPolicy.autoTriggerAllowed(state)
        }
        if (!guardOk) return Outcome.BlockedGuard

        if (SearchPagingPolicy.withinDebounce(
                lastLoadMoreAt,
                now,
                SearchTokens.LOAD_MORE_DEBOUNCE_MS,
            )
        ) {
            return Outcome.BlockedDebounce
        }

        val command = gate.next()
        dispatch(SearchIntent.LoadMoreStart)

        // D1-R4：归约器二次闸门拒收时不落去抖时间戳，避免拒收后窗口内的合法触发被压住。
        if (!SearchPagingPolicy.acceptedIntoFlight(snapshot())) {
            return Outcome.RejectedByReducer
        }

        lastLoadMoreAt = now
        onSearch(command)
        return Outcome.Started
    }

    /**
     * 翻页协程体复核（D1-R2）：命令仍为当前有效、且状态仍在途，旧翻页体才允许继续；
     * 挂起期间新检索作废命令序号，或状态已被打断收口，旧体必须退出，不得用新字段再调模型。
     */
    fun bodyStillEligible(command: Long, state: SearchUiState): Boolean =
        gate.isCurrent(command) && state.paging is SearchPagingState.LoadingMore

    /** 命令序号是否仍为当前有效（跨词竞态复核用）。 */
    fun isCurrent(command: Long): Boolean = gate.isCurrent(command)

    /** 新检索发起：清掉翻页去抖账；命令序号作废由 [SearchCommandGate.next] 承接。 */
    fun onNewSearch() {
        lastLoadMoreAt = 0L
    }
}
