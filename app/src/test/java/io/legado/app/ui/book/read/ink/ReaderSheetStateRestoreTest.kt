package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.page.ReaderDetailKind
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import io.legado.app.ui.components.ReaderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B4-03 回位铁律（R14）保存 / 恢复路径与边界兜底单测（纯 JVM；mutableStateOf 可在普通单测
 * 直接实例化，先例见 ReaderSheetStateTest）。
 *
 * 三条回位场景：
 *  - 进程重建：基线纯数据保存到全新守卫 / 状态持有者恢复，逐项相等仍判 true；
 *  - 代次闸：重建后代次 0 的残留请求拒收，代次 1 活进程请求放行；
 *  - 离场后重进：收束后新会话旧 after 清空，新会话回位可重新判定。
 *
 * 五条边界：未开请求关闭、连续两次离场请求、离场中途再次打开、宿主销毁时弹层仍开、
 * 未知阶段（未知 ordinal 兜底见 SheetExitPhaseGateRejectTest）。
 */
class ReaderSheetStateRestoreTest {

    private fun snap(scroll: Int = 320) = ReaderSnapshot(
        chapterIndex = 2,
        segmentOffset = 11,
        scrollPosition = scroll,
        underlineVisible = true,
        selectedSegments = setOf("seg-a", "seg-b"),
    )

    private fun req(kind: ReaderDetailKind, token: String) =
        ReaderDetailRequest(kind = kind, token = token, detail = "正文-$token")

    @Test
    fun `场景一 进程重建 守卫基线纯数据保存恢复后逐项相等判 true`() {
        val before = ReaderSheetGuard()
        assertTrue(before.open(snap(scroll = 320), alreadyOpen = false))
        val savedBaseline = before.saveBaseline()

        // 进程重建：全新守卫从保存的纯数据基线恢复，无 Bundle / Context 依赖。
        val after = ReaderSheetGuard()
        after.restoreBaseline(savedBaseline)
        assertEquals(snap(scroll = 320), after.snapshotBefore)

        after.dismiss(snap(scroll = 320), alreadyOpen = true)
        assertEquals(true, after.restoredExactly)
    }

    @Test
    fun `场景一 状态持有者重建恢复 离场闸回落 清空当前请求 基线在位`() {
        val first = ReaderSheetState()
        first.open(req(ReaderDetailKind.CHAR, "道"), snap(scroll = 320))
        val savedBaseline = first.saveSnapshotBaseline()

        val restored = ReaderSheetState()
        restored.restoreAfterRecreation(savedBaseline)

        assertNull(restored.request)
        assertFalse(restored.isExiting)
        assertEquals(snap(scroll = 320), restored.snapshotBefore)

        // 无基线可恢复时回到未打开态，不抛异常。
        val blank = ReaderSheetState()
        blank.restoreAfterRecreation(null)
        assertNull(blank.request)
        assertNull(blank.snapshotBefore)
    }

    @Test
    fun `场景二 代次闸 重建后代次0残留请求拒收 代次1活进程请求放行`() {
        val state = ReaderSheetState()

        state.bindHostGeneration(0)
        state.open(req(ReaderDetailKind.CHAR, "道"), snap(scroll = 320))

        assertNull(state.request)
        assertNull(state.snapshotBefore)
        assertEquals(1, state.rejectedRequestCount)

        state.bindHostGeneration(1)
        val live = req(ReaderDetailKind.PERSON, "孔子")
        state.open(live, snap(scroll = 320))

        assertSame(live, state.request)
        assertTrue(state.isOpen)
    }

    @Test
    fun `场景三 离场收束后重进 旧 after 清空 新会话回位可重新判定`() {
        val state = ReaderSheetState()
        state.open(req(ReaderDetailKind.CHAR, "道"), snap(scroll = 320))
        state.dismiss(snap(scroll = 320))
        assertEquals(true, state.restoredExactly)
        state.finishExitAnimation()

        state.open(req(ReaderDetailKind.PLACE, "曲阜"), snap(scroll = 500))
        assertNull(state.restoredExactly)

        state.dismiss(snap(scroll = 500))
        assertEquals(true, state.restoredExactly)
    }

    @Test
    fun `边界一 弹层未开时请求关闭 空操作不回写 after 计数可观测`() {
        val state = ReaderSheetState()

        state.dismiss(snap(scroll = 999))

        assertFalse(state.isOpen)
        assertNull(state.restoredExactly)
        assertEquals(1, state.rejectedRequestCount)
    }

    @Test
    fun `边界二 连续两次离场请求 第二次拒收 保留请求不替换`() {
        val state = ReaderSheetState()
        val first = req(ReaderDetailKind.PERSON, "孔子")
        state.open(first, snap(scroll = 320))

        state.dismiss(snap(scroll = 320))
        state.dismiss(snap(scroll = 999))

        assertSame(first, state.exitRequest)
        assertTrue(state.isExiting)
        assertNull(state.request)
    }

    @Test
    fun `边界三 离场中途再次打开被拒收 保留首请求不重采快照`() {
        val state = ReaderSheetState()
        val first = req(ReaderDetailKind.PERSON, "孔子")
        state.open(first, snap(scroll = 320))

        state.dismiss(snap(scroll = 320))
        state.open(req(ReaderDetailKind.CHAR, "仁"), snap(scroll = 400))

        assertSame(first, state.exitRequest)
        assertNull(state.request)
        assertTrue(state.isExiting)
    }

    @Test
    fun `边界四 宿主销毁时弹层仍开 强制收口清空请求与负载 基线保留`() {
        val state = ReaderSheetState()
        state.open(req(ReaderDetailKind.CHAR, "道"), snap(scroll = 320))

        state.releaseOnHostDestroy()

        assertNull(state.request)
        assertNull(state.exitRequest)
        assertFalse(state.isExiting)
        assertFalse(state.exitVisible)
        // 基线保留，以备重建后回位比对；不回调已销毁宿主。
        assertEquals(snap(scroll = 320), state.snapshotBefore)
    }
}
