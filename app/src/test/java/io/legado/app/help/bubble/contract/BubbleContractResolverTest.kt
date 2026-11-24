package io.legado.app.help.bubble.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-02 气泡协议 · resolve 出口兜底骨架验收（纯 JVM，不依赖 Android / Compose / 资源）。
 *
 * 覆盖四条边界（任务书 §4）：① 空输入；② 未命中；③ 年份缺失；④ 正常命中。
 * 兜底三态断言返回非 null、字段结构齐全、confidence=low、source=未命中、isDefault=false；
 * 命中态断言注册表候选原样回传、不被兜底条替换。
 */
class BubbleContractResolverTest {

    /** 命中用种子候选：春秋晋国（place，L1，high，可进默认命中）。 */
    private fun seedJin() = Candidate(
        id = "place-jin",
        name = "晋",
        type = CandidateType.PLACE,
        tier = "L1",
        start = -1033,
        end = -376,
        aliases = listOf("晋国"),
        confidence = Confidence.HIGH,
        source = "公版《史记》",
        isDefault = true,
    )

    /** 兜底条结构断言：十条字段全部就位，置信 / 来源 / 默认位按未命中口径取值。 */
    private fun assertMissStructure(c: Candidate, expectedName: String) {
        assertNotNull("兜底候选不得为 null", c)
        assertNotNull("id 字段必须就位（可为空串）", c.id)
        assertEquals("兜底候选名回传可查 token", expectedName, c.name)
        assertNotNull("type 字段必须就位", c.type)
        assertNotNull("tier 字段必须就位", c.tier)
        assertNotNull("aliases 字段必须就位（可为空列表）", c.aliases)
        assertEquals("未命中置信度必须为 low", Confidence.LOW, c.confidence)
        assertEquals("未命中 source 必须为「未命中」", MISSING_SOURCE, c.source)
        assertFalse("low 置信兜底条不得进默认命中", c.isDefault)
    }

    @Test
    fun `empty input returns one complete miss candidate`() {
        val resolver = BubbleContractResolver(emptyList())

        val byBlank = resolver.resolve("   ", null)
        val byEmpty = resolver.resolve("", -700)

        assertNotNull("空输入返回列表不得为 null", byBlank)
        assertEquals("空输入只回一条兜底候选", 1, byBlank.size)
        assertMissStructure(byBlank.first(), "未识")
        assertNotNull("空串入参同样兜底", byEmpty)
        assertEquals(1, byEmpty.size)
        assertMissStructure(byEmpty.first(), "未识")
    }

    @Test
    fun `unknown name returns complete miss candidate instead of null`() {
        val resolver = BubbleContractResolver(emptyList())

        val result = resolver.resolve("魑魅魍魉", -700)

        assertNotNull("未命中返回列表不得为 null", result)
        assertEquals("未命中只回一条兜底候选", 1, result.size)
        assertMissStructure(result.first(), "魑魅魍魉")
    }

    @Test
    fun `missing year still returns structurally complete record without guessing`() {
        // 注册表为空 + 年份缺失：走未命中兜底，不抛异常、不留空态。
        val emptyResolver = BubbleContractResolver(emptyList())

        val result = emptyResolver.resolve("甲子", null)

        assertNotNull("年份缺失返回列表不得为 null", result)
        assertEquals(1, result.size)
        assertMissStructure(result.first(), "甲子")

        // 年份缺失且有种子候选：锚点年为空不猜归属，不做纪年过滤，全候选原样回。
        val seeded = BubbleContractResolver(listOf(seedJin()))
        val passthrough = seeded.resolve("晋", null)

        assertNotNull("年份缺失命中候选不得为 null", passthrough)
        assertEquals(1, passthrough.size)
        assertEquals("晋", passthrough.first().name)
    }

    @Test
    fun `seeded name hits registered candidate with full fields`() {
        val resolver = BubbleContractResolver(listOf(seedJin()))

        val byName = resolver.resolve("晋", -700)
        val byAlias = resolver.resolve("晋国", -700)

        assertNotNull("命中返回列表不得为 null", byName)
        assertEquals(1, byName.size)
        val hit = byName.first()
        assertEquals("place-jin", hit.id)
        assertEquals("晋", hit.name)
        assertEquals(CandidateType.PLACE, hit.type)
        assertEquals("L1", hit.tier)
        assertEquals(-1033, hit.start)
        assertEquals(-376, hit.end)
        assertEquals(listOf("晋国"), hit.aliases)
        assertEquals(Confidence.HIGH, hit.confidence)
        assertEquals("公版《史记》", hit.source)
        assertTrue("high 置信候选可进默认命中", hit.isDefault)

        assertNotNull("别名同样可命中", byAlias)
        assertEquals(1, byAlias.size)
        assertEquals("晋", byAlias.first().name)
    }
}
