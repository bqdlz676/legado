package io.legado.app.ui.book.source.manage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 分组删除契约测试（Compose 重写后口径）。
 *
 * 旧 BookSourceViewModel.delGroup 拆除，落点为 SourceManageViewModel.deleteGroup；
 * 删除分组仍委托给统一的分组更新路径（renameGroup 传 null），不逐条直接摘除分组串。
 */
class BookSourceViewModelGroupContractTest {

    private val source by lazy {
        projectFile("src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt")
            .readText()
            .replace("\r\n", "\n")
            .replace(Regex("\\s+"), " ")
    }

    @Test
    fun `group deletion delegates to exact group update`() {
        assertTrue(source.contains("fun deleteGroup(group: String) = renameGroup(group, null)"))
        val deleteSection = source.substringAfter("fun deleteGroup(group: String)")
        assertFalse(
            "deleteGroup must not call removeGroup directly",
            deleteSection.contains("removeGroup(")
        )
    }

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
    }
}
