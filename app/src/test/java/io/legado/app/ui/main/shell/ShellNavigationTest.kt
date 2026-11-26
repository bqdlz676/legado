package io.legado.app.ui.main.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2 主壳导航契约单测：三槽固定顺序、默认主页回落、二级返回决策。
 * 纯 JVM，不触 Compose 运行时与 Android 框架。
 */
class ShellNavigationTest {

    @Test
    fun `default order is bookshelf explore settings`() {
        val order = ShellTab.defaultOrder
        assertEquals(3, order.size)
        assertEquals(
            listOf(ShellTab.BOOKSHELF, ShellTab.EXPLORE, ShellTab.SETTINGS),
            order,
        )
    }

    @Test
    fun `initial tab maps legacy home page preferences`() {
        assertEquals(ShellTab.EXPLORE, ShellTab.initialTab("explore"))
        assertEquals(ShellTab.SETTINGS, ShellTab.initialTab("my"))
        assertEquals(ShellTab.BOOKSHELF, ShellTab.initialTab("bookshelf"))
    }

    @Test
    fun `unknown blank or corrupt home page falls back to bookshelf`() {
        assertEquals(ShellTab.BOOKSHELF, ShellTab.initialTab(null))
        assertEquals(ShellTab.BOOKSHELF, ShellTab.initialTab(""))
        assertEquals(ShellTab.BOOKSHELF, ShellTab.initialTab("  "))
        assertEquals(ShellTab.BOOKSHELF, ShellTab.initialTab("profile"))
    }

    @Test
    fun `back from non bookshelf navigates to bookshelf`() {
        assertEquals(
            ShellBackDecision.NAVIGATE_BOOKSHELF,
            ShellNavigation.backDecision(ShellTab.EXPLORE),
        )
        assertEquals(
            ShellBackDecision.NAVIGATE_BOOKSHELF,
            ShellNavigation.backDecision(ShellTab.SETTINGS),
        )
    }

    @Test
    fun `back from bookshelf exits shell`() {
        assertEquals(
            ShellBackDecision.EXIT,
            ShellNavigation.backDecision(ShellTab.BOOKSHELF),
        )
    }

    @Test
    fun `each tab carries label and dual state icons`() {
        ShellTab.values().forEach { tab ->
            assertTrue(tab.labelRes != 0)
            assertTrue(tab.iconOffRes != 0)
            assertTrue(tab.iconOnRes != tab.iconOffRes)
        }
    }
}
