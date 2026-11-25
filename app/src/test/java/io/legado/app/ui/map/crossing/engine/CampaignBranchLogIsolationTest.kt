package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.BranchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-1 分支存档 · 事件日志时间线隔离单测（纯 JVM，同包可经 [CampaignController.saveBranch] 回读存档）。
 *
 * 把守口径：
 * 1. 日志往返：存档携带事件日志全表；恢复后除路径末尾追加的「已恢复分支存档」系统行外，
 *    存档时刻条目逐字回到快照。
 * 2. 跨时间线无残留：存档 A → 推进产生新日志 → 恢复 A → 再存档，不得出现被丢弃时间线的条目。
 * 3. 空日志往返：旧档缺省空表恢复不产生 null / 异常，日志不残留上一时间线条目。
 *
 * 断言口径：先取存档表条目再断言；夹具必须先证明自身产生了目标日志，否则断言空转（M2）。
 */
class CampaignBranchLogIsolationTest {

    @Test
    fun `D1-1 日志往返一致：恢复后存档时刻条目逐字回到快照`() {
        val controller = CampaignController.load(LOG_FIXTURE_JSON, null)
        controller.step() // turn 1：点燃首回合标记，日志非空
        val branchA = controller.saveBranch()
        assertTrue("夹具首回合应写入日志（否则断言空转，M2）", branchA.eventLog.isNotEmpty())

        controller.restoreBranch(branchA)
        val again = controller.saveBranch()

        // 恢复路径末尾追加一条「已恢复分支存档」系统行；其余条目必须与存档时刻逐字一致。
        assertEquals(
            "恢复后日志条目数 = 存档数 + 1 条恢复标记",
            branchA.eventLog.size + 1,
            again.eventLog.size,
        )
        assertEquals("存档时刻日志往返一致", branchA.eventLog, again.eventLog.dropLast(1))
        assertTrue("末行为分支恢复标记", again.eventLog.last().contains("已恢复分支存档"))
    }

    @Test
    fun `D1-2 跨时间线隔离：推进产生的新日志不残留到恢复后的存档`() {
        val controller = CampaignController.load(LOG_FIXTURE_JSON, null)
        controller.step()
        val branchA = controller.saveBranch()
        val markerFirst = "首回合时间线标记"
        assertTrue("存档 A 应含首回合标记", branchA.eventLog.any { it.contains(markerFirst) })

        repeat(3) { controller.step() } // turn 2..4：turn 3 点燃后续回合标记
        val branchB = controller.saveBranch()
        val markerLater = "后续回合时间线标记"
        assertTrue("推进后应产生新时间线日志（否则断言空转，M2）", branchB.eventLog.any { it.contains(markerLater) })
        assertTrue("新时间线日志不得与 A 完全相同", branchB.eventLog != branchA.eventLog)

        controller.restoreBranch(branchA)
        val restored = controller.saveBranch()

        assertFalse("恢复 A 后不得残留被丢弃时间线的后续回合标记", restored.eventLog.any { it.contains(markerLater) })
        assertTrue("恢复 A 后首回合标记仍在", restored.eventLog.any { it.contains(markerFirst) })
        assertEquals("恢复后日志（除恢复标记）与 A 逐字一致", branchA.eventLog, restored.eventLog.dropLast(1))
    }

    @Test
    fun `D1-3 空日志往返：缺省空表旧档恢复不产生 null 或异常`() {
        val controller = CampaignController.load("{}", null)
        // 模拟旧版存档：只传核心四字段，eventLog 走缺省空表。
        val oldStyle = BranchSnapshot(
            turn = 0,
            elapsedDays = 0,
            corps = emptyList(),
            firedEventIds = emptySet(),
        )
        assertTrue("旧档缺省日志为空", oldStyle.eventLog.isEmpty())

        controller.restoreBranch(oldStyle)
        val reopened = controller.saveBranch()

        // 恢复路径至多追加一条恢复标记；不得残留上一时间线条目，条目不得为 null / 空白。
        assertTrue("空日志往返后条目数不超过 1 条恢复标记", reopened.eventLog.size <= 1)
        assertTrue("日志条目不得为 null / 空白", reopened.eventLog.all { it.isNotBlank() })
        controller.step()
        controller.runToEnd(2)
    }

    private companion object {

        /**
         * 日志隔离夹具：a1 驻点驻守、b1 远在半径外；turn 1 点燃首回合标记，turn 3 点燃后续回合标记，
         * 两条事件均为 moraleChange → steady，军队保持可作战，保证两条标记都能落日志。
         */
        const val LOG_FIXTURE_JSON: String = """
{
  "id": "d1-log-isolation",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.01, 0.01],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.30, 0.30],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ],
  "events": [
    { "trigger": { "turn": 1 }, "action": "moraleChange", "target": "a1",
      "value": "steady", "reason": "首回合时间线标记" },
    { "trigger": { "turn": 3 }, "action": "moraleChange", "target": "a1",
      "value": "steady", "reason": "后续回合时间线标记" }
  ]
}
"""
    }
}
