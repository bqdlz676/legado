package io.legado.app.help.allusion

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块 D4 · 典故查询性能 bench（规格切片-02 §5：气泡冷启动首触 ≤ 300ms，之后 ≤ 80ms）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * - 冷路径：索引构建后的第一次命中；
 * - 热路径：同一批词反复查询，走 HashMap 精确命中；
 * - 本 bench 在宿主 JVM 上跑，只验证查找路径不超时；荣耀 WIN 真机上的耗时
 *   仍需真机复测，未在本机验证的部分不计入通过结论；
 * - 预算取宽松上限：单次 find 热路径均值 ≤ 2ms、峰值 ≤ 20ms。
 */
class AllusionDictBenchTest {

    private companion object {
        const val WARM_ROUNDS = 100
        const val MEASURE_ROUNDS = 2000
        const val BATCH_ROUNDS = 10_000
        const val WARM_AVG_BUDGET_MS = 2.0
        const val WARM_MAX_BUDGET_MS = 20.0
        const val COLD_MAX_BUDGET_MS = 50.0
    }

    /** 命中词：覆盖两字 / 三字 / 四字典故。 */
    private val hits = listOf(
        "破釜沉舟", "四面楚歌", "鸿门宴", "卧薪尝胆", "完璧归赵", "负荆请罪",
        "守株待兔", "狐假虎威", "画蛇添足", "退避三舍", "唇亡齿寒", "老马识途",
        "滥竽充数", "自相矛盾", "揠苗助长", "缘木求鱼", "井底之蛙", "鹏程万里",
        "刻舟求剑", "掩耳盗铃", "愚公移山", "精卫填海", "举一反三", "温故知新",
        "三顾茅庐", "乐不思蜀", "闻鸡起舞", "望梅止渴", "青梅竹马", "乘风破浪",
    )

    /** 未命中词：单字、普通词、超长串，覆盖归一化与空命中分支。 */
    private val misses = listOf(
        "抽", "人", "山", "桌子", "吃饭", "现代", "破釜沉舟之计", "有志者事竟成",
    )

    private fun lookup(token: String) {
        AllusionDict.find(token)
    }

    @Test
    fun `cold first query stays within budget`() {
        val start = System.nanoTime()
        lookup("破釜沉舟")
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("冷路径首查应 ≤ ${COLD_MAX_BUDGET_MS}ms，实际 ${elapsedMs}ms", elapsedMs < COLD_MAX_BUDGET_MS)
    }

    @Test
    fun `warm repeated lookup within avg budget`() {
        repeat(WARM_ROUNDS) { lookup(hits[it % hits.size]) }

        val samples = ArrayList<Double>(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) { i ->
            val token = if (i % 2 == 0) hits[i % hits.size] else misses[i % misses.size]
            val start = System.nanoTime()
            lookup(token)
            samples += (System.nanoTime() - start) / 1_000_000.0
        }
        val avgMs = samples.average()
        val maxMs = samples.max()

        assertTrue("热路径单次均值应 ≤ ${WARM_AVG_BUDGET_MS}ms，实际均值 ${avgMs}ms，峰值 ${maxMs}ms", avgMs < WARM_AVG_BUDGET_MS)
        assertTrue("热路径单次峰值应 ≤ ${WARM_MAX_BUDGET_MS}ms，实际 ${maxMs}ms", maxMs < WARM_MAX_BUDGET_MS)
    }

    @Test
    fun `batch mixed hit and miss per call stays under budget`() {
        repeat(WARM_ROUNDS) { lookup(hits[it % hits.size]) }

        val start = System.nanoTime()
        repeat(BATCH_ROUNDS) { i ->
            val token = if (i % 3 == 0) misses[i % misses.size] else hits[i % hits.size]
            lookup(token)
        }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val perCallMs = totalMs / BATCH_ROUNDS

        assertTrue("批量单次查找应 ≤ ${WARM_AVG_BUDGET_MS}ms，实际 ${perCallMs}ms（总 ${totalMs}ms）", perCallMs < WARM_AVG_BUDGET_MS)
    }

    @Test
    fun `seed table size kept within offline bundle budget`() {
        // 离线包口径：种子宁精勿滥；规模断言写死，防止后续无出处灌量把包体撑爆。
        val count = AllusionDict.count()
        assertTrue("种子条目数应在 50~200 之间，实际 $count", count in 50..200)
    }
}
