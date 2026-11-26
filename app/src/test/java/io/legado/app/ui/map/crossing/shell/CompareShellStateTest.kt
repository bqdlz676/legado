package io.legado.app.ui.map.crossing.shell

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.CorpsView
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.TurnSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B10 对比壳可判定状态纯函数单测（纯 JVM，不启 Compose 运行时）。
 *
 * 锁定两类壳层行为：快照 → 栏内渲染态（占位卡 / 空注 / 逐军行）；回合下标钳制边界。
 * 改坏任一分支（null 直接解引用、下标越界、maxTurns=0 区间反转）对应用例立即变红。
 */
class CompareShellStateTest {

    private fun snap(corps: List<CorpsView>) = TurnSnapshot(
        turn = 1,
        elapsedDays = 1,
        status = BattleStatus.ONGOING,
        winnerSide = null,
        corps = corps,
        events = emptyList(),
    )

    private fun view() = CorpsView(
        id = "army-1",
        name = "army-1",
        sideId = "A",
        role = FactionRole.OWN,
        type = ArmyType.REGULAR,
        strength = 5.0,
        fatigue = 0.0,
        morale = Morale.STEADY,
        intent = Intent.HOLD,
        lat = 28.35,
        lng = 105.85,
        targetLat = null,
        targetLng = null,
        alive = true,
        surrendered = false,
        route = emptyList(),
    )

    @Test
    fun `render mode placeholder when snapshot missing`() {
        assertEquals(CompareRender.PLACEHOLDER, compareRenderMode(null))
    }

    @Test
    fun `render mode empty note when snapshot has no visible corps`() {
        assertEquals(CompareRender.EMPTY_NOTE, compareRenderMode(snap(emptyList())))
    }

    @Test
    fun `render mode rows when snapshot has corps`() {
        assertEquals(CompareRender.ROWS, compareRenderMode(snap(listOf(view()))))
    }

    @Test
    fun `clamped index stays within bounds`() {
        assertEquals(0, compareClampedIndex(raw = -5, maxTurns = 4))
        assertEquals(3, compareClampedIndex(raw = 9, maxTurns = 4))
        assertEquals(2, compareClampedIndex(raw = 2, maxTurns = 4))
    }

    @Test
    fun `clamped index falls back to zero when line empty`() {
        // maxTurns=0 时下标必须回落到 0，区间不得反转成 [0, -1]。
        assertEquals(0, compareClampedIndex(raw = 0, maxTurns = 0))
        assertEquals(0, compareClampedIndex(raw = 3, maxTurns = 0))
    }
}
