package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.BranchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B7 分支存档 · 据点计数 / 归属隔离单测（纯 JVM，同包可经 [CampaignController.saveBranch] 回读存档）。
 *
 * 把守口径：
 * 1. 写入往返：存档携带非空 [BranchSnapshot.captureHold]（连续占领回合数）与
 *    [BranchSnapshot.pointHolders]（据点标识 → 阵营），恢复后逐字段回到存档时刻。
 * 2. 跨分支隔离：A 存档与 B 存档归属不同；恢复 A 后归属必须是 A 的持有方，不得串成 B 的。
 * 3. 缺字段旧档：两表缺省空值时按恢复后的军位重建归属，计数从 1 回合基线起算，不崩。
 * 4. 空据点表 / 空军队：无胜利点或军队为空时两表自然空转，推进不抛异常。
 * 5. 同回合重复存档、重复恢复：计数不翻倍。
 *
 * 断言口径：先 [assertNotNull] 取出表条目再取值，禁止非空断言穿透——情报表 / 据点表缺条目时
 * 失败表现为断言失败而非 NPE（M4）。
 */
class CampaignBranchIsolationTest {

    @Test
    fun `B7-1 写入往返：计数与归属非空，恢复后逐字段回到存档时刻`() {
        val controller = CampaignController.load(HOLD_A_JSON, null)
        controller.step()
        val branch = controller.saveBranch()

        assertTrue("夹具必须产生非空占领计数（否则断言空转，M2）", branch.captureHold.isNotEmpty())
        assertTrue("夹具必须产生非空归属表（否则断言空转，M2）", branch.pointHolders.isNotEmpty())
        val holderA = branch.pointHolders.values.firstOrNull()
        assertNotNull("存档时刻据点应有占据方", holderA)
        assertEquals("首回合占据方应为胜利方 a", "a", holderA)

        repeat(3) { controller.step() }
        controller.restoreBranch(branch)
        val again = controller.saveBranch()

        assertEquals("占领计数往返一致", branch.captureHold, again.captureHold)
        assertEquals("据点归属往返一致", branch.pointHolders, again.pointHolders)
    }

    @Test
    fun `B7-2 跨分支隔离：A 存档推进后切到 B，恢复 A 归属不串成 B`() {
        val controller = CampaignController.load(HOLD_A_JSON, null)
        controller.step()
        val branchA = controller.saveBranch()
        val pointKey = branchA.pointHolders.keys.firstOrNull()
        assertNotNull("存档 A 应含据点标识", pointKey)
        assertNotNull("存档 A 占据方应为 a", branchA.pointHolders[pointKey])
        assertEquals("存档 A 占据方为 a", "a", branchA.pointHolders[pointKey])

        // 手工构造分支 B：同一批军队，归属记为敌方 b、计数记 2，模拟另一条时间线的存档。
        // pointKey 已在上方 assertNotNull 判空；mapOf 显式类型参数对齐 BranchSnapshot 的非空键签名。
        val branchB = branchA.copy(
            captureHold = mapOf<String, Int>(pointKey!! to 2),
            pointHolders = mapOf<String, String>(pointKey!! to "b"),
        )

        controller.restoreBranch(branchB)
        val restoredB = controller.saveBranch()
        assertEquals("切到 B 后归属应为 b（证明 B 存档真实生效，非空表空转）", "b", restoredB.pointHolders[pointKey])
        assertEquals("切到 B 后计数应为 2", 2, restoredB.captureHold[pointKey] ?: 0)

        controller.restoreBranch(branchA)
        val restoredA = controller.saveBranch()
        assertEquals("恢复 A 后归属回到 A 的持有方 a", "a", restoredA.pointHolders[pointKey])
        assertTrue("归属不得串成 B 的持有方 b", restoredA.pointHolders[pointKey] != "b")
        assertEquals("恢复 A 后计数回到 A 的计数", branchA.captureHold, restoredA.captureHold)
    }

    @Test
    fun `B7-3 缺字段旧档：从恢复后军位重建归属，计数基线 1，推进不崩`() {
        val controller = CampaignController.load(HOLD_A_JSON, null)
        controller.step()
        val live = controller.saveBranch()
        assertTrue("推进后计数非空", live.captureHold.isNotEmpty())

        // 模拟旧版存档：只传核心四字段，captureHold / pointHolders 走缺省空值。
        val oldStyle = BranchSnapshot(
            turn = live.turn,
            elapsedDays = live.elapsedDays,
            corps = live.corps,
            firedEventIds = live.firedEventIds,
        )
        controller.restoreBranch(oldStyle)

        val rebuilt = controller.saveBranch()
        val key = rebuilt.pointHolders.keys.firstOrNull()
        assertNotNull("旧档按军位重建后据点应有占据方", key)
        assertEquals("重建归属应为军位所在方 a", "a", rebuilt.pointHolders[key])
        assertEquals("旧档无历史计数，基线从 1 回合起算", 1, rebuilt.captureHold[key] ?: 0)

        controller.step()
        controller.runToEnd(3)
    }

    @Test
    fun `B7-4 空据点表：无胜利点的战役存档恢复后两表为空，不崩`() {
        val controller = CampaignController.load(NO_POINT_JSON, null)
        controller.step()
        val branch = controller.saveBranch()
        assertTrue("无胜利点时计数表为空", branch.captureHold.isEmpty())
        assertTrue("无胜利点时归属表为空", branch.pointHolders.isEmpty())

        controller.restoreBranch(branch)
        val again = controller.saveBranch()
        assertTrue("恢复后计数表仍为空", again.captureHold.isEmpty())
        assertTrue("恢复后归属表仍为空", again.pointHolders.isEmpty())
        controller.runToEnd(3)
    }

    @Test
    fun `B7-5 同回合重复存档：两次快照一致，重复恢复计数不翻倍`() {
        val controller = CampaignController.load(HOLD_A_JSON, null)
        controller.step()
        val first = controller.saveBranch()
        val second = controller.saveBranch()
        assertEquals("同回合两次存档计数一致", first.captureHold, second.captureHold)
        assertEquals("同回合两次存档归属一致", first.pointHolders, second.pointHolders)

        controller.restoreBranch(first)
        controller.restoreBranch(first)
        val again = controller.saveBranch()
        assertEquals("重复恢复不叠加计数", first.captureHold, again.captureHold)
        assertEquals("重复恢复不叠加归属", first.pointHolders, again.pointHolders)
    }

    @Test
    fun `B7-6 恢复后推进一回合：计数在存档值上加 1，不翻倍`() {
        val controller = CampaignController.load(HOLD_A_JSON, null)
        controller.step()
        val branch = controller.saveBranch()
        val key = branch.captureHold.keys.firstOrNull()
        assertNotNull("存档应含据点计数键", key)
        val holdAtBranch = branch.captureHold[key] ?: 0

        controller.restoreBranch(branch)
        controller.step()
        val after = controller.saveBranch()
        assertEquals("恢复后再守一回合：计数 = 存档值 + 1", holdAtBranch + 1, after.captureHold[key] ?: 0)
        assertEquals("推进后占据方不变", branch.pointHolders[key], after.pointHolders[key])
    }

    @Test
    fun `B7-7 空军队降级档：空存档恢复后两表为空，推进不崩`() {
        val controller = CampaignController.load("{}", null)
        val emptyBranch = BranchSnapshot(
            turn = 0,
            elapsedDays = 0,
            corps = emptyList(),
            firedEventIds = emptySet(),
        )
        controller.restoreBranch(emptyBranch)
        val reopened = controller.saveBranch()
        assertTrue("空军队存档计数表为空", reopened.captureHold.isEmpty())
        assertTrue("空军队存档归属表为空", reopened.pointHolders.isEmpty())
        controller.step()
        controller.runToEnd(2)
    }

    private companion object {

        /** 据点用例：a1 驻胜利点驻守，b1 远在半径外；首回合即由 a 占据据点，产生非空两表。 */
        const val HOLD_A_JSON: String = """
{
  "id": "b7-hold-a",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "capture", "side": "a", "point": [0.01, 0.01], "holdTurns": 3 },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.01, 0.01],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.30, 0.30],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""

        /** 无胜利点用例：DEFEND 条件不带 point，据点两表应全程为空。 */
        const val NO_POINT_JSON: String = """
{
  "id": "b7-no-point",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.0, 0.0],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.40, 0.40],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""
    }
}
