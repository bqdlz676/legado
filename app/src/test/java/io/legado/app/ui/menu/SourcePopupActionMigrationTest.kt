package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 书源相关行菜单迁移契约测试。
 *
 * ChangeBook / ChangeChapter / Explore / RSS 四个 Adapter 仍走共享纵向弹窗；旧 BookSourceAdapter 拆除，
 * 单行菜单落点为 SourceManageSheets.ItemActionsSheet（Compose 半屏）。
 */
class SourcePopupActionMigrationTest {

    @Test
    fun `source related row menus use the shared vertical builder`() {
        sourceMenuFiles.forEach { path ->
            val source = readProjectFile(path)
            assertFalse("$path should not import platform PopupMenu", source.contains("import android.widget.PopupMenu"))
            assertFalse("$path should not import AppCompat PopupMenu", source.contains("import androidx.appcompat.widget.PopupMenu"))
            assertContains(path, source, "popupActionMenu(context)")
        }
        assertContains(CHANGE_BOOK, readProjectFile(CHANGE_BOOK), "danger(\"deleteSource\")")
        assertContains(CHANGE_CHAPTER, readProjectFile(CHANGE_CHAPTER), "danger(\"deleteSource\")")
        assertContains(EXPLORE, readProjectFile(EXPLORE), "danger(\"delete\")")
        assertContains(RSS, readProjectFile(RSS), "danger(\"delete\")")

        // 旧 BookSourceAdapter 单行菜单：删除行在新半屏中以 danger 样式呈现。
        val sheet = readProjectFile(SOURCE_SHEETS)
        assertContains(SOURCE_SHEETS, sheet, "danger = true")

        legacyMenuFiles.forEach { path ->
            assertFalse(
                "$path should be removed",
                sequenceOf(File(path), File("app/$path")).any(File::isFile)
            )
        }
    }

    @Test
    fun `dynamic source menu entries keep their visibility and labels`() {
        assertContains(
            EXPLORE,
            readProjectFile(EXPLORE),
            "item(context.getString(R.string.login), \"login\", source.hasLoginUrl)"
        )
        assertContains(
            RSS,
            readProjectFile(RSS),
            "item(context.getString(R.string.login), \"login\", !rssSource.loginUrl.isNullOrBlank())"
        )

        // 新书源半屏：登录项按 hasLoginUrl 可用，发现开关按 hasExploreUrl 可用，文案随启用态切换。
        val sheet = readProjectFile(SOURCE_SHEETS)
        assertContains(SOURCE_SHEETS, sheet, "enabled = source.hasLoginUrl")
        assertContains(SOURCE_SHEETS, sheet, "enabled = source.hasExploreUrl")
        assertContains(SOURCE_SHEETS, sheet, "stringResource(R.string.disable_explore)")
        assertContains(SOURCE_SHEETS, sheet, "stringResource(R.string.enable_explore)")
    }

    @Test
    fun `source menu labels keep their previous order`() {
        listOf(CHANGE_BOOK, CHANGE_CHAPTER).forEach { path ->
            assertOrdered(
                path,
                "R.string.to_top",
                "R.string.to_bottom",
                "R.string.edit_source",
                "R.string.disable_source",
                "R.string.delete_source"
            )
        }
        assertOrdered(
            EXPLORE,
            "R.string.edit",
            "R.string.to_top",
            "R.string.login",
            "R.string.search",
            "R.string.refresh",
            "R.string.delete"
        )
        assertOrdered(
            RSS,
            "R.string.edit",
            "R.string.to_top",
            "R.string.login",
            "R.string.disable_source",
            "R.string.delete"
        )

        // 新书源半屏动作次序：置顶 / 置底 / 登录 / 搜索 / 调试 / 发现开关 / 删除。
        val sheet = readProjectFile(SOURCE_SHEETS)
        assertOrdered(
            SOURCE_SHEETS,
            "R.string.to_top",
            "R.string.to_bottom",
            "R.string.login",
            "R.string.search",
            "R.string.debug",
            "R.string.delete"
        )
    }

    @Test
    fun `source menu callbacks and delete side effects are preserved`() {
        assertActions(
            CHANGE_BOOK,
            "\"topSource\" -> callBack.topSource(searchBook)",
            "\"bottomSource\" -> callBack.bottomSource(searchBook)",
            "\"editSource\" -> callBack.editSource(searchBook)",
            "\"disableSource\" -> callBack.disableSource(searchBook)",
            "\"deleteSource\" -> {",
            "deleteSourceDialog = context.alert(R.string.draw)",
            "callBack.deleteSource(searchBook)",
            "updateItems(0, itemCount, listOf<Int>())"
        )
        assertActions(
            CHANGE_CHAPTER,
            "\"topSource\" -> callBack.topSource(searchBook)",
            "\"bottomSource\" -> callBack.bottomSource(searchBook)",
            "\"editSource\" -> callBack.editSource(searchBook)",
            "\"disableSource\" -> callBack.disableSource(searchBook)",
            "\"deleteSource\" -> {",
            "callBack.deleteSource(searchBook)",
            "updateItems(0, itemCount, listOf<Int>())"
        )
        assertActions(
            EXPLORE,
            "\"edit\" -> callBack.editSource(source.bookSourceUrl)",
            "\"top\" -> callBack.toTop(source)",
            "\"search\" -> callBack.searchBook(source)",
            "\"login\" -> context.startActivity<SourceLoginActivity>",
            "\"refresh\" -> refreshExplore(source, position, binding)",
            "\"delete\" -> callBack.deleteSource(source)"
        )
        assertActions(
            RSS,
            "\"edit\" -> callBack.edit(rssSource)",
            "\"top\" -> callBack.toTop(rssSource)",
            "\"login\" -> callBack.login(rssSource)",
            "\"disable\" -> callBack.disable(rssSource)",
            "\"delete\" -> callBack.del(rssSource)"
        )

        // 新书源半屏回调：置顶 / 置底 / 登录 / 搜索 / 调试 / 发现开关 / 删除逐条接到 VM 或 Actions。
        val sheet = readProjectFile(SOURCE_SHEETS)
        assertActions(
            SOURCE_SHEETS,
            "vm.topSource(listOf(source))",
            "vm.bottomSource(listOf(source))",
            "actions.onLogin(source)",
            "actions.onSearch(source)",
            "actions.onDebug(source)",
            "vm.enableExplore(!source.enabledExplore, source)",
            "vm.delete(listOf(source))"
        )
    }

    private fun assertActions(path: String, vararg expected: String) {
        val source = readProjectFile(path)
        expected.forEach { assertContains(path, source, it) }
    }

    private fun assertOrdered(path: String, vararg expected: String) {
        val source = readProjectFile(path)
        var previous = -1
        expected.forEach { snippet ->
            val current = source.indexOf(snippet, previous + 1)
            assertTrue("$path should contain $snippet after the previous item", current > previous)
            previous = current
        }
    }

    private fun assertContains(path: String, source: String, expected: String) {
        assertTrue("$path should contain $expected", source.contains(expected))
    }

    private fun readProjectFile(pathInApp: String): String =
        sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)
            ?.readText()
            .orEmpty()

    private companion object {
        const val CHANGE_BOOK = "src/main/java/io/legado/app/ui/book/changesource/ChangeBookSourceAdapter.kt"
        const val CHANGE_CHAPTER = "src/main/java/io/legado/app/ui/book/changesource/ChangeChapterSourceAdapter.kt"
        const val EXPLORE = "src/main/java/io/legado/app/ui/main/explore/ExploreAdapter.kt"
        const val RSS = "src/main/java/io/legado/app/ui/main/rss/RssAdapter.kt"
        const val SOURCE_SHEETS = "src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt"
        val sourceMenuFiles = listOf(CHANGE_BOOK, CHANGE_CHAPTER, EXPLORE, RSS)
        val legacyMenuFiles = listOf(
            "src/main/res/menu/book_source_item.xml",
            "src/main/res/menu/change_source_item.xml",
            "src/main/res/menu/explore_item.xml",
            "src/main/res/menu/rss_main_item.xml"
        )
    }
}
