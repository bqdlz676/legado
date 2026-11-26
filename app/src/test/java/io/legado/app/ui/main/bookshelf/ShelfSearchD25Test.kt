package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * D25 契约测试：导航三 Tab + 书架下拉搜索浮层。
 *
 * 纯 JVM：开合状态机走 [ShelfSearch] 直测；菜单 / 图标 / 幂等入栈走源码与 XML 回证，
 * 不依赖 Android 框架与 Compose 运行时。本地三路投影旧纯函数已随检索栈收口清剿，
 * 本文件不再锁定其过滤行为。
 */
class ShelfSearchD25Test {

    private val javaRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    private val resRoot: File by lazy {
        sequenceOf(File("src/main/res"), File("app/src/main/res"))
            .first { it.isDirectory }
    }

    // 1. 底部导航菜单定义处恰好 3 个 Tab：书架 · 发现 · 设置。
    @Test
    fun `bottom menu defines exactly three tabs`() {
        val menuFile = File(resRoot, "menu/main_bnv.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(menuFile)
        val items = doc.getElementsByTagName("item")
        assertEquals("D25：底部导航只能有 3 个 Tab", 3, items.length)

        val ids = (0 until items.length).map {
            (items.item(it) as Element).getAttribute("android:id")
        }.toSet()
        assertTrue(ids.contains("@+id/menu_bookshelf"))
        assertTrue(ids.contains("@+id/menu_discovery"))
        assertTrue(ids.contains("@+id/menu_my_config"))
        assertFalse("旧画板 Tab 必须下线", ids.contains("@+id/menu_paint"))

        val titles = (0 until items.length).map {
            (items.item(it) as Element).getAttribute("android:title")
        }.toSet()
        assertTrue(titles.contains("@string/bookshelf"))
        assertTrue(titles.contains("@string/discovery"))
        assertTrue(titles.contains("@string/setting"))
    }

    // 2. 浮层状态机：展开保留关键词；收起清空关键词；输入更新关键词。
    @Test
    fun `overlay state machine opens closes and clears query`() {
        var state = ShelfSearch.closed
        assertFalse(state.isOpen)

        state = ShelfSearch.open(state)
        assertTrue(state.isOpen)

        state = ShelfSearch.updateQuery(state, "左传")
        assertEquals("左传", state.query)
        assertTrue(state.isOpen)

        // 收起即清空：下次打开是一次新搜索，不留陈旧查询。
        state = ShelfSearch.close(state)
        assertFalse(state.isOpen)
        assertEquals("", state.query)

        state = ShelfSearch.open(state)
        assertEquals("重新展开不应带上次关键词", "", state.query)
    }

    // 3. 设置 Tab 幂等入栈：源码回证 switchTab 在已在目标槽位时不重复切换 / 不压 Activity。
    // B2 壳化后守卫落在壳态 MainShellState；旧 ViewPager 条件串随旧壳迁出。
    @Test
    fun `settings tab switch is idempotent without pushing activity`() {
        val stateFile = File(javaRoot, "io/legado/app/ui/main/shell/MainShellState.kt").readText()
        val guard = stateFile.indexOf("if (selectedTab.ordinal != position)")
        assertTrue("switchTab 必须先判当前槽位再切换", guard >= 0)

        val activity = File(javaRoot, "io/legado/app/ui/main/MainActivity.kt").readText()
        assertFalse("旧画板导航分支必须清零", activity.indexOf("menu_paint") >= 0)

        // 壳内全部源码只做槽位切换，不向返回栈压入 Activity。
        val shellRoot = File(javaRoot, "io/legado/app/ui/main/shell")
        shellRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { source ->
                assertTrue("槽位切换不得 startActivity", !source.readText().contains("startActivity"))
            }
    }

    // 4. 导航图标资源恰好 3 枚（书架 / 发现 / 设置，规格切片 ic_tab_*），旧人像 / 画板 / RSS / 填充齿轮清零。
    @Test
    fun `nav icon resources are exactly three`() {
        val drawableDir = File(resRoot, "drawable")
        val selectors = drawableDir.listFiles { f ->
            f.name.startsWith("ic_tab_") && f.name.endsWith(".xml") &&
                !f.name.endsWith("_e.xml") && !f.name.endsWith("_s.xml")
        }?.map { it.name }?.toSet().orEmpty()

        assertEquals(
            setOf("ic_tab_bookshelf.xml", "ic_tab_discover.xml", "ic_tab_settings.xml"),
            selectors,
        )
        // 旧导航图标族不得残留
        val allRes = drawableDir.listFiles { it.name.startsWith("ic_bottom_") }.orEmpty()
        assertTrue("旧 ic_bottom_* 导航图标应全部清零", allRes.isEmpty())

        // 规格口径：线宽 1.5px、默认暗铜、选中暖金，无填充渐变
        listOf("ic_tab_bookshelf_e", "ic_tab_discover_e", "ic_tab_settings_e").forEach { name ->
            val vec = File(drawableDir, "$name.xml").readText()
            assertTrue(vec.contains("strokeWidth=\"1.5\""))
            assertTrue(vec.contains("#8B6914"))
        }
        listOf("ic_tab_bookshelf_s", "ic_tab_discover_s", "ic_tab_settings_s").forEach { name ->
            val vec = File(drawableDir, "$name.xml").readText()
            assertTrue(vec.contains("#D4A54A"))
        }
    }
}
