package io.legado.app.ui.map.crossing.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B10 分支对比接线单测（纯 JVM，不依赖 Compose 运行时）。
 *
 * 覆盖三组：
 * 1. diff 新维度：暴露姿态 / 单侧事件 / 胜负档位 / 告警台账透传；
 * 2. 装配三态：无存档 NeedArchive、失效 Invalidated、可对照 Ready（含两线一致 identical）；
 * 3. 入口行为锁：存档 / 对比按钮启用口径纯判定。
 */
class BranchCompareAssemblyTest {

    private fun view(
        id: String = "army-1",
        strength: Double = 5.0,
        morale: Morale = Morale.STEADY,
        lat: Double = 28.35,
        lng: Double = 105.85,
        alive: Boolean = true,
        surrendered: Boolean = false,
        exposed: Boolean = false,
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
        exposed = exposed,
    )

    private fun snap(
        turn: Int,
        corps: List<CorpsView>,
        events: List<EventEntry> = emptyList(),
        status: BattleStatus = BattleStatus.ONGOING,
        winner: String? = null,
    ) = TurnSnapshot(
        turn = turn,
        elapsedDays = turn,
        status = status,
        winnerSide = winner,
        corps = corps,
        events = events,
    )

    private fun event(text: String) = EventEntry(EventAttribution.NEUTRAL, text)

    // ── 1. diff 新维度 ──────────────────────────────────────

    @Test
    fun `exposed posture difference is flagged as exposed field`() {
        val diff = SnapshotDiff.diff(
            snap(3, listOf(view(exposed = false))),
            snap(3, listOf(view(exposed = true))),
        )
        val byId = diff.corps.associateBy { it.corpsId }
        assertEquals(setOf(DiffField.EXPOSED), byId.getValue("army-1").fields)
        assertTrue(diff.hasAnyDiff)
    }

    @Test
    fun `exposed same posture is not flagged`() {
        val diff = SnapshotDiff.diff(
            snap(3, listOf(view(exposed = true))),
            snap(3, listOf(view(exposed = true))),
        )
        assertTrue(diff.corps.first().fields.isEmpty())
        assertFalse(diff.hasAnyDiff)
    }

    @Test
    fun `events present on only one side are split into one-sided lists`() {
        val onlyLeft = event("遭遇战：红军 对 黔军")
        val diff = SnapshotDiff.diff(
            snap(3, listOf(view()), events = listOf(onlyLeft)),
            snap(3, listOf(view())),
        )
        assertEquals(listOf(onlyLeft), diff.eventOnlyLeft)
        assertTrue(diff.eventOnlyRight.isEmpty())
        assertEquals(listOf(onlyLeft), diff.eventsLeft)
        assertTrue(diff.eventsRight.isEmpty())
    }

    @Test
    fun `result diff flags status mismatch and winner mismatch`() {
        val statusDiff = SnapshotDiff.diff(
            snap(3, listOf(view()), status = BattleStatus.ONGOING),
            snap(3, listOf(view()), status = BattleStatus.VICTORY),
        )
        assertTrue(statusDiff.resultDiff)

        val winnerDiff = SnapshotDiff.diff(
            snap(3, listOf(view()), winner = "A"),
            snap(3, listOf(view()), winner = "B"),
        )
        assertTrue(winnerDiff.resultDiff)

        val same = SnapshotDiff.diff(
            snap(3, listOf(view()), status = BattleStatus.DRAW, winner = null),
            snap(3, listOf(view()), status = BattleStatus.DRAW, winner = null),
        )
        assertFalse(same.resultDiff)
    }

    @Test
    fun `warnings null stays unknown while populated lists pass through`() {
        val unknown = SnapshotDiff.diff(snap(1, listOf(view())), snap(1, listOf(view())))
        assertNull(unknown.warningsLeft)
        assertNull(unknown.warningsRight)

        val withWarnings = SnapshotDiff.diff(
            snap(1, listOf(view())),
            snap(1, listOf(view())),
            warningsLeft = listOf("高程图缺失"),
            warningsRight = null,
        )
        assertEquals(listOf("高程图缺失"), withWarnings.warningsLeft)
        assertNull(withWarnings.warningsRight)
    }

    @Test
    fun `turn identical requires no corps diff no one-sided event and same result`() {
        val same = snap(3, listOf(view()), events = listOf(event("同一事件")))
        val identical = SnapshotDiff.diff(same, same.copy())
        assertTrue(identical.turnIdentical)

        val withEvent = SnapshotDiff.diff(
            snap(3, listOf(view()), events = listOf(event("单侧事件"))),
            snap(3, listOf(view())),
        )
        assertFalse(withEvent.turnIdentical)
    }

    // ── 2. 装配三态 ─────────────────────────────────────────

    @Test
    fun `no archived branch resolves to NeedArchive`() {
        val current = listOf(snap(1, listOf(view())), snap(2, listOf(view())))
        val session = BranchCompareAssembly.prepare(current, null, null, null)
        assertTrue(session is CompareSession.NeedArchive)
        val need = session as CompareSession.NeedArchive
        assertEquals(0, need.archivedCount)
        assertEquals(2, need.currentTurns)
    }

    @Test
    fun `empty archived line also resolves to NeedArchive`() {
        val session = BranchCompareAssembly.prepare(listOf(snap(1, listOf(view()))), emptyList(), null, null)
        assertTrue(session is CompareSession.NeedArchive)
    }

    @Test
    fun `empty current line resolves to Invalidated with current-empty reason`() {
        val archive = listOf(snap(1, listOf(view())))
        val session = BranchCompareAssembly.prepare(emptyList(), archive, null, null)
        assertTrue(session is CompareSession.Invalidated)
        assertEquals(BranchCompareAssembly.REASON_CURRENT_EMPTY, (session as CompareSession.Invalidated).reason)
    }

    @Test
    fun `archive line with no visible corps resolves to Invalidated`() {
        val deadArchive = listOf(snap(1, emptyList()), snap(2, emptyList()))
        val session = BranchCompareAssembly.prepare(listOf(snap(1, listOf(view()))), deadArchive, null, null)
        assertTrue(session is CompareSession.Invalidated)
        assertEquals(BranchCompareAssembly.REASON_ARCHIVE_DEAD, (session as CompareSession.Invalidated).reason)
    }

    @Test
    fun `two equal lines resolve to Ready identical`() {
        val line = listOf(snap(1, listOf(view())), snap(2, listOf(view())))
        val session = BranchCompareAssembly.prepare(line, line.map { it.copy() }, listOf("告警甲"), null)
        assertTrue(session is CompareSession.Ready)
        val ready = session as CompareSession.Ready
        assertTrue(ready.identical)
        assertEquals(line.size, ready.left.size)
        assertEquals(listOf("告警甲"), ready.warningsLeft)
        assertNull(ready.warningsRight)
    }

    @Test
    fun `two differing lines resolve to Ready non-identical`() {
        val left = listOf(snap(1, listOf(view(strength = 5.0))))
        val right = listOf(snap(1, listOf(view(strength = 2.0))))
        val session = BranchCompareAssembly.prepare(left, right, null, listOf("告警乙"))
        assertTrue(session is CompareSession.Ready)
        val ready = session as CompareSession.Ready
        assertFalse(ready.identical)
        assertEquals(left, ready.left)
        assertEquals(right, ready.right)
        assertEquals(listOf("告警乙"), ready.warningsRight)
    }

    @Test
    fun `different line lengths are not identical`() {
        val short = listOf(snap(1, listOf(view())))
        val long = listOf(snap(1, listOf(view())), snap(2, listOf(view())))
        val session = BranchCompareAssembly.prepare(long, short, null, null)
        assertTrue(session is CompareSession.Ready)
        assertFalse((session as CompareSession.Ready).identical)
    }

    // ── 3. 入口行为锁 ───────────────────────────────────────

    @Test
    fun `entry disabled when current battlefield has no visible corps`() {
        val entry = CompareEntry.decide(archivedCount = 3, currentAlive = false)
        assertFalse(entry.canSave)
        assertFalse(entry.canCompare)
        assertEquals("当前战场无可视军队", entry.compareDisabledReason)
    }

    @Test
    fun `compare disabled until at least one branch archived`() {
        val entry = CompareEntry.decide(archivedCount = 0, currentAlive = true)
        assertTrue(entry.canSave)
        assertFalse(entry.canCompare)
        assertEquals("先存档一条分支再对照", entry.compareDisabledReason)
    }

    @Test
    fun `both actions enabled with alive battlefield and one archive`() {
        val entry = CompareEntry.decide(archivedCount = 1, currentAlive = true)
        assertTrue(entry.canSave)
        assertTrue(entry.canCompare)
        assertNull(entry.compareDisabledReason)
    }
}
