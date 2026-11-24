package io.legado.app.help.bubble.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-05 点晋纪年时空联动 · 纪年过滤纯函数验收（纯 JVM，只用 org.junit + java.*，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖锚点卡 §三判据 1-4 与本单六项断言：
 *  ①《左传》锚 -722~-453：春秋晋国入选、唐以后的晋被排掉且春秋晋国排第一；
 *  ② year=null 数量不变、按 tier 降序（同 tier 按名升序）；
 *  ③ start / end 为空视为该侧不设限；
 *  ④ 全被过滤返回空列表，不抛异常；
 *  ⑤ N=3 文案「还有 3 个同名」，N=0 为空串；
 *  ⑥ 输入空列表返回空列表。
 */
class EraCandidateFilterTest {

    /** 构造种子候选：除主键 / 名 / 分层 / 年代区间外，其余字段按契约默认值补齐。 */
    private fun seed(
        id: String,
        name: String,
        tier: String,
        start: Int?,
        end: Int?,
    ): Candidate = Candidate(
        id = id,
        name = name,
        type = CandidateType.PLACE,
        tier = tier,
        start = start,
        end = end,
        aliases = emptyList(),
        confidence = Confidence.MEDIUM,
        source = "单测种子",
        isDefault = false,
    )

    @Test
    fun `zuozhuan anchor negative 600 keeps spring autumn jin first and drops tang later jin`() {
        // 切片-05:3：读《左传》锚到 -722~-453，命中「春秋晋国」，把「唐以后的晋」全排掉。
        val springJin = seed("place-jin-spring", "春秋晋国", "L1", -722, -453)
        val tangJin = seed("place-jin-tang", "唐以后的晋", "L1", 618, 907)

        val result = filterByEra(listOf(tangJin, springJin), -600)

        assertEquals("春秋晋国必须入选", 1, result.size)
        assertEquals("春秋晋国排第一", "春秋晋国", result.first().name)
        assertFalse("唐以后的晋必须被排掉", result.any { it.id == tangJin.id })
    }

    @Test
    fun `null chapter year keeps all candidates and orders by tier then name`() {
        // 锚点卡 §二：年份缺失不做纪年过滤，全候选按 tier 排序回；同 tier 按名升序。
        val tierLow = seed("id-l3", "乙候选", "L3", 618, 907)
        val tierTop = seed("id-l1", "甲候选", "L1", -722, -453)
        val tierMid = seed("id-l2", "丙候选", "L2", -403, -222)
        val input = listOf(tierLow, tierMid, tierTop)

        val result = filterByEra(input, null)

        assertEquals("year 为 null 时候选数量不变", input.size, result.size)
        assertEquals("L1 手校核心优先", "甲候选", result[0].name)
        assertEquals("L2 多源核验次之", "丙候选", result[1].name)
        assertEquals("L3 孤证存疑垫底", "乙候选", result[2].name)
    }

    @Test
    fun `null start or end treats that side as unbounded`() {
        // 区间任一端为空视为不设限：start 缺只看 end，end 缺只看 start，双空视为全时段。
        val bothOpen = seed("id-both-open", "双端不设限", "L1", null, null)
        val endOpen = seed("id-end-open", "起限讫不限", "L1", -400, null)
        val startOpen = seed("id-start-open", "起不限讫限", "L1", null, -200)

        val result = filterByEra(listOf(bothOpen, endOpen, startOpen), 1000)
        val keptIds = result.map { it.id }

        assertTrue("双端为空必须视为全时段命中", keptIds.contains(bothOpen.id))
        assertTrue("end 为空必须视为讫侧不设限", keptIds.contains(endOpen.id))
        assertFalse("start 为空但 end 已过期必须出局", keptIds.contains(startOpen.id))
    }

    @Test
    fun `all candidates out of era returns empty list without throwing`() {
        // 锚点卡 §三判据 4：任何情况下不返回 null、不抛异常；全过滤回空列表。
        val tangOne = seed("id-tang-1", "唐以后的晋", "L2", 618, 907)
        val tangTwo = seed("id-tang-2", "晋地州府", "L3", 937, 1279)

        val result = filterByEra(listOf(tangOne, tangTwo), -500)

        assertTrue("全被过滤时返回空列表", result.isEmpty())
    }

    @Test
    fun `same name label interpolates passed count and blanks non positive`() {
        // 锚点卡 §三判据 3：N 必须来自入参，禁止写死；N<=0 返回空串。
        assertEquals("N 为 3 时文案带实际数量", "还有 3 个同名", sameNameLabel(3))
        assertEquals("N 为 0 时返回空串", "", sameNameLabel(0))
        assertEquals("N 为负时同样返回空串", "", sameNameLabel(-1))
    }

    @Test
    fun `empty candidate list returns empty list for anchored and null year`() {
        val anchored = filterByEra(emptyList(), -600)
        val missingYear = filterByEra(emptyList(), null)

        assertTrue("锚定年份下空输入返回空列表", anchored.isEmpty())
        assertTrue("年份缺失下空输入返回空列表", missingYear.isEmpty())
    }
}
