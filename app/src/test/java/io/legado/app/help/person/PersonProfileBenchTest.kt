package io.legado.app.help.person

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块 D2 · 人物查询性能 bench（规格切片-02 §5：气泡冷启动首触 ≤ 300ms，之后 ≤ 80ms）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * - 冷路径：首次查询触发索引构建后的第一次命中；
 * - 热路径：同一批称呼反复查询，走 HashMap 命中；
 * - 本 bench 在宿主 JVM 上跑，只验证归并 / 消歧路径不超时；荣耀 WIN 真机上的耗时
 *   仍需真机复测，未在本机验证的部分不计入通过结论。
 * - 预算取宽松上限：单次「profileOf + canonical + lifeSpan」三连查热路径均值 ≤ 5ms。
 */
class PersonProfileBenchTest {

    private companion object {
        const val WARM_ROUNDS = 100
        const val MEASURE_ROUNDS = 2000
        const val BATCH_ROUNDS = 10_000
        const val WARM_AVG_BUDGET_MS = 5.0
        const val WARM_MAX_BUDGET_MS = 50.0
    }

    /** 本名 + 字 + 号 + 封号 + 别称混合，覆盖归并索引各分支。 */
    private val tokens = listOf(
        "孔子", "仲尼", "孟子", "子舆", "屈原", "三闾大夫", "商鞅", "卫鞅",
        "张仪", "张子", "范雎", "张禄", "白起", "武安君", "项羽", "项籍",
        "刘邦", "沛公", "张良", "子房", "萧何", "淮阴侯", "韩王信", "陈平",
        "司马迁", "子长", "曹操", "孟德", "诸葛亮", "孔明", "周瑜", "公瑾",
        "李白", "太白", "青莲居士", "杜甫", "子美", "苏轼", "子瞻", "东坡居士",
    )

    private fun tripleQuery(token: String) {
        PersonProfile.profileOf(token)
        PersonProfile.canonical(token)
        PersonProfile.lifeSpan(token)
    }

    @Test
    fun `cold first query stays within budget`() {
        val start = System.nanoTime()
        tripleQuery("孔子")
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("冷路径首查应 ≤ ${WARM_MAX_BUDGET_MS}ms，实际 ${elapsedMs}ms", elapsedMs < WARM_MAX_BUDGET_MS)
    }

    @Test
    fun `warm repeated triple query within avg budget`() {
        repeat(WARM_ROUNDS) { tripleQuery(tokens[it % tokens.size]) }

        val samples = ArrayList<Double>(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) { i ->
            val start = System.nanoTime()
            tripleQuery(tokens[i % tokens.size])
            samples += (System.nanoTime() - start) / 1_000_000.0
        }
        val avgMs = samples.average()
        val maxMs = samples.max()

        assertTrue("热路径三连查均值应 ≤ ${WARM_AVG_BUDGET_MS}ms，实际均值 ${avgMs}ms，峰值 ${maxMs}ms", avgMs < WARM_AVG_BUDGET_MS)
        assertTrue("热路径单次峰值应 ≤ ${WARM_MAX_BUDGET_MS}ms，实际 ${maxMs}ms", maxMs < WARM_MAX_BUDGET_MS)
    }

    @Test
    fun `batch mixed lookup per call stays under budget`() {
        repeat(WARM_ROUNDS) { tripleQuery(tokens[it % tokens.size]) }

        val start = System.nanoTime()
        repeat(BATCH_ROUNDS) { i -> tripleQuery(tokens[i % tokens.size]) }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val perCallMs = totalMs / BATCH_ROUNDS

        assertTrue("批量单次三连查应 ≤ ${WARM_AVG_BUDGET_MS}ms，实际 ${perCallMs}ms（总 ${totalMs}ms）", perCallMs < WARM_AVG_BUDGET_MS)
    }

    @Test
    fun `same-name disambiguation lookup cost comparable to normal lookup`() {
        repeat(WARM_ROUNDS) { PersonProfile.candidatesOf("韩信") }

        val start = System.nanoTime()
        repeat(MEASURE_ROUNDS) {
            PersonProfile.profileOf("韩信")
            PersonProfile.candidatesOf("韩信")
            PersonProfile.profileOf("韩信", "seed:hanxin-hanwang")
        }
        val perCallMs = (System.nanoTime() - start) / 1_000_000.0 / MEASURE_ROUNDS

        assertTrue("同名消歧单次应 ≤ ${WARM_AVG_BUDGET_MS}ms，实际 ${perCallMs}ms", perCallMs < WARM_AVG_BUDGET_MS)
    }
}
