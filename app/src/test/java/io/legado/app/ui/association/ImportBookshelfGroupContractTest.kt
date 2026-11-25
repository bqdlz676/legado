package io.legado.app.ui.association

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B34 契约：外部分享/文件入口拿不到用户当前可见分组，书单导入统一落未分组，
 * 分组 id 必须用具名常量表达，不允许再出现裸 0；成功后必须提示去向。
 */
class ImportBookshelfGroupContractTest {

    private val viewModel = projectFile(
        "src/main/java/io/legado/app/ui/association/FileAssociationViewModel.kt"
    ).readText()

    private val importData = viewModel
        .substringAfter("fun importData(type: String, source: String)")
        .substringBefore("private suspend fun dispatch")

    @Test
    fun `bookshelf import passes explicit default group constant`() {
        assertTrue(importData.contains("IMPORT_DEFAULT_GROUP_ID"))
        assertFalse(importData.contains(", 0)"))
        assertTrue(viewModel.contains("const val IMPORT_DEFAULT_GROUP_ID = 0L"))
    }

    @Test
    fun `bookshelf success toast explains default group destination`() {
        assertTrue(importData.contains("R.string.import_bookshelf_default_group"))
        assertTrue(
            importData.indexOf("if (type == \"bookshelf\")") <
                importData.indexOf("R.string.import_bookshelf_default_group")
        )
        val defaultStrings = projectFile("src/main/res/values/strings.xml").readText()
        val zhStrings = projectFile("src/main/res/values-zh/strings.xml").readText()
        assertTrue(defaultStrings.contains("import_bookshelf_default_group"))
        assertTrue(zhStrings.contains("import_bookshelf_default_group"))
    }

    private fun projectFile(pathInApp: String): File =
        listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
}
