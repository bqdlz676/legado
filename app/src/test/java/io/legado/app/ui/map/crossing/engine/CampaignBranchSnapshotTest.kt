package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.BranchSnapshot
import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.Morale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B7 分支快照全变量往返单测（纯 JVM，同包可触达内部测试口径出口）。
 *
 * 把守口径：
 * 1. 全字段往返一致：军队 21 个运行态字段 + 控制器级时间 / 终局 / 日志 / 行军累计 /
 *    双情报表，逐字段 assertEquals，不许只比对象引用。
 * 2. 士气 / 补给 / 疲劳 / 意图被改动后，切分支再切回，值回到快照时刻。
 * 3. 决策层 [intel] 与视图层 [reports] 双情报表真快照往返一致（非清空重播种）。
 * 4. 空 / 缺字段存档恢复不崩（空军队列表、缺省空表 / 空状态兜底）。
 */
class CampaignBranchSnapshotTest {

    @Test
    fun `① 全字段往返一致：军队逐字段与控制器状态全部回到快照时刻`() {
        val controller = CampaignController.load(ENGAGE_JSON, null)
        controller.step()
        val branch = controller.saveBranch()

        // 推进若干回合，让兵力 / 位置 / 情报 / 日志全部发生变化。
        repeat(3) { controller.step() }

        controller.restoreBranch(branch)
        val again = controller.saveBranch()

        // ── 控制器级状态 ──────────────────────────────────────
        assertEquals("回合数往返一致", branch.turn, again.turn)
        assertEquals("经过天数往返一致", branch.elapsedDays, again.elapsedDays)
        assertEquals("终局状态往返一致", branch.status, again.status)
        assertEquals("胜负方往返一致", branch.winnerSide, again.winnerSide)
        assertEquals("终局原因往返一致", branch.finishReason, again.finishReason)
        assertEquals("已点燃事件集合往返一致", branch.firedEventIds, again.firedEventIds)
        assertEquals("据点占领计数往返一致", branch.captureHold, again.captureHold)
        assertEquals("据点归属表往返一致", branch.pointHolders, again.pointHolders)
        assertEquals("行军格数累计往返一致", branch.marchCells, again.marchCells)
        assertEquals("决策层情报表往返一致", branch.intel, again.intel)
        assertEquals("视图层情报表往返一致", branch.reports, again.reports)
        // 恢复动作会追加一条日志；快照日志须为恢复后日志的前缀。
        assertEquals(
            "事件日志快照条目全部保留",
            branch.eventLog,
            again.eventLog.take(branch.eventLog.size),
        )

        // ── 军队逐字段对照（不只比引用）─────────────────────────
        assertEquals("军队数量往返一致", branch.corps.size, again.corps.size)
        branch.corps.zip(again.corps).forEach { (before, after) ->
            assertEquals("id 往返一致", before.id, after.id)
            assertEquals("name 往返一致", before.name, after.name)
            assertEquals("sideId 往返一致", before.sideId, after.sideId)
            assertEquals("role 往返一致", before.role, after.role)
            assertEquals("type 往返一致", before.type, after.type)
            assertEquals("strength 往返一致", before.strength, after.strength, 1e-9)
            assertEquals("fatigue 往返一致", before.fatigue, after.fatigue, 1e-9)
            assertEquals("morale 往返一致", before.morale, after.morale)
            assertEquals("intent 往返一致", before.intent, after.intent)
            assertEquals("position 往返一致", before.position, after.position)
            assertEquals("latLng 往返一致", before.latLng, after.latLng)
            assertEquals("target 往返一致", before.target, after.target)
            assertEquals("supply 往返一致", before.supply, after.supply)
            assertEquals("supplyCutTurns 往返一致", before.supplyCutTurns, after.supplyCutTurns)
            assertEquals("alive 往返一致", before.alive, after.alive)
            assertEquals("surrendered 往返一致", before.surrendered, after.surrendered)
            assertEquals("exposed 往返一致", before.exposed, after.exposed)
            assertEquals("highGround 往返一致", before.highGround, after.highGround)
            assertEquals("route 往返一致", before.route, after.route)
            assertEquals("lastKnown 往返一致", before.lastKnown, after.lastKnown)
            assertEquals("visionRadiusCells 往返一致", before.visionRadiusCells, after.visionRadiusCells)
            assertEquals("losses 往返一致", before.losses, after.losses, 1e-9)
        }
    }

    @Test
    fun `② 士气补给疲劳意图改动后切回分支，值回到快照值`() {
        val controller = CampaignController.load(ENGAGE_JSON, null)
        controller.step()
        val branch = controller.saveBranch()
        val snapshotCorps = branch.corps.first { it.id == "a1" }

        // 人为改动运行态：士气 / 补给 / 断粮回合 / 疲劳 / 意图。
        val live = controller.corps.first { it.id == "a1" }
        live.morale = Morale.BROKEN
        live.supply = 0
        live.supplyCutTurns = 5
        live.fatigue = 9.0
        live.intent = io.legado.app.ui.map.crossing.model.Intent.ROUT
        repeat(2) { controller.step() }

        controller.restoreBranch(branch)
        val restored = controller.corps.first { it.id == "a1" }
        assertEquals("士气回到快照值", snapshotCorps.morale, restored.morale)
        assertEquals("补给回到快照值", snapshotCorps.supply, restored.supply)
        assertEquals("断粮连续回合回到快照值", snapshotCorps.supplyCutTurns, restored.supplyCutTurns)
        assertEquals("疲劳回到快照值", snapshotCorps.fatigue, restored.fatigue, 1e-9)
        assertEquals("意图回到快照值", snapshotCorps.intent, restored.intent)
    }

    @Test
    fun `③ 情报双表真快照往返一致：推进后恢复，条目不被清空重播种`() {
        val controller = CampaignController.load(ENGAGE_JSON, null)
        controller.step()
        val branch = controller.saveBranch()
        val intelBefore = controller.intelSnapshotOf()
        val reportsBefore = controller.reportsSnapshotOf()
        assertTrue("开局播种后决策层情报表非空", intelBefore.isNotEmpty())

        repeat(3) { controller.step() }
        controller.restoreBranch(branch)

        assertEquals("决策层 intel 表往返一致", intelBefore, controller.intelSnapshotOf())
        assertEquals("视图层 reports 表往返一致", reportsBefore, controller.reportsSnapshotOf())

        // 真快照口径：恢复后决策层感知位置取快照时刻报告位置，不沿用推进阶段的实时机动。
        val snapshotEnemy = branch.corps.first { it.id == "b1" }
        val perceived = controller.perceivedEnemiesOf("a1").firstOrNull { it.id == "b1" }
        assertTrue("回滚后决策层仍持有敌军情报", perceived != null)
        assertEquals("感知格回到快照时刻位置", snapshotEnemy.position, perceived!!.position)
    }

    @Test
    fun `④ 空与缺字段存档恢复不崩`() {
        val controller = CampaignController.load("{}", null)

        // 缺省构造：军队空列表，其余表 / 状态走缺省空值兜底。
        val emptyBranch = BranchSnapshot(
            turn = 0,
            elapsedDays = 0,
            corps = emptyList(),
            firedEventIds = emptySet(),
        )
        controller.restoreBranch(emptyBranch)
        val reopened = controller.saveBranch()
        assertEquals(0, reopened.corps.size)
        assertEquals(BattleStatus.ONGOING, reopened.status)

        // 残缺存档：只给军队与回合，情报表 / 日志 / 行军累计全部缺省空。
        controller.step()
        val partial = BranchSnapshot(
            turn = controller.turn,
            elapsedDays = controller.elapsedDays,
            corps = controller.corps.map { it.copy() },
            firedEventIds = emptySet(),
        )
        controller.restoreBranch(partial)
        controller.step()
        controller.runToEnd(3)
    }

    private companion object {

        /** 近距离对抗用例：两支军队落在同一视野内，便于目睹条目落进视图情报表。 */
        const val ENGAGE_JSON: String = """
{
  "id": "b7-engage",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.01, 0.01],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.05, 0.05],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""
    }
}
