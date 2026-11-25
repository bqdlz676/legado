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
 * B20 点字半屏连点竞态单测（纯 JVM；[ReaderSheetState] 的 mutableStateOf 可在普通单测
 * 直接实例化，先例见 ReaderShellStateTest）。
 *
 * 断言与锚点卡六条判据一一对应：
 *  1. 同字重复请求：不新开、不重建，request 与首份 snapshotBefore 保持首次值；
 *  2. 异字请求：本单口径为「拒绝」——不切换内容，首份快照保留；
 *  3. isOpen 真实消费：open / close 路径均读取，初始 / 打开 / 关闭状态可断言；
 *  4. 关闭后再开：快照正确重置，不残留上一轮 after；
 *  5. 快速连点两次（同步连调模拟 <200ms）：只持有一层请求；
 *  6. B1 旧判据不破：开合快照五字段全等仍判 true，字段漂移判 false。
 */
class ReaderSheetStateTest {

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
    fun `判据1 同字重复请求被拒收且首份快照不动`() {
        val state = ReaderSheetState()
        val first = req(ReaderDetailKind.CHAR, "道")
        state.open(first, snap(scroll = 320))

        val dup = req(ReaderDetailKind.CHAR, "道")
        state.open(dup, snap(scroll = 400))

        assertSame(first, state.request)
        assertTrue(state.isOpen)
        assertEquals(snap(scroll = 320), state.snapshotBefore)
        assertNull(state.restoredExactly)
    }

    @Test
    fun `判据2 异字请求按拒绝口径处理并保留首份快照`() {
        val state = ReaderSheetState()
        val first = req(ReaderDetailKind.PERSON, "孔子")
        state.open(first, snap(scroll = 320))

        val other = req(ReaderDetailKind.CHAR, "仁")
        state.open(other, snap(scroll = 400))

        assertSame(first, state.request)
        assertEquals(snap(scroll = 320), state.snapshotBefore)
    }

    @Test
    fun `判据3 isOpen 在 open close 路径真实消费`() {
        val state = ReaderSheetState()
        assertFalse(state.isOpen)

        state.open(req(ReaderDetailKind.CHAR, "道"), snap())
        assertTrue(state.isOpen)

        state.dismiss(snap())
        assertFalse(state.isOpen)
        assertNull(state.request)

        // 已关态冗余 dismiss 不回写 after，比对结果仍为上一轮的 true。
        state.open(req(ReaderDetailKind.CHAR, "道"), snap())
        state.dismiss(snap())
        state.dismiss(snap(scroll = 999))
        assertEquals(true, state.restoredExactly)
    }

    @Test
    fun `判据4 关闭后再开快照正确重置`() {
        val state = ReaderSheetState()
        state.open(req(ReaderDetailKind.CHAR, "道"), snap(scroll = 320))
        state.dismiss(snap(scroll = 320))
        assertEquals(true, state.restoredExactly)

        state.open(req(ReaderDetailKind.PLACE, "曲阜"), snap(scroll = 500))
        assertEquals(snap(scroll = 500), state.snapshotBefore)
        assertNull(state.restoredExactly)

        state.dismiss(snap(scroll = 500))
        assertEquals(true, state.restoredExactly)
    }

    @Test
    fun `判据5 快速连点两次只产生一层`() {
        val state = ReaderSheetState()
        val first = req(ReaderDetailKind.YEAR, "鲁隐公元年")
        // 同步连续两次 open，模拟 <200ms 连点；中间无 dismiss。
        state.open(first, snap(scroll = 320))
        state.open(req(ReaderDetailKind.YEAR, "桓公二年"), snap(scroll = 400))

        assertSame(first, state.request)
        assertTrue(state.isOpen)
    }

    @Test
    fun `判据6 B1 快照比对旧判据不破`() {
        val state = ReaderSheetState()
        state.open(req(ReaderDetailKind.CHAR, "道"), snap())
        state.dismiss(snap())
        assertEquals(true, state.restoredExactly)

        val drifted = ReaderSheetState()
        drifted.open(req(ReaderDetailKind.CHAR, "道"), snap())
        drifted.dismiss(snap(scroll = 321))
        assertEquals(false, drifted.restoredExactly)
    }
}
