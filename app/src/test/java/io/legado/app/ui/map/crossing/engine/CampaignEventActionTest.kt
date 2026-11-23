package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.Morale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B15 crossing 层事件动作接线单测（纯 JVM）。
 *
 * 只把守回合末仍稳定的可观察项：兵力 / 存活 / 投降 / 士气 / 暴露。
 * 意图字段每回合由 IntentResolver 重新裁决，事件写入的意图可能被覆盖，故不在此断言意图。
 */
class CampaignEventActionTest {

    @Test
    fun `事件型动作在 crossing 引擎内真实生效`() {
        val controller = CampaignController.load(STATE_JSON, null)
        controller.step()

        fun corps(id: String) = controller.corps.first { it.id == id }

        // fireAttack：折损 + 士气降一级
        assertTrue("火攻折损：${corps("c_fire").strength}", corps("c_fire").strength < 10.0)
        assertEquals(Morale.WAVERING, corps("c_fire").morale)

        // siegeEngine：重挫 + 击溃
        assertTrue("攻城折损：${corps("c_siege").strength}", corps("c_siege").strength < 10.0)
        assertEquals(Morale.BROKEN, corps("c_siege").morale)

        // breakthrough：折损
        assertTrue("突破折损：${corps("c_break").strength}", corps("c_break").strength < 10.0)

        // navalRout：击溃（回合末士气系统对 BROKEN 军队做解散处理）
        assertEquals(Morale.BROKEN, corps("c_naval").morale)

        // defendFall：殉国出局，非投降
        assertFalse(corps("c_fall").alive)
        assertFalse("殉国不等于投降", corps("c_fall").surrendered)

        // capture：被俘出局
        assertFalse(corps("c_cap").alive)
        assertTrue(corps("c_cap").surrendered)

        // annihilate：覆没出局
        assertFalse(corps("c_annih").alive)
        assertFalse("覆没不等于投降", corps("c_annih").surrendered)

        // feignedRetreat：中计追击，位置暴露
        assertTrue(corps("c_feign").exposed)
    }

    @Test
    fun `rout 事件在 crossing 引擎内击溃目标`() {
        val controller = CampaignController.load(ROUT_JSON, null)
        controller.step()
        val target = controller.corps.first { it.id == "c_rout" }
        assertEquals(Morale.BROKEN, target.morale)
    }

    private companion object {

        const val STATE_JSON: String = """
        {
          "id": "b15-state",
          "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
          "duration": 10,
          "victory": { "type": "defend", "side": "a" },
          "armies": [
            { "id": "c_fire",   "name": "火攻",   "side": "a", "initialPos": [0.02, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_siege",  "name": "攻城",   "side": "a", "initialPos": [0.12, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_break",  "name": "突破",   "side": "a", "initialPos": [0.22, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_naval",  "name": "舟溃",   "side": "a", "initialPos": [0.32, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_fall",   "name": "城破",   "side": "a", "initialPos": [0.42, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_cap",    "name": "被俘",   "side": "a", "initialPos": [0.52, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_annih",  "name": "覆没",   "side": "a", "initialPos": [0.62, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_feign",  "name": "佯退",   "side": "a", "initialPos": [0.72, 0.02], "strength": 10, "type": "regular", "morale": "steady" }
          ],
          "events": [
            { "trigger": { "turn": 1 }, "action": "fireAttack",    "target": "c_fire",  "reason": "火攻折损" },
            { "trigger": { "turn": 1 }, "action": "siegeEngine",  "target": "c_siege", "reason": "攻城破城" },
            { "trigger": { "turn": 1 }, "action": "breakthrough", "target": "c_break", "reason": "中央突破" },
            { "trigger": { "turn": 1 }, "action": "navalRout",    "target": "c_naval", "reason": "舟师溃乱" },
            { "trigger": { "turn": 1 }, "action": "defendFall",   "target": "c_fall",  "reason": "城破殉国" },
            { "trigger": { "turn": 1 }, "action": "capture",      "target": "c_cap",   "reason": "主帅被俘" },
            { "trigger": { "turn": 1 }, "action": "annihilate",   "target": "c_annih", "reason": "全军覆没" },
            { "trigger": { "turn": 1 }, "action": "feignedRetreat", "target": "c_feign", "reason": "佯退诱敌" }
          ]
        }
        """

        const val ROUT_JSON: String = """
        {
          "id": "b15-rout",
          "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
          "duration": 10,
          "victory": { "type": "defend", "side": "a" },
          "armies": [
            { "id": "c_rout", "name": "溃散", "side": "a", "initialPos": [0.02, 0.02], "strength": 10, "type": "regular", "morale": "steady" }
          ],
          "events": [
            { "trigger": { "turn": 1 }, "action": "rout", "target": "c_rout", "reason": "阵脚溃散" }
          ]
        }
        """
    }
}
