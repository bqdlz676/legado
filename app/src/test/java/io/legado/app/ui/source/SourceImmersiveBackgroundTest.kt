package io.legado.app.ui.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SourceImmersiveBackgroundTest {

    @Test
    fun sourceEditorsMatchTitleBarTransparency() {
        val titleBar = projectFile("src/main/java/io/legado/app/ui/widget/TitleBar.kt").readText()
        assertTrue(titleBar.contains("if (AppConfig.isEInkMode)"))
        assertTrue(titleBar.contains("else if (!opaque && context.transparentNavBar)"))

        // RSS 编辑仍是旧 ViewBinding 壳：标题栏 / 字段导航条按透明状态栏口径取色。
        val rssEdit = projectFile(
            "src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditActivity.kt"
        ).readText()
        assertTrue(rssEdit.contains("transparentNavBar && !AppConfig.isEInkMode"))
        assertTrue(rssEdit.contains("listOf(binding.tabLayout, binding.fieldNav)"))
        assertTrue(rssEdit.contains("if (transparentBar) Color.TRANSPARENT else backgroundColor"))
        assertTrue(rssEdit.contains("if (transparentBar) tabs.elevation = 0f"))

        // 书源编辑已 Compose 化（旧 ActivityBookSourceEditBinding 拆除，commit 93e1946）：
        // 标题栏 / 页面底色走组件 token，不再有 ViewBinding 导航条透明 hook。
        val bookScreen = projectFile(
            "src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt"
        ).readText()
        assertTrue(bookScreen.contains(".background(ComponentTokens.bgPage)"))
        assertFalse(bookScreen.contains("binding.tabLayout"))
        assertFalse(bookScreen.contains("binding.fieldNav"))
    }

    @Test
    fun transparentBottomBarsUseVisibleBackgroundForContrast() {
        mapOf(
            "src/main/java/io/legado/app/ui/widget/SelectActionBar.kt" to
                "if (context.transparentNavBar) context.backgroundColor else context.bottomBackground",
            "src/main/java/io/legado/app/ui/widget/text/AccentStrokeTextView.kt" to
                "if (context.transparentNavBar) context.backgroundColor else context.bottomBackground",
            "src/main/java/io/legado/app/lib/theme/view/ThemeBottomNavigationVIew.kt" to
                "setBackgroundResource(R.drawable.bg_ink_bottom)",
        ).forEach { (path, expression) ->
            assertTrue(projectFile(path).readText().contains(expression))
        }
    }

    @Test
    fun transparentTitleBarsUseVisibleBackgroundForContrast() {
        val theme = projectFile("src/main/java/io/legado/app/lib/theme/MaterialValueHelper.kt")
            .readText()
        assertTrue(
            theme.contains("toolbarBackgroundColor(transparentBar, primaryColor, backgroundColor)")
        )

        val titleBar = projectFile("src/main/java/io/legado/app/ui/widget/TitleBar.kt")
            .readText()
        assertTrue(titleBar.contains("automaticForeground = themeMode == 0 && !opaque"))
        assertTrue(titleBar.contains("!AppConfig.isEInkMode && background?.alpha == 0"))
        assertTrue(titleBar.contains("context.getToolbarTextColor(true)"))
        assertTrue(titleBar.contains("toolbar.navigationIcon?.colorFilter = colorFilter"))
        assertTrue(titleBar.contains("toolbar.overflowIcon?.colorFilter = colorFilter"))
        assertTrue(titleBar.contains("findViewById<SearchView>(R.id.search_view)?.applyTint(color)"))
        assertTrue(titleBar.contains("findViewById<TabLayout>(R.id.tab_layout)"))
        assertTrue(titleBar.contains("setTabTextColors(tabUnselectedColor, color)"))
        assertTrue(titleBar.contains("R.color.md_light_secondary"))
        assertTrue(titleBar.contains("R.color.md_dark_secondary"))
        assertTrue(titleBar.contains("toolbar.menu.forEach { item ->"))

        val menu = projectFile("src/main/java/io/legado/app/utils/MenuExtensions.kt").readText()
        assertTrue(menu.contains("(impl.actionView as? SearchView)?.applyTint(tintColor)"))

        val activity = projectFile("src/main/java/io/legado/app/base/BaseActivity.kt").readText()
        val fragment = projectFile("src/main/java/io/legado/app/base/BaseFragment.kt").readText()
        assertTrue(
            activity.contains("transparentBar = titleBar?.usesTransparentForeground == true")
        )
        assertTrue(fragment.contains("titleBar?.post { titleBar.applyForegroundColor() }"))
    }

    @Test
    fun navigationBarsDisablePlatformContrastScrimOnAndroidQ() {
        val source = projectFile("src/main/java/io/legado/app/utils/ActivityExtensions.kt")
            .readText()
            .substringAfter("fun Activity.setNavigationBarColorAuto")
            .substringBefore("\nfun ")
        assertTrue(source.contains("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)"))
        assertTrue(source.contains("window.isNavigationBarContrastEnforced = false"))
    }

    @Test
    fun sourceDebugHelpPanelsHideLogsAndMatchTitleBarTransparency() {
        // RSS 调试仍是旧壳：帮助面板展开时隐藏日志列表，loading 与透明状态栏口径保留。
        val rssDebug = projectFile(
            "src/main/java/io/legado/app/ui/rss/source/debug/RssSourceDebugActivity.kt"
        ).readText()
        assertTrue(rssDebug.contains("transparentNavBar && !AppConfig.isEInkMode"))
        assertTrue(rssDebug.contains("Color.TRANSPARENT else backgroundColor"))
        assertTrue(rssDebug.contains("binding.recyclerView.visibility = View.GONE"))
        assertTrue(rssDebug.contains("binding.recyclerView.visibility = View.VISIBLE"))
        assertTrue(rssDebug.contains("loading = true"))
        assertTrue(rssDebug.contains("loading = false"))
        assertTrue(
            Regex("private fun startSearch\\(key: String\\) \\{\\s*openOrCloseHelp\\(false\\)")
                .containsMatchIn(rssDebug)
        )
        assertTrue(
            rssDebug.contains(
                "if (open || !loading) binding.rotateLoading.gone() " +
                    "else binding.rotateLoading.visible()"
            )
        )

        // 书源调试已 Compose 化：帮助面板 / 日志空态 / 加载态由单向状态流驱动，
        // 面板与日志列表互斥渲染，底色走组件 token。
        val bookDebug = projectFile(
            "src/main/java/io/legado/app/ui/book/source/debug/SourceDebugScreen.kt"
        ).readText()
        assertTrue(bookDebug.contains("state.helpOpen -> HelpPanel(state = state, actions = actions)"))
        assertTrue(bookDebug.contains("state.logs.isEmpty() && !state.loading"))
        assertTrue(bookDebug.contains("itemsIndexed(state.logs)"))
        assertTrue(bookDebug.contains("if (state.loading)"))
        assertTrue(bookDebug.contains(".background(ComponentTokens.bgSurface)"))
    }

    @Test
    fun debugHelpBackgroundBelongsToScrollablePanel() {
        // activity_source_debug.xml 随书源调试 Compose 化删除（commit 76e7db3）；
        // 仅保留 RSS 调试旧布局的滚动面板背景断言。
        val layout = "activity_rss_source_debug.xml"
        val help = viewById(layout, "help")
        assertEquals("@color/background", help.getAttribute("android:background"))
        val content = (0 until help.childNodes.length)
            .map { help.childNodes.item(it) }
            .filterIsInstance<Element>()
            .single()
        assertFalse(content.hasAttribute("android:background"))
    }

    private fun viewById(layout: String, id: String): Element {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(projectFile("src/main/res/layout/$layout"))
        return document.getElementsByTagName("*").let { nodes ->
            (0 until nodes.length)
                .map { nodes.item(it) as Element }
                .single { it.getAttribute("android:id") == "@+id/$id" }
        }
    }

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
    }
}
