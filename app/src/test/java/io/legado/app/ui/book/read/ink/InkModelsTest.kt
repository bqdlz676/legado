package io.legado.app.ui.book.read.ink

import androidx.compose.ui.geometry.Rect
import io.legado.app.ui.book.read.vertical.CharHit as VerticalCharHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 落文 · 壳模型纯数据不变式单测（B4-05 I-32）。
 *
 * 只断言不可变数据的透传 / 派生口径：命中字段回传引擎结果、气泡锚点由命中矩形派生、
 * 分区 / 方向 / 派发模式枚举在位、气泡状态两态构造合法。不触 Android / Compose 运行时。
 */
class InkModelsTest {

    private fun hitAt(left: Float, top: Float, right: Float, bottom: Float, offset: Int = 7): CharHit {
        val engineHit = VerticalCharHit(char = '字', offset = offset, paragraphText = "段落原文", pos = 3)
        return CharHit(anchor = Rect(left, top, right, bottom), engineHit = engineHit)
    }

    @Test
    fun `CharHit 只读派生字段透传引擎命中`() {
        val hit = hitAt(10f, 20f, 30f, 40f)
        assertEquals("字", hit.text)
        assertEquals("段落原文", hit.paragraphText)
        assertEquals(3, hit.pos)
        assertEquals(7, hit.offset)
    }

    @Test
    fun `BubbleAnchor 由命中矩形派生字心与行边界`() {
        val hit = hitAt(10f, 20f, 30f, 40f)
        val anchor = BubbleAnchor(hit)
        assertEquals(20f, anchor.centerX, 1e-6f)
        assertEquals(20f, anchor.topY, 1e-6f)
        assertEquals(40f, anchor.bottomY, 1e-6f)
        assertEquals(20f, anchor.lineTopY, 1e-6f)
        assertEquals(40f, anchor.lineBottomY, 1e-6f)
    }

    @Test
    fun `手势分区方向与派发模式枚举四值在位`() {
        assertEquals(ReaderZone.LeftEdge, ReaderZone.valueOf("LeftEdge"))
        assertEquals(ReaderZone.Center, ReaderZone.valueOf("Center"))
        assertEquals(ReaderZone.RightEdge, ReaderZone.valueOf("RightEdge"))
        assertEquals(ReaderZone.BottomBar, ReaderZone.valueOf("BottomBar"))
        assertEquals(EdgeDirection.Prev, EdgeDirection.Prev)
        assertEquals(EdgeDirection.Next, EdgeDirection.Next)
        assertEquals(DispatchMode.ComposeOverlay, DispatchMode.ComposeOverlay)
        assertEquals(DispatchMode.ViewBridge, DispatchMode.ViewBridge)
    }

    @Test
    fun `气泡状态 Hidden 无锚点，Visible 携带锚点与命中`() {
        assertFalse(BubbleState.Visible::class.java.isInstance(BubbleState.Hidden))
        val hit = hitAt(10f, 20f, 30f, 40f)
        val visible = BubbleState.Visible(BubbleAnchor(hit), hit)
        assertEquals(hit, visible.hit)
    }
}
