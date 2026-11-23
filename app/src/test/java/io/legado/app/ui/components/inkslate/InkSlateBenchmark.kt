package io.legado.app.ui.components.inkslate

import io.legado.app.ui.components.ComponentPalette
import kotlin.math.roundToLong

/**
 * U7 InkSlate 模型层 bench（JVM 可跑，不依赖 Compose 渲染）。
 * 测量笔迹追加、撤销重做、橡皮命中、压感模拟、SVG 拼装五类纯模型操作；
 * 画布渲染帧耗时需真机 / 模拟器实测，本 bench 不冒充渲染结果。
 */
data class InkSlateBenchPhase(
    val name: String,
    val iterations: Int,
    val avgMs: Double,
    val p95Ms: Double,
    val maxMs: Double,
)

data class InkSlateBenchReport(
    val phases: List<InkSlateBenchPhase>,
    val budgetMs: Long,
) {
    val withinBudget: Boolean get() = phases.all { it.p95Ms <= budgetMs }
}

object InkSlateBenchmark {

    private const val WARMUP = 2_000
    private const val ITERATIONS = 50_000

    fun run(): InkSlateBenchReport {
        warmup()
        val phases = listOf(
            phase("slate-stroke-append", ::benchAppend),
            phase("slate-undo-redo", ::benchUndoRedo),
            phase("slate-erase", ::benchErase),
            phase("slate-pressure", ::benchPressure),
            phase("slate-svg-build", ::benchSvg),
        )
        return InkSlateBenchReport(phases, ComponentPalette.MODEL_BUDGET_MS)
    }

    private fun warmup() {
        repeat(WARMUP) {
            benchAppend()
            benchUndoRedo()
            benchErase()
            benchPressure()
            benchSvg()
        }
    }

    private inline fun phase(name: String, crossinline block: () -> Unit): InkSlateBenchPhase {
        val samples = LongArray(ITERATIONS)
        repeat(ITERATIONS) { i ->
            val start = System.nanoTime()
            block()
            samples[i] = System.nanoTime() - start
        }
        samples.sort()
        return InkSlateBenchPhase(
            name = name,
            iterations = ITERATIONS,
            avgMs = samples.average() / 1_000_000.0,
            p95Ms = samples[(ITERATIONS * 0.95).toInt()] / 1_000_000.0,
            maxMs = samples.last() / 1_000_000.0,
        )
    }

    private fun base(): InkSlateState =
        InkSlateState.Empty.setCanvasSize(300f, 400f)

    private fun benchAppend() {
        var state = base()
        repeat(6) {
            state = state.beginStroke(it * 10f, it * 10f, 0.8f)
                .extendStroke(it * 10f + 5f, it * 10f + 5f, 0.6f)
                .finishStroke()
        }
    }

    private fun benchUndoRedo() {
        var state = base()
        repeat(4) {
            state = state.beginStroke(1f, 1f, 1f).finishStroke()
        }
        repeat(2) { state = state.undo() }
        repeat(2) { state = state.redo() }
    }

    private fun benchErase() {
        var state = base()
        repeat(5) {
            state = state.beginStroke(it * 40f, it * 40f, 1f).finishStroke()
        }
        state = state.eraseAt(2f, 2f)
    }

    private fun benchPressure() {
        InkPressure.simulate(0f)
        InkPressure.simulate(12f)
        InkPressure.simulate(60f)
    }

    private fun benchSvg() {
        var state = base()
        repeat(3) {
            state = state.beginStroke(it * 20f, it * 20f, 0.9f)
                .extendStroke(it * 20f + 30f, it * 20f + 30f, 0.7f)
                .finishStroke()
        }
        InkSlateSvg.build(state)
    }
}

/** 供日志输出的紧凑摘要 */
fun InkSlateBenchReport.summary(): String = buildString {
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
