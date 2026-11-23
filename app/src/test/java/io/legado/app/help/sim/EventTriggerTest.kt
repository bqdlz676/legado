package io.legado.app.help.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 事件触发器单测（纯 JVM，B15）。
 *
 * 把守：① 13 种 action 各有一次可观察生效；② 未知 action 记告警且不静默吞掉；
 * ③ target 缺失 / 目标已阵亡不崩；④ 一次性语义：重复触发不叠加；
 * ⑤ value 非法数字 / 空白回落默认值，不抛异常。
 */
class EventTriggerTest {

    private fun army(
        id: String = "a1",
        strength: Int = 100,
        morale: Morale = Morale.STEADY,
        intent: String = "hold",
    ) = SimArmy(
        ArmySpec(
            id = id,
            name = id,
            side = "red",
            initialPos = listOf(0.0, 0.0),
            strength = strength,
            type = "regular",
            morale = morale.label,
            intent = intent,
        ),
    )

    private fun event(
        action: String,
        target: String? = "a1",
        value: String? = null,
        reason: String = "测试事件",
    ) = EventSpec(
        trigger = TriggerSpec(turn = 1),
        action = action,
        target = target,
        value = value,
        reason = reason,
    )

    private fun fireOne(action: String, target: String? = "a1", value: String? = null, army: SimArmy? = null) {
        val subject = army ?: army()
        EventTrigger.fire(listOf(event(action, target, value)), listOf(subject), turn = 1)
    }

    // ── ① 13 种动作各一次生效 ────────────────────────────────────

    @Test
    fun `fireAttack 折损兵力并降一级士气`() {
        val a = army(strength = 100)
        fireOne("fireAttack", army = a)
        assertTrue("火攻应造成折损：${a.strength}", a.strength in 1..99)
        assertEquals(Morale.WAVERING, a.morale)
        assertTrue(a.alive)
    }

    @Test
    fun `siegeEngine 重挫兵力并直接击溃士气`() {
        val a = army(strength = 100)
        fireOne("siegeEngine", army = a)
        assertTrue("攻城器械应造成重挫：${a.strength}", a.strength in 1..80)
        assertEquals(Morale.BROKEN, a.morale)
    }

    @Test
    fun `breakthrough 凿穿阵形并折损兵力`() {
        val a = army(strength = 100)
        fireOne("breakthrough", army = a)
        assertTrue("中央突破应造成折损：${a.strength}", a.strength in 1..99)
        assertEquals(Morale.WAVERING, a.morale)
    }

    @Test
    fun `navalRout 折损、击溃并转溃逃意图`() {
        val a = army(strength = 100)
        fireOne("navalRout", army = a)
        assertTrue("舟师溃乱应折损：${a.strength}", a.strength in 1..99)
        assertEquals(Morale.BROKEN, a.morale)
        assertEquals("rout", a.intent)
        assertEquals("rout", a.intentActive)
    }

    @Test
    fun `defendFall 守军殉国：出局、兵力归零、非投降`() {
        val a = army()
        fireOne("defendFall", army = a)
        assertFalse(a.alive)
        assertEquals(0, a.strength)
        assertEquals(Morale.BROKEN, a.morale)
        assertFalse("殉国不等于投降", a.surrendered)
    }

    @Test
    fun `feignedRetreat 诱敌转入追击并松动士气`() {
        val a = army()
        fireOne("feignedRetreat", army = a)
        assertEquals("advance", a.intent)
        assertEquals(Morale.WAVERING, a.morale)
    }

    @Test
    fun `ambush 转入设伏意图`() {
        val a = army()
        fireOne("ambush", army = a)
        assertEquals("ambush", a.intent)
        assertEquals("ambush", a.intentActive)
    }

    @Test
    fun `flank 转入侧翼包抄意图`() {
        val a = army()
        fireOne("flank", army = a)
        assertEquals("flank", a.intent)
        assertEquals("flank", a.intentActive)
    }

    @Test
    fun `rout 击溃士气并转溃逃意图`() {
        val a = army()
        fireOne("rout", army = a)
        assertEquals(Morale.BROKEN, a.morale)
        assertEquals("rout", a.intent)
        assertEquals("rout", a.intentActive)
    }

    @Test
    fun `capture 主帅被俘：出局并标记投降`() {
        val a = army()
        fireOne("capture", army = a)
        assertFalse(a.alive)
        assertTrue(a.surrendered)
        assertEquals(0, a.strength)
    }

    @Test
    fun `annihilate 全军覆没：出局、兵力归零、击溃`() {
        val a = army()
        fireOne("annihilate", army = a)
        assertFalse(a.alive)
        assertEquals(0, a.strength)
        assertEquals(Morale.BROKEN, a.morale)
        assertFalse("覆没不等于投降", a.surrendered)
    }

    @Test
    fun `retreat 转入撤退意图`() {
        val a = army()
        fireOne("retreat", army = a)
        assertEquals("retreat", a.intent)
        assertEquals("retreat", a.intentActive)
    }

    @Test
    fun `escape 转入突围意图`() {
        val a = army()
        fireOne("escape", army = a)
        assertEquals("escape", a.intent)
        assertEquals("escape", a.intentActive)
    }

    // ── ② 未知 action：告警 + 照常登记事件，不静默吞掉 ─────────────

    @Test
    fun `未知 action 记告警且 FiredEvent 照常登记`() {
        val a = army()
        val fired = EventTrigger.fire(listOf(event("nuke-weapon")), listOf(a), turn = 1)
        assertEquals(1, fired.size)
        assertEquals("nuke-weapon", fired.first().action)
        assertTrue(
            "告警台账应记录未知动作：${EventTrigger.warnings}",
            EventTrigger.warnings.any { it.contains("nuke-weapon") },
        )
    }

    // ── ③ target 缺失 / 已阵亡：不崩，记告警，目标军不受影响 ──────

    @Test
    fun `target 不存在时不崩溃且军队状态不变`() {
        val a = army(strength = 100)
        val fired = EventTrigger.fire(listOf(event("fireAttack", target = "ghost")), listOf(a), turn = 1)
        assertEquals(1, fired.size)
        assertEquals(100, a.strength)
        assertEquals(Morale.STEADY, a.morale)
        assertTrue(EventTrigger.warnings.any { it.contains("ghost") })
    }

    @Test
    fun `目标已阵亡时动作不重复结算`() {
        val a = army()
        a.alive = false
        val before = a.strength
        EventTrigger.fire(listOf(event("rout")), listOf(a), turn = 1)
        assertEquals(before, a.strength)
        assertTrue(EventTrigger.warnings.any { it.contains("已阵亡") || it.contains("出局") })
    }

    // ── ④ 一次性语义：重复触发不叠加 ──────────────────────────────

    @Test
    fun `同一事件第二次触发不再叠加折损`() {
        val a = army(strength = 100)
        val spec = event("fireAttack")
        val first = EventTrigger.fire(listOf(spec), listOf(a), turn = 1)
        val strengthAfterFirst = a.strength
        assertEquals(1, first.size)
        val second = EventTrigger.fire(listOf(spec), listOf(a), turn = 2)
        assertTrue("第二次应被 done 标记跳过", second.isEmpty())
        assertEquals("重复触发不得二次扣兵", strengthAfterFirst, a.strength)
    }

    // ── ⑤ value 边界：非法数字 / 空白回落默认比例 ─────────────────

    @Test
    fun `value 非法数字按默认折损处理且不抛异常`() {
        val a = army(strength = 100)
        fireOne("fireAttack", value = "not-a-number", army = a)
        assertTrue(a.strength in 1..99)
    }

    @Test
    fun `value 空白按默认折损处理`() {
        val a = army(strength = 100)
        fireOne("siegeEngine", value = "", army = a)
        assertEquals(Morale.BROKEN, a.morale)
    }

    @Test
    fun `折损比例越界钳制在 0 到 1 之间`() {
        val a = army(strength = 100)
        fireOne("fireAttack", value = "5.0", army = a)
        assertTrue("比例 5.0 应钳制为 1.0，兵力不为负", a.strength >= 0)
        assertNotNull(a)
    }
}
