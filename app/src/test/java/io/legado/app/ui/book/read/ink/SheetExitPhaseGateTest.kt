package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.NoteDialogExitGate
import io.legado.app.ui.book.read.page.ReaderDetailKind
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B30 离场闸基类单测（纯 JVM，不依赖 Compose / Android 运行时）。
 *
 * 覆盖三件事：
 *  - 宿主入口连点拒收：[SheetExitPhaseGate.tryOpen] 仅 REMOVED 段放行，OPEN / EXITING 段
 *    第二次打开返回 false 且不改阶段、不登记第二节点；
 *  - [SheetExitPhaseGate.canOpen] 阶段取值：仅 EXITING 段 false；
 *  - `isComposed` / `visible` / `canOpen` 各阶段取值矩阵：B21 负载守卫
 *    [ReaderSheetExitGate] 与 B26 无负载守卫 [NoteDialogExitGate] 逐阶段对齐，
 *    锁定派生口径已收敛到基类单一实现，不存在第二套判断。
 */
class SheetExitPhaseGateTest {

    private fun req(token: String) =
        ReaderDetailRequest(kind = ReaderDetailKind.CHAR, token = token, detail = "正文-$token")

    @Test
    fun `宿主入口 tryOpen 首次放行 在场段二次拒收 不登记第二节点`() {
        val gate = NoteDialogExitGate()

        val first = gate.tryOpen()

        assertTrue(first)
        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.isComposed)

        val second = gate.tryOpen()

        assertFalse(second)
        // 拒收不改阶段：仍停留在首次 OPEN，不产生第二次会话登记。
        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.isComposed)
    }

    @Test
    fun `离场段 canOpen 与 tryOpen 均拒收 收束后放行`() {
        val gate = NoteDialogExitGate()
        gate.tryOpen()
        gate.beginExit()

        assertEquals(ReaderSheetExitPhase.EXITING, gate.phase)
        assertFalse(gate.canOpen)
        assertFalse(gate.tryOpen())

        gate.finishExit()

        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertTrue(gate.canOpen)
        assertTrue(gate.tryOpen())
    }

    @Test
    fun `两守卫各阶段取值矩阵同源 派生口径单一实现`() {
        val payloadGate = ReaderSheetExitGate()
        val plainGate = NoteDialogExitGate()

        // REMOVED 初始段：子树不在、不可见、可开。
        assertMatrix(
            payloadGate, plainGate,
            composed = false, visible = false, canOpen = true,
        )

        payloadGate.open(req("道"))
        plainGate.open()
        // OPEN 在场段：子树保留、可见、可开。
        assertMatrix(
            payloadGate, plainGate,
            composed = true, visible = true, canOpen = true,
        )

        payloadGate.beginExit(req("道"))
        plainGate.beginExit()
        // EXITING 离场段：子树保留、可见性已 false、拒收新打开。
        assertMatrix(
            payloadGate, plainGate,
            composed = true, visible = false, canOpen = false,
        )

        payloadGate.finishExit()
        plainGate.finishExit()
        // REMOVED 收束段：子树摘除、不可见、可再次打开。
        assertMatrix(
            payloadGate, plainGate,
            composed = false, visible = false, canOpen = true,
        )
    }

    @Test
    fun `负载守卫收束清空请求 重开登记新请求`() {
        val gate = ReaderSheetExitGate()
        val first = req("道")
        gate.open(first)
        gate.beginExit(first)
        gate.finishExit()

        assertNull(gate.retainedRequest)

        val second = req("仁")
        gate.open(second)

        assertSame(second, gate.retainedRequest)
        assertTrue(gate.isComposed)
        assertTrue(gate.visible)
        assertTrue(gate.canOpen)
    }

    @Test
    fun `离场段重复 beginExit 不替换保留请求 非离场 finishExit 空操作`() {
        val gate = ReaderSheetExitGate()
        val first = req("道")
        gate.open(first)
        gate.beginExit(first)

        gate.beginExit(req("仁"))

        assertEquals(ReaderSheetExitPhase.EXITING, gate.phase)
        assertSame(first, gate.retainedRequest)

        gate.open(req("仁"))
        gate.finishExit() // 在场段直接收尾：非离场段，空操作，负载不清。

        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.isComposed)
    }

    /** 逐字段比对两个守卫的派生取值；任一守卫口径漂移即失败。 */
    private fun assertMatrix(
        payloadGate: ReaderSheetExitGate,
        plainGate: NoteDialogExitGate,
        composed: Boolean,
        visible: Boolean,
        canOpen: Boolean,
    ) {
        assertEquals(composed, payloadGate.isComposed)
        assertEquals(composed, plainGate.isComposed)
        assertEquals(visible, payloadGate.visible)
        assertEquals(visible, plainGate.visible)
        assertEquals(canOpen, payloadGate.canOpen)
        assertEquals(canOpen, plainGate.canOpen)
    }
}
