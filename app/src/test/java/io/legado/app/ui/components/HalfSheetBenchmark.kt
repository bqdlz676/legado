package io.legado.app.ui.components

/**
 * U6 半屏 Sheet 模型层 bench（JVM 可跑，不依赖 Compose 渲染）。
 * 测量锚点夹紧 / 遮罩下沉联动 / 内容自检三类纯模型操作；
 * 渲染帧耗时需真机 / 模拟器实测，本 bench 不冒充渲染结果。
 */
data class HalfSheetBenchPhase(
    val name: String,
    val iterations: Int,
    val avgMs: Double,
    val p95Ms: Double,
    val maxMs: Double,
)

data class HalfSheetBenchReport(
    val phases: List<HalfSheetBenchPhase>,
    val budgetMs: Long,
) {
    val withinBudget: Boolean get() = phases.all { it.p95Ms <= budgetMs }
}

object HalfSheetBenchmark {

    private const val WARMUP = 2_000
    private const val ITERATIONS = 50_000

    fun run(): HalfSheetBenchReport {
        warmup()
        val phases = listOf(
            phase("sheet-clamp", ::benchClamp),
            phase("sheet-mask-sink", ::benchMaskSink),
            phase("sheet-validate", ::benchValidate),
        )
        return HalfSheetBenchReport(phases, ComponentPalette.MODEL_BUDGET_MS)
    }

    private fun warmup() {
        repeat(WARMUP) {
            benchClamp()
            benchMaskSink()
            benchValidate()
        }
    }

    private inline fun phase(name: String, crossinline block: () -> Unit): HalfSheetBenchPhase {
        val samples = LongArray(ITERATIONS)
        repeat(ITERATIONS) { i ->
            val start = System.nanoTime()
            block()
            samples[i] = System.nanoTime() - start
        }
        samples.sort()
        return HalfSheetBenchPhase(
            name = name,
            iterations = ITERATIONS,
            avgMs = samples.average() / 1_000_000.0,
            p95Ms = samples[(ITERATIONS * 0.95).toInt()] / 1_000_000.0,
            maxMs = samples.last() / 1_000_000.0,
        )
    }

    private fun benchClamp() {
        HalfSheetAnchors.clampHeight(0.82f)
        HalfSheetAnchors.clampHeight(0.31f)
        HalfSheetAnchors.shouldDismiss(0.18f, 720f)
        HalfSheetAnchors.restingFraction()
    }

    private fun benchMaskSink() {
        HalfSheetAnchors.maskAlpha(0.66f)
        HalfSheetAnchors.hostSinkDp(0.66f)
        HalfSheetAnchors.maskAlpha(1.2f)
    }

    private fun benchValidate() {
        val content = HalfSheetContent(
            HalfSheetKind.PERSON,
            listOf(
                HalfSheetSection(HalfSheetSectionKind.PERSON_ACTION, "行为", listOf("一句"), boldFirst = true),
                HalfSheetSection(HalfSheetSectionKind.PERSON_CAST, "人物", listOf("甲", "乙")),
                HalfSheetSection(HalfSheetSectionKind.PERSON_TIMELINE, "时间轴", listOf("出", "事", "末")),
                HalfSheetSection(HalfSheetSectionKind.PERSON_ARCHIVE, "档案", listOf("本名")),
                HalfSheetSection(HalfSheetSectionKind.PERSON_DOUBLETS, "同名", listOf("›")),
            ),
        )
        content.validate()
    }
}

/** 供日志输出的紧凑摘要 */
fun HalfSheetBenchReport.summary(): String = buildString {
    append("budget=${budgetMs}ms withinBudget=$withinBudget\n")
    phases.forEach {
        append(
            "  ${it.name}: avg=${"%.4f".format(it.avgMs)}ms " +
                "p95=${"%.4f".format(it.p95Ms)}ms max=${"%.4f".format(it.maxMs)}ms " +
                "iter=${it.iterations}\n",
        )
    }
}
