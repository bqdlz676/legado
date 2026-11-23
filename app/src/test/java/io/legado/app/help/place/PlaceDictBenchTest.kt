package io.legado.app.help.place

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块 D3 · 地名查询性能 bench（规格切片-02 §5：气泡冷启动首触 ≤ 300ms，之后 ≤ 80ms）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * - 冷路径：首次查询触发索引构建后的第一次命中；
 * - 热路径：同一批地名反复查询，走 HashMap 命中；
 * - 本 bench 在宿主 JVM 上跑，只验证归并 / 消歧路径不超时；荣耀 WIN 真机上的耗时
 *   仍需真机复测，未在本机验证的部分不计入通过结论。
 * - 预算取宽松上限：单次「find + geoOf + defaultOf」三连查热路径均值 ≤ 5ms。
 */
class PlaceDictBenchTest {

    private companion object {
        const val WARM_ROUNDS = 100
        const val MEASURE_ROUNDS = 2000
        const val BATCH_ROUNDS = 10_000
        const val WARM_AVG_BUDGET_MS = 5.0
        const val WARM_MAX_BUDGET_MS = 50.0
    }

    /** 覆盖诸侯国 / 都邑 / 县色块 / 今址参考层 / 名称快照各分支。 */
    private val tokens = listOf(
        "晋", "绛", "新田", "晋国", "临淄", "曲阜", "郑韩故城", "长安", "成周",
        "雒阳", "咸阳", "邯郸", "大梁", "郢", "成都", "会稽", "蓟", "商丘",
        "宛", "晋阳", "长勺", "鹿上", "晋州", "晋城",
    )

    private fun tripleQuery(token: String) {
        val candidates = PlaceDict.find(token)
        val hit = PlaceDict.defaultOf(candidates)
        if (hit != null) PlaceDict.geoOf(hit.placeId)
    }

    @Test
    fun `cold first query stays within budget`() {
        val start = System.nanoTime()
        tripleQuery("晋")
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
    fun `anchored disambiguation lookup cost comparable to normal lookup`() {
        repeat(WARM_ROUNDS) { PlaceDict.find("晋", -722..-453) }

        val start = System.nanoTime()
        repeat(MEASURE_ROUNDS) {
            PlaceDict.find("晋")
            PlaceDict.find("晋", -722..-453)
            PlaceDict.find("晋", year = -600)
        }
        val perCallMs = (System.nanoTime() - start) / 1_000_000.0 / MEASURE_ROUNDS

        assertTrue("带年代锚点消歧单次应 ≤ ${WARM_AVG_BUDGET_MS}ms，实际 ${perCallMs}ms", perCallMs < WARM_AVG_BUDGET_MS)
    }
}
