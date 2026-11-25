package io.legado.app.ui.map.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回放导演栈事件派发单测（切片-07 / D2 修复）。
 *
 * 把守三条派发口径：断粮清补给不降士气、投降标记溃散、目标缺失 / 未接动作进告警台账
 * 且不抛异常。纯 JVM，时钟固定 0ms，不 sleep、不依赖系统时间。
 */
class ReplayDispatchTest {

    private fun director(armies: List<ArmyPiece>, events: List<BattleEvent>): ReplayDirector {
        val campaign = Campaign(
            id = "dispatch-test", title = "派发测试", era = "", context = "",
            rounds = 10,
            sides = listOf(
                BattleSide("a", "甲", SideRole.OWN),
                BattleSide("b", "乙", SideRole.ENEMY),
            ),
            armies = armies,
            phases = emptyList(),
            events = events,
            dailyLi = 50,
        )
        return ReplayDirector(campaign, { 0L })
    }

    private fun army(
        id: String,
        morale: Morale = Morale.STEADY,
        supply: Int = ArmyPiece.SUPPLY_FULL,
    ): ArmyPiece = ArmyPiece(
        id = id, name = id, sideId = "a",
        lat = 35.0, lng = 112.0, strength = 10, type = "regular",
        morale = morale, intent = Intent.ADVANCE, supply = supply,
    )

    @Test
    fun `supplyCut 清补给且不降士气`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.SUPPLY_CUT, reason = "断粮测试"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals("断粮作用对象是补给，必须清零", 0, piece.supply)
        assertEquals("断粮不得错降士气", Morale.STEADY, piece.morale)
        assertTrue("正常派发不应产生告警", director.warnings.isEmpty())
    }

    @Test
    fun `surrender 派发标记目标溃散`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.SURRENDER, reason = "投降测试"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(Morale.ROUT, piece.morale)
        assertTrue(piece.isRout)
    }

    @Test
    fun `已溃散棋子断粮不重复结算`() {
        val director = director(
            armies = listOf(army("a1", morale = Morale.ROUT)),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.SUPPLY_CUT, reason = "溃兵断粮"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(Morale.ROUT, piece.morale)
        assertEquals("已溃散棋子补给必须保留，不得被断粮分支清零",
            ArmyPiece.SUPPLY_FULL, piece.supply)
        assertTrue("已溃散棋子不重复结算补给", director.warnings.isEmpty())
    }

    @Test
    fun `目标棋子缺失记告警且不抛异常`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "ghost", action = EventAction.SURRENDER, reason = "幽灵目标"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(Morale.STEADY, piece.morale)
        assertTrue(
            "缺失目标必须进告警台账：${director.warnings}",
            director.warnings.any { it.contains("目标 ghost 缺失") },
        )
    }

    // ── 本批补接：六种有状态位动作真分支 ──

    @Test
    fun `setIntent 改写棋子意图字段`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.SET_INTENT,
                    value = "flank", reason = "侧翼机动"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(Intent.FLANK, piece.intent)
        assertTrue("意图改写不应产生告警", director.warnings.isEmpty())
    }

    @Test
    fun `setTarget 写入目标经纬度`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.SET_TARGET,
                    value = "35.85,112.85", reason = "转进落点"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(35.85, piece.targetLat!!, 1e-9)
        assertEquals(112.85, piece.targetLng!!, 1e-9)
        assertTrue(piece.hasTarget)
        assertTrue("目标写入不应产生告警", director.warnings.isEmpty())
    }

    @Test
    fun `strengthChange 按增减量改兵力且钳制非负`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.STRENGTH_CHANGE,
                    value = "-3", reason = "战损折兵"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(7, piece.strength)
        assertTrue("兵力变动不应产生告警", director.warnings.isEmpty())
    }

    @Test
    fun `ambush 转入设伏意图并虚化`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.AMBUSH, reason = "关隘设伏"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(Intent.AMBUSH, piece.intent)
        val visual = PieceVisual.of(piece, SideRole.OWN, selected = false)
        assertEquals(ReplayConfig.AMBUSH_OPACITY, visual.opacity, 0f)
    }

    @Test
    fun `flank 转入包抄意图`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.FLANK, reason = "侧翼包抄"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(Intent.FLANK, piece.intent)
    }

    @Test
    fun `retreat 转入撤退意图`() {
        val director = director(
            armies = listOf(army("a1")),
            events = listOf(
                BattleEvent(turn = 1, targetId = "a1", action = EventAction.RETREAT, reason = "收兵回撤"),
            ),
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals(Intent.RETREAT, piece.intent)
    }

    // ── supply 消费链：断粮视觉后果 ──

    @Test
    fun `断粮棋子视觉压暗并标记 starved，满补给棋子不受影响`() {
        val starved = army("a1", supply = 0)
        val full = army("a2", supply = ArmyPiece.SUPPLY_FULL)

        val starvedVisual = PieceVisual.of(starved, SideRole.OWN, selected = false)
        val fullVisual = PieceVisual.of(full, SideRole.OWN, selected = false)

        assertTrue(starvedVisual.supplyStarved)
        assertFalse(fullVisual.supplyStarved)
        assertTrue("断粮棋子透明度必须低于满补给", starvedVisual.opacity < fullVisual.opacity)
        assertTrue("断粮棋子顶光必须短于满补给", starvedVisual.topLightUnits < fullVisual.topLightUnits)
    }

    // ── warnings 上限 / 复位 ──

    @Test
    fun `告警台账超上限截断保最新条目`() {
        val director = director(armies = emptyList(), events = emptyList())

        repeat(ReplayDirector.MAX_WARNINGS + 5) { index ->
            director.recordWarning("告警 $index")
        }

        assertEquals(ReplayDirector.MAX_WARNINGS, director.warnings.size)
        assertTrue(
            "截断后必须保留最新一条：${director.warnings.last()}",
            director.warnings.last().contains("告警 ${ReplayDirector.MAX_WARNINGS + 4}"),
        )

        director.clearWarnings()
        assertTrue("复位后台账应为空", director.warnings.isEmpty())
    }

    // ── 真缺字段动作：显式拒绝 + 告警写明理由，棋子状态不变 ──

    @Test
    fun `缺字段动作显式拒绝并写明缺哪个位，棋子状态原样保留`() {
        val rejected = listOf(
            EventAction.DEFEND_FALL,
            EventAction.CAPTURE,
            EventAction.ANNIHILATE,
            EventAction.FEIGNED_RETREAT,
        )
        val director = director(
            armies = listOf(army("a1")),
            events = rejected.map {
                BattleEvent(turn = 1, targetId = "a1", action = it, reason = "缺字段拒绝")
            },
        )

        director.seek(1.0)
        director.update(0)

        val piece = director.armies().first { it.id == "a1" }
        assertEquals("被拒绝动作不得改动兵力", 10, piece.strength)
        assertEquals("被拒绝动作不得改动士气", Morale.STEADY, piece.morale)
        assertEquals("四种缺字段动作各记一条告警", rejected.size, director.warnings.size)
        assertTrue(
            "告警必须写明显式拒绝理由：${director.warnings}",
            director.warnings.all { it.contains("显式拒绝") },
        )
    }
}
