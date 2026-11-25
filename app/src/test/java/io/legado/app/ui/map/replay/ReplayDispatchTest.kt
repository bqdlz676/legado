package io.legado.app.ui.map.replay

import org.junit.Assert.assertEquals
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
}
