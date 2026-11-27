package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 落文 · 手势分区纯函数单测（契约第五节）。
 *
 * 分区口径：左 1/5 边缘 ｜ 中间 3/5 正文 ｜ 右 1/5 边缘；底栏 60dp 优先于左右边缘。
 * 全部 px 坐标判定，不依赖 Android 尺寸换算。
 */
class ZoneClassifierTest {

    private companion object {
        const val W = 1000f
        const val H = 1920f
        const val BOTTOM_BAR_PX = 180f
    }

    @Test
    fun `左缘五分之一判为 LeftEdge`() {
        val zone = ZoneClassifier.classify(x = 50f, y = 500f, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX)
        assertEquals(ReaderZone.LeftEdge, zone)
    }

    @Test
    fun `右缘五分之一判为 RightEdge`() {
        val zone = ZoneClassifier.classify(x = 950f, y = 500f, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX)
        assertEquals(ReaderZone.RightEdge, zone)
    }

    @Test
    fun `中间五分之三判为 Center`() {
        val zone = ZoneClassifier.classify(x = 500f, y = 500f, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX)
        assertEquals(ReaderZone.Center, zone)
    }

    @Test
    fun `底栏高度内一律归 BottomBar，优先于左右边缘`() {
        val onLeft = ZoneClassifier.classify(x = 10f, y = H - 1f, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX)
        val onRight = ZoneClassifier.classify(x = W - 10f, y = H - 1f, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX)
        assertEquals(ReaderZone.BottomBar, onLeft)
        assertEquals(ReaderZone.BottomBar, onRight)
    }

    @Test
    fun `底栏上沿压线（含）归 BottomBar，线上方一线才回边缘区`() {
        val atLine = ZoneClassifier.classify(x = 50f, y = H - BOTTOM_BAR_PX, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX)
        assertEquals(ReaderZone.BottomBar, atLine)
        val aboveLine = ZoneClassifier.classify(x = 50f, y = H - BOTTOM_BAR_PX - 1f, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX)
        assertEquals(ReaderZone.LeftEdge, aboveLine)
    }

    @Test
    fun `退化尺寸兜底为 Center，不崩`() {
        val zone = ZoneClassifier.classify(x = 10f, y = 10f, width = 0f, height = 0f, bottomBarPx = 0f)
        assertEquals(ReaderZone.Center, zone)
    }

    @Test
    fun `坐标 NaN 兜底为 Center 不崩`() {
        assertEquals(
            ReaderZone.Center,
            ZoneClassifier.classify(x = Float.NaN, y = 500f, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX),
        )
        assertEquals(
            ReaderZone.Center,
            ZoneClassifier.classify(x = 500f, y = Float.NaN, width = W, height = H, bottomBarPx = BOTTOM_BAR_PX),
        )
    }

    @Test
    fun `边缘方向映射：左 Prev 右 Next，中间与底栏无方向`() {
        assertEquals(EdgeDirection.Prev, ZoneClassifier.edgeDirection(ReaderZone.LeftEdge))
        assertEquals(EdgeDirection.Next, ZoneClassifier.edgeDirection(ReaderZone.RightEdge))
        assertNull(ZoneClassifier.edgeDirection(ReaderZone.Center))
        assertNull(ZoneClassifier.edgeDirection(ReaderZone.BottomBar))
    }
}
