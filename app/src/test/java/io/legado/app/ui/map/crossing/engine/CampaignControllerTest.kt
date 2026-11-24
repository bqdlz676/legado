package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.BranchSnapshot
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `B4-R load 顶层兜底：畸形输入返回降级空战役，warning 文案可读且快照不留白`() {
        val controller = CampaignController.load("{不是合法 json", null)
        assertTrue(controller.degraded)
        assertTrue(controller.corps.isEmpty())
        assertTrue("warning 必须能被界面读取", controller.warnings.isNotEmpty())
        val snapshot = controller.snapshot()
        assertEquals(0, snapshot.corps.size)
        assertTrue("降级态快照事件日志可读", snapshot.events.isNotEmpty() || controller.warnings.isNotEmpty())
    }

    @Test
    fun `B4-R 装配链抛异常：顶层兜底走降级空战场，warning 可读且快照不留白`() {
        // 注入装配期故障（internal 桩，生产路径恒 null），验证 catch → assembleDegraded 分支：
        // 不抛、降级标志置真、corps 清空、warning 含可读失败文案、快照 corps 为 0。
        CampaignController.assemblyFault = IllegalStateException("栅格解码崩溃")
        try {
            val controller = CampaignController.load(DemoCampaigns.CHISHUI_JSON, null)
            assertTrue(controller.degraded)
            assertTrue(controller.corps.isEmpty())
            assertTrue(controller.warnings.any { it.contains("战役载入失败") })
            assertEquals(0, controller.snapshot().corps.size)
        } finally {
            CampaignController.assemblyFault = null
        }
    }

    @Test
    fun `B4-R rivers 字段整体为 JsonNull 时记字段级 warning 且回落空表`() {
        val def = CampaignJsonParser.parse("""
        {
          "id": "rivers-null",
          "terrain": { "rivers": null }
        }
        """)
        assertTrue(def.terrain.rivers.isEmpty())
        assertTrue(def.warnings.any { it.contains("rivers") && it.contains("null") })
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

    @Test
    fun `F2 外交盟友不进入伤害结算，并肩多回合兵力不互耗`() {
        val controller = CampaignController.load(ALLIED_JSON, null)
        val ally = controller.corps.first { it.id == "b1" }
        val allyBefore = ally.strength
        repeat(5) { controller.step() }
        assertEquals("盟友不应被己方阵营攻击", allyBefore, ally.strength, 1e-6)
    }

    @Test
    fun `F6 分支存档回滚据点占领计数，不沿用旧时间线`() {
        val controller = CampaignController.load(CAPTURE_JSON, null)
        controller.step()
        val branch = controller.saveBranch()
        val holdAtBranch = branch.captureHold.toMap()
        repeat(3) { controller.step() }

        controller.restoreBranch(branch)
        val restored = controller.saveBranch()
        assertEquals(holdAtBranch, restored.captureHold)
        assertEquals(branch.pointHolders, restored.pointHolders)
    }

    @Test
    fun `F8 视野外敌军不上报实时坐标，从未观测 report 为 null`() {
        val controller = CampaignController.load(FOG_JSON, null)
        controller.step()
        val enemyView = controller.snapshot().corps.first { it.id == "b1" }
        assertFalse("视野外敌军不应被标记观测", enemyView.observed)
        assertTrue("从未观测的敌军不给实时坐标", enemyView.reportLat == null && enemyView.reportLng == null)
    }

    // ── B9 情报模糊半径（决策 / 展示双路）─────────────────────────

    @Test
    fun `B9-1 未侦察敌军：视图不泄露真实坐标，路线清空，主坐标为 NaN 占位`() {
        val controller = CampaignController.load(FOG_JSON, null)
        controller.step()
        val real = controller.corps.first { it.id == "b1" }
        val view = controller.snapshot().corps.first { it.id == "b1" }

        assertFalse(view.observed)
        assertNull(view.reportLat)
        assertNull(view.reportLng)
        assertTrue("盲区主坐标不得回填真实纬度", Double.isNaN(view.lat))
        assertTrue("盲区主坐标不得回填真实经度", Double.isNaN(view.lng))
        assertTrue("未观测敌军规划路线不下发", view.route.isEmpty())
        assertNull("从未侦察敌军无模糊半径", controller.reportRadiusCells("b1"))
        assertTrue("盲区占位文案不得为空", !controller.blindZoneLabel("b1").isNullOrEmpty())
        // 真实坐标仍只存在于内部运行态，不进快照主坐标 / 上报坐标。
        assertTrue(real.latLng.lat != view.lat || real.latLng.lng != view.lng)
    }

    @Test
    fun `B9-2 侦察目睹：报告刷新为实时真值，模糊半径清零，盲区文案消失`() {
        val controller = CampaignController.load(NEAR_JSON, null)
        controller.step()
        val view = controller.snapshot().corps.first { it.id == "b1" }

        assertTrue("近距离敌军应被标记观测", view.observed)
        assertNotNull("目睹后上报坐标非空", view.reportLat)
        assertNotNull("目睹后上报经度非空", view.reportLng)
        assertEquals("刚目睹半径为 0", 0, controller.reportRadiusCells("b1"))
        assertNull("已有报告的敌军不出盲区文案", controller.blindZoneLabel("b1"))
    }

    @Test
    fun `B9-3 模糊半径：随龄期单调放大并在封顶处持平`() {
        val ages = listOf(0, 1, 2, 3, 4, 5, 10, 20)
        val radii = ages.map { CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, it) }
        assertTrue("半径随龄期单调不减", radii.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(5, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 1))
        assertEquals(8, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 4))
        assertEquals("超封顶后保持上限", 8, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 20))
    }

    @Test
    fun `B9-4 交战接触：接触即情报刷新，本方报告落条且半径清零`() {
        val controller = CampaignController.load(NEAR_JSON, null)
        controller.step()
        val view = controller.snapshot().corps.first { it.id == "b1" }

        assertTrue("接战后敌军应在本方报告中", view.observed)
        assertNotNull("接触刷新后上报坐标非空", view.reportLat)
        assertEquals("接触视同贴脸侦察，半径为 0", 0, controller.reportRadiusCells("b1"))
        assertNull("接战后不再标盲区", controller.blindZoneLabel("b1"))
    }

    @Test
    fun `B9-5 空情报占位：从未侦察敌军返回盲区文案，友方不返回`() {
        val controller = CampaignController.load(FOG_JSON, null)
        controller.step()
        assertEquals(CampaignController.BLIND_ZONE_LABEL, controller.blindZoneLabel("b1"))
        assertNull("本方军队不标盲区", controller.blindZoneLabel("a1"))
    }

    @Test
    fun `B9-6 旧存档兼容：缺情报字段的分支存档恢复后降级位置未知且不崩`() {
        val controller = CampaignController.load(FOG_JSON, null)
        controller.step()
        val branch = controller.saveBranch()
        // 模拟旧版存档：只传核心四字段，据点计数 / 归属 / 情报全部走默认空值重建。
        val oldStyle = BranchSnapshot(
            turn = branch.turn,
            elapsedDays = branch.elapsedDays,
            corps = branch.corps,
            firedEventIds = branch.firedEventIds,
        )

        controller.restoreBranch(oldStyle)

        assertEquals(oldStyle.turn, controller.turn)
        assertNull("旧档无目睹记录，视图半径为 null", controller.reportRadiusCells("b1"))
        assertNotNull("恢复后决策层情报按存档军位重新播种", controller.perceivedEnemiesOf("a1").firstOrNull { it.id == "b1" })
        assertEquals(CampaignController.BLIND_ZONE_LABEL, controller.blindZoneLabel("b1"))
    }

    @Test
    fun `B9-7 同回合多观察者侦察：目标只入感知集一次，报告不重复落条`() {
        val controller = CampaignController.load(DUO_JSON, null)
        controller.step()
        val perceived = controller.perceivedEnemiesOf("a1")
        assertEquals("感知集中敌军条目唯一", 1, perceived.count { it.id == "b1" })
        val view = controller.snapshot().corps.first { it.id == "b1" }
        assertTrue(view.observed)
        assertEquals(0, controller.reportRadiusCells("b1"))
        assertNotNull(view.reportLat)
    }

    @Test
    fun `B9-8 半径边界：龄期 0 与负值归零，超长龄期封顶，兵种基准解耦`() {
        assertEquals(0, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 0))
        assertEquals("负龄期视同刚目睹", 0, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, -3))
        assertEquals(8, CampaignController.fuzzyRadiusCells(ArmyType.REGULAR, 10))
        assertEquals(4, CampaignController.fuzzyRadiusCells(ArmyType.MILITIA, 1))
        assertEquals(7, CampaignController.fuzzyRadiusCells(ArmyType.CAVALRY, 1))
    }

    @Test
    fun `B9-9 时间推进不自动刷新：未目睹敌军感知位置仍是开局部署报告`() {
        val controller = CampaignController.load(FOG_JSON, null)
        controller.step()
        controller.step()
        val perceived = controller.perceivedEnemiesOf("a1").firstOrNull { it.id == "b1" }
        assertNotNull(perceived)
        assertEquals("感知经度取开局报告 0.40", 0.40, perceived!!.latLng.lng, 1e-9)
        assertEquals("感知纬度取开局报告 0.40", 0.40, perceived.latLng.lat, 1e-9)
        val view = controller.snapshot().corps.first { it.id == "b1" }
        assertNull("两回合后仍未目睹，视图不下发坐标", view.reportLat)
    }

    // ── B9 补测：recordRadius 记录级半径确定性（距离项 + 龄期扩圈 + 封顶）─────────

    /**
     * 记录级半径公式（与 [CampaignController.recordRadius] 同口径，先算后断言）：
     *   age = turn - seenTurn；age ≤ 0 → 0
     *   distanceTerm = (seenDistCells / 2).coerceIn(0, visionRadiusFor(type))
     *   ageTerm = age × STALE_DRIFT_CELLS_PER_TURN（=1）
     *   radius  = (distanceTerm + ageTerm).coerceAtMost(MAX_STALE_RADIUS_CELLS = 8)
     * 平场栅格保证通视恒真；布位在载入后直接写运行态格 / 经纬度，首回合目睹距离可确定性留档。
     */
    @Test
    fun `B9-10 记录级半径·远距离目睹：距离项 2，龄期 1/4/10 → 3/6/8 封顶`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        a1.position = Cell(8, 8)
        a1.latLng = controller.projection.toGeo(Cell(8, 8))
        b1.position = Cell(12, 8)
        b1.latLng = controller.projection.toGeo(Cell(12, 8))

        controller.step() // turn=1：目睹落条 seenTurn=1、seenDistCells=4；age=0 → 半径 0
        assertEquals(0, controller.reportRadiusCells("b1"))

        // 移出视野：b1 拉到 col=28（octile 20 > 视野 4），此后不再目睹，报告保留旧时刻 / 旧距离。
        b1.position = Cell(28, 8)
        b1.latLng = controller.projection.toGeo(Cell(28, 8))

        controller.step() // turn=2，age=1：距离项 4/2=2，时间项 1 → 2+1=3
        assertEquals(3, controller.reportRadiusCells("b1"))

        repeat(3) { controller.step() } // turn=5，age=4：2+4=6
        assertEquals(6, controller.reportRadiusCells("b1"))

        repeat(6) { controller.step() } // turn=11，age=10：2+10=12，封顶 8
        assertEquals(8, controller.reportRadiusCells("b1"))
    }

    @Test
    fun `B9-11 记录级半径·近距离目睹：距离项 1，龄期 1/4/10 → 2/5/8 封顶`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        // octile=2：目睹距离留档 2 格，距离项 2/2=1（不触发兵种视野封顶）。
        a1.position = Cell(8, 8)
        a1.latLng = controller.projection.toGeo(Cell(8, 8))
        b1.position = Cell(10, 8)
        b1.latLng = controller.projection.toGeo(Cell(10, 8))

        controller.step() // turn=1，age=0 → 0
        assertEquals(0, controller.reportRadiusCells("b1"))

        b1.position = Cell(28, 8)
        b1.latLng = controller.projection.toGeo(Cell(28, 8))

        controller.step() // age=1：1+1=2
        assertEquals(2, controller.reportRadiusCells("b1"))

        repeat(3) { controller.step() } // age=4：1+4=5
        assertEquals(5, controller.reportRadiusCells("b1"))

        repeat(6) { controller.step() } // age=10：1+10=11，封顶 8
        assertEquals(8, controller.reportRadiusCells("b1"))
    }

    @Test
    fun `B9-12 陈旧报告位置冻结：走出视野后上报坐标仍是目睹位，不回填实时坐标`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        a1.position = Cell(8, 8)
        a1.latLng = controller.projection.toGeo(Cell(8, 8))
        val sightCell = Cell(12, 8)
        b1.position = sightCell
        b1.latLng = controller.projection.toGeo(sightCell)
        val sightGeo = controller.projection.toGeo(sightCell)

        controller.step()
        b1.position = Cell(28, 8)
        b1.latLng = controller.projection.toGeo(Cell(28, 8))
        repeat(4) { controller.step() } // age=4，报告已陈旧

        val view = controller.snapshot().corps.first { it.id == "b1" }
        assertFalse(view.observed)
        assertEquals("陈旧报告纬度冻结在目睹位", sightGeo.lat, view.reportLat!!, 1e-9)
        assertEquals("陈旧报告经度冻结在目睹位", sightGeo.lng, view.reportLng!!, 1e-9)
        assertTrue("未观测敌军路线不下发", view.route.isEmpty())
    }

    /** 平场栅格：全域同高程，通视恒真、无水面 / 关隘，供半径用例确定性布位。 */
    private fun flatPlainRaster(): HeightRaster =
        HeightRaster.create(
            CampaignController.GRID_N,
            CampaignController.GRID_N,
            IntArray(CampaignController.GRID_N * CampaignController.GRID_N) { 16 },
            degradedHint = false,
        )

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

        const val ALLIED_JSON: String = """
{
  "id": "allied-test",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "annihilate", "target": "c1", "side": "a" },
  "sides": [ { "id": "a" }, { "id": "b" }, { "id": "c" } ],
  "diplomacy": [ { "a": "a", "b": "b", "relation": "allied" } ],
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.01, 0.01],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.02, 0.02],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "c1", "name": "丙", "side": "c", "initialPos": [0.40, 0.40],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""

        const val CAPTURE_JSON: String = """
{
  "id": "capture-test",
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

        const val FOG_JSON: String = """
{
  "id": "fog-test",
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

        /** 近距离用例：双方近乎同格，首回合即在视野内并触发接触交战；等力交战不致溃散。 */
        const val NEAR_JSON: String = """
{
  "id": "b9-near",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.000, 0.000],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.001, 0.001],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""

        /** 双观察者用例：同阵营两支军队贴近同一敌军，验证同回合多侦察不重复落条。 */
        const val DUO_JSON: String = """
{
  "id": "b9-duo",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.000, 0.000],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "a2", "name": "丙", "side": "a", "initialPos": [0.001, 0.001],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.002, 0.002],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""

        /** 半径确定性用例：双 REGULAR 据守、等力；初始格位由测试体直接覆写，JSON 坐标仅作占位。 */
        const val RADIUS_JSON: String = """
{
  "id": "b9-radius",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 20,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.01, 0.01],
      "strength": 5, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.02, 0.02],
      "strength": 5, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""
    }
}
