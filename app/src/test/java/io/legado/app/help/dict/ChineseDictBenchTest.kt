package io.legado.app.help.dict

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 模块 D1 · 字典 Provider 性能 bench（规格切片-02 §5）。
 *
 * 预算口径：
 * - 冷路径：清空内存索引后的首次查询（对应真机「首触含词典加载」），≤ 300ms；
 * - 热路径：索引已就位后的重复命中，均值 ≤ 80ms；
 * - 批量单次调用 ≤ 80ms。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * 本 bench 在宿主 JVM 上跑，只验证解析编排路径不超时；荣耀 WIN 真机上的
 * 冷启动加载耗时仍需真机复测。未在本机 / 真机验证的部分，交付报告中显式标注。
 */
class ChineseDictBenchTest {

    private companion object {
        const val COLD_BUDGET_MS = 300L
        const val WARM_BUDGET_MS = 80L
        const val WARM_ROUNDS = 200
        const val WARM_DROP = 20
        const val BATCH_ROUNDS = 500
    }

    private val paragraph = "晋侯、秦伯围郑，以其无礼于晋，且贰于楚也。"

    @Before
    fun setUp() {
        ChineseDict.resetForBench()
    }

    @Test
    fun `cold first touch within 300ms budget`() {
        // 索引为空：首次 getChar 触发种子解析，对应冷启动首触路径。
        val start = System.nanoTime()
        val entry = ChineseDict.getChar('晋')
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("冷路径首触应 ≤ ${COLD_BUDGET_MS}ms，实际 ${elapsedMs}ms", elapsedMs < COLD_BUDGET_MS)
        assertTrue(entry!!.char.isNotEmpty())
    }

    @Test
    fun `warm getChar repeated hits within 80ms budget`() {
        // 先填满索引并触发 JIT 预热，丢掉前若干轮。
        repeat(WARM_DROP) { ChineseDict.getChar('晋') }

        val samples = ArrayList<Double>(WARM_ROUNDS)
        repeat(WARM_ROUNDS) {
            val start = System.nanoTime()
            ChineseDict.getChar('晋')
            samples += (System.nanoTime() - start) / 1_000_000.0
        }
        val steady = samples.drop(WARM_DROP)
        val avgMs = steady.average()
        val maxMs = steady.max()

        assertTrue(
            "热路径 getChar 均值应 ≤ ${WARM_BUDGET_MS}ms，实际均值 ${avgMs}ms，峰值 ${maxMs}ms",
            avgMs < WARM_BUDGET_MS
        )
    }

    @Test
    fun `warm wordAt repeated hits within 80ms budget`() {
        repeat(WARM_DROP) { ChineseDict.wordAt(paragraph, 0) }

        val samples = ArrayList<Double>(WARM_ROUNDS)
        repeat(WARM_ROUNDS) { i ->
            val start = System.nanoTime()
            ChineseDict.wordAt(paragraph, i % paragraph.length)
            samples += (System.nanoTime() - start) / 1_000_000.0
        }
        val steady = samples.drop(WARM_DROP)
        val avgMs = steady.average()
        val maxMs = steady.max()

        assertTrue(
            "热路径 wordAt 均值应 ≤ ${WARM_BUDGET_MS}ms，实际均值 ${avgMs}ms，峰值 ${maxMs}ms",
            avgMs < WARM_BUDGET_MS
        )
    }

    @Test
    fun `batch mixed calls stay under budget per call`() {
        repeat(WARM_DROP) {
            ChineseDict.getChar('晋')
            ChineseDict.getShuowen('晋')
            ChineseDict.getUsage('晋')
            ChineseDict.wordAt(paragraph, 0)
        }
        val start = System.nanoTime()
        repeat(BATCH_ROUNDS) { i ->
            val c = paragraph[i % paragraph.length]
            ChineseDict.getChar(c)
            ChineseDict.getShuowen(c)
            ChineseDict.getUsage(c)
            ChineseDict.wordAt(paragraph, i % paragraph.length)
        }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val perCallMs = totalMs / (BATCH_ROUNDS * 4.0)

        assertTrue(
            "批量单次查询应 ≤ ${WARM_BUDGET_MS}ms，实际 ${perCallMs}ms（总 ${totalMs}ms）",
            perCallMs < WARM_BUDGET_MS
        )
    }
}
