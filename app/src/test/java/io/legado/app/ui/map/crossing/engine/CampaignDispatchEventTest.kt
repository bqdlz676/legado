package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.Morale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B 栈（crossing 活栈）事件派发层单测（D3 补测，纯 JVM）。
 *
 * 把守：分支真实落地副作用（断粮 / 投降 / 改目标 / 兵力增减）、未知动作在解析层记告警
 * 且控制器照常推进不抛异常、意图型分支写日志可观测。意图字段每回合由 IntentResolver
 * 重新裁决，事件写入的意图可能被覆盖，故不在此断言意图（口径同 CampaignEventActionTest）。
 */
class CampaignDispatchEventTest {

    @Test
    fun `supplyCut 清补给且不降士气`() {
        val controller = CampaignController.load(FIXTURE, null)
        controller.step()

        val target = controller.corps.first { it.id == "c_supply" }
        assertEquals("断粮作用对象是补给，必须清零", 0, target.supply)
        assertEquals("断粮不得错降士气", Morale.STEADY, target.morale)
    }

    @Test
    fun `surrender 派发后目标被俘出局`() {
        val controller = CampaignController.load(FIXTURE, null)
        controller.step()

        val target = controller.corps.first { it.id == "c_surr" }
        assertTrue(target.surrendered)
        assertFalse("投降出局后不应仍存活", target.alive)
    }

    @Test
    fun `setTarget 更新目标点`() {
        val controller = CampaignController.load(FIXTURE, null)
        controller.step()

        val target = controller.corps.first { it.id == "c_target" }
        assertNotNull(target.target)
        // targetPos 数组口径 [lng, lat]；夹具给 [0.90, 0.30]。
        assertEquals(0.30, target.target!!.lat, 1e-6)
        assertEquals(0.90, target.target!!.lng, 1e-6)
    }

    @Test
    fun `strengthDelta 兵力按值增减且不为负`() {
        val controller = CampaignController.load(FIXTURE, null)
        controller.step()

        val target = controller.corps.first { it.id == "c_str" }
        assertEquals(7.0, target.strength, 1e-6)
    }

    @Test
    fun `意图型分支写日志可观测且不抛异常`() {
        val controller = CampaignController.load(FIXTURE, null)
        val snapshot = controller.step()

        val texts = snapshot.events.map { it.text }
        assertTrue("设伏分支应有日志：$texts", texts.any { it.contains("设伏测试") })
        assertTrue("包抄分支应有日志：$texts", texts.any { it.contains("包抄测试") })
        assertTrue("撤退分支应有日志：$texts", texts.any { it.contains("撤退测试") })
        assertTrue("突围分支应有日志：$texts", texts.any { it.contains("突围测试") })
    }

    @Test
    fun `未知动作记告警并跳过，控制器照常推进不抛异常`() {
        val controller = CampaignController.load(FIXTURE, null)
        controller.step()

        assertTrue(
            "未知动作必须进告警台账：${controller.warnings}",
            controller.warnings.any { it.contains("action 未知") },
        )
        val unknown = controller.corps.first { it.id == "c_nuke" }
        assertTrue("未知动作不得改动目标状态", unknown.alive)
        assertEquals(10.0, unknown.strength, 1e-6)
    }

    private companion object {

        const val FIXTURE: String = """
        {
          "id": "dispatch-b",
          "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
          "duration": 10,
          "victory": { "type": "defend", "side": "a" },
          "armies": [
            { "id": "c_supply",  "name": "断粮", "side": "a", "initialPos": [0.02, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_surr",    "name": "投降", "side": "a", "initialPos": [0.12, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_target",  "name": "改目标", "side": "a", "initialPos": [0.22, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_str",     "name": "折兵", "side": "a", "initialPos": [0.32, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_ambush",  "name": "设伏", "side": "a", "initialPos": [0.42, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_flank",   "name": "包抄", "side": "a", "initialPos": [0.52, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_retreat", "name": "撤退", "side": "a", "initialPos": [0.62, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_escape",  "name": "突围", "side": "a", "initialPos": [0.72, 0.02], "strength": 10, "type": "regular", "morale": "steady" },
            { "id": "c_nuke",    "name": "未知", "side": "a", "initialPos": [0.82, 0.02], "strength": 10, "type": "regular", "morale": "steady" }
          ],
          "events": [
            { "trigger": { "turn": 1 }, "action": "supplyCut",     "target": "c_supply",  "reason": "断粮测试" },
            { "trigger": { "turn": 1 }, "action": "surrender",     "target": "c_surr",    "reason": "投降测试" },
            { "trigger": { "turn": 1 }, "action": "setTarget",     "target": "c_target",  "targetPos": [0.90, 0.30], "reason": "改目标测试" },
            { "trigger": { "turn": 1 }, "action": "strengthDelta", "target": "c_str",     "value": "-3", "reason": "折兵测试" },
            { "trigger": { "turn": 1 }, "action": "ambush",        "target": "c_ambush",  "reason": "设伏测试" },
            { "trigger": { "turn": 1 }, "action": "flank",         "target": "c_flank",   "reason": "包抄测试" },
            { "trigger": { "turn": 1 }, "action": "retreat",       "target": "c_retreat", "reason": "撤退测试" },
            { "trigger": { "turn": 1 }, "action": "escape",        "target": "c_escape",  "reason": "突围测试" },
            { "trigger": { "turn": 1 }, "action": "nuke",          "target": "c_nuke",    "reason": "未知动作测试" }
          ]
        }
        """
    }
}
