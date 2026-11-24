package io.legado.app.help.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 事件触发器单测（纯 JVM，B15 复审）。
 *
 * 把守：① 13 种补登 action 各一次可观察生效——断言触发次数（fired.size）与状态增量；
 * ② 显式 no-op 路径（全局无目标事件 / 未知 action）状态不变，事件仍登记；
 * ③ target 缺失 / 目标已阵亡不崩；④ 一次性语义：重复触发不叠加；
 * ⑤ value 非法数字 / 空白 / 越界回落默认值，不抛异常。
 */
class EventTriggerTest {

    /** 触发前快照：只取分支可能改写的运行态字段，便于逐字段比对增量。 */
    private data class Snap(
        val strength: Int,
        val morale: Morale,
        val alive: Boolean,
        val surrendered: Boolean,
        val intent: String,
        val intentActive: String,
        val supply: Double,
    )

    private fun snap(a: SimArmy) = Snap(
        strength = a.strength,
        morale = a.morale,
        alive = a.alive,
        surrendered = a.surrendered,
        intent = a.intent,
        intentActive = a.intentActive,
        supply = a.supply,
    )

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

    private fun fireOne(
        action: String,
        target: String? = "a1",
        value: String? = null,
        army: SimArmy? = null,
    ): List<FiredEvent> {
        val subject = army ?: army()
        return EventTrigger.fire(listOf(event(action, target, value)), listOf(subject), turn = 1)
    }

    // ── ① 13 种补登动作：触发次数 = 1，且有可观察状态增量 ──────────

    @Test
    fun `fireAttack 折损兵力并降一级士气`() {
        val a = army(strength = 100)
        val before = snap(a)
        val fired = fireOne("fireAttack", army = a)
        assertEquals(1, fired.size)
        assertEquals("fireAttack", fired.first().action)
        assertEquals("测试事件", fired.first().reason)
        assertTrue("火攻应造成折损：${a.strength}", a.strength in 1..before.strength - 1)
        assertEquals(Morale.WAVERING, a.morale)
        assertTrue(a.alive)
        assertEquals(before.supply, a.supply, 0.0)
    }

    @Test
    fun `siegeEngine 重挫兵力并直接击溃士气`() {
        val a = army(strength = 100)
        val before = snap(a)
        val fired = fireOne("siegeEngine", army = a)
        assertEquals(1, fired.size)
        assertEquals("siegeEngine", fired.first().action)
        assertTrue("攻城器械应造成重挫：${a.strength}", a.strength in 1..before.strength - 1)
        assertEquals(Morale.BROKEN, a.morale)
    }

    @Test
    fun `breakthrough 凿穿阵形并折损兵力`() {
        val a = army(strength = 100)
        val before = snap(a)
        val fired = fireOne("breakthrough", army = a)
        assertEquals(1, fired.size)
        assertEquals("breakthrough", fired.first().action)
        assertTrue("中央突破应造成折损：${a.strength}", a.strength in 1..before.strength - 1)
        assertEquals(Morale.WAVERING, a.morale)
    }

    @Test
    fun `navalRout 折损、击溃并转溃逃意图`() {
        val a = army(strength = 100)
        val before = snap(a)
        val fired = fireOne("navalRout", army = a)
        assertEquals(1, fired.size)
        assertEquals("navalRout", fired.first().action)
        assertTrue("舟师溃乱应折损：${a.strength}", a.strength in 1..before.strength - 1)
        assertEquals(Morale.BROKEN, a.morale)
        assertEquals("rout", a.intent)
        assertEquals("rout", a.intentActive)
    }

    @Test
    fun `defendFall 守军殉国：出局、兵力归零、非投降`() {
        val a = army()
        val fired = fireOne("defendFall", army = a)
        assertEquals(1, fired.size)
        assertEquals("defendFall", fired.first().action)
        assertFalse(a.alive)
        assertEquals(0, a.strength)
        assertEquals(Morale.BROKEN, a.morale)
        assertFalse("殉国不等于投降", a.surrendered)
    }

    @Test
    fun `feignedRetreat 诱敌转入追击并松动士气`() {
        val a = army()
        val fired = fireOne("feignedRetreat", army = a)
        assertEquals(1, fired.size)
        assertEquals("feignedRetreat", fired.first().action)
        assertEquals("advance", a.intent)
        assertEquals("advance", a.intentActive)
        assertEquals(Morale.WAVERING, a.morale)
    }

    @Test
    fun `ambush 转入设伏意图`() {
        val a = army()
        val fired = fireOne("ambush", army = a)
        assertEquals(1, fired.size)
        assertEquals("ambush", fired.first().action)
        assertEquals("ambush", a.intent)
        assertEquals("ambush", a.intentActive)
    }

    @Test
    fun `flank 转入侧翼包抄意图`() {
        val a = army()
        val fired = fireOne("flank", army = a)
        assertEquals(1, fired.size)
        assertEquals("flank", fired.first().action)
        assertEquals("flank", a.intent)
        assertEquals("flank", a.intentActive)
    }

    @Test
    fun `rout 击溃士气并转溃逃意图`() {
        val a = army()
        val fired = fireOne("rout", army = a)
        assertEquals(1, fired.size)
        assertEquals("rout", fired.first().action)
        assertEquals(Morale.BROKEN, a.morale)
        assertEquals("rout", a.intent)
        assertEquals("rout", a.intentActive)
    }

    @Test
    fun `capture 主帅被俘：出局并标记投降`() {
        val a = army()
        val fired = fireOne("capture", army = a)
        assertEquals(1, fired.size)
        assertEquals("capture", fired.first().action)
        assertFalse(a.alive)
        assertTrue(a.surrendered)
        assertEquals(0, a.strength)
    }

    @Test
    fun `annihilate 全军覆没：出局、兵力归零、击溃`() {
        val a = army()
        val fired = fireOne("annihilate", army = a)
        assertEquals(1, fired.size)
        assertEquals("annihilate", fired.first().action)
        assertFalse(a.alive)
        assertEquals(0, a.strength)
        assertEquals(Morale.BROKEN, a.morale)
        assertFalse("覆没不等于投降", a.surrendered)
    }

    @Test
    fun `retreat 转入撤退意图`() {
        val a = army()
        val fired = fireOne("retreat", army = a)
        assertEquals(1, fired.size)
        assertEquals("retreat", fired.first().action)
        assertEquals("retreat", a.intent)
        assertEquals("retreat", a.intentActive)
    }

    @Test
    fun `escape 转入突围意图`() {
        val a = army()
        val fired = fireOne("escape", army = a)
        assertEquals(1, fired.size)
        assertEquals("escape", fired.first().action)
        assertEquals("escape", a.intent)
        assertEquals("escape", a.intentActive)
    }

    // ── ② 显式 no-op：全局事件 / 未知 action——状态不变，事件仍登记 ──

    @Test
    fun `全局事件无目标军：不改动任何军队但照常登记`() {
        val a = army(strength = 100)
        val before = snap(a)
        val fired = EventTrigger.fire(listOf(event("ambush", target = null)), listOf(a), turn = 1)
        assertEquals(1, fired.size)
        assertEquals("ambush", fired.first().action)
        assertEquals(before, snap(a))
        assertTrue("全局事件不应产生告警：${EventTrigger.warnings}", EventTrigger.warnings.isEmpty())
    }

    @Test
    fun `未知 action 记告警、状态不变且 FiredEvent 照常登记`() {
        val a = army(strength = 100)
        val before = snap(a)
        val fired = EventTrigger.fire(listOf(event("nuke-weapon")), listOf(a), turn = 1)
        assertEquals(1, fired.size)
        assertEquals("nuke-weapon", fired.first().action)
        assertEquals(before, snap(a))
        assertTrue(
            "告警台账应记录未知动作：${EventTrigger.warnings}",
            EventTrigger.warnings.any { it.contains("nuke-weapon") },
        )
    }

    // ── ③ target 缺失 / 已阵亡：不崩，记告警，目标军不受影响 ──────

    @Test
    fun `target 不存在时不崩溃且军队状态不变`() {
        val a = army(strength = 100)
        val before = snap(a)
        val fired = EventTrigger.fire(listOf(event("fireAttack", target = "ghost")), listOf(a), turn = 1)
        assertEquals(1, fired.size)
        assertEquals(before, snap(a))
        assertTrue(EventTrigger.warnings.any { it.contains("ghost") })
    }

    @Test
    fun `目标已阵亡时动作不重复结算`() {
        val a = army()
        a.alive = false
        val before = snap(a)
        val fired = EventTrigger.fire(listOf(event("rout")), listOf(a), turn = 1)
        assertEquals(1, fired.size)
        assertEquals(before, snap(a))
        assertTrue(EventTrigger.warnings.any { it.contains("已阵亡") || it.contains("出局") })
    }

    // ── ④ 一次性语义：重复触发不叠加 ──────────────────────────────

    @Test
    fun `同一事件第二次触发不再叠加折损`() {
        val a = army(strength = 100)
        val spec = event("fireAttack")
        val first = EventTrigger.fire(listOf(spec), listOf(a), turn = 1)
        assertEquals(1, first.size)
        val strengthAfterFirst = a.strength
        val second = EventTrigger.fire(listOf(spec), listOf(a), turn = 2)
        assertTrue("第二次应被 done 标记跳过", second.isEmpty())
        assertEquals("重复触发不得二次扣兵", strengthAfterFirst, a.strength)
    }

    @Test
    fun `空事件列表与 null 列表均返回空且不抛异常`() {
        val a = army()
        assertTrue(EventTrigger.fire(emptyList(), listOf(a), turn = 1).isEmpty())
        assertTrue(EventTrigger.fire(null, listOf(a), turn = 1).isEmpty())
    }

    // ── ⑤ value 边界：非法数字 / 空白 / 越界回落默认比例 ──────────

    @Test
    fun `value 非法数字按默认折损处理且不抛异常`() {
        val a = army(strength = 100)
        val fired = fireOne("fireAttack", value = "not-a-number", army = a)
        assertEquals(1, fired.size)
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
