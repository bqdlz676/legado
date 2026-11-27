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
 * B4-03 闸族拒收计数 / 未知阶段兜底 / 宿主销毁强制收口单测（纯 JVM，不依赖 Compose / Android 运行时）。
 *
 * 覆盖：
 *  - 非法迁移不崩、阶段不动、拒收计数与拒收阶段可观测；
 *  - 持久化回读未知 ordinal 回落 null 安全兜底；
 *  - 宿主销毁强制收口回落摘除段、清存负载，不计非法迁移拒收。
 */
class SheetExitPhaseGateRejectTest {

    private fun req(token: String) =
        ReaderDetailRequest(kind = ReaderDetailKind.CHAR, token = token, detail = "正文-$token")

    @Test
    fun `在场段重复 tryOpen 拒收并累计计数 阶段不动`() {
        val gate = NoteDialogExitGate()

        assertTrue(gate.tryOpen())
        assertFalse(gate.tryOpen())

        assertEquals(1, gate.rejectedTransitionCount)
        assertEquals(ReaderSheetExitPhase.OPEN, gate.lastRejectedPhase)
        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
    }

    @Test
    fun `摘除段 beginExit 与在场段 finishExit 均拒收计数`() {
        val gate = NoteDialogExitGate()

        gate.beginExit()
        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertEquals(1, gate.rejectedTransitionCount)

        gate.tryOpen()
        gate.finishExit()

        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertEquals(2, gate.rejectedTransitionCount)
        assertEquals(ReaderSheetExitPhase.OPEN, gate.lastRejectedPhase)
    }

    @Test
    fun `未知 ordinal 回读返回 null 合法 ordinal 逐位映射`() {
        assertNull(ReaderSheetExitPhase.safeFromOrdinal(null))
        assertNull(ReaderSheetExitPhase.safeFromOrdinal(-1))
        assertNull(ReaderSheetExitPhase.safeFromOrdinal(99))

        assertEquals(ReaderSheetExitPhase.OPEN, ReaderSheetExitPhase.safeFromOrdinal(0))
        assertEquals(ReaderSheetExitPhase.EXITING, ReaderSheetExitPhase.safeFromOrdinal(1))
        assertEquals(ReaderSheetExitPhase.REMOVED, ReaderSheetExitPhase.safeFromOrdinal(2))
    }

    @Test
    fun `宿主销毁强制收口回落摘除段 清负载 不计拒收`() {
        val gate = ReaderSheetExitGate()
        gate.open(req("道"))

        gate.releaseOnHostDestroy()

        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertNull(gate.retainedRequest)
        assertFalse(gate.isComposed)
        assertFalse(gate.visible)
        assertEquals(0, gate.rejectedTransitionCount)
    }

    @Test
    fun `离场段重复 beginExit 拒收 保留请求不被替换`() {
        val gate = ReaderSheetExitGate()
        val first = req("道")
        gate.open(first)
        gate.beginExit(first)

        gate.beginExit(req("仁"))

        assertEquals(ReaderSheetExitPhase.EXITING, gate.phase)
        assertSame(first, gate.retainedRequest)
        assertEquals(1, gate.rejectedTransitionCount)
    }
}
