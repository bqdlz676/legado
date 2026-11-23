package io.legado.app.ui.main.search

import kotlin.math.roundToLong

/**
 * 搜索页模型层 bench（纯 JVM，离线可跑，不依赖 Compose 渲染）。
 *
 * 测量四类纯模型操作：候选匹配排序、高亮切分、状态归约、历史整理；
 * 另设兜底阶段：空候选排名、空结果归约、离线缓存保留，覆盖空态 / 无网路径。
 *
 * 预算：轻量模型阶段 [SearchTokens.MODEL_BUDGET_MS]（4ms）；
 * 候选排序阶段 [SearchTokens.FRAME_BUDGET_MS]（16ms 单帧红线）。
 * Compose 重组 / 真实绘制 / 触摸派发需真机或模拟器复测，本 bench 不冒充渲染结果。
 */
data class SearchBenchPhase(
    val name: String,
    val iterations: Int,
    val avgMs: Double,
    val p95Ms: Double,
    val maxMs: Double,
    val budgetMs: Double,
)

data class SearchBenchReport(
    val phases: List<SearchBenchPhase>,
) {
    val withinBudget: Boolean get() = phases.all { it.p95Ms <= it.budgetMs }
}

object SearchBenchmark {

    private const val WARMUP = 500
    private const val ITERATIONS = 5_000

    private val candidates = List(200) { i ->
        SearchResultItem(
            id = "book-$i",
            title = "春秋纪事卷${i % 17}",
            author = if (i % 3 == 0) "左丘明" else "佚名$i",
            snippet = if (i % 2 == 0) "春秋时期诸侯会盟于郊" else "",
        )
    }

    fun run(): SearchBenchReport {
        warmup()
        val phases = listOf(
            phase("match-rank", SearchTokens.FRAME_BUDGET_MS) { benchRank() },
            phase("highlight-split", SearchTokens.MODEL_BUDGET_MS) { benchHighlight() },
            phase("reducer-transition", SearchTokens.MODEL_BUDGET_MS) { benchReducer() },
            phase("history-ops", SearchTokens.MODEL_BUDGET_MS) { benchHistory() },
            phase("fallback-empty-offline", SearchTokens.MODEL_BUDGET_MS) { benchFallback() },
        )
        return SearchBenchReport(phases)
    }

    private fun warmup() {
        repeat(WARMUP) {
            benchRank()
            benchHighlight()
            benchReducer()
            benchHistory()
            benchFallback()
        }
    }

    private inline fun phase(
        name: String,
        budgetMs: Double,
        crossinline block: () -> Unit,
    ): SearchBenchPhase {
        val samples = LongArray(ITERATIONS)
        repeat(ITERATIONS) { i ->
            val start = System.nanoTime()
            block()
            samples[i] = System.nanoTime() - start
        }
        samples.sort()
        val avgNs = samples.average()
        val p95Ns = samples[(ITERATIONS * 0.95).toInt()]
        val maxNs = samples.last()
        return SearchBenchPhase(
            name = name,
            iterations = ITERATIONS,
            avgMs = avgNs / 1_000_000.0,
            p95Ms = p95Ns / 1_000_000.0,
            maxMs = maxNs / 1_000_000.0,
            budgetMs = budgetMs,
        )
    }

    private fun benchRank() {
        SearchMatcher.rank(candidates, "春秋")
        SearchMatcher.rank(candidates, "左丘")
    }

    private fun benchHighlight() {
        SearchHighlight.split("春秋时期诸侯会盟于郊，左传庄公十年", "春秋")
        SearchHighlight.split("史记·廉颇蔺相如列传", "列传")
    }

    private fun benchReducer() {
        var state = SearchUiState(query = "春秋")
        state = SearchReducer.reduce(state, SearchIntent.Submit)
        state = SearchReducer.reduce(state, SearchIntent.ResultsReady(candidates.take(8)))
        state = SearchReducer.reduce(state, SearchIntent.QueryChange("史"))
        state = SearchReducer.reduce(state, SearchIntent.PickHistory("史记"))
        state.loadState
    }

    private fun benchHistory() {
        var history = listOf("春秋", "史记")
        history = SearchHistory.add(history, "左传")
        history = SearchHistory.remove(history, "史记")
        SearchHistory.clean(history)
        SearchHistory.hot(listOf("春秋", "左传", "春秋"))
    }

    /** 兜底路径：空候选、空结果归约、离线缓存保留。 */
    private fun benchFallback() {
        SearchMatcher.rank(emptyList(), "春秋")
        var state = SearchUiState(lastResults = candidates.take(4))
        state = SearchReducer.reduce(state, SearchIntent.ResultsReady(emptyList()))
        state = SearchReducer.reduce(state, SearchIntent.Offline)
        state = SearchReducer.reduce(state, SearchIntent.Retry)
        state.visibleResults
    }
}

/** 供日志输出的紧凑摘要。 */
fun SearchBenchReport.summary(): String = buildString {
    append("withinBudget=$withinBudget\n")
    phases.forEach {
        append(
            "  ${it.name}: avg=${"%.4f".format(it.avgMs)}ms " +
                "p95=${"%.4f".format(it.p95Ms)}ms max=${"%.4f".format(it.maxMs)}ms " +
                "budget=${it.budgetMs}ms iter=${it.iterations}\n",
        )
    }
}

@Suppress("unused")
private fun Long.roundToMs(): Long = (this / 1_000_000.0).roundToLong()
