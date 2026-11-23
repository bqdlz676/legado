package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B3 外交盟友判定单测（纯 JVM）。
 *
 * 把守三种行为（规格 §三 意图表 + 验收清单 B3 判据「盟友不参战、不追击、不做溃逃方向」）：
 * 1. 遭遇：ALLIED 异阵营军队同格 / 相邻不触发战斗，双方兵力零损耗；
 * 2. 支援：相邻盟友遭攻击时本军转 SUPPORT，盟友不计入敌军增援集；
 * 3. 溃逃：BROKEN 军队的追击方集不含盟友，溃逃落点背离敌军一侧。
 *
 * 集合口径直接断言 [CampaignController.filterEnemiesOf] / [CampaignController.filterAlliesOf]
 * （internal 仅供同包测试），决策方向另在 [IntentResolver] 纯函数层复核。
 */
class CampaignDiplomacyTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val projection = GeoProjection(focus, 32)

    private fun plainMap(): TerrainMap {
        val data = IntArray(32 * 32) { 128 }
        val raster = HeightRaster.create(32, 32, data, degradedHint = false)
        return TerrainMap(raster, projection, emptyList())
    }

    private fun corps(
        id: String,
        side: String,
        position: Cell,
        morale: Morale = Morale.STEADY,
        strength: Double = 8.0,
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
        position = position,
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

    // ── 行为 1：遭遇 ──────────────────────────────────────────

    @Test
    fun `B3 遭遇：同格盟友不触发战斗，双方零损耗且盟友不进敌军集`() {
        val controller = CampaignController.load(ENCOUNTER_JSON, null)
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        val c1 = controller.corps.first { it.id == "c1" }

        // 集合口径：盟友 b1 只进盟友集；敌军集只有真正敌对的 c1。
        assertEquals(listOf("c1"), controller.filterEnemiesOf(a1).map { it.id })
        assertEquals(listOf("b1"), controller.filterAlliesOf(a1).map { it.id })
        // 盟约对称：b1 视角下 a1 是盟友，c1 仍是敌军。
        assertEquals(listOf("c1"), controller.filterEnemiesOf(b1).map { it.id })
        assertEquals(listOf("a1"), controller.filterAlliesOf(b1).map { it.id })
        // 第三方 c1 视角：a、b 两方都不是其盟友，均计入敌军。
        assertEquals(setOf("a1", "b1"), controller.filterEnemiesOf(c1).map { it.id }.toSet())

        val a1Before = a1.strength
        val b1Before = b1.strength
        controller.step()

        assertEquals("盟友并肩不得互耗", a1Before, a1.strength, 1e-6)
        assertEquals("盟友并肩不得互耗", b1Before, b1.strength, 1e-6)
        assertEquals(0.0, a1.losses, 1e-6)
        assertEquals(0.0, b1.losses, 1e-6)
        assertFalse(
            "同格盟友之间不得写入遭遇战日志",
            controller.snapshot().events.any { it.contains("遭遇战") },
        )
    }

    // ── 行为 2：支援 ──────────────────────────────────────────

    @Test
    fun `B3 支援：相邻盟友遭攻击时本军转 SUPPORT，盟友不计入敌军增援`() {
        val controller = CampaignController.load(SUPPORT_JSON, null)
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }

        assertEquals(listOf("b1"), controller.filterAlliesOf(a1).map { it.id })
        assertFalse("盟友不得出现在敌军增援集", controller.filterEnemiesOf(a1).any { it.id == "b1" })
        assertEquals(listOf("c1"), controller.filterEnemiesOf(a1).map { it.id })

        controller.step()

        assertEquals("盟友相邻遭攻击应转支援意图", Intent.SUPPORT, a1.intent)
        assertEquals("支援落点应为盟友所在格", b1.position, a1.route.lastOrNull() ?: a1.position)
    }

    // ── 行为 3：溃逃 ──────────────────────────────────────────

    @Test
    fun `B3 溃逃：追击方集不含盟友，BROKEN 军队按 ROUT 脱离`() {
        val controller = CampaignController.load(ROUT_JSON, null)
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        val c1 = controller.corps.first { it.id == "c1" }

        assertEquals(listOf("c1"), controller.filterEnemiesOf(a1).map { it.id })
        assertEquals(listOf("b1"), controller.filterAlliesOf(a1).map { it.id })
        assertFalse("盟友不得被当作追击方", controller.filterEnemiesOf(a1).any { it.id == "b1" })

        controller.step()

        assertEquals("溃散军队应转溃逃意图", Intent.ROUT, a1.intent)
        assertTrue("溃逃后不得与敌军同格", a1.position != c1.position)
        assertTrue("溃逃方向应背离西侧敌军", a1.position.col >= 16)
    }

    @Test
    fun `B3 溃逃方向：盟友不计入追击方，落点选在背离敌军的一侧`() {
        val map = plainMap()
        val me = corps(id = "a1", side = "a", position = Cell(16, 16), morale = Morale.BROKEN)
        val ally = corps(id = "b1", side = "b", position = Cell(17, 16))
        val enemy = corps(id = "c1", side = "c", position = Cell(13, 16))

        val decision = IntentResolver.resolve(
            corps = me,
            allies = listOf(ally),
            enemies = listOf(enemy),
            map = map,
            chokeCells = emptyList(),
            supplyCells = listOf(ally.position),
        )

        assertEquals(Intent.ROUT, decision.intent)
        val target = decision.target
        requireNotNull(target) { "溃逃必须给出落点格" }
        assertTrue("溃逃落点应在背离敌军（西）的一侧", target.col >= 16)
        assertTrue("溃逃落点不得朝向敌军所在格", target != enemy.position)
    }

    private companion object {

        /** 遭遇用例：a 与 b 盟约，b1 与 a1 同格，c1 远在另一侧。 */
        const val ENCOUNTER_JSON: String = """
{
  "id": "b3-encounter",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "annihilate", "target": "c1", "side": "a" },
  "sides": [ { "id": "a" }, { "id": "b" }, { "id": "c" } ],
  "diplomacy": [ { "a": "a", "b": "b", "relation": "allied" } ],
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.000, 0.000],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.001, -0.001],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "c1", "name": "丙", "side": "c", "initialPos": [0.400, 0.400],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""

        /** 支援用例：a1(16,16) — b1(16,17) 盟友 — c1(16,18) 敌军，盟友相邻受压。 */
        const val SUPPORT_JSON: String = """
{
  "id": "b3-support",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "annihilate", "target": "c1", "side": "a" },
  "sides": [ { "id": "a" }, { "id": "b" }, { "id": "c" } ],
  "diplomacy": [ { "a": "a", "b": "b", "relation": "allied" } ],
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.000, 0.000],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.000, -0.030],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "c1", "name": "丙", "side": "c", "initialPos": [0.000, -0.060],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""

        /** 溃逃用例：a1 溃散居中，盟友 b1 在东侧，敌军 c1 在西侧远处（相邻敌军优先应战，故拉开距离）。 */
        const val ROUT_JSON: String = """
{
  "id": "b3-rout",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "annihilate", "target": "c1", "side": "a" },
  "sides": [ { "id": "a" }, { "id": "b" }, { "id": "c" } ],
  "diplomacy": [ { "a": "a", "b": "b", "relation": "allied" } ],
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.000, 0.000],
      "strength": 8, "type": "regular", "morale": "broken", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.030, 0.000],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "c1", "name": "丙", "side": "c", "initialPos": [-0.168, 0.000],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""
    }
}
