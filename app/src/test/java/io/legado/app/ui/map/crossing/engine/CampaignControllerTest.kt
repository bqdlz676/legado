package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.BattleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推演门面不变量单测（纯 JVM）。
 * 把守：时间单调、兵力守恒（非负且不凭空增加）、河道不可越、JSON 失败兜底、空图兜底、
 * 条件触发器点燃、分支存档回滚。
 */
class CampaignControllerTest {

    @Test
    fun `内置演示载入六支军队且初始进行中`() {
        val controller = CampaignController.demo()
        assertEquals(6, controller.corps.size)
        assertEquals(BattleStatus.ONGOING, controller.status)
        assertTrue(controller.definition.events.isNotEmpty())
    }

    @Test
    fun `时间单调递增：回合与天数只增不减`() {
        val controller = CampaignController.demo()
        controller.step()
        val turnAfterFirst = controller.turn
        val daysAfterFirst = controller.elapsedDays
        controller.step()
        assertTrue(controller.turn > turnAfterFirst)
        assertTrue(controller.elapsedDays > daysAfterFirst)
        assertEquals(controller.turn, controller.elapsedDays)
    }

    @Test
    fun `兵力守恒：全程非负且总兵力不超过初始值`() {
        val controller = CampaignController.demo()
        val initialTotal = controller.corps.sumOf { it.strength }
        controller.runToEnd()
        var total = 0.0
        controller.corps.forEach { corps ->
            assertTrue("兵力不得为负：${corps.id} = ${corps.strength}", corps.strength >= 0.0)
            assertTrue("累计损耗不得为负：${corps.id}", corps.losses >= 0.0)
            total += corps.strength
        }
        assertTrue("总兵力不得超过初始值：$total > $initialTotal", total <= initialTotal + 1e-6)
    }

    @Test
    fun `河道不可越：每支存活军队落点都可通行`() {
        val controller = CampaignController.demo()
        repeat(5) {
            controller.step()
            controller.corps.filter { it.alive }.forEach { corps ->
                assertTrue(
                    "军队 ${corps.name} 落点 ${corps.position} 不可通行",
                    controller.map.passable(corps.position, corps.type),
                )
                assertFalse(
                    "军队 ${corps.name} 站上了非渡口水面",
                    controller.map.isWater(corps.position) && !controller.map.isFord(corps.position),
                )
            }
        }
    }

    @Test
    fun `非法 JSON 走兜底：不抛异常且标记降级`() {
        val controller = CampaignController.load("{不是合法 json", null)
        assertTrue(controller.degraded)
        assertTrue(controller.corps.isEmpty())
        val snapshot = controller.snapshot()
        assertEquals(0, snapshot.corps.size)
    }

    @Test
    fun `空高程图走合成兜底场并置降级标志`() {
        val controller = CampaignController.load(DemoCampaigns.CHISHUI_JSON, null)
        assertTrue(controller.raster.degraded)
        assertTrue(controller.raster.width > 0)
    }

    @Test
    fun `缺字段最小 JSON 给默认值且推进不崩`() {
        val controller = CampaignController.load("{}", null)
        assertTrue(controller.degraded)
        assertEquals(30, controller.definition.duration)
        controller.step()
        controller.runToEnd(5)
    }

    @Test
    fun `条件触发器在首回合点燃并写入日志`() {
        val controller = CampaignController.load(TRIGGER_JSON, null)
        val snapshot = controller.step()
        assertTrue(snapshot.events.any { it.contains("测试点燃") })
        val target = controller.corps.first { it.id == "a1" }
        assertFalse("被击溃事件作用后军队应退出战场", target.alive)
    }

    @Test
    fun `分支存档可回滚回合与兵力`() {
        val controller = CampaignController.demo()
        controller.step()
        val branch = controller.saveBranch()
        val strengthAtBranch = controller.corps.map { it.strength }
        repeat(3) { controller.step() }
        assertTrue(controller.turn > branch.turn)

        controller.restoreBranch(branch)
        assertEquals(branch.turn, controller.turn)
        assertEquals(branch.elapsedDays, controller.elapsedDays)
        controller.corps.forEachIndexed { index, corps ->
            assertEquals(strengthAtBranch[index], corps.strength, 1e-6)
        }
        assertEquals(BattleStatus.ONGOING, controller.status)
    }

    @Test
    fun `终局后继续推进不改变状态`() {
        val controller = CampaignController.load(TRIGGER_JSON, null)
        controller.runToEnd()
        val status = controller.status
        val turn = controller.turn
        controller.step()
        assertEquals(status, controller.status)
        assertEquals(turn, controller.turn)
    }

    private companion object {
        const val TRIGGER_JSON: String = """
{
  "id": "trigger-test",
  "title": "触发器测试",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.0, 0.0],
      "strength": 5, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.5, 0.5],
      "strength": 5, "type": "regular", "morale": "steady", "intent": "hold" }
  ],
  "events": [
    { "trigger": { "turn": 1 }, "action": "moraleChange", "target": "a1",
      "value": "broken", "reason": "测试点燃" }
  ]
}
"""
    }
}
