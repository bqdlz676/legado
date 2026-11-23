package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue 1008 菜单图标 / 溢出契约测试（Compose 重写后口径）。
 *
 * source_login.xml 与 rss_read.xml 菜单资源保留；旧 book_source.xml 按域名分组菜单项拆除，
 * 落点为 SourceManageSheets 的分组切换开关。
 */
class Issue1008MenuContractTest {

    @Test
    fun sourceIconsAndRssOverflowFollowReportedMenuContract() {
        assertItemIcon(
            "src/main/res/menu/source_login.xml",
            "menu_show_login_header",
            "ic_add_online"
        )

        // 按域名分组：旧 book_source.xml 菜单项 → 新主选项 Sheet 中的分组切换开关。
        val sheets = readProjectFile(SOURCE_SHEETS)
        assertTrue(sheets.contains("stringResource(R.string.group_sources_by_domain)"))
        assertTrue(sheets.contains("onToggle = vm::toggleGroupByDomain"))

        val rssMenu = readProjectFile("src/main/res/menu/rss_read.xml")
        val aloud = itemBlock(rssMenu, "menu_aloud")
        assertFalse(aloud.contains("android:icon="))
        assertContains("rss_read.xml", aloud, """app:showAsAction="never"""")

        val rssActivity = readProjectFile(
            "src/main/java/io/legado/app/ui/rss/read/ReadRssActivity.kt"
        )
        assertContains(
            "ReadRssActivity.kt",
            rssActivity,
            "class ReadRssActivity :"
        )
        assertContains(
            "ReadRssActivity.kt",
            rssActivity,
            "showOpenMenuIcon = false"
        )
    }

    private fun assertItemIcon(path: String, id: String, drawable: String) {
        val item = itemBlock(readProjectFile(path), id)
        assertContains(path, item, """android:icon="@drawable/$drawable"""")
    }

    private fun itemBlock(source: String, id: String): String {
        val marker = """android:id="@+id/$id""""
        val start = source.indexOf(marker)
        assertTrue("$id should exist", start >= 0)
        val end = source.indexOf("/>", start)
        assertTrue("$id should have a complete item", end > start)
        return source.substring(start, end)
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
        const val SOURCE_SHEETS = "src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt"
    }
}
