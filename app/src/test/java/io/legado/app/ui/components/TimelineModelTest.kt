package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间轴模型：边界箭头、节点导航、数据自检。
 */
class TimelineModelTest {

    private fun sample() = TimelineModel(
        nodes = listOf(
            TimelineNode(
                yearLabel = "前 722 年 · 辛酉",
                eraLabel = "鲁隐公元年",
                regimes = listOf(
                    RegimeRow("周平王", "四十九年"),
                    RegimeRow("郑庄公", "二十二年"),
                ),
            ),
            TimelineNode(yearLabel = "前 721 年 · 壬戌", eraLabel = "鲁隐公二年"),
            TimelineNode(yearLabel = "前 720 年 · 癸亥", eraLabel = "鲁隐公三年", current = true),
        ),
        index = 0,
    )

    @Test
    fun `首节点不可后退 末节点不可前进`() {
        val model = sample()
        assertFalse(model.canGoBack)
        assertTrue(model.canGoForward)
        assertEquals(0, model.goBack().index)
        assertEquals(1, model.goForward().index)
    }

    @Test
    fun `前进到末节点后箭头状态翻转`() {
        val atEnd = sample().goForward().goForward()
        assertTrue(atEnd.canGoBack)
        assertFalse(atEnd.canGoForward)
        assertEquals(2, atEnd.goForward().index)
        assertEquals("鲁隐公三年", atEnd.current()?.eraLabel)
    }

    @Test
    fun `政权对照行随节点带出`() {
        val first = sample().current()!!
        assertEquals(2, first.regimes.size)
        assertEquals("周平王", first.regimes.first().regime)
    }

    @Test
    fun `空模型 current 为空且自检报错`() {
        assertNull(TimelineModel.Empty.current())
        assertTrue(TimelineModel.Empty.validate().any { it.contains("nodes") })
    }

    @Test
    fun `索引越界自检报错`() {
        val broken = sample().copy(index = 99)
        assertTrue(broken.validate().any { it.contains("index") })
    }

    @Test
    fun `缺年份标签的节点自检报错`() {
        val broken = TimelineModel(listOf(TimelineNode(yearLabel = "", eraLabel = "x")))
        assertTrue(broken.validate().any { it.contains("yearLabel") })
    }
}
