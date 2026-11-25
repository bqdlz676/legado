package io.legado.app.ui.map.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 战役回放导演单测（切片-07）：出生编排时序、推演时钟、事件派发、栏位空闲隐藏、
 * 剧场锁定、交战两阶段、棋子视觉派生。纯 JVM，时钟注入可直接推进毫秒断言。
 */
class ReplayDirectorTest {

    private fun director(now: Long = 0L, campaign: Campaign = SeedReplay.changping): Pair<ReplayDirector, MutableLongClock> {
        val clock = MutableLongClock(now)
        return ReplayDirector(campaign, clock::get) to clock
    }

    // ── 出生编排 ──

    @Test
    fun `出生编排按毫秒推进五个阶段`() {
        val (director, clock) = director(0)
        clock.set(200); assertEquals(BirthStage.GRID, director.birthStage(clock.get()))
        clock.set(600); assertEquals(BirthStage.RISE, director.birthStage(clock.get()))
        clock.set(900); assertEquals(BirthStage.FOG, director.birthStage(clock.get()))
        clock.set(1100); assertEquals(BirthStage.DROP, director.birthStage(clock.get()))
        clock.set(1300); assertEquals(BirthStage.READY, director.birthStage(clock.get()))
    }

    // ── 推演时钟 ──

    @Test
    fun `播放按 1_2 秒每回合推进，暂停后不走`() {
        val (director, clock) = director()
        director.play()
        director.update(ReplayConfig.TURN_DURATION_MS)
        assertEquals(1.0, director.turn, 1e-9)
        director.pause()
        director.update(ReplayConfig.TURN_DURATION_MS)
        assertEquals(1.0, director.turn, 1e-9)
    }

    @Test
    fun `播完自动暂停并钳制在总回合`() {
        val (director, _) = director()
        director.seek(30.0)
        director.play()
        director.update(10_000)
        assertFalse(director.playing)
        assertEquals(30.0, director.turn, 1e-9)
    }

    @Test
    fun `单步仅在暂停态可用，跳转钳制区间`() {
        val (director, _) = director()
        director.play()
        director.stepForward()
        assertEquals(0.0, director.turn, 1e-9)
        director.pause()
        director.stepForward()
        assertEquals(1.0, director.turn, 1e-9)
        director.seek(99.0)
        assertEquals(30.0, director.turn, 1e-9)
        director.seek(-3.0)
        assertEquals(0.0, director.turn, 1e-9)
    }

    @Test
    fun `阶段铜点跳转到该阶段首回合`() {
        val (director, _) = director()
        val phase = SeedReplay.changping.phases.first { it.label == "合围" }
        director.jumpPhase(phase)
        assertEquals(phase.turn.toDouble(), director.turn, 1e-9)
        assertEquals(phase.label, director.currentPhase()?.label)
    }

    // ── 事件派发 ──

    @Test
    fun `回合事件依次改变士气直至溃散`() {
        val (director, _) = director()

        director.seek(16.0)
        director.update(0)
        val lianpo = director.armies().first { it.id == "lianpo" }
        assertEquals(Morale.WAVERING, lianpo.morale)

        director.seek(22.0)
        director.update(0)
        val zhaokuo = director.armies().first { it.id == "zhaokuo" }
        // 断粮清补给，不降士气（D2 语义修正：旧实现错把 supplyCut 写成士气动摇）。
        assertEquals(0, zhaokuo.supply)
        assertEquals(Morale.STEADY, zhaokuo.morale)

        director.seek(28.0)
        director.update(0)
        val routed = director.armies().first { it.id == "zhaokuo" }
        assertEquals(Morale.ROUT, routed.morale)
    }

    // ── 栏位空闲隐藏 ──

    @Test
    fun `三秒无操作顶栏淡出底栏收起，碰屏恢复`() {
        val (director, clock) = director()
        director.update(0)
        assertTrue(director.topBarVisible)
        assertTrue(director.bottomBarExpanded)

        clock.set(ReplayConfig.TOP_BAR_IDLE_MS + 1)
        director.update(0)
        assertFalse(director.topBarVisible)
        assertFalse(director.bottomBarExpanded)

        director.interact()
        assertTrue(director.topBarVisible)
        assertTrue(director.bottomBarExpanded)
    }

    @Test
    fun `信息卡三秒超时收回`() {
        val (director, clock) = director()
        director.showInfo(ReplayInfoKind.PIECE, "白起", "主力 · 士气高昂")
        assertNotNull(director.infoCard)
        clock.set(ReplayConfig.INFO_CARD_TTL_MS)
        director.update(0)
        assertFalse(director.infoCard != null)
    }

    // ── 剧场模式 ──

    @Test
    fun `剧场模式锁定选中与碰屏，退出后恢复`() {
        val (director, clock) = director()
        director.enterTheater()
        assertTrue(director.theaterMode)
        director.selectPiece("baiqi")
        assertFalse(director.selectedPieceId == "baiqi")
        director.interact()
        assertTrue(director.theaterMode)
        director.exitTheater()
        assertFalse(director.theaterMode)
    }

    // ── 交战效果 ──

    @Test
    fun `敌我棋子相邻触发交战，两阶段后结束`() {
        val campaign = Campaign(
            id = "clash-test", title = "测试交战", era = "", context = "",
            rounds = 10,
            sides = listOf(
                BattleSide("a", "甲", SideRole.OWN),
                BattleSide("b", "乙", SideRole.ENEMY),
            ),
            armies = listOf(
                ArmyPiece("a1", "甲一", "a", 35.000, 112.000, 10, "elite",
                    Morale.HIGH, Intent.ADVANCE, 35.01, 112.01),
                ArmyPiece("b1", "乙一", "b", 35.005, 112.005, 8, "regular",
                    Morale.HIGH, Intent.ADVANCE, 35.00, 112.00),
            ),
            phases = emptyList(), events = emptyList(), dailyLi = 50,
        )
        val (director, clock) = director(campaign = campaign)
        director.update(0)
        assertEquals(1, director.clashes.size)

        val clash = director.clashes.first()
        clock.set(100)
        assertEquals(ClashStage.RING, clash.stage(clock.get()))
        assertTrue(clash.ringRadiusDp(clock.get()) > 0f)
        clock.set(400)
        assertEquals(ClashStage.CHARGE, clash.stage(clock.get()))
        assertEquals(2, clash.chargeCycles(clock.get()))
        clock.set(700)
        assertEquals(ClashStage.DONE, clash.stage(clock.get()))
        director.update(0)
        assertTrue(director.clashes.isEmpty())
    }

    @Test
    fun `冲击环 200ms 扩到 120dp`() {
        val effect = ClashEffect("c1", LatLng(35.0, 112.0), 0)
        assertEquals(ReplayConfig.CLASH_RING_RADIUS_DP, effect.ringRadiusDp(200), 0.01f)
        val sparks = effect.sparks()
        assertTrue(sparks.size in ReplayConfig.SPARKS_MIN..ReplayConfig.SPARKS_MAX)
    }

    @Test
    fun `遭遇光环 300ms 内有效`() {
        val ring = EncounterRing("e1", LatLng(35.0, 112.0), 0)
        assertTrue(ring.active(150))
        assertFalse(ring.active(300))
    }

    // ── 棋子 / 箭头视觉 ──

    @Test
    fun `溃散碎片 6 到 8 片，超时留残影圈`() {
        val rout = ArmyPiece("r1", "溃兵", "b", 35.0, 112.0, 5, "militia",
            Morale.ROUT, Intent.RETREAT)
        val visual = PieceVisual.of(rout, SideRole.ENEMY, selected = false, routElapsedMs = 0)
        assertNotNull(visual.routShards)
        assertTrue(visual.routShards!!.size in ReplayConfig.ROUT_SHARDS_MIN..ReplayConfig.ROUT_SHARDS_MAX)
        val late = PieceVisual.of(rout, SideRole.ENEMY, selected = false,
            routElapsedMs = ReplayConfig.ROUT_DURATION_MS + 1)
        assertTrue(late.residualRing)
    }

    @Test
    fun `设伏棋子虚化 0_3，选中呼吸加快到 400ms`() {
        val ambush = ArmyPiece("am1", "伏兵", "a", 35.0, 112.0, 6, "cavalry",
            Morale.HIGH, Intent.AMBUSH)
        val visual = PieceVisual.of(ambush, SideRole.OWN, selected = false)
        assertEquals(ReplayConfig.AMBUSH_OPACITY, visual.opacity, 0f)

        val normal = ArmyPiece("n1", "正兵", "a", 35.1, 112.1, 10, "elite",
            Morale.HIGH, Intent.ADVANCE)
        val selected = PieceVisual.of(normal, SideRole.OWN, selected = true)
        assertEquals(ReplayConfig.SELECTED_BREATH_MS, selected.breathMs)
    }

    @Test
    fun `主力棋子尺寸乘 1_5 倍`() {
        val main = ArmyPiece("m1", "主力", "a", 35.0, 112.0, 14, "elite",
            Morale.HIGH, Intent.ADVANCE)
        val minor = ArmyPiece("s1", "偏师", "a", 35.2, 112.2, 5, "militia",
            Morale.HIGH, Intent.ADVANCE)
        val mainVisual = PieceVisual.of(main, SideRole.OWN, false)
        val minorVisual = PieceVisual.of(minor, SideRole.OWN, false)
        assertTrue(mainVisual.scale > minorVisual.scale)
        assertTrue(mainVisual.scale / minorVisual.scale > ReplayConfig.MAIN_FORCE_SCALE * 0.9)
    }

    @Test
    fun `意图映射箭头五种样式，设伏不出兵锋`() {
        assertEquals(ArrowStyle.SOLID, BattleArrow.styleFor(Intent.ADVANCE))
        assertEquals(ArrowStyle.DASHED, BattleArrow.styleFor(Intent.RAPID))
        assertEquals(ArrowStyle.ARC, BattleArrow.styleFor(Intent.FLANK))
        assertEquals(ArrowStyle.SCATTER, BattleArrow.styleFor(Intent.RETREAT))
        assertEquals(ArrowStyle.AMBUSH_DOT, BattleArrow.styleFor(Intent.AMBUSH))

        val ambush = ArmyPiece("am2", "伏兵", "a", 35.0, 112.0, 6, "cavalry",
            Morale.HIGH, Intent.AMBUSH)
        assertFalse(BattleArrow.fromPiece(ambush, SideRole.OWN) != null)
    }

    private class MutableLongClock(var value: Long) {
        fun get(): Long = value
        fun set(v: Long) { value = v }
    }
}
