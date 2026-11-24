package io.legado.app.ui.map.crossing

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.BattleStatus
import io.legado.app.ui.map.crossing.model.CorpsView
import io.legado.app.ui.map.crossing.model.DiffField
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.SnapshotDiff
import io.legado.app.ui.map.crossing.model.TurnSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B10 分支对比 diff 纯函数表驱动单测。
 * 覆盖：identical / 单字段差（兵力、士气、位置、存亡各一例）/ 全字段差 / 空侧 /
 * 一侧缺编 / 容差边界（浮点抖动与投影舍入不误标）。
 */
class SnapshotDiffTest {

    private fun view(
        id: String = "army-1",
        strength: Double = 5.0,
        morale: Morale = Morale.STEADY,
        lat: Double = 28.35,
        lng: Double = 105.85,
        alive: Boolean = true,
        surrendered: Boolean = false,
    ) = CorpsView(
        id = id,
        name = id,
        sideId = "A",
        role = FactionRole.OWN,
        type = ArmyType.REGULAR,
        strength = strength,
        fatigue = 0.0,
        morale = morale,
        intent = Intent.HOLD,
        lat = lat,
        lng = lng,
        targetLat = null,
        targetLng = null,
        alive = alive,
        surrendered = surrendered,
        route = emptyList(),
    )

    private fun snap(turn: Int, corps: List<CorpsView>) = TurnSnapshot(
        turn = turn,
        elapsedDays = turn,
        status = BattleStatus.ONGOING,
        winnerSide = null,
        corps = corps,
        events = emptyList(),
    )

    private data class DiffCase(
        val title: String,
        val left: TurnSnapshot?,
        val right: TurnSnapshot?,
        val expectMissingSide: Boolean,
        val expectAnyDiff: Boolean,
        val expectFields: Map<String, Set<DiffField>>,
    )

    @Test
    fun `table-driven diff cases`() {
        val same = listOf(view())
        val cases = listOf(
            DiffCase(
                title = "identical：两线同回合同状态，无差异",
                left = snap(3, same),
                right = snap(3, same.map { it.copy() }),
                expectMissingSide = false,
                expectAnyDiff = false,
                expectFields = mapOf("army-1" to emptySet()),
            ),
            DiffCase(
                title = "单字段差-兵力：强度超出容差只标兵力",
                left = snap(3, listOf(view(strength = 5.0))),
                right = snap(3, listOf(view(strength = 3.0))),
                expectMissingSide = false,
                expectAnyDiff = true,
                expectFields = mapOf("army-1" to setOf(DiffField.STRENGTH)),
            ),
            DiffCase(
                title = "单字段差-士气：档位变化只标士气",
                left = snap(3, listOf(view(morale = Morale.STEADY))),
                right = snap(3, listOf(view(morale = Morale.WAVERING))),
                expectMissingSide = false,
                expectAnyDiff = true,
                expectFields = mapOf("army-1" to setOf(DiffField.MORALE)),
            ),
            DiffCase(
                title = "单字段差-位置：经纬度位移超出容差只标位置",
                left = snap(3, listOf(view(lat = 28.35, lng = 105.85))),
                right = snap(3, listOf(view(lat = 28.50, lng = 105.85))),
                expectMissingSide = false,
                expectAnyDiff = true,
                expectFields = mapOf("army-1" to setOf(DiffField.POSITION)),
            ),
            DiffCase(
                title = "单字段差-存亡：一侧溃散只标存亡",
                left = snap(3, listOf(view(alive = true))),
                right = snap(3, listOf(view(alive = false))),
                expectMissingSide = false,
                expectAnyDiff = true,
                expectFields = mapOf("army-1" to setOf(DiffField.STATUS)),
            ),
            DiffCase(
                title = "全字段差：兵力/士气/位置/存亡四项同时标出",
                left = snap(3, listOf(view(strength = 6.0, morale = Morale.HIGH, lat = 28.35, lng = 105.85, alive = true))),
                right = snap(3, listOf(view(strength = 2.0, morale = Morale.BROKEN, lat = 28.60, lng = 105.90, alive = false))),
                expectMissingSide = false,
                expectAnyDiff = true,
                expectFields = mapOf(
                    "army-1" to setOf(DiffField.STRENGTH, DiffField.MORALE, DiffField.POSITION, DiffField.STATUS),
                ),
            ),
            DiffCase(
                title = "空侧-左线缺快照：整回合占位，不逐行比对",
                left = null,
                right = snap(3, same),
                expectMissingSide = true,
                expectAnyDiff = false,
                expectFields = emptyMap(),
            ),
            DiffCase(
                title = "空侧-右线缺快照：整回合占位，不逐行比对",
                left = snap(3, same),
                right = null,
                expectMissingSide = true,
                expectAnyDiff = false,
                expectFields = emptyMap(),
            ),
            DiffCase(
                title = "空侧-两侧皆空：回合号回落 -1，不抛异常",
                left = null,
                right = null,
                expectMissingSide = true,
                expectAnyDiff = false,
                expectFields = emptyMap(),
            ),
            DiffCase(
                title = "一侧缺编：只在一侧在册的军队按存亡差异标注",
                left = snap(3, listOf(view(id = "a"), view(id = "b"))),
                right = snap(3, listOf(view(id = "a"))),
                expectMissingSide = false,
                expectAnyDiff = true,
                expectFields = mapOf(
                    "a" to emptySet(),
                    "b" to setOf(DiffField.STATUS),
                ),
            ),
            DiffCase(
                title = "容差边界：兵力差 0.03、位移 0.0002 度均在容差内，不误标",
                left = snap(3, listOf(view(strength = 5.0, lat = 28.3500, lng = 105.8500))),
                right = snap(3, listOf(view(strength = 5.03, lat = 28.3502, lng = 105.8500))),
                expectMissingSide = false,
                expectAnyDiff = false,
                expectFields = mapOf("army-1" to emptySet()),
            ),
            // 容差上界锁定（M1）：实现比较符为严格 `>`（CampaignModels.kt:320 兵力、324-325 坐标），
            // 即 abs(差) > ε 才标差异、= ε 也不标。故取四个贴边值：
            // 兵力 ε=0.05：0.051 > 0.05 应标「兵力」；0.049 ≤ 0.05 不标。
            // 坐标 ε=0.0005 度：0.0006 > 0.0005 应标「位置」；0.0004 ≤ 0.0005 不标。
            // 选 0.049/0.051、0.0004/0.0006 是为留出单向舍入余量，避免浮点尾差把贴边用例反推到另一侧。
            DiffCase(
                title = "容差上界-兵力：差 0.051 超出 ε=0.05，只标兵力",
                left = snap(3, listOf(view(strength = 5.0))),
                right = snap(3, listOf(view(strength = 5.051))),
                expectMissingSide = false,
                expectAnyDiff = true,
                expectFields = mapOf("army-1" to setOf(DiffField.STRENGTH)),
            ),
            DiffCase(
                title = "容差上界-兵力：差 0.049 在 ε=0.05 内侧，不标兵力",
                left = snap(3, listOf(view(strength = 5.0))),
                right = snap(3, listOf(view(strength = 5.049))),
                expectMissingSide = false,
                expectAnyDiff = false,
                expectFields = mapOf("army-1" to emptySet()),
            ),
            DiffCase(
                title = "容差上界-位置：纬差 0.0006 度超出 ε=0.0005，只标位置",
                left = snap(3, listOf(view(lat = 28.3500, lng = 105.8500))),
                right = snap(3, listOf(view(lat = 28.3506, lng = 105.8500))),
                expectMissingSide = false,
                expectAnyDiff = true,
                expectFields = mapOf("army-1" to setOf(DiffField.POSITION)),
            ),
            DiffCase(
                title = "容差上界-位置：纬差 0.0004 度在 ε=0.0005 内侧，不标位置",
                left = snap(3, listOf(view(lat = 28.3500, lng = 105.8500))),
                right = snap(3, listOf(view(lat = 28.3504, lng = 105.8500))),
                expectMissingSide = false,
                expectAnyDiff = false,
                expectFields = mapOf("army-1" to emptySet()),
            ),
        )

        for (case in cases) {
            val diff = SnapshotDiff.diff(case.left, case.right)
            assertEquals(case.title, case.expectMissingSide, diff.missingSide)
            assertEquals(case.title, case.expectAnyDiff, diff.hasAnyDiff)
            if (!case.expectMissingSide) {
                val byId = diff.corps.associateBy { it.corpsId }
                for ((id, fields) in case.expectFields) {
                    assertEquals(case.title, fields, byId.getValue(id).fields)
                }
            } else {
                assertTrue(case.title, diff.corps.isEmpty())
            }
        }
    }

    @Test
    fun `snapshot turn number falls back to non-null side`() {
        val diff = SnapshotDiff.diff(null, snap(7, emptyList()))
        assertTrue(diff.missingSide)
        assertEquals(7, diff.turn)
        assertFalse(diff.hasAnyDiff)
    }
}
