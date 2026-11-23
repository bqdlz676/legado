package io.legado.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SelectionIntervalGuardTest {

    @Test
    fun `selection interval returns before reading empty bounds`() {
        // 旧适配器口径：checkSelectedInterval() 内先判空再 Collections.min。
        legacyAdapters.forEach { relativePath ->
            val source = File(sourceRoot, relativePath).readText()
            val methodStart = source.indexOf("fun checkSelectedInterval()")
            val guard = source.indexOf("if (selectedPosition.isEmpty()) return", methodStart)
            val min = source.indexOf("Collections.min(selectedPosition)", methodStart)

            assertTrue("missing checkSelectedInterval in $relativePath", methodStart >= 0)
            assertTrue("missing empty selection guard in $relativePath", guard > methodStart)
            assertTrue("empty selection guard must precede Collections.min in $relativePath", guard < min)
        }

        // 书源列表已 Compose 化，区间全选收口到 SourceManageViewModel.selectCheckedInterval()：
        // 同样必须先判空，再取 positions.min()/max()，空选择不得读下标边界。
        val viewModel = File(
            sourceRoot,
            "io/legado/app/ui/book/source/manage/SourceManageViewModel.kt"
        ).readText()
        val methodStart = viewModel.indexOf("fun selectCheckedInterval()")
        val guard = viewModel.indexOf("if (positions.isEmpty()) return", methodStart)
        val min = viewModel.indexOf("positions.min()", methodStart)
        assertTrue("missing selectCheckedInterval in SourceManageViewModel.kt", methodStart >= 0)
        assertTrue("missing empty selection guard in SourceManageViewModel.kt", guard > methodStart)
        assertTrue("empty selection guard must precede positions.min()", guard < min)
    }

    @Test
    fun `book selection uses stable book urls`() {
        val source = File(
            sourceRoot,
            "io/legado/app/ui/book/manage/BookAdapter.kt"
        ).readText()
        assertTrue(source.contains("HashSet<String>"))
        assertTrue(source.contains("AdvanceCallback<String>"))
        assertTrue(source.contains("selectedBookUrls.contains(it.bookUrl)"))
    }

    private val sourceRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    private companion object {
        val legacyAdapters = listOf(
            "io/legado/app/ui/book/manage/BookAdapter.kt",
            "io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt",
        )
    }
}
