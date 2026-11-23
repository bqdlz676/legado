package io.legado.app.ui.book.read.ink

import androidx.compose.ui.geometry.Rect
import io.legado.app.ui.book.read.vertical.CharHit as VerticalCharHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 落文 · 手势路由优先级单测（契约第五节铁律）。
 *
 * - 点中字永远优先于翻页：边缘区先 probeChar，命中出气泡，空白才翻页；
 * - 翻页动画进行中屏蔽一切点击 / 长按；
 * - 已有文字选中时长按不抢手势；
 * - 底栏点击不进路由。
 */
class GestureRouterTest {

    private companion object {
        const val W = 1000f
        const val H = 1920f
        const val BOTTOM_BAR_PX = 180f
    }

    private class FakeHost : ReaderShellHost {
        var animating = false
        var selected = false
        var probeResult: CharHit? = null
        var probeCalls = 0
        var requestBubbleHit: CharHit? = null
        var turnDir: EdgeDirection? = null
        var centerTapHit: CharHit? = null
        var longPressHit: CharHit? = null

        override fun probeChar(x: Float, y: Float): CharHit? {
            probeCalls++
            return probeResult
        }

        override fun turnPage(direction: EdgeDirection) {
            turnDir = direction
        }

        override fun onCenterTap(x: Float, y: Float, hit: CharHit?) {
            centerTapHit = hit
        }

        override fun onCenterLongPress(x: Float, y: Float, hit: CharHit?) {
            longPressHit = hit
        }

        override fun requestBubble(hit: CharHit) {
            requestBubbleHit = hit
        }

        override fun dismissBubble() {}

        override fun isTextSelected(): Boolean = selected

        override fun isPageAnimating(): Boolean = animating

        var backResult: InkBackResult = InkBackResult.ExitToShelf
        var backCalls = 0

        override fun handleBack(): InkBackResult {
            backCalls++
            return backResult
        }
    }

    private fun newHit(): CharHit {
        val engineHit = VerticalCharHit(char = '字', offset = 0, paragraphText = "段落原文", pos = 0)
        return CharHit(anchor = Rect(480f, 300f, 520f, 340f), engineHit = engineHit)
    }

    private fun zoneOf(x: Float, y: Float) = ZoneClassifier.classify(
        x = x, y = y, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX,
    )

    @Test
    fun `中间区点中字走 onCenterTap 并带上命中结果`() {
        val host = FakeHost().apply { probeResult = newHit() }
        GestureRouter.onTap(host, zoneOf(500f, 500f), 500f, 500f)
        assertSame(host.probeResult, host.centerTapHit)
        assertNull(host.requestBubbleHit)
        assertNull(host.turnDir)
    }

    @Test
    fun `中间区点空白走 onCenterTap 传 null`() {
        val host = FakeHost()
        GestureRouter.onTap(host, zoneOf(500f, 500f), 500f, 500f)
        assertNull(host.centerTapHit)
    }

    @Test
    fun `左边缘点中字优先出气泡，不翻页`() {
        val host = FakeHost().apply { probeResult = newHit() }
        GestureRouter.onTap(host, zoneOf(50f, 500f), 50f, 500f)
        assertSame(host.probeResult, host.requestBubbleHit)
        assertNull(host.turnDir)
    }

    @Test
    fun `左边缘点空白翻上一页`() {
        val host = FakeHost()
        GestureRouter.onTap(host, zoneOf(50f, 500f), 50f, 500f)
        assertEquals(EdgeDirection.Prev, host.turnDir)
        assertNull(host.requestBubbleHit)
    }

    @Test
    fun `右边缘点空白翻下一页`() {
        val host = FakeHost()
        GestureRouter.onTap(host, zoneOf(950f, 500f), 950f, 500f)
        assertEquals(EdgeDirection.Next, host.turnDir)
    }

    @Test
    fun `底栏点击不探测、不路由`() {
        val host = FakeHost().apply { probeResult = newHit() }
        GestureRouter.onTap(host, zoneOf(500f, H - 10f), 500f, H - 10f)
        assertEquals(0, host.probeCalls)
        assertNull(host.turnDir)
        assertNull(host.centerTapHit)
        assertNull(host.requestBubbleHit)
    }

    @Test
    fun `翻页动画进行中点击全部屏蔽`() {
        val host = FakeHost().apply { animating = true; probeResult = newHit() }
        GestureRouter.onTap(host, zoneOf(500f, 500f), 500f, 500f)
        GestureRouter.onTap(host, zoneOf(50f, 500f), 50f, 500f)
        assertEquals(0, host.probeCalls)
        assertNull(host.turnDir)
        assertNull(host.centerTapHit)
    }

    @Test
    fun `翻页动画进行中长按屏蔽`() {
        val host = FakeHost().apply { animating = true; probeResult = newHit() }
        GestureRouter.onLongPress(host, zoneOf(500f, 500f), 500f, 500f)
        assertEquals(0, host.probeCalls)
        assertNull(host.longPressHit)
    }

    @Test
    fun `已有选区时长按不抢手势`() {
        val host = FakeHost().apply { selected = true; probeResult = newHit() }
        GestureRouter.onLongPress(host, zoneOf(500f, 500f), 500f, 500f)
        assertEquals(0, host.probeCalls)
        assertNull(host.longPressHit)
    }

    @Test
    fun `普通长按探测字符并交宿主进入选择`() {
        val host = FakeHost().apply { probeResult = newHit() }
        GestureRouter.onLongPress(host, zoneOf(500f, 500f), 500f, 500f)
        assertSame(host.probeResult, host.longPressHit)
    }

    @Test
    fun `返回手势路由委托宿主同一出口，不自判层级`() {
        val host = FakeHost().apply { backResult = InkBackResult.BubbleDismissed }
        val result = GestureRouter.onBack(host)
        assertEquals(InkBackResult.BubbleDismissed, result)
        assertEquals(1, host.backCalls)
    }
}
