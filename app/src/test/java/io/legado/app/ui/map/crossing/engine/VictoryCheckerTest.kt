package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.VictoryDef
import io.legado.app.ui.map.crossing.model.VictoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 胜利条件判定单测（纯 JVM）。
 * 把守 F4（DEFEND 据点失守判负）、F5（ROUT 严格全溃口径，投降 / 被歼不混算）、
 * CAPTURE 占领回合、COMPOUND AND/OR 组合、盟友不计入敌军 / 失守。
 */
class VictoryCheckerTest {

    private val point = Geo(lat = 28.35, lng = 105.85)
    private val pointKey = "${"%.4f".format(point.lng)},${"%.4f".format(point.lat)}"

    private fun corps(
        id: String,
        side: String,
        strength: Double = 5.0,
        morale: Morale = Morale.STEADY,
        alive: Boolean = true,
        surrendered: Boolean = false,
        role: FactionRole = FactionRole.OWN,
        latLng: Geo? = null,
    ) = Corps(
        id = id,
        name = id,
        sideId = side,
        role = role,
        type = ArmyType.REGULAR,
        strength = strength,
        fatigue = 0.0,
        morale = morale,
        intent = Intent.HOLD,
        position = Cell(16, 16),
        latLng = latLng ?: point,
        target = null,
        supply = 5,
        supplyCutTurns = 0,
        alive = alive,
        surrendered = surrendered,
        exposed = false,
        highGround = false,
        route = emptyList(),
        lastKnown = emptyMap(),
        visionRadiusCells = 4,
    )

    private fun defendDef() = VictoryDef(
        type = VictoryType.DEFEND,
        target = null,
        side = "a",
        point = point,
        holdTurns = 3,
        op = "AND",
        sub = emptyList(),
    )

    @Test
    fun `F4 DEFEND 据点被敌方占据即判负，不论有生力量是否尚存`() {
        val defenders = listOf(corps("a1", "a", strength = 8.0))
        val holders = mapOf(pointKey to "b")
        val outcome = VictoryChecker.check(
            def = defendDef(),
            corps = defenders,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = holders,
            alliedSideIds = emptySet(),
        )
        assertEquals(BattleStatus.DEFEAT, outcome.status)
        assertTrue(outcome.reason.contains("据点"))
    }

    @Test
    fun `F4 盟友占据据点不算失守，防御方尚存即继续`() {
        val defenders = listOf(corps("a1", "a", strength = 8.0))
        val holders = mapOf(pointKey to "c")
        val outcome = VictoryChecker.check(
            def = defendDef(),
            corps = defenders,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = holders,
            alliedSideIds = setOf("c"),
        )
        assertEquals(BattleStatus.ONGOING, outcome.status)
    }

    @Test
    fun `F4 撑满全部回合且据点未失守判胜`() {
        val defenders = listOf(corps("a1", "a", strength = 8.0))
        val outcome = VictoryChecker.check(
            def = defendDef(),
            corps = defenders,
            turn = 30,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = mapOf(pointKey to "a"),
            alliedSideIds = emptySet(),
        )
        assertEquals(BattleStatus.VICTORY, outcome.status)
    }

    @Test
    fun `F4 盟友协防据点并撑满全部回合，盟友占据算己方判胜`() {
        val defenders = listOf(corps("a1", "a", strength = 8.0))
        val holders = mapOf(pointKey to "c")
        val outcome = VictoryChecker.check(
            def = defendDef(),
            corps = defenders,
            turn = 30,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = holders,
            alliedSideIds = setOf("c"),
        )
        assertEquals(BattleStatus.VICTORY, outcome.status)
    }

    @Test
    fun `F4 非盟友第三方占据据点即判负，盟友集合不覆盖第三方`() {
        val defenders = listOf(corps("a1", "a", strength = 8.0))
        // c 是盟友，但占据方 d 不在盟友集合内 → 按非友方判失守。
        val holders = mapOf(pointKey to "d")
        val outcome = VictoryChecker.check(
            def = defendDef(),
            corps = defenders,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = holders,
            alliedSideIds = setOf("c"),
        )
        assertEquals(BattleStatus.DEFEAT, outcome.status)
    }

    @Test
    fun `F5 ROUT 敌方存活且士气未全溃时继续推演`() {
        val blockers = listOf(corps("b1", "b", morale = Morale.HIGH))
        val def = VictoryDef(VictoryType.ROUT, null, "a", null, 3, "AND", emptyList())
        val outcome = VictoryChecker.check(
            def = def,
            corps = blockers,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = emptyMap(),
            alliedSideIds = emptySet(),
        )
        assertEquals(BattleStatus.ONGOING, outcome.status)
    }

    @Test
    fun `F5 ROUT 敌方存活军队全部 BROKEN 才判胜`() {
        val blockers = listOf(
            corps("b1", "b", morale = Morale.BROKEN),
            corps("b2", "b", morale = Morale.BROKEN),
        )
        val def = VictoryDef(VictoryType.ROUT, null, "a", null, 3, "AND", emptyList())
        val outcome = VictoryChecker.check(
            def = def,
            corps = blockers,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = emptyMap(),
            alliedSideIds = emptySet(),
        )
        assertEquals(BattleStatus.VICTORY, outcome.status)
    }

    @Test
    fun `F5 投降或被歼不计入溃散，blockers 为空不据 ROUT 判胜`() {
        val wiped = listOf(
            corps("b1", "b", alive = false, strength = 0.0),
            corps("b2", "b", surrendered = true),
        )
        val def = VictoryDef(VictoryType.ROUT, null, "a", null, 3, "AND", emptyList())
        val outcome = VictoryChecker.check(
            def = def,
            corps = wiped,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = emptyMap(),
            alliedSideIds = emptySet(),
        )
        assertEquals(BattleStatus.ONGOING, outcome.status)
    }

    @Test
    fun `F2 盟友不计入 ROUT  blockers`() {
        val mixed = listOf(
            corps("c1", "c", morale = Morale.HIGH, role = FactionRole.ALLY),
            corps("b1", "b", morale = Morale.BROKEN),
        )
        val def = VictoryDef(VictoryType.ROUT, null, "a", null, 3, "AND", emptyList())
        val outcome = VictoryChecker.check(
            def = def,
            corps = mixed,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = emptyMap(),
            alliedSideIds = setOf("c"),
        )
        // 盟友 c1 士气高昂但被排除；敌军 b1 已全溃 → 判胜。
        assertEquals(BattleStatus.VICTORY, outcome.status)
    }

    @Test
    fun `CAPTURE 连续占领达到回合数判胜`() {
        val def = VictoryDef(VictoryType.CAPTURE, null, "a", point, 3, "AND", emptyList())
        val holders = listOf(corps("a1", "a"))
        val outcome = VictoryChecker.check(
            def = def,
            corps = holders,
            turn = 4,
            duration = 30,
            captureHoldTurns = mapOf(pointKey to 3),
            pointHolders = emptyMap(),
            alliedSideIds = emptySet(),
        )
        assertEquals(BattleStatus.VICTORY, outcome.status)
    }

    @Test
    fun `COMPOUND OR 任一子条件达成即判胜，AND 需全部达成`() {
        val subA = VictoryDef(VictoryType.ANNIHILATE, "b1", "a", null, 3, "AND", emptyList())
        val subB = VictoryDef(VictoryType.REACH_POINT, null, "a", point, 3, "AND", emptyList())
        val corps = listOf(
            corps("a1", "a", latLng = point),
            corps("b1", "b", alive = false, strength = 0.0),
        )
        val orDef = VictoryDef(VictoryType.COMPOUND, null, "a", null, 3, "OR", listOf(subA, subB))
        val orOutcome = VictoryChecker.check(
            def = orDef,
            corps = corps,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = emptyMap(),
            alliedSideIds = emptySet(),
        )
        assertEquals(BattleStatus.VICTORY, orOutcome.status)

        val andDef = VictoryDef(VictoryType.COMPOUND, null, "a", null, 3, "AND", listOf(subA, subB))
        val andOutcome = VictoryChecker.check(
            def = andDef,
            corps = corps,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = emptyMap(),
            alliedSideIds = emptySet(),
        )
        // b1 已歼灭但 a1 在点上（REACH_POINT 达成），两者均达成 → AND 亦胜；
        // 再构造一个只达成子条件之一的阵营验证 AND 不宽松。
        assertTrue(andOutcome.finished)
        val oneSided = listOf(corps("b1", "b", alive = false, strength = 0.0))
        val andPartial = VictoryChecker.check(
            def = andDef,
            corps = oneSided,
            turn = 5,
            duration = 30,
            captureHoldTurns = emptyMap(),
            pointHolders = emptyMap(),
            alliedSideIds = emptySet(),
        )
        assertFalse(andPartial.finished)
    }
}
