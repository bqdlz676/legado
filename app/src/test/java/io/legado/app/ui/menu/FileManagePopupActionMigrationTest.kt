package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 本机文件管理删除契约测试。
 *
 * 旧 FileManageActivity 长按 + 纵向弹窗菜单已拆除；新实现为 Compose 行长按 → 父目录守卫 →
 * requestDelete → 确认对话框 → executeDelete。删除副作用与父目录排除关注点保留，断言指向新落点。
 */
class FileManagePopupActionMigrationTest {

    @Test
    fun `file deletion uses the shared vertical popup action menu`() {
        val screen = readProjectFile(FILE_SCREEN)
        val viewModel = readProjectFile(FILE_VIEW_MODEL)

        assertFalse(screen.contains("PopupMenu(context, view)"))
        assertFalse(screen.contains("import android.widget.PopupMenu"))
        assertTrue(screen.contains("onRequestDelete(file)"))
        assertTrue(viewModel.contains("fun requestDelete(file: File)"))
        assertTrue(viewModel.contains("fun executeDelete(file: File)"))
        assertTrue(viewModel.contains("file.deleteRecursively()"))
        assertTrue(viewModel.contains("upFiles(lastDir)"))
    }

    @Test
    fun `parent directory remains excluded from the deletion menu`() {
        val screen = readProjectFile(FILE_SCREEN)
        val parentGuard = screen.indexOf("val isParent = file ==")
        val longClick = screen.indexOf("onLongClick = { if (!isParent) onRequestDelete(file) }")

        assertTrue("file rows should compute the parent directory flag", parentGuard >= 0)
        assertTrue("parent directory guard should remain before the long-click delete", longClick > parentGuard)
        assertTrue("parent directory should be excluded from the delete request", screen.contains("if (!isParent) onRequestDelete(file)"))
    }

    @Test
    fun `legacy file deletion menu resource is removed`() {
        assertFalse(projectFileExists("src/main/res/menu/file_long_click.xml"))
    }

    private fun readProjectFile(pathInApp: String): String =
        projectFile(pathInApp)?.readText().orEmpty()

    private fun projectFileExists(pathInApp: String): Boolean =
        projectFile(pathInApp) != null

    private fun projectFile(pathInApp: String): File? =
        sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)

    private companion object {
        const val FILE_SCREEN = "src/main/java/io/legado/app/ui/file/FileManageScreen.kt"
        const val FILE_VIEW_MODEL = "src/main/java/io/legado/app/ui/file/FileManageViewModel.kt"
    }
}
