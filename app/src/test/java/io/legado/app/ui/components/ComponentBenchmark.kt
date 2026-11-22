package io.legado.app.ui.components

import kotlin.math.roundToLong

/**
 * 组件模型层 bench（JVM 可跑，不依赖 Compose 渲染）。
 *
 * 测量四类纯模型操作的单次耗时：气泡构建+自检、时间轴导航、七态解析、Sheet 分段判定。
 * 动画不掉帧红线：单帧 16ms；模型层预算 [ComponentPalette.MODEL_BUDGET_MS]（4ms），
 * 给绘制 / 布局留出余量。渲染层动画帧耗时需在真机 / 模拟器上另测，本 bench 不冒充渲染结果。
 */
data class BenchPhaseReport(
    val name: String,
    val iterations: Int,
    val avgMs: Double,
    val p95Ms: Double,
    val maxMs: Double,
)

data class BenchReport(
    val phases: List<BenchPhaseReport>,
    val budgetMs: Long,
) {
    val withinBudget: Boolean get() = phases.all { it.p95Ms <= budgetMs }
}

object ComponentBenchmark {

    private const val WARMUP = 2_000
    private const val ITERATIONS = 50_000

    fun run(): BenchReport {
        warmup()
        val phases = listOf(
            phase("bubble-build-validate", ::benchBubble),
            phase("timeline-navigate", ::benchTimeline),
            phase("interaction-resolve", ::benchInteraction),
            phase("sheet-detent", ::benchSheet),
        )
        return BenchReport(phases, ComponentPalette.MODEL_BUDGET_MS)
    }

    private fun warmup() {
        repeat(WARMUP) {
            benchBubble()
            benchTimeline()
            benchInteraction()
            benchSheet()
        }
    }

    private inline fun phase(name: String, crossinline block: () -> Unit): BenchPhaseReport {
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
        return BenchPhaseReport(
            name = name,
            iterations = ITERATIONS,
            avgMs = avgNs / 1_000_000.0,
            p95Ms = p95Ns / 1_000_000.0,
            maxMs = maxNs / 1_000_000.0,
        )
    }

    private fun benchBubble() {
        val content = BubbleContent(
            kind = BubbleKind.PERSON,
            token = "桓公",
            phonetic = "huán gōng",
            headline = "鲁桓公",
            body = "在位十八年。",
            actions = listOf(BubbleAction.OpenPerson("鲁桓公")),
            candidates = listOf("鲁桓公", "齐桓公"),
            source = "CBDB #1",
        )
        content.validate()
        content.hasCandidates
    }

    private fun benchTimeline() {
        var model = TimelineModel(
            nodes = List(6) { i ->
                TimelineNode(
                    yearLabel = "前 ${722 - i} 年",
                    eraLabel = "鲁隐公 ${i + 1} 年",
                    regimes = listOf(RegimeRow("周", "${49 + i} 年")),
                )
            },
        )
        repeat(3) { model = model.goForward() }
        repeat(2) { model = model.goBack() }
        model.current()
        model.validate()
    }

    private fun benchInteraction() {
        val states = listOf(
            InkInteraction.Default,
            InkInteraction(hovered = true),
            InkInteraction(pressed = true),
            InkInteraction.Disabled,
            InkInteraction.Loading,
            InkInteraction.Empty,
            InkInteraction.Offline,
        )
        states.forEach {
            it.resolve()
            it.clickEnabled
            it.accentOverlayAlpha
            it.contentAlpha
        }
    }

    private fun benchSheet() {
        SheetDetent.nearest(0.42f)
        SheetDetent.nearest(0.71f)
        SheetDetent.shouldDismiss(0.18f, 700f)
        SheetDetent.maskAlpha(0.66f)
        SheetDetent.normalize(0.55f)
    }
}

/** 供日志输出的紧凑摘要 */
fun BenchReport.summary(): String = buildString {
    append("budget=${budgetMs}ms withinBudget=$withinBudget\n")
    phases.forEach {
        append(
            "  ${it.name}: avg=${"%.4f".format(it.avgMs)}ms " +
                "p95=${"%.4f".format(it.p95Ms)}ms max=${"%.4f".format(it.maxMs)}ms " +
                "iter=${it.iterations}\n",
        )
    }
}

@Suppress("unused")
private fun Long.roundToMs(): Long = (this / 1_000_000.0).roundToLong()
