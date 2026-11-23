package io.legado.app.ui.book.read.ink

import io.legado.app.help.chrono.Chronology
import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 落文 · 纪年时间轴装配单测（切片-03 纪年时间轴页 + 切片-04 §3 Timeline 组件）。
 *
 * 覆盖验收断言：点「鲁隐公元年」→ 前722，干支己未，同年对照行含周 / 鲁 / 郑；
 * 左右切年边界兜底；无在世政权年份保留干支标题并走「世系未载」空态，不空白崩溃。
 * 纪年换算本身归 C1 [Chronology]，本类只断言展示模型装配口径。
 */
class InkTimelineActivityTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
    }

    @Test
    fun `前722 干支己未，标题含公元前 722 与距今`() {
        val state = InkTimelinePresenter.build(-722, "鲁隐公元年")
        assertEquals(-722, state.year)
        assertEquals("己未", state.ganzhi)
        assertTrue(state.title.contains("前722"))
        assertTrue(state.title.contains("己未"))
        assertEquals("鲁隐公元年", state.label)
    }

    @Test
    fun `前722 同年对照行：周平王四十九年、鲁隐公元年、郑庄公二十二年`() {
        val state = InkTimelinePresenter.build(-722, null)
        val byState = state.rows.associateBy { it.state }

        val zhou = byState["周"]
        assertTrue(zhou != null && zhou.title == "平王" && zhou.nth == 49)

        val lu = byState["鲁"]
        assertTrue(lu != null && lu.title == "隐公" && lu.nth == 1)

        val zheng = byState["郑"]
        assertTrue(zheng != null && zheng.title == "庄公" && zheng.nth == 22)
    }

    @Test
    fun `左右切年：显式年份 ±1；锚点未定年回退基准年 1950`() {
        assertEquals(619, InkTimelinePresenter.shiftYear(618, 1))
        assertEquals(-723, InkTimelinePresenter.shiftYear(-722, -1))

        ContextAnchor.setYear(-722)
        assertEquals(-721, InkTimelinePresenter.shiftYear(null, 1))

        ContextAnchor.reset()
        assertEquals(Chronology.PRESENT_BASE, InkTimelinePresenter.shiftYear(null, 0))
    }

    @Test
    fun `无在世政权的年份：对照行为空，干支标题仍在，走空态兜底`() {
        val state = InkTimelinePresenter.build(1800, null)
        assertTrue(state.rows.isEmpty())
        assertTrue(state.ganzhi.isNotBlank())
        assertTrue(state.title.isNotBlank())
    }

    @Test
    fun `公元后年份正常出标题与干支`() {
        val state = InkTimelinePresenter.build(627, "贞观元年")
        assertEquals(627, state.year)
        assertTrue(state.title.contains("公元627"))
        assertEquals("贞观元年", state.label)
    }
}
