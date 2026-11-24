package io.legado.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-11 阅读页快照契约单测：纯 JVM，只用 org.junit 与 kotlin 语言层，
 * 不引用任何 Android / Compose 类型。
 * 规格：切片-11 §5.4「关闭后阅读位置、滚动位置、下划线状态一字不差」；
 * 判据为 [isRestoredExactly] 逐字段相等，集合按内容比对、与顺序无关。
 */
class ReaderSnapshotTest {

    private val base = ReaderSnapshot(
        chapterIndex = 2,
        segmentOffset = 11,
        scrollPosition = 320,
        underlineVisible = true,
        selectedSegments = setOf("seg-a", "seg-b"),
    )

    @Test
    fun `全同快照还原判定为真`() {
        val after = base.copy()
        assertTrue(isRestoredExactly(base, after))
    }

    @Test
    fun `章序号差一字段判定为假`() {
        val after = base.copy(chapterIndex = 3)
        assertFalse(isRestoredExactly(base, after))
    }

    @Test
    fun `下划线集合少一项判定为假`() {
        val after = base.copy(selectedSegments = setOf("seg-a"))
        assertFalse(isRestoredExactly(base, after))
    }

    @Test
    fun `空状态快照往返还原为真`() {
        val before = ReaderSnapshot.Empty
        val after = ReaderSnapshot.Empty.copy()
        assertTrue(isRestoredExactly(before, after))
    }

    @Test
    fun `下划线可见性翻转判定为假`() {
        val after = base.copy(underlineVisible = false)
        assertFalse(isRestoredExactly(base, after))
    }

    @Test
    fun `段内偏移差一判定为假`() {
        val after = base.copy(segmentOffset = 12)
        assertFalse(isRestoredExactly(base, after))
    }

    @Test
    fun `滚动位置差一像素判定为假`() {
        val after = base.copy(scrollPosition = 321)
        assertFalse(isRestoredExactly(base, after))
    }

    @Test
    fun `集合顺序不同内容相同仍判定为真`() {
        val reversed = ReaderSnapshot(
            chapterIndex = base.chapterIndex,
            segmentOffset = base.segmentOffset,
            scrollPosition = base.scrollPosition,
            underlineVisible = base.underlineVisible,
            selectedSegments = linkedSetOf("seg-b", "seg-a"),
        )
        assertTrue(isRestoredExactly(base, reversed))
    }
}
