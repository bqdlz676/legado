package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.ink.ReaderSheetExitPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B26 批注弹窗两段式离场守卫单测（纯 JVM，不依赖 Compose / Android 运行时）。
 *
 * 断言与锚点卡判据一一对应：
 *  - 判据 1：关闭先进离场段播动画，收束（finishExit）后才摘除子树；
 *  - 判据 3：离场段 [NoteDialogExitGate.canOpen] 拒收重复打开；
 *  - 判据 5：关闭瞬间子树仍在（[NoteDialogExitGate.isComposed] 为 true）、可见性已 false。
 * 阶段枚举与迁移口径复用 B21 [ReaderSheetExitPhase]。
 */
class NoteDialogExitGateTest {

    @Test
    fun `初始为摘除态 无子树 不可见 可接受新请求`() {
        val gate = NoteDialogExitGate()

        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertFalse(gate.isComposed)
        assertFalse(gate.visible)
        assertTrue(gate.canOpen)
    }

    @Test
    fun `open 后在场态 可见 子树保留 可开`() {
        val gate = NoteDialogExitGate()

        gate.open()

        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.isComposed)
        assertTrue(gate.visible)
        assertTrue(gate.canOpen)
    }

    @Test
    fun `判据一与五 关闭瞬间子树仍在且 visible 已 false`() {
        val gate = NoteDialogExitGate()
        gate.open()

        gate.beginExit()

        assertEquals(ReaderSheetExitPhase.EXITING, gate.phase)
        // 子树保留：离场段仍按原内容组合；可见性已翻 false，播组件内离场动画。
        assertTrue(gate.isComposed)
        assertFalse(gate.visible)
    }

    @Test
    fun `判据三 离场段拒收重复打开`() {
        val gate = NoteDialogExitGate()
        gate.open()
        gate.beginExit()

        assertFalse(gate.canOpen)
        // 离场段重复 beginExit 不改阶段，子树仍在。
        gate.beginExit()
        assertEquals(ReaderSheetExitPhase.EXITING, gate.phase)
        assertTrue(gate.isComposed)
    }

    @Test
    fun `判据一 动画收束后摘除子树`() {
        val gate = NoteDialogExitGate()
        gate.open()
        gate.beginExit()

        gate.finishExit()

        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertFalse(gate.isComposed)
        assertFalse(gate.visible)
        assertTrue(gate.canOpen)
    }

    @Test
    fun `空操作兜底 未在场 beginExit 不动状态 未离场 finishExit 不迁移`() {
        val gate = NoteDialogExitGate()

        gate.beginExit()
        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertFalse(gate.isComposed)

        gate.open()
        gate.finishExit() // 在场段直接收尾：非离场段，空操作
        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.isComposed)
    }

    @Test
    fun `收束后重开回到在场态并可见`() {
        val gate = NoteDialogExitGate()
        gate.open()
        gate.beginExit()
        gate.finishExit()

        gate.open()

        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.visible)
        assertTrue(gate.canOpen)
        assertTrue(gate.isComposed)
    }
}
