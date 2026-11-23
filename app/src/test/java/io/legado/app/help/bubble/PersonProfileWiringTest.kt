package io.legado.app.help.bubble

import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.person.ConfidenceTier
import io.legado.app.help.person.PersonDirectory
import io.legado.app.help.person.PersonProfile
import io.legado.app.help.person.PersonSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * D2 人物模块观察项 O2 · 下游消费接线验收（气泡 / 人物卡）。
 *
 * 覆盖四条接线：
 * 1. 人名异读命中：气泡 phonetic 取档案专用读音门面；
 * 2. 回落通用读音：专用读音未命中时回落注入的通用字库 supplier，supplier 异常不抛；
 * 3. 在位起讫展示：reignOf 门面真实取值，双载 / 缺位 / 双无载三种文案；
 * 4. L3 孤证不进默认命中，但仍保留在同名候选清单。
 *
 * 本类为宿主 JVM 纯逻辑测试，不依赖 android.Context；通用字库读音以桩件 supplier 注入。
 */
class PersonProfileWiringTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
    }

    private fun profile(
        id: String,
        name: String,
        tier: ConfidenceTier = ConfidenceTier.L1,
        birth: Int? = null,
        death: Int? = null,
        pinyin: String? = null,
        reignStart: Int? = null,
        reignEnd: Int? = null,
        identity: String = "接线测试",
    ) = PersonProfile(
        personId = id,
        name = name,
        nationality = "唐",
        identity = identity,
        biography = "x",
        notableEvent = "x",
        source = PersonSource.Classic("《史记·接线测试列传》"),
        birthYear = birth,
        deathYear = death,
        tier = tier,
        pinyin = pinyin,
        reignStart = reignStart,
        reignEnd = reignEnd,
    )

    private fun fmt(y: Int): String = if (y <= 0) "前${1 - y}" else "$y"

    // ---------- 1. 人名异读命中 ----------

    @Test
    fun `provider bubble phonetic carries dedicated person reading`() {
        PersonDirectory.register(listOf(profile(
            id = "wiring:yidu",
            name = "接线异读客",
            pinyin = "jiē xiàn kè",
        )))

        val bubble = PersonProvider.query("接线异读客", ContextAnchor)
        assertNotNull(bubble)
        assertEquals("jiē xiàn kè", bubble!!.phonetic)
    }

    @Test
    fun `provider bubble phonetic stays null when facade has no reading`() {
        PersonDirectory.register(listOf(profile(id = "wiring:wuyin", name = "接线无音客")))

        val bubble = PersonProvider.query("接线无音客", ContextAnchor)
        assertNotNull(bubble)
        assertNull(bubble!!.phonetic)
    }

    // ---------- 2. 回落通用读音 ----------

    @Test
    fun `wiring pinyin falls back to generic supplier when facade misses`() {
        val py = PersonProfileWiring.pinyin("查无此名") { "tōng yòng" }
        assertEquals("tōng yòng", py)
    }

    @Test
    fun `wiring pinyin prefers dedicated reading over generic supplier`() {
        PersonDirectory.register(listOf(profile(
            id = "wiring:youxian",
            name = "接线优先客",
            pinyin = "zhuān yòng",
        )))

        val py = PersonProfileWiring.pinyin("接线优先客") { "gōng yòng" }
        assertEquals("zhuān yòng", py)
    }

    @Test
    fun `wiring pinyin returns null when generic supplier throws`() {
        val py = PersonProfileWiring.pinyin("查无此名") { throw IllegalStateException("boom") }
        assertNull(py)
    }

    @Test
    fun `wiring pinyin ignores blank dedicated and generic readings`() {
        val py = PersonProfileWiring.pinyin("查无此名") { "   " }
        assertNull(py)
    }

    // ---------- 3. 在位起讫展示 ----------

    @Test
    fun `reign label renders full reign range`() {
        val label = PersonProfileWiring.reignLabel(-636, -628) { fmt(it) }
        assertEquals("在位　前636 — 前628", label)
    }

    @Test
    fun `reign label marks missing side with question mark`() {
        assertEquals("在位　前636 — ?", PersonProfileWiring.reignLabel(-636, null) { fmt(it) })
        assertEquals("在位　? — 前628", PersonProfileWiring.reignLabel(null, -628) { fmt(it) })
    }

    @Test
    fun `reign label says unrecorded when both sides null`() {
        assertEquals("在位无载", PersonProfileWiring.reignLabel(null, null) { fmt(it) })
    }

    @Test
    fun `reign facade feeds label with registered reign years`() {
        PersonDirectory.register(listOf(profile(
            id = "wiring:junzhu",
            name = "接线君主",
            reignStart = -636,
            reignEnd = -628,
        )))

        val reign = PersonProfile.reignOf("接线君主")
        assertNotNull(reign)
        val label = PersonProfileWiring.reignLabel(reign!!.first, reign.second) { fmt(it) }
        assertEquals("在位　前636 — 前628", label)
    }

    // ---------- 4. L3 孤证不进默认命中 ----------

    @Test
    fun `L3 solo evidence does not default hit but stays in candidates`() {
        val l2 = profile(
            id = "wiring:l2",
            name = "接线同名",
            tier = ConfidenceTier.L2,
            birth = 700,
            death = 750,
        )
        val l3 = profile(
            id = "wiring:l3",
            name = "接线同名",
            tier = ConfidenceTier.L3,
            identity = "孤证存疑",
        )
        // 先注册孤证，再注册核验项；默认命中仍须是 L2。
        PersonDirectory.register(listOf(l3, l2))

        val bubble = PersonProvider.query("接线同名", ContextAnchor)
        assertNotNull(bubble)
        val open = bubble!!.actions.filterIsInstance<Action.OpenPerson>().single()
        assertEquals("wiring:l2", open.personId)

        // 孤证不丢，仍进同名候选清单。
        assertEquals(1, bubble.candidates.size)
        assertTrue(bubble.candidates.single().display.contains("孤证存疑"))
    }

    @Test
    fun `when all candidates are L3 fallback pool keeps solo evidence reachable`() {
        val l3a = profile(id = "wiring:l3a", name = "接线全孤证", tier = ConfidenceTier.L3)
        val l3b = profile(id = "wiring:l3b", name = "接线全孤证", tier = ConfidenceTier.L3)
        PersonDirectory.register(listOf(l3a, l3b))

        val bubble = PersonProvider.query("接线全孤证", ContextAnchor)
        assertNotNull(bubble)
        // 全为孤证时回退孤证池，不返回空气泡；另一位仍进候选。
        // 注释说明：assertNotNull 是普通调用不触发 Kotlin 智能转换，这里必须用 !! 才能访问成员（否则 compileAppDebugUnitTestKotlin 直接报可空接收者）。
        assertEquals(1, bubble!!.candidates.size)
    }
}
