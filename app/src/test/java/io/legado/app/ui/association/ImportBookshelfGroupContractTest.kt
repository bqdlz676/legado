package io.legado.app.ui.association

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Q-244 B34 契约：书单导入按来源计算分组，同一来源复用同一分组；
 * 来源不可识别 / 分组位耗尽时回落未分组（groupId = 0），旧有 group = 0 的书籍兼容不变。
 */
class ImportBookshelfGroupContractTest {

    private val viewModel = projectFile(
        "src/main/java/io/legado/app/ui/association/FileAssociationViewModel.kt"
    ).readText()

    private val importData = viewModel
        .substringAfter("fun importData(type: String, source: String)")
        .substringBefore("private suspend fun dispatch")

    @Test
    fun `bookshelf import resolves group from source before importing`() {
        assertTrue(importData.contains("resolveImportGroupId(source)"))
        assertTrue(importData.contains("importBookshelfJson("))
        assertTrue(importData.contains("importGroupId"))
        assertFalse(importData.contains(", 0)"))
        assertTrue(viewModel.contains("const val IMPORT_DEFAULT_GROUP_ID = 0L"))
    }

    @Test
    fun `resolver reuses source group or falls back to ungrouped`() {
        assertTrue(viewModel.contains("sourceImportGroupKey(source)"))
        assertTrue(viewModel.contains("matchesImportGroupTag"))
        assertTrue(viewModel.contains("allocateFreeGroupBit"))
        assertTrue(viewModel.contains("appDb.bookGroupDao.insert("))
    }

    @Test
    fun `success toast explains actual group destination`() {
        assertTrue(importData.contains("R.string.import_bookshelf_source_group"))
        assertTrue(importData.contains("R.string.import_bookshelf_default_group"))
        assertTrue(importData.contains("importGroupId > 0"))
        val defaultStrings = projectFile("src/main/res/values/strings.xml").readText()
        val zhStrings = projectFile("src/main/res/values-zh/strings.xml").readText()
        assertTrue(defaultStrings.contains("import_bookshelf_source_group"))
        assertTrue(zhStrings.contains("import_bookshelf_source_group"))
        assertTrue(defaultStrings.contains("import_bookshelf_default_group"))
        assertTrue(zhStrings.contains("import_bookshelf_default_group"))
    }

    @Test
    fun `legacy zero group stays ungrouped by import guard`() {
        val importer = projectFile(
            "src/main/java/io/legado/app/ui/main/bookshelf/BookshelfViewModel.kt"
        ).readText()
        assertTrue(importer.contains("if (groupId > 0) book.group = groupId"))
    }

    private fun projectFile(pathInApp: String): File =
        listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
}
