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
        guard.open(base)
        guard.dismiss(base.copy())
        assertEquals(true, guard.restoredExactly)
    }

    @Test
    fun snapshotMismatchScrollPositionReturnsFalse() {
        val guard = ReaderSheetGuard()
        guard.open(base)
        guard.dismiss(base.copy(scrollPosition = 321))
        assertEquals(false, guard.restoredExactly)
    }

    @Test
    fun snapshotMismatchChapterIndexReturnsFalse() {
        val guard = ReaderSheetGuard()
        guard.open(base)
        guard.dismiss(base.copy(chapterIndex = 3))
        assertFalse(guard.restoredExactly!!)
    }

    @Test
    fun selectedSegmentsMismatchReturnsFalse() {
        val guard = ReaderSheetGuard()
        guard.open(base)
        guard.dismiss(base.copy(selectedSegments = setOf("seg-a")))
        assertFalse(guard.restoredExactly!!)
    }

    @Test
    fun underlineVisibilityFlipReturnsFalse() {
        val guard = ReaderSheetGuard()
        guard.open(base)
        guard.dismiss(base.copy(underlineVisible = false))
        assertFalse(guard.restoredExactly!!)
    }

    @Test
    fun undismissedReturnsNull() {
        val guard = ReaderSheetGuard()
        guard.open(base)
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
        guard.open(base)
        guard.dismiss(base.copy())
        // 第二次打开：上一轮关闭后快照必须清空，未再次关闭前不可判定。
        guard.open(base)
        assertNull(guard.restoredExactly)
        guard.dismiss(base.copy(segmentOffset = 12))
        assertFalse(guard.restoredExactly!!)
    }

    @Test
    fun emptySnapshotRoundTripReturnsTrue() {
        val guard = ReaderSheetGuard()
        guard.open(ReaderSnapshot.Empty)
        guard.dismiss(ReaderSnapshot.Empty.copy())
        assertTrue(guard.restoredExactly!!)
    }
}
