package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.page.ReaderDetailKind
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B21 离场两段式状态机单测（纯 JVM，不依赖 Compose / Android 运行时）。
 *
 * 断言与锚点卡判据一一对应：
 *  - 判据 1：关闭先进离场段播动画，动画收束（finishExit）后才清空保留请求摘子树；
 *  - 判据 2：阶段迁移口径全仓单一，四个调用点共用同一守卫；
 *  - 判据 3：离场段 [ReaderSheetExitGate.canOpen] 拒收新半屏请求；
 *  - 判据 5：关闭瞬间子树仍在（[ReaderSheetExitGate.isComposed] 为 true）、可见性已 false。
 */
class ReaderSheetExitGateTest {

    private fun req(token: String) =
        ReaderDetailRequest(kind = ReaderDetailKind.CHAR, token = token, detail = "正文-$token")

    @Test
    fun `初始为摘除态：无子树、不可见、可接受新请求`() {
        val gate = ReaderSheetExitGate()

        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertFalse(gate.isComposed)
        assertFalse(gate.visible)
        assertTrue(gate.canOpen)
        assertNull(gate.retainedRequest)
    }

    @Test
    fun `open 后在场态：可见、子树保留、可开`() {
        val gate = ReaderSheetExitGate()
        val first = req("道")

        gate.open(first)

        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.isComposed)
        assertTrue(gate.visible)
        assertTrue(gate.canOpen)
        assertSame(first, gate.retainedRequest)
    }

    @Test
    fun `判据1+5 关闭瞬间子树仍在且 visible 已 false`() {
        val gate = ReaderSheetExitGate()
        val first = req("道")
        gate.open(first)

        gate.beginExit(first)

        assertEquals(ReaderSheetExitPhase.EXITING, gate.phase)
        // 子树保留：保留请求未清空，覆盖层仍按原请求组合；可见性已翻 false，播组件内离场动画。
        assertTrue(gate.isComposed)
        assertSame(first, gate.retainedRequest)
        assertFalse(gate.visible)
    }

    @Test
    fun `判据3 离场段拒收新半屏请求`() {
        val gate = ReaderSheetExitGate()
        gate.open(req("道"))
        gate.beginExit(req("道"))

        assertFalse(gate.canOpen)
        // 离场段重复 beginExit / 提前 finishExit 之外的迁移均被守卫空操作口径挡住。
        gate.beginExit(req("仁"))
        assertEquals(ReaderSheetExitPhase.EXITING, gate.phase)
        assertTrue(gate.isComposed)
    }

    @Test
    fun `判据1 动画收束后清空保留请求摘除子树`() {
        val gate = ReaderSheetExitGate()
        gate.open(req("道"))
        gate.beginExit(req("道"))

        gate.finishExit()

        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertFalse(gate.isComposed)
        assertNull(gate.retainedRequest)
        assertFalse(gate.visible)
        assertTrue(gate.canOpen)
    }

    @Test
    fun `空操作兜底：未在场 beginExit 不动状态，未离场 finishExit 不清缓存`() {
        val gate = ReaderSheetExitGate()

        gate.beginExit(req("道"))
        assertEquals(ReaderSheetExitPhase.REMOVED, gate.phase)
        assertNull(gate.retainedRequest)

        gate.open(req("道"))
        gate.finishExit() // 在场段直接收尾：非离场段，空操作
        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.isComposed)
    }

    @Test
    fun `收束后重开回到在场态并登记新请求`() {
        val gate = ReaderSheetExitGate()
        gate.open(req("道"))
        gate.beginExit(req("道"))
        gate.finishExit()

        val second = req("仁")
        gate.open(second)

        assertEquals(ReaderSheetExitPhase.OPEN, gate.phase)
        assertTrue(gate.visible)
        assertTrue(gate.canOpen)
        assertSame(second, gate.retainedRequest)
    }
}
