package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · ViewGestureBridge 纯逻辑入口单测（离线 JVM，不触 Android View）。
 *
 * 覆盖：
 *  - 触摸快照不消费事件，单击 / 长按经分区交 [GestureRouter] 同一套判定；
 *  - 左缘空白上翻、右缘空白下翻、中间区点空白交 onCenterTap、底栏不探测；
 *  - 翻页动画中点击 / 长按被屏蔽；退化尺寸回退 Center。
 *
 * 接线侧 attach / detach 属 Android View 挂摘，不在本单测覆盖范围（定界 I-09：
 * View 监听绑定在新壳接线时重接，运行期行为留 L2 CI 与真机验收）。
 */
class ViewGestureBridgeTest {

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
        var backResult: InkBackResult = InkBackResult.ExitToShelf

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

        override fun dismissBubble() = Unit

        override fun isTextSelected(): Boolean = selected

        override fun isPageAnimating(): Boolean = animating

        override fun handleBack(): InkBackResult = backResult
    }

    private fun bridge(host: FakeHost) = ViewGestureBridge(host, BOTTOM_BAR_PX)

    @Test
    fun `触摸移动返回 false 不消费事件`() {
        val host = FakeHost()
        val bridge = bridge(host)
        assertFalse(bridge.touchMove(50f, 500f, W, H))
    }

    @Test
    fun `左缘点空白经桥接上翻一页`() {
        val host = FakeHost()
        val bridge = bridge(host)
        bridge.touchMove(50f, 500f, W, H)
        bridge.tap()
        assertEquals(EdgeDirection.Prev, host.turnDir)
        assertNull(host.requestBubbleHit)
    }

    @Test
    fun `右缘点空白经桥接下翻一页`() {
        val host = FakeHost()
        val bridge = bridge(host)
        bridge.tap(950f, 500f, W, H)
        assertEquals(EdgeDirection.Next, host.turnDir)
    }

    @Test
    fun `中间区点空白交宿主 onCenterTap 传 null`() {
        val host = FakeHost()
        val bridge = bridge(host)
        bridge.tap(500f, 500f, W, H)
        assertNull(host.centerTapHit)
        assertNull(host.turnDir)
    }

    @Test
    fun `底栏点击不探测字符不路由`() {
        val host = FakeHost()
        val bridge = bridge(host)
        bridge.tap(500f, H - 10f, W, H)
        assertEquals(0, host.probeCalls)
        assertNull(host.turnDir)
        assertNull(host.centerTapHit)
    }

    @Test
    fun `长按返回已消费并交宿主进入选择`() {
        val host = FakeHost()
        val bridge = bridge(host)
        val consumed = bridge.longPress(500f, 500f, W, H)
        assertTrue(consumed)
        assertNull(host.longPressHit)
    }

    @Test
    fun `翻页动画进行中点击被屏蔽`() {
        val host = FakeHost().apply { animating = true }
        val bridge = bridge(host)
        bridge.tap(50f, 500f, W, H)
        assertEquals(0, host.probeCalls)
        assertNull(host.turnDir)
    }

    @Test
    fun `退化尺寸分区回退 Center`() {
        val host = FakeHost()
        val bridge = bridge(host)
        assertEquals(ReaderZone.Center, bridge.zoneOf(10f, 10f, 0f, 0f))
    }
}
