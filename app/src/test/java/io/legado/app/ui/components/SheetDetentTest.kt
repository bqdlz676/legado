package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BottomSheet 分段吸附、关闭阈值、遮罩联动与 detent 收口。
 */
class SheetDetentTest {

    @Test
    fun `最近分段吸附到 30 60 90`() {
        assertEquals(0.30f, SheetDetent.nearest(0.20f), 1e-6f)
        assertEquals(0.60f, SheetDetent.nearest(0.55f), 1e-6f)
        assertEquals(0.90f, SheetDetent.nearest(0.95f), 1e-6f)
    }

    @Test
    fun `拖过低段阈值关闭 未过阈值回弹`() {
        assertTrue(SheetDetent.shouldDismiss(0.10f, 0f))
        assertTrue(SheetDetent.shouldDismiss(0.25f, 800f))
        assertFalse(SheetDetent.shouldDismiss(0.28f, 0f))
        assertFalse(SheetDetent.shouldDismiss(0.60f, 100f))
    }

    @Test
    fun `遮罩透明度与升起进度联动并夹紧到 0 1`() {
        assertEquals(0f, SheetDetent.maskAlpha(0f), 1e-6f)
        assertEquals(1f, SheetDetent.maskAlpha(1f), 1e-6f)
        assertEquals(1f, SheetDetent.maskAlpha(1.4f), 1e-6f)
        assertEquals(0f, SheetDetent.maskAlpha(-0.3f), 1e-6f)
    }

    @Test
    fun `非法 detent 回落到中段 60`() {
        assertEquals(0.60f, SheetDetent.normalize(0.42f), 1e-6f)
        assertEquals(0.30f, SheetDetent.normalize(0.30f), 1e-6f)
        assertEquals(0.90f, SheetDetent.normalize(0.90f), 1e-6f)
    }
}
