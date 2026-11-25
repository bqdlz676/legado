package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.main.bookshelf.search.NO_MATCH_HINT
import io.legado.app.ui.main.bookshelf.search.SearchField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-13 接线层验收：[ShelfSearch.search] / [ShelfSearch.SearchOutcome] / `toLite` 换算分支
 * （纯 JVM，不依赖 Compose / Android 框架）。
 *
 * 覆盖分支：空查询与全角空白回退全量；无命中给占位 hint；多命中三路排序与片段下标对齐；
 * 大小写不敏感；重复 Lite 投影不折叠丢条目；命中结果不被隐藏上限截断；空章节文案回落书名+作者。
 */
class ShelfSearchOutcomeTest {

    private fun shelfBook(
        id: String,
        title: String,
        author: String,
        chapterText: String = "未起读",
    ) = ShelfBookUi(
        id = id,
        title = title,
        author = author,
        chapterText = chapterText,
        progress = 0f,
        percentText = "未读",
        isNew = false,
    )

    @Test
    fun `empty query returns full shelf with blank hint and no segments`() {
        val books = listOf(
            shelfBook("1", "春秋左传", "左丘明"),
            shelfBook("2", "史记", "司马迁"),
            shelfBook("3", "三国志", "陈寿"),
        )

        val outcome = ShelfSearch.search(books, "")

        assertEquals("空词必须原样返回全量书架", 3, outcome.results.size)
        assertEquals("", outcome.hint)
        assertTrue("空词路径不产出命中片段", outcome.segments.isEmpty())
    }

    @Test
    fun `full width whitespace query falls back to full shelf`() {
        val books = listOf(shelfBook("1", "史记", "司马迁"))

        val outcome = ShelfSearch.search(books, "　 　")

        assertEquals("全角空白按空词处理，返回全量", 1, outcome.results.size)
        assertEquals("", outcome.hint)
    }

    @Test
    fun `no match returns empty results with no match hint`() {
        val books = listOf(
            shelfBook("1", "史记", "司马迁"),
            shelfBook("2", "三国志", "陈寿"),
        )

        val outcome = ShelfSearch.search(books, "庄子")

        assertTrue("无命中必须返回空列表", outcome.results.isEmpty())
        assertEquals(NO_MATCH_HINT, outcome.hint)
        assertTrue("无命中不产出命中片段", outcome.segments.isEmpty())
    }

    @Test
    fun `multiple hits ranked title author body with aligned segments`() {
        val bodyOnly = shelfBook("1", "乙篇", "作者乙", "正文关键词片段")
        val authorOnly = shelfBook("2", "丙篇", "关键词作者", "正文无")
        val titleBook = shelfBook("3", "关键词甲", "作者甲", "正文无")

        val outcome = ShelfSearch.search(listOf(bodyOnly, authorOnly, titleBook), "关键词")

        assertEquals(3, outcome.results.size)
        assertEquals("书名组在前，作者组次之，正文组垫底",
            listOf("关键词甲", "丙篇", "乙篇"), outcome.results.map { it.title })
        assertEquals("片段列表与结果按下标对齐", outcome.results.size, outcome.segments.size)

        val titleSeg = outcome.segments[0].first { it.field == SearchField.TITLE }
        assertEquals("书名片段按区间切出查询词", "关键词",
            outcome.results[0].title.substring(titleSeg.start, titleSeg.end))

        val authorSeg = outcome.segments[1].first { it.field == SearchField.AUTHOR }
        assertEquals("作者片段按区间切出查询词", "关键词",
            outcome.results[1].author.substring(authorSeg.start, authorSeg.end))
    }

    @Test
    fun `matching is case insensitive for title and author`() {
        val books = listOf(shelfBook("1", "Analects", "Confucius"))

        val lowerTitle = ShelfSearch.search(books, "analects")
        val upperAuthor = ShelfSearch.search(books, "CONFUCIUS")

        assertEquals(1, lowerTitle.results.size)
        assertEquals("Analects", lowerTitle.results.first().title)
        assertEquals(1, upperAuthor.results.size)
        assertEquals("Confucius", upperAuthor.results.first().author)
    }

    @Test
    fun `duplicate lite projection keeps both books with original identity`() {
        // 两本书投影出完全相同的 Lite（同书名/作者/章节文案），旧 associateBy 折叠会漏掉第二条。
        val first = shelfBook("dup-a", "春秋左传", "左丘明")
        val second = shelfBook("dup-b", "春秋左传", "左丘明")

        val outcome = ShelfSearch.search(listOf(first, second), "左传")

        assertEquals("重复 Lite 键不得折叠丢条目", 2, outcome.results.size)
        assertEquals(setOf("dup-a", "dup-b"), outcome.results.map { it.id }.toSet())
        assertEquals(2, outcome.segments.size)
    }

    @Test
    fun `large matching shelf is not capped by hidden result limit`() {
        val books = (0 until 20).map { shelfBook("$it", "春秋$it", "作者$it") }

        val outcome = ShelfSearch.search(books, "春秋")

        assertEquals("规格未给结果条数上限，20 条命中必须全部返回", 20, outcome.results.size)
        assertEquals(20, outcome.segments.size)
    }

    @Test
    fun `blank chapter text falls back to title author without dropping author hit`() {
        val book = shelfBook("1", "史记", "司马迁", chapterText = "")

        val outcome = ShelfSearch.search(listOf(book), "司马迁")

        assertEquals(1, outcome.results.size)
        assertTrue("空章节文案回落书名+作者后，作者路仍可命中",
            outcome.segments.first().any { it.field == SearchField.AUTHOR })
    }
}
