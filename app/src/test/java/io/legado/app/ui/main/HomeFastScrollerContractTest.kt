package io.legado.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class HomeFastScrollerContractTest {

    @Test
    fun `discovery keeps independent fast scroller while bookshelf is single-path compose`() {
        assertFastScrollerLayout(
            layout = "fragment_explore.xml",
            recyclerId = "rv_find",
            constraintTarget = "rv_find",
        )

        assertBookshelfSinglePathContract()

        val explore = projectFile(
            "src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt",
        ).readText()
        assertInOrder(
            explore.sourceSection(
                "private fun initRecyclerView() {",
                "private fun upFastScrollerBar() {",
            ),
            "fastScroller.attachRecyclerView(binding.rvFind)",
            "upFastScrollerBar()",
        )
        assertFastScrollerToggle(
            explore.sourceSection(
                "private fun upFastScrollerBar() {",
                "private fun initGroupData() {",
            ),
            "AppConfig.showDiscoveryFastScroller",
            "binding.rvFind",
        )
        assertInOrder(
            explore.sourceSection("override fun onResume() {", "override fun onPause() {"),
            "super.onResume()",
            "upFastScrollerBar()",
        )
        assertDetachBeforeSuper(
            explore.sourceSection(
                "override fun onDestroyView() {",
                "private fun upGroupsMenu(",
            ),
        )

        val config = projectFile(
            "src/main/java/io/legado/app/help/config/AppConfig.kt",
        ).readText()
        assertTrue(
            config.contains(
                "get() = appCtx.getPrefBoolean(PreferKey.showDiscoveryFastScroller, false)",
            ),
        )

        val preferences = parseProjectXml("src/main/res/xml/pref_config_other.xml")
        val discoverySwitch = preferences.findElementByAndroidKey("showDiscoveryFastScroller")
        assertEquals("false", discoverySwitch.androidAttribute("defaultValue"))
        assertEquals(
            "@string/show_discovery_fast_scroller",
            discoverySwitch.androidAttribute("title"),
        )
    }

    /**
     * 书架已改为单路 Compose（InkShelfFragment → BookshelfScreen），旧 style1/style2
     * 双路 RecyclerView + FastScroller 已拆除。这里做正向（新入口存在且接线）+
     * 负向（旧双路类名 / 绑定 / 滚动条不残留）契约；探索侧 FastScroller 断言保持不变。
     */
    private fun assertBookshelfSinglePathContract() {
        val shelfFragment = projectFile(
            "src/main/java/io/legado/app/ui/main/bookshelf/InkShelfFragment.kt",
        ).readText()
        val shelfScreen = projectFile(
            "src/main/java/io/legado/app/ui/main/bookshelf/BookshelfScreen.kt",
        ).readText()
        val mainActivity = projectFile(
            "src/main/java/io/legado/app/ui/main/MainActivity.kt",
        ).readText()

        // 正向：新单路入口存在，宿主 Fragment 把画面交给 Compose Screen，主界面按单路反射实例化。
        assertTrue(shelfFragment.contains("ComposeView"))
        assertTrue(shelfFragment.contains("BookshelfScreen("))
        assertTrue(shelfScreen.contains("fun BookshelfScreen("))
        assertTrue(mainActivity.contains("InkShelfFragment"))

        // 负向：新书架源码不得残留旧双路 FastScroller / RecyclerView 绑定 / 旧分路类名与旧布局名。
        listOf(shelfFragment, shelfScreen).forEach { source ->
            assertTrue("Residual FastScroller in shelf source", !source.contains("FastScroller"))
            assertTrue("Residual rvBookshelf binding in shelf source", !source.contains("rvBookshelf"))
            assertTrue("Residual style1 split in shelf source", !source.contains("style1"))
            assertTrue("Residual style2 split in shelf source", !source.contains("style2"))
            assertTrue("Residual old adapter in shelf source", !source.contains("BaseBooksAdapter"))
            assertTrue("Residual old fragment in shelf source", !source.contains("BookshelfFragment2"))
            assertTrue(
                "Residual old base fragment in shelf source",
                !source.contains("BaseBookshelfFragment"),
            )
            assertTrue(
                "Residual old shelf layout in shelf source",
                !source.contains("fragment_bookshelf2"),
            )
        }

        // 负向：旧双路 Fragment 源文件已不在源码树（两种工程目录口径都查）。
        val legacyFragment = listOf(
            File("src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfFragment2.kt"),
            File("app/src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfFragment2.kt"),
        )
        assertFalse("Legacy BookshelfFragment2.kt must be removed", legacyFragment.any { it.isFile })
    }

    private fun assertFastScrollerLayout(
        layout: String,
        recyclerId: String,
        constraintTarget: String,
    ) {
        val document = parseProjectXml("src/main/res/layout/$layout")
        val recycler = document.findElementByAndroidId(recyclerId)
        val fastScroller = document.findElementByAndroidId("fast_scroller")

        assertEquals(
            "io.legado.app.ui.widget.recycler.RecyclerViewAtPager2",
            recycler.tagName,
        )
        assertEquals(
            "io.legado.app.ui.widget.recycler.scroller.FastScroller",
            fastScroller.tagName,
        )
        assertEquals("0dp", fastScroller.androidAttribute("layout_height"))
        assertEquals(
            "@dimen/fastscroll_scrollbar_margin_top",
            fastScroller.androidAttribute("layout_marginTop"),
        )
        assertEquals(
            "@dimen/fastscroll_scrollbar_margin_bottom",
            fastScroller.androidAttribute("layout_marginBottom"),
        )
        listOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintEnd_toEndOf",
        ).forEach { attribute ->
            assertEquals("@id/$constraintTarget", fastScroller.appAttribute(attribute))
        }
    }

    private fun parseProjectXml(pathInApp: String): Document {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(projectFile(pathInApp))
    }

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
    }

    private fun String.sourceSection(startMarker: String, endMarker: String): String {
        val start = indexOf(startMarker)
        val end = indexOf(endMarker, start + startMarker.length)
        assertTrue("Missing source marker: $startMarker", start >= 0)
        assertTrue("Missing source marker after $startMarker: $endMarker", end > start)
        return substring(start, end)
    }

    private fun assertFastScrollerToggle(
        source: String,
        config: String,
        recycler: String,
    ) {
        assertInOrder(
            source,
            "val show = $config",
            "binding.fastScroller.isEnabled = show",
            "$recycler.scrollBarSize = if (show) {",
            "0",
            "ViewConfiguration.get(requireContext()).scaledScrollBarSize",
        )
    }

    private fun assertDetachBeforeSuper(source: String) {
        assertInOrder(
            source,
            "fastScroller.detachRecyclerView()",
            "super.onDestroyView()",
        )
    }

    private fun assertInOrder(source: String, vararg values: String) {
        var previous = -1
        values.forEach { value ->
            val index = source.indexOf(value, previous + 1)
            assertTrue("Missing or out-of-order source: $value", index > previous)
            previous = index
        }
    }

    private fun Document.findElementByAndroidId(id: String): Element {
        val nodes = getElementsByTagName("*")
        return (0 until nodes.length)
            .asSequence()
            .map { nodes.item(it) as Element }
            .single { it.androidAttribute("id") == "@+id/$id" }
    }

    private fun Document.findElementByAndroidKey(key: String): Element {
        val nodes = getElementsByTagName("*")
        return (0 until nodes.length)
            .asSequence()
            .map { nodes.item(it) as Element }
            .single { it.androidAttribute("key") == key }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun Element.appAttribute(name: String): String =
        getAttributeNS(APP_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
    }
}
