package io.legado.app.ui.main.bookshelf

import io.legado.app.data.entities.Book
import io.legado.app.help.book.readProgress
import io.legado.app.help.config.BookshelfReadProgressMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 书架进度相关纯逻辑单测。
 *
 * 旧版 RecyclerView 书架（style1/style2 / BaseBookshelfFragment / Material
 * LinearProgressIndicator 扩展）已随单路改造拆除；本文件只保留仍在生产代码中的纯口径：
 * [Book.readProgress] 进度换算、[BookshelfReadProgressMode] 模式收口、BookDao 查询口径。
 * 旧布局 XML / 旧 Fragment 的结构契约断言已随旧实现一并移除。
 */
class BookshelfReadProgressTest {

    @Test
    fun `unopened books do not expose progress`() {
        assertNull(Book(totalChapterNum = 20).readProgress())
    }

    @Test
    fun `single chapter books become complete after reading starts`() {
        val book = Book(totalChapterNum = 1, durChapterPos = 1)
        assertEquals(1f, book.readProgress() ?: 0f, 0f)
    }

    @Test
    fun `chapter index maps to bounded progress`() {
        assertEquals(
            0.5f,
            Book(totalChapterNum = 11, durChapterIndex = 5).readProgress() ?: 0f,
            0f,
        )
        assertEquals(
            1f,
            Book(totalChapterNum = 10, durChapterIndex = 20).readProgress() ?: 0f,
            0f,
        )
    }

    @Test
    fun `bookshelf progress modes preserve legacy settings and thickness`() {
        assertEquals(
            BookshelfReadProgressMode.HIDDEN,
            BookshelfReadProgressMode.resolve(null, false),
        )
        assertEquals(
            BookshelfReadProgressMode.STANDARD,
            BookshelfReadProgressMode.resolve(null, true),
        )
        assertEquals(
            BookshelfReadProgressMode.ENHANCED,
            BookshelfReadProgressMode.resolve("2", false),
        )
        assertEquals(
            BookshelfReadProgressMode.STANDARD_THICKNESS_DP,
            BookshelfReadProgressMode.thicknessDp(BookshelfReadProgressMode.HIDDEN),
        )
        assertEquals(
            BookshelfReadProgressMode.STANDARD_THICKNESS_DP,
            BookshelfReadProgressMode.thicknessDp(BookshelfReadProgressMode.STANDARD),
        )
        assertEquals(
            BookshelfReadProgressMode.ENHANCED_THICKNESS_DP,
            BookshelfReadProgressMode.thicknessDp(BookshelfReadProgressMode.ENHANCED),
        )

        val appConfig = projectFile("src/main/java/io/legado/app/help/config/AppConfig.kt").readText()
        assertTrue(appConfig.contains("PreferKey.bookshelfReadProgressMode"))
        assertTrue(appConfig.contains("PreferKey.showBookshelfReadProgress"))

        val restore = projectFile("src/main/java/io/legado/app/help/storage/Restore.kt").readText()
        assertTrue(restore.contains("PreferKey.bookshelfReadProgressMode !in map"))
        assertTrue(restore.contains("BookshelfReadProgressMode.STANDARD"))
    }

    @Test
    fun `continue reading query includes every shelf media type`() {
        val source = projectFile("src/main/java/io/legado/app/data/dao/BookDao.kt").readText()
        val propertyIndex = source.indexOf("val lastReadBookOnShelf")
        val queryStart = source.lastIndexOf("@get:Query(", propertyIndex)
        val query = source.substring(queryStart, propertyIndex)

        assertTrue(query.contains("type & \${BookType.notShelf} = 0"))
        assertFalse(query.replace("BookType.notShelf", "").contains("BookType."))
        assertTrue(query.contains("durChapterIndex > 0 OR durChapterPos > 0"))
        assertTrue(query.contains("durChapterTime DESC limit 1"))
    }

    @Test
    fun `reading count query only includes started shelf books`() {
        val source = projectFile("src/main/java/io/legado/app/data/dao/BookDao.kt").readText()
        val propertyIndex = source.indexOf("val readingCount")
        val queryStart = source.lastIndexOf("@get:Query(", propertyIndex)
        val query = source.substring(queryStart, propertyIndex)

        assertTrue(query.contains("(durChapterIndex > 0 OR durChapterPos > 0)"))
        assertTrue(query.contains("and type & \${BookType.notShelf} = 0"))
        assertFalse(query.replace("BookType.notShelf", "").contains("BookType."))
        assertFalse(query.contains("durChapterTime"))
    }

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
    }
}
