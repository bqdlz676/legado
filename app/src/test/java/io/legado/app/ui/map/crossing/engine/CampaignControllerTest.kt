package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.BranchSnapshot
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.EventAttribution
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
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
        assertTrue(snapshot.events.any { it.text.contains("测试点燃") })
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
        assertTrue("盲区主坐标不得回填真实纬度", view.lat.isNaN())
        assertTrue("盲区主坐标不得回填真实经度", view.lng.isNaN())
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
     *   distanceTerm = (seenDistCells / 2).coerceIn(0, type.visionRadiusCells)
     *   ageTerm = age × STALE_DRIFT_CELLS_PER_TURN（=1）
     *   radius  = (distanceTerm + ageTerm).coerceAtMost(MAX_STALE_RADIUS_CELLS = 8)
     * 平场栅格保证通视恒真；布位在载入后直接写运行态格 / 经纬度，首回合目睹距离可确定性留档。
     */
    @Test
    fun `B9-10 记录级半径·远距离目睹：距离项 2，龄期 1-4-10 → 3-6-8 封顶`() {
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
    fun `B9-11 记录级半径·近距离目睹：距离项 1，龄期 1-4-10 → 2-5-8 封顶`() {
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
    fun `B8R3 斜向偏移三二格在四格视野内 按八向度量目睹留档`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        // (8,8) → (11,10)：横三纵二，octile 3.828 ≤ 常规兵半径 4；旧曼哈顿 5 > 4 会漏报。
        a1.position = Cell(8, 8)
        a1.latLng = controller.projection.toGeo(Cell(8, 8))
        b1.position = Cell(11, 10)
        b1.latLng = controller.projection.toGeo(Cell(11, 10))

        controller.step() // 本回合目睹：age=0，模糊半径 0，视图标记已观测。
        assertEquals("斜向格位应在四格八向视野内被目睹", 0, controller.reportRadiusCells("b1"))
        val view = controller.snapshot().corps.first { it.id == "b1" }
        assertTrue("斜向格位应标记为已观测", view.observed)
    }

    @Test
    fun `B8R4 侧翼被迫降级时暴露入账 快照下发降级标识且事件留档`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        // 双方拉开距离避免首回合接触交战干扰暴露口径；目标格给到远端，敌军视野半径拉满
        // 覆盖全图，迫使侧翼严格模式无路、走退化链路（加价 / 折线兜底）。
        a1.position = Cell(2, 5)
        a1.latLng = controller.projection.toGeo(Cell(2, 5))
        b1.position = Cell(28, 26)
        b1.latLng = controller.projection.toGeo(Cell(28, 26))
        a1.target = controller.projection.toGeo(Cell(28, 26))
        b1.visionRadiusCells = 30

        val snapshot = controller.step(mapOf("a1" to Intent.FLANK))

        assertTrue("全图视野封锁侧翼应置降级标志", a1.routeDegraded)
        assertTrue("穿越视野环的降级路线应置暴露姿态", a1.exposed)
        val ownView = snapshot.corps.first { it.id == "a1" }
        assertTrue("快照应向本方下发降级标识", ownView.routeDegraded)
        val degradeEntry = snapshot.events.first { it.text.contains("降级") }
        assertTrue("事件日志应留档降级口径", degradeEntry.text.contains("降级"))
        assertEquals("本方绕行条目应标注我方归属", EventAttribution.FRIENDLY, degradeEntry.attribution)
        val enemyView = snapshot.corps.first { it.id == "b1" }
        assertFalse("未观测敌军不下发我方降级标识", enemyView.routeDegraded)
    }

    @Test
    fun `B8R6 纯地形兜底降级不置暴露 视线穿越事实与降级标志分级`() {
        // 厚水带横断全图且无渡口，无敌军视线参与（b1 远在视野半径外）；侧翼严格 / 加价 A*
        // 均被地形堵死，落到直线折线兜底。旧口径把这种纯地形绕行一并置 exposed，属过度标记。
        val controller = CampaignController.load(BAND_JSON, blockedBandRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        a1.position = Cell(2, 5)
        a1.latLng = controller.projection.toGeo(Cell(2, 5))
        b1.position = Cell(28, 26)
        b1.latLng = controller.projection.toGeo(Cell(28, 26))
        a1.target = controller.projection.toGeo(Cell(2, 26))

        val snapshot = controller.step(mapOf("a1" to Intent.FLANK))

        assertTrue("地形封锁侧翼仍应挂降级绕行标识", a1.routeDegraded)
        assertFalse("纯地形兜底不含视线穿越事实，不得废掉伏击隐蔽姿态", a1.exposed)
        val ownView = snapshot.corps.first { it.id == "a1" }
        assertTrue("本方降级标识照常下发", ownView.routeDegraded)
        val degradeEntry = snapshot.events.first { it.text.contains("降级") }
        assertEquals("本方地形绕行条目标注我方归属", EventAttribution.FRIENDLY, degradeEntry.attribution)
    }

    @Test
    fun `B8R7 上回合佯退暴露 本回合纯地形兜底 暴露姿态复位不残留`() {
        // O1 回归：暴露是回合级姿态。回合 1 佯退事件置暴露；回合 2 水带纯地形兜底、敌军在视野
        // 半径外。旧粘性口径下 exposed 仍残留 true；复位口径下应洗清为未暴露。
        val controller = CampaignController.load(BAND_FEIGN_JSON, blockedBandRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        a1.position = Cell(2, 5)
        a1.latLng = controller.projection.toGeo(Cell(2, 5))
        b1.position = Cell(28, 26)
        b1.latLng = controller.projection.toGeo(Cell(28, 26))
        b1.visionRadiusCells = 1
        a1.target = controller.projection.toGeo(Cell(2, 26))

        controller.step() // 回合 1：佯退诱出事件在复位之后点燃，置 exposed=true。
        assertTrue("回合 1 佯退诱出应置暴露姿态", a1.exposed)

        controller.step(mapOf("a1" to Intent.FLANK)) // 回合 2：水带折线兜底，未穿越视线环。

        assertTrue("地形封锁侧翼仍应挂降级绕行标识", a1.routeDegraded)
        assertFalse("回合 2 无视线穿越事实，上回合暴露不得粘性残留", a1.exposed)
    }

    @Test
    fun `B8R7 上回合暴露 本回合仍穿越视野环 暴露由本回合事实重算为真`() {
        // 反向锁定：复位只清旧姿态，不误置本回合新事实。两回合全图视野封锁，加价路径必然入境，
        // 两回合都应置暴露；回合 2 开头先复位、再由本回合 crossedSight 重新置真。
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        a1.position = Cell(2, 5)
        a1.latLng = controller.projection.toGeo(Cell(2, 5))
        b1.position = Cell(28, 26)
        b1.latLng = controller.projection.toGeo(Cell(28, 26))
        a1.target = controller.projection.toGeo(Cell(28, 26))
        b1.visionRadiusCells = 30

        controller.step(mapOf("a1" to Intent.FLANK))
        assertTrue("回合 1 穿越视野环应置暴露", a1.exposed)

        // 回合 2 前重新钉住敌军位与半径，避免敌军自主机动放大几何前提的偶发性。
        b1.position = Cell(28, 26)
        b1.latLng = controller.projection.toGeo(Cell(28, 26))
        b1.visionRadiusCells = 30
        controller.step(mapOf("a1" to Intent.FLANK))

        assertTrue("回合 2 仍穿越视野环，暴露应按本回合事实重新置真", a1.exposed)
    }

    @Test
    fun `B8R7 连续两回合无视线接触 双方保持未暴露 无粘性无误置`() {
        val controller = CampaignController.load(BAND_JSON, blockedBandRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        a1.position = Cell(2, 5)
        a1.latLng = controller.projection.toGeo(Cell(2, 5))
        b1.position = Cell(28, 26)
        b1.latLng = controller.projection.toGeo(Cell(28, 26))
        b1.visionRadiusCells = 1
        a1.target = controller.projection.toGeo(Cell(2, 26))

        controller.step(mapOf("a1" to Intent.FLANK))
        assertFalse("回合 1 纯地形兜底不得置暴露", a1.exposed)
        assertFalse("回合 1 敌军未接战不得被置暴露", b1.exposed)

        controller.step(mapOf("a1" to Intent.FLANK))
        assertFalse("回合 2 仍无视线穿越，不得沿用上回合状态误置", a1.exposed)
        assertFalse("回合 2 敌军保持未暴露", b1.exposed)
    }

    @Test
    fun `B8R6 未观测敌军掩码：已置降级的未观测敌军视图清空标识坐标路线`() {
        // N2 正向断言：夹具内 b1 近距被目睹、b2 远距未观测；两支敌军运行态都人为置 routeDegraded，
        // 若掩码分支失效（未观测也透传），b2 视图的 routeDegraded / 路线 / 坐标断言会变红。
        val controller = CampaignController.load(MASK_JSON, flatPlainRaster())
        controller.step()
        val b1 = controller.corps.first { it.id == "b1" }
        val b2 = controller.corps.first { it.id == "b2" }
        b1.routeDegraded = true
        b2.routeDegraded = true

        val snapshot = controller.snapshot()
        val observed = snapshot.corps.first { it.id == "b1" }
        val unseen = snapshot.corps.first { it.id == "b2" }

        assertTrue("近距敌军应被本方目睹", observed.observed)
        assertNotNull("已观测敌军下发上报纬度", observed.reportLat)
        assertTrue("已观测敌军保留降级标识", observed.routeDegraded)

        assertFalse("远距敌军不应被标记观测", unseen.observed)
        assertNull("未观测敌军不下上报纬度", unseen.reportLat)
        assertNull("未观测敌军不下上报经度", unseen.reportLng)
        assertTrue("未观测敌军规划路线不下发", unseen.route.isEmpty())
        assertFalse("未观测敌军即使运行态降级，视图也必须清空降级标识", unseen.routeDegraded)
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

    // ── B9-R2 情报数据层掩码收口（N1）+ 五条必补断言 ──────────────────────────

    @Test
    fun `B9-R2-0 未观测敌军意图与目标点掩码为 null，已观测敌军完整下发`() {
        val controller = CampaignController.load(MASK_JSON, flatPlainRaster())
        controller.step()
        val b1 = controller.corps.first { it.id == "b1" }
        val b2 = controller.corps.first { it.id == "b2" }
        // 运行态两支敌军都挂目标点：验证掩码只作用于视图下发，不清空运行态真值。
        b1.target = controller.projection.toGeo(Cell(20, 20))
        b2.target = controller.projection.toGeo(Cell(25, 25))

        val snapshot = controller.snapshot()
        val observed = snapshot.corps.first { it.id == "b1" }
        val unseen = snapshot.corps.first { it.id == "b2" }

        assertTrue("近距敌军应被本方目睹", observed.observed)
        assertNotNull("已观测敌军意图照常下发", observed.intent)
        assertEquals("已观测敌军目标点纬度照常下发", b1.target!!.lat, observed.targetLat!!, 1e-9)
        assertEquals("已观测敌军目标点经度照常下发", b1.target!!.lng, observed.targetLng!!, 1e-9)

        assertFalse("远距敌军不应被标记观测", unseen.observed)
        assertNull("未观测敌军意图不下发（未知态，不用缺省意图冒充情报）", unseen.intent)
        assertNull("未观测敌军目标点纬度不下发", unseen.targetLat)
        assertNull("未观测敌军目标点经度不下发", unseen.targetLng)
    }

    @Test
    fun `B9-R2-1 陈旧报告位置落在当前模糊半径内，刚目睹报告格即真值格`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        a1.position = Cell(8, 8)
        a1.latLng = controller.projection.toGeo(Cell(8, 8))
        val sightCell = Cell(12, 8) // octile 4 = 常规兵视野边界，本回合目睹
        b1.position = sightCell
        b1.latLng = controller.projection.toGeo(sightCell)

        controller.step(mapOf("a1" to Intent.HOLD, "b1" to Intent.HOLD))

        // 断言①-刚目睹：半径 0，报告格必须与真值格重合。
        assertEquals(0, controller.reportRadiusCells("b1"))
        val fresh = controller.snapshot().corps.first { it.id == "b1" }
        val freshReportCell = controller.projection.toCell(Geo(fresh.reportLat!!, fresh.reportLng!!))
        assertEquals("刚目睹报告格应等于真值格", b1.position, freshReportCell)

        // 移出视野一格：(13,8) 距 5 > 视野 4；双方 HOLD 不动，真值停在钉住格。
        b1.position = Cell(13, 8)
        b1.latLng = controller.projection.toGeo(Cell(13, 8))
        controller.step(mapOf("a1" to Intent.HOLD, "b1" to Intent.HOLD))

        // 断言①-陈旧：报告格与当前真值格的八向距离不得超出当前模糊半径。
        val radius = controller.reportRadiusCells("b1")!!
        val stale = controller.snapshot().corps.first { it.id == "b1" }
        val staleReportCell = controller.projection.toCell(Geo(stale.reportLat!!, stale.reportLng!!))
        assertTrue(
            "陈旧报告格 $staleReportCell 与真值格 ${b1.position} 距离应 ≤ 半径 $radius",
            octileCells(staleReportCell, b1.position) <= radius,
        )
    }

    @Test
    fun `B9-R2-2 同回合两次半径取用一致，两次快照同字段相等`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        a1.position = Cell(8, 8)
        a1.latLng = controller.projection.toGeo(Cell(8, 8))
        b1.position = Cell(12, 8)
        b1.latLng = controller.projection.toGeo(Cell(12, 8))
        controller.step(mapOf("a1" to Intent.HOLD, "b1" to Intent.HOLD))

        // 断言②：同回合两次取半径结果相等（纯函数确定性，无逐帧飘移）。
        val radiusFirst = controller.reportRadiusCells("b1")
        val radiusSecond = controller.reportRadiusCells("b1")
        assertEquals("同回合两次半径取用应相等", radiusFirst, radiusSecond)

        // 断言②：同回合两次快照逐军同字段相等。
        val snapFirst = controller.snapshot()
        val snapSecond = controller.snapshot()
        assertEquals(snapFirst.corps.size, snapSecond.corps.size)
        snapFirst.corps.zip(snapSecond.corps).forEach { (x, y) ->
            assertEquals(x.id, y.id)
            assertEquals(x.observed, y.observed)
            assertEquals(x.reportLat, y.reportLat)
            assertEquals(x.reportLng, y.reportLng)
            assertEquals(x.intent, y.intent)
            assertEquals(x.targetLat, y.targetLat)
            assertEquals(x.targetLng, y.targetLng)
            assertEquals(x.route, y.route)
            assertEquals(x.routeDegraded, y.routeDegraded)
            assertEquals(x.lat, y.lat, 1e-9)
            assertEquals(x.lng, y.lng, 1e-9)
        }
    }

    @Test
    fun `B9-R2-3 视野精确边界：距 = 半径可见，距 = 半径 + 1 不可见`() {
        fun snapshotAt(dist: Int): io.legado.app.ui.map.crossing.model.TurnSnapshot {
            val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
            val a1 = controller.corps.first { it.id == "a1" }
            val b1 = controller.corps.first { it.id == "b1" }
            a1.position = Cell(8, 8)
            a1.latLng = controller.projection.toGeo(Cell(8, 8))
            b1.position = Cell(8 + dist, 8)
            b1.latLng = controller.projection.toGeo(Cell(8 + dist, 8))
            controller.step(mapOf("a1" to Intent.HOLD, "b1" to Intent.HOLD))
            return controller.snapshot()
        }

        // 断言③：常规兵视野半径 4，可见性闸门为闭区间（distCells <= visionRadiusCells）。
        val edge = snapshotAt(4).corps.first { it.id == "b1" }
        assertTrue("距 = 视野半径 4 应标记观测", edge.observed)
        assertNotNull("贴边目睹应下发上报坐标", edge.reportLat)

        val beyond = snapshotAt(5).corps.first { it.id == "b1" }
        assertFalse("距 = 半径 + 1（5）不应标记观测", beyond.observed)
        assertNull("视野外敌军不下发上报坐标", beyond.reportLat)
    }

    @Test
    fun `B9-R2-4 距离项半距封顶兵种视野半径，负值钳到 0`() {
        val controller = CampaignController.load(RADIUS_JSON, flatPlainRaster())
        // 断言④：常规兵视野 4——目击距离 10 → 半距 5 封顶 4；距离 8 → 半距 4 不触顶。
        assertEquals(4, controller.distanceTermCells(10, ArmyType.REGULAR))
        assertEquals(4, controller.distanceTermCells(8, ArmyType.REGULAR))
        assertEquals(1, controller.distanceTermCells(2, ArmyType.REGULAR))
        assertEquals(0, controller.distanceTermCells(0, ArmyType.REGULAR))
        assertEquals("负目击距离钳到 0", 0, controller.distanceTermCells(-4, ArmyType.REGULAR))
        // 骑兵视野 6：半距超出同样封顶到 6，半径表真源仍取 model 层兵种口径。
        assertEquals(6, controller.distanceTermCells(20, ArmyType.CAVALRY))
    }

    @Test
    fun `B9-R2-5 两敌同格：感知集两条目不合并，裁决与快照不抛`() {
        val controller = CampaignController.load(TIE_JSON, flatPlainRaster())
        val a1 = controller.corps.first { it.id == "a1" }
        val b1 = controller.corps.first { it.id == "b1" }
        val b2 = controller.corps.first { it.id == "b2" }
        a1.position = Cell(8, 8)
        a1.latLng = controller.projection.toGeo(Cell(8, 8))
        b1.position = Cell(12, 8)
        b1.latLng = controller.projection.toGeo(Cell(12, 8))
        b2.position = Cell(12, 8)
        b2.latLng = controller.projection.toGeo(Cell(12, 8))

        controller.step(mapOf("a1" to Intent.HOLD, "b1" to Intent.HOLD, "b2" to Intent.HOLD))

        // 断言⑤：同格两敌在感知集各占一条（按军 id 去重，不按位置合并），平局取用稳定不抛。
        val perceived = controller.perceivedEnemiesOf("a1")
        val tied = perceived.filter { it.id == "b1" || it.id == "b2" }
        assertEquals("同格两敌在感知集应各占一条", 2, tied.size)
        assertEquals("两条感知记录位置相同（同格平局）", tied[0].position, tied[1].position)

        val snapshot = controller.snapshot()
        val enemyViews = snapshot.corps.filter { it.sideId == "b" }
        assertEquals("快照应保留两支敌军视图", 2, enemyViews.size)
        assertTrue("贴边目睹两支敌军均标记观测", enemyViews.all { it.observed })
    }

    /** 平场栅格：全域同高程，通视恒真、无水面 / 关隘，供半径用例确定性布位。 */
    private fun flatPlainRaster(): HeightRaster =
        HeightRaster.create(
            CampaignController.GRID_N,
            CampaignController.GRID_N,
            IntArray(CampaignController.GRID_N * CampaignController.GRID_N) { 16 },
            degradedHint = false,
        )

    /** 厚水带栅格：rows 10..21 零高程全域为非渡口水面，横断全图，供纯地形兜底用例布位。 */
    private fun blockedBandRaster(): HeightRaster {
        val n = CampaignController.GRID_N
        val data = IntArray(n * n) { i -> if (i / n in 10..21) 0 else 128 }
        return HeightRaster.create(n, n, data, degradedHint = false)
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

        /** 纯地形兜底用例：双 REGULAR；厚水带栅格由测试体注入，JSON 坐标仅作占位。 */
        const val BAND_JSON: String = """
{
  "id": "b8r6-band",
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

        /** 佯退 + 水带用例：回合 1 触发器点燃 feignedRetreat 暴露 a1；回合 2 走纯地形兜底，
         *  供 B8R7 回合复位回归用例验证旧暴露不跨回合残留。 */
        const val BAND_FEIGN_JSON: String = """
{
  "id": "b8r7-band-feign",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 20,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.01, 0.01],
      "strength": 5, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.02, 0.02],
      "strength": 5, "type": "regular", "morale": "steady", "intent": "hold" }
  ],
  "events": [
    { "trigger": { "turn": 1 }, "action": "feignedRetreat", "target": "a1", "reason": "佯退诱敌" }
  ]
}
"""

        /** 掩码对比用例：a1 本方，b1 近距被目睹，b2 远距未观测；双敌军对照快照口径。 */
        const val MASK_JSON: String = """
{
  "id": "b8r6-mask",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.000, 0.000],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.001, 0.001],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b2", "name": "丙", "side": "b", "initialPos": [0.40, 0.40],
      "strength": 10, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""

        /** 同格平局用例：b1 / b2 同阵营同格，验证感知集按军 id 保留两条、不按位置合并。 */
        const val TIE_JSON: String = """
{
  "id": "b9-r2-tie",
  "terrainFocus": { "center": [0.0, 0.0], "radius": 50 },
  "duration": 10,
  "victory": { "type": "defend", "side": "a" },
  "armies": [
    { "id": "a1", "name": "甲", "side": "a", "initialPos": [0.01, 0.01],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b1", "name": "乙", "side": "b", "initialPos": [0.02, 0.02],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" },
    { "id": "b2", "name": "丙", "side": "b", "initialPos": [0.02, 0.02],
      "strength": 8, "type": "regular", "morale": "steady", "intent": "hold" }
  ]
}
"""
    }
}
