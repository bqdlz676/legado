package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 书架展示模型与进度换算单测（纯 JVM）。
 *
 * 覆盖：进度夹取、百分比文案、字段缺失兜底、新书判定窗口、竖排书名切分、id 兜底。
 */
class ShelfBookUiTest {

    private companion object {
        const val NOW: Long = 1_764_000_000_000L
    }

    @Test
    fun `章节口径进度按章序夹取在 0 到 1`() {
        assertEquals(0f, ShelfBookMath.progressOf(0, 0), 1e-6f)
        assertEquals(0.25f, ShelfBookMath.progressOf(0, 4), 1e-6f)
        assertEquals(0.6f, ShelfBookMath.progressOf(2, 5), 1e-6f)
        // 越界章序 / 总章数都夹回 0..1
        assertEquals(1f, ShelfBookMath.progressOf(99, 10), 1e-6f)
        assertEquals(0f, ShelfBookMath.progressOf(-5, 10), 1e-6f)
    }

    @Test
    fun `百分比文案：未读与百分比两档`() {
        assertEquals("未读", ShelfBookMath.percentText(0f))
        assertEquals("50%", ShelfBookMath.percentText(0.5f))
        assertEquals("100%", ShelfBookMath.percentText(1f))
    }

    @Test
    fun `空字段全部走兜底文案，不出空串`() {
        val ui = shelfBookUi(
            id = "",
            title = "   ",
            author = "",
            durChapterIndex = 0,
            totalChapterNum = 0,
            chapterTitle = null,
            latestChapterTime = 0L,
            now = NOW,
        )
        assertEquals("无名卷", ui.title)
        assertEquals("佚名", ui.author)
        assertEquals("未起读", ui.chapterText)
        assertEquals("未读", ui.percentText)
        assertEquals("无名卷|佚名", ui.id)
    }

    @Test
    fun `正常字段原样保留并换算进度`() {
        val ui = shelfBookUi(
            id = "url-1",
            title = "春秋左传",
            author = "杜预 注 · 孔颖达 疏",
            durChapterIndex = 2,
            totalChapterNum = 10,
            chapterTitle = "隐公元年",
            latestChapterTime = NOW,
            now = NOW,
        )
        assertEquals("url-1", ui.id)
        assertEquals(0.3f, ui.progress, 1e-6f)
        assertEquals("30%", ui.percentText)
        assertEquals("隐公元年", ui.chapterText)
        assertTrue(ui.isNew)
    }

    @Test
    fun `新书判定以 7 天窗口为准`() {
        val window = ShelfTokens.NEW_BOOK_WINDOW_MS
        assertTrue(ShelfBookMath.isNew(NOW - window / 2, NOW))
        assertFalse(ShelfBookMath.isNew(NOW - window - 1, NOW))
        assertFalse(ShelfBookMath.isNew(0L, NOW))
    }

    @Test
    fun `竖排书名逐字成行，超长收为省略号`() {
        assertEquals(listOf("史", "记"), verticalTitle("史记"))
        assertEquals(listOf("春", "秋", "左", "传"), verticalTitle("春秋左传"))
        val longTitle = verticalTitle("資治通鑑綱目卷之一")
        assertEquals(6, longTitle.size)
        assertEquals("…", longTitle.last())
    }

    @Test
    fun `状态机七态齐备，不允许空白态`() {
        val states = ShelfState.entries.map { it.name }.toSet()
        assertTrue(states.containsAll(listOf(
            "CONTENT", "INIT_LOADING", "REFRESHING", "EMPTY",
            "ERROR", "DISABLED", "OFFLINE",
        )))
    }
}
