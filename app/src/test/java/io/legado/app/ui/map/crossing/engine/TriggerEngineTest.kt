package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.EventAction
import io.legado.app.ui.map.crossing.model.EventDef
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.model.TriggerDef
import io.legado.app.ui.map.crossing.model.ArmyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 条件触发器单测（纯 JVM）。
 * 覆盖：回合触发与一次性语义、兵力阈值、士气命中、军队绑定、AND 组合、全局事件。
 */
class TriggerEngineTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val gridN = 32

    private fun corps(
        id: String,
        side: String = "red",
        strength: Double = 5.0,
        morale: Morale = Morale.STEADY,
    ) = Corps(
        id = id,
        name = id,
        sideId = side,
        role = FactionRole.OWN,
        type = ArmyType.REGULAR,
        strength = strength,
        fatigue = 0.0,
        morale = morale,
        intent = Intent.HOLD,
        position = Cell(16, 16),
        latLng = Geo(0.0, 0.0),
        target = null,
        supply = 5,
        supplyCutTurns = 0,
        alive = true,
        surrendered = false,
        exposed = false,
        highGround = false,
        route = emptyList(),
        lastKnown = emptyMap(),
        visionRadiusCells = 4,
    )

    private fun event(
        trigger: TriggerDef,
        action: EventAction = EventAction.MORALE_CHANGE,
        target: String? = "a",
        value: String? = "wavering",
    ) = EventDef(trigger, action, target, value, null, "测试事件")

    @Test
    fun `回合触发到达回合才点燃且只触发一次`() {
        val events = listOf(event(TriggerDef(turn = 3, null, null, null, null)))
        val army = listOf(corps("a"))

        assertFalse(TriggerEngine.evaluate(events, army, turn = 2, fired = emptySet(), focus, gridN).isNotEmpty())
        val firedFirst = TriggerEngine.evaluate(events, army, turn = 3, fired = emptySet(), focus, gridN)
        assertEquals(1, firedFirst.size)
        // 已触发下标再次求值不得重复点燃。
        val firedAgain = TriggerEngine.evaluate(events, army, turn = 4, fired = setOf(firedFirst.first().index), focus, gridN)
        assertTrue(firedAgain.isEmpty())
    }

    @Test
    fun `兵力阈值低于才触发，高于不触发`() {
        val events = listOf(event(TriggerDef(null, "a", 3.0, null, null)))
        val strong = listOf(corps("a", strength = 5.0))
        assertTrue(TriggerEngine.evaluate(events, strong, 1, emptySet(), focus, gridN).isEmpty())

        val weak = listOf(corps("a", strength = 2.0))
        assertEquals(1, TriggerEngine.evaluate(events, weak, 1, emptySet(), focus, gridN).size)
    }

    @Test
    fun `士气标签命中才触发`() {
        val events = listOf(event(TriggerDef(null, "a", null, "wavering", null)))
        val wavering = listOf(corps("a", morale = Morale.WAVERING))
        assertEquals(1, TriggerEngine.evaluate(events, wavering, 1, emptySet(), focus, gridN).size)

        val steady = listOf(corps("a", morale = Morale.STEADY))
        assertTrue(TriggerEngine.evaluate(events, steady, 1, emptySet(), focus, gridN).isEmpty())
    }

    @Test
    fun `绑定其他军队不触发`() {
        val events = listOf(event(TriggerDef(null, "b", null, null, null), target = "b"))
        val army = listOf(corps("a"))
        assertTrue(TriggerEngine.evaluate(events, army, 1, emptySet(), focus, gridN).isEmpty())
    }

    @Test
    fun `多条件 AND 同时满足才触发`() {
        val events = listOf(event(TriggerDef(turn = 3, "a", 3.0, null, null)))
        val weak = listOf(corps("a", strength = 2.0))
        assertTrue(TriggerEngine.evaluate(events, weak, 2, emptySet(), focus, gridN).isEmpty())
        assertEquals(1, TriggerEngine.evaluate(events, weak, 3, emptySet(), focus, gridN).size)

        val strong = listOf(corps("a", strength = 5.0))
        assertTrue(TriggerEngine.evaluate(events, strong, 3, emptySet(), focus, gridN).isEmpty())
    }

    @Test
    fun `无绑定条件的全局事件按回合触发`() {
        val events = listOf(event(TriggerDef(turn = 1, null, null, null, null), target = null))
        val army = listOf(corps("a"))
        assertEquals(1, TriggerEngine.evaluate(events, army, 1, emptySet(), focus, gridN).size)
    }
}
