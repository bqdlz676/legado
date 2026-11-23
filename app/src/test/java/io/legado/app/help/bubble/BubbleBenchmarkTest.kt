package io.legado.app.help.bubble

import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.dict.ChineseDict
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 气泡解析性能 bench（规格切片-02 §5：冷启动首触气泡 ≤ 300ms（含加载）；之后 ≤ 80ms）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * - 冷路径：清空 D1 内存索引、全新 resolver、缓存为空的第一次 resolve
 *   （对应真机「首触含词典加载」的代码路径）；
 * - 热路径：同一位置反复点击，走 LRU 缓存命中；
 * - 本 bench 在宿主 JVM 上跑，只验证解析编排路径不超时；荣耀 WIN 真机上的冷启动词典加载
 *   耗时仍需真机复测，未在本机验证的部分不计入通过结论。
 */
class BubbleBenchmarkTest {

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
        ContextAnchor.reset()
        ChineseDict.resetForBench()
    }

    private fun newResolver() = BubbleResolverImpl(
        DateProvider, WordProvider, PersonProvider, PlaceProvider, WordProvider, CharProvider, MissingProvider,
    )

    @Test
    fun `cold first resolve within 300ms budget`() {
        // 全新装配，D1 索引已清空；模拟首触。
        val resolver = newResolver()
        val start = System.nanoTime()
        val bubble = resolver.resolve(paragraph, 0)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("冷路径首触应 ≤ ${COLD_BUDGET_MS}ms，实际 ${elapsedMs}ms", elapsedMs < COLD_BUDGET_MS)
        assertTrue(bubble!!.token.isNotEmpty())
    }

    @Test
    fun `warm repeated resolve within 80ms budget`() {
        val resolver = newResolver()
        // 先填满缓存 / 触发 JIT 预热，再丢掉前若干轮。
        repeat(WARM_DROP) { resolver.resolve(paragraph, it % paragraph.length) }

        val samples = ArrayList<Double>(WARM_ROUNDS)
        repeat(WARM_ROUNDS) { i ->
            val start = System.nanoTime()
            resolver.resolve(paragraph, i % paragraph.length)
            samples += (System.nanoTime() - start) / 1_000_000.0
        }
        val steady = samples.drop(WARM_DROP)
        val avgMs = steady.average()
        val maxMs = steady.max()

        assertTrue("热路径均值应 ≤ ${WARM_BUDGET_MS}ms，实际均值 ${avgMs}ms，峰值 ${maxMs}ms", avgMs < WARM_BUDGET_MS)
    }

    @Test
    fun `batch resolve stays under budget per call`() {
        val resolver = newResolver()
        val start = System.nanoTime()
        repeat(BATCH_ROUNDS) { i -> resolver.resolve(paragraph, i % paragraph.length) }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val perCallMs = totalMs / BATCH_ROUNDS.toDouble()

        assertTrue("批量单次解析应 ≤ ${WARM_BUDGET_MS}ms，实际 ${perCallMs}ms（总 ${totalMs}ms）", perCallMs < WARM_BUDGET_MS)
    }
}
