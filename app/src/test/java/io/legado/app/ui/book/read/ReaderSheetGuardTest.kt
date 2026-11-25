package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.ink.ReaderSheetGuard
import io.legado.app.ui.components.ReaderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1 开合快照守卫单测（纯 JVM，只用 org.junit 与 Kotlin 语言层，不引用 Android / Compose 类型）。
 *
 * 规格：切片-11 §5.4「关闭后阅读位置、滚动位置、下划线状态一字不差」；
 * 覆盖「打开 → 关闭 → 状态字段相等」主路径，以及字段漂移、未关闭、二次开合串味等边界。
 * B20 增补：已开态重复 open 拒收且首份快照不动；已关态冗余 dismiss 不回写 after 快照。
 */
class ReaderSheetGuardTest {

    private val base = ReaderSnapshot(
        chapterIndex = 2,
        segmentOffset = 11,
        scrollPosition = 320,
        underlineVisible = true,
        selectedSegments = setOf("seg-a", "seg-b"),
    )

    @Test
    fun snapshotEqualAfterOpenDismissReturnsTrue() {
        val guard = ReaderSheetGuard()
        assertTrue(guard.open(base, alreadyOpen = false))
        guard.dismiss(base.copy(), alreadyOpen = true)
        assertEquals(true, guard.restoredExactly)
    }

    @Test
    fun snapshotMismatchScrollPositionReturnsFalse() {
        val guard = ReaderSheetGuard()
        guard.open(base, alreadyOpen = false)
        guard.dismiss(base.copy(scrollPosition = 321), alreadyOpen = true)
        assertEquals(false, guard.restoredExactly)
    }

    @Test
    fun snapshotMismatchChapterIndexReturnsFalse() {
        val guard = ReaderSheetGuard()
        guard.open(base, alreadyOpen = false)
        guard.dismiss(base.copy(chapterIndex = 3), alreadyOpen = true)
        assertFalse(guard.restoredExactly!!)
    }

    @Test
    fun selectedSegmentsMismatchReturnsFalse() {
        val guard = ReaderSheetGuard()
        guard.open(base, alreadyOpen = false)
        guard.dismiss(base.copy(selectedSegments = setOf("seg-a")), alreadyOpen = true)
        assertFalse(guard.restoredExactly!!)
    }

    @Test
    fun underlineVisibilityFlipReturnsFalse() {
        val guard = ReaderSheetGuard()
        guard.open(base, alreadyOpen = false)
        guard.dismiss(base.copy(underlineVisible = false), alreadyOpen = true)
        assertFalse(guard.restoredExactly!!)
    }

    @Test
    fun undismissedReturnsNull() {
        val guard = ReaderSheetGuard()
        guard.open(base, alreadyOpen = false)
        assertNull(guard.restoredExactly)
    }

    @Test
    fun neverOpenedReturnsNull() {
        val guard = ReaderSheetGuard()
        assertNull(guard.restoredExactly)
    }

    @Test
    fun reopenClearsPreviousAfterSnapshot() {
        val guard = ReaderSheetGuard()
        guard.open(base, alreadyOpen = false)
        guard.dismiss(base.copy(), alreadyOpen = true)
        // 第二次打开：上一轮关闭后快照必须清空，未再次关闭前不可判定。
        guard.open(base, alreadyOpen = false)
        assertNull(guard.restoredExactly)
        guard.dismiss(base.copy(segmentOffset = 12), alreadyOpen = true)
        assertFalse(guard.restoredExactly!!)
    }

    @Test
    fun emptySnapshotRoundTripReturnsTrue() {
        val guard = ReaderSheetGuard()
        guard.open(ReaderSnapshot.Empty, alreadyOpen = false)
        guard.dismiss(ReaderSnapshot.Empty.copy(), alreadyOpen = true)
        assertTrue(guard.restoredExactly!!)
    }

    @Test
    fun repeatOpenWhileOpenRejectedKeepsFirstSnapshot() {
        val guard = ReaderSheetGuard()
        assertTrue(guard.open(base, alreadyOpen = false))
        // 已开态第二次 open：拒收，首份快照保持 base，after 仍为 null，基线不被冲掉。
        assertFalse(guard.open(base.copy(scrollPosition = 400), alreadyOpen = true))
        assertEquals(base, guard.snapshotBefore)
        assertNull(guard.snapshotAfter)
        assertNull(guard.restoredExactly)
    }

    @Test
    fun redundantDismissWhenClosedKeepsAfterNull() {
        val guard = ReaderSheetGuard()
        // 未打开过的冗余 dismiss：不回写 after，比对结果仍为不可判定。
        guard.dismiss(base, alreadyOpen = false)
        assertNull(guard.snapshotAfter)
        assertNull(guard.restoredExactly)
    }
}
