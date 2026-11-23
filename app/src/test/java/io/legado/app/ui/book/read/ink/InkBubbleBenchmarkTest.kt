package io.legado.app.ui.book.read.ink

import io.legado.app.help.bubble.Action
import io.legado.app.help.bubble.BubbleCandidate
import io.legado.app.help.bubble.BubbleContent
import io.legado.app.help.bubble.BubbleKind
import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 落文 · 气泡交互片性能 bench（切片-02 §5 / 切片-04 性能预算表）。
 *
 * 预算口径（写死断言）：
 *  - 冷启动首触气泡 ≤ 300ms（含加载）；
 *  - 之后热路径 ≤ 80ms。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 *  - 本 bench 只覆盖 UI 装配纯路径：候选循环 + 动作路由 + 时间轴展示模型构建；
 *  - 词典 / 人物 / 地名表的首次惰性加载耗时归 C2 [BubbleBenchmarkTest] 冷路径；
 *  - Compose 重组、真实绘制、触摸派发与首帧可见仍需荣耀 WIN 真机复测，
 *    未在本机验证的部分不计入通过结论。
 */
class InkBubbleBenchmarkTest {

    private companion object {
        const val COLD_BUDGET_MS = 300.0
        const val WARM_BUDGET_MS = 80.0
        const val WARM_ROUNDS = 200
        const val WARM_DROP = 20
    }

    private lateinit var content: BubbleContent

    @Before
    fun setUp() {
        ContextAnchor.reset()
        ContextAnchor.setYear(-722)
        content = BubbleContent(
            kind = BubbleKind.PLACE,
            token = "晋",
            phonetic = "jìn",
            headline = "晋（诸侯国）",
            body = "周初封国，春秋霸主之一。",
            detail = "晋国，姬姓，唐叔虞之后。",
            actions = listOf(Action.OpenMap("P-JIN", "晋")),
            candidates = listOf(
                BubbleCandidate("晋", "晋（诸侯国）"),
                BubbleCandidate("晋州", "晋（今山西晋城）"),
            ),
            source = "CHGIS 对齐种子",
        )
    }

    /** 单次 UI 装配纯路径：候选循环一步 + 动作路由 + 时间轴模型构建。 */
    private fun assembleStep(i: Int) {
        val cycle = CandidateCycle(content.candidates)
        cycle.next()
        InkActionDispatcher.route(content.actions.first(), ContextAnchor)
        InkTimelinePresenter.build(-722 + (i % 5), null)
    }

    @Test
    fun `cold first UI assembly within 300ms budget`() {
        val start = System.nanoTime()
        assembleStep(0)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("冷路径 UI 装配应 ≤ ${COLD_BUDGET_MS}ms，实际 ${elapsedMs}ms", elapsedMs < COLD_BUDGET_MS)
    }

    @Test
    fun `warm repeated assembly within 80ms budget`() {
        // 先预热丢前若干轮，避免 JIT / 类初始化摊进热路径均值。
        repeat(WARM_DROP) { assembleStep(it) }

        val samples = ArrayList<Double>(WARM_ROUNDS)
        repeat(WARM_ROUNDS) { i ->
            val start = System.nanoTime()
            assembleStep(i)
            samples += (System.nanoTime() - start) / 1_000_000.0
        }
        val steady = samples.drop(WARM_DROP)
        val avgMs = steady.average()
        val maxMs = steady.max()

        assertTrue(
            "热路径均值应 ≤ ${WARM_BUDGET_MS}ms，实际均值 ${avgMs}ms，峰值 ${maxMs}ms",
            avgMs < WARM_BUDGET_MS,
        )
    }
}
