package io.legado.app.help.book

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B33 契约：books.json 的旧分享导出器已在 B31 清剿中删除，仓内无任何写入方，
 * clearInvalidCache 里对应的清理行是永久空操作，必须摘除；仍有写入方的两个
 * 分享书源 json 清理行必须保留。
 */
class ClearInvalidCacheBooksJsonTest {

    private val source = projectFile(
        "src/main/java/io/legado/app/help/book/BookHelp.kt"
    ).readText()

    private val cleanup = source
        .substringAfter("suspend fun clearInvalidCache()")
        .substringBefore("private fun clearComicCache")

    @Test
    fun `stale books json cleanup line was removed`() {
        assertFalse(cleanup.contains("books.json"))
        assertFalse(source.contains("books.json"))
    }

    @Test
    fun `live share source cleanup lines are kept`() {
        assertTrue(cleanup.contains("shareBookSource.json"))
        assertTrue(cleanup.contains("shareRssSource.json"))
    }

    private fun projectFile(pathInApp: String): File =
        listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
}
