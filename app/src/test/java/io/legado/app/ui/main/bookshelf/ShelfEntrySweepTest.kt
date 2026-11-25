package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 旧独立搜索入口清剿断言（纯 JVM，读源码文本回证，离线可跑）。
 *
 * 对应验收报告《验收-导航三Tab》判据 2（旧入口清剿）FAIL 返工：
 * 1. 生产代码零可达：`app/src/main` 内不存在 `InkSearchActivity.start` 调用、不存在
 *    壳类 import；壳文件已删除、Manifest 已摘除。
 * 2. 外部分享改道后仍可打开搜索：`SharedReceiverActivity` 文本分支走
 *    [ShelfSearchRouter]，MainActivity 以 extras 承接冷启 / onNewIntent 投递。
 * 3. 发现页入口可触发浮层：典籍卡 / 专题行改道 router；书架 Fragment 暴露
 *    `openSearchRequest`，画面层按请求序号打开浮层。
 * 4. 跨页排队单源（[ShelfSearchHandoff]）纯 JVM 语义：入队 / 消费 / 序号自增 / 覆盖。
 * 5. 单源作用域检索（书详情标签、书源管理 / 编辑、RSS JS 桥）经 router 透传
 *    `SearchScope` 串，不再借独立搜索页当通用结果框。
 */
class ShelfEntrySweepTest {

    private val javaRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    private val manifestFile: File by lazy {
        sequenceOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
    }

    private fun readMain(relative: String): String =
        File(javaRoot, "io/legado/app/$relative").readText()

    private fun walkMainSources(): List<Pair<String, String>> =
        javaRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .map { it.relativeTo(javaRoot).path to it.readText() }
            .toList()

    // 1. 旧入口零可达：生产代码不得再出现壳类启动调用或 import；壳文件与 Manifest 注册均清除。
    @Test
    fun `no production InkSearchActivity start call or import remains`() {
        val violations = walkMainSources().filter { (_, text) ->
            text.contains("InkSearchActivity.start") ||
                text.contains("import io.legado.app.ui.main.search.InkSearchActivity")
        }
        assertTrue("旧独立搜索入口残留: $violations", violations.none())

        val shell = File(javaRoot, "io/legado/app/ui/main/search/InkSearchActivity.kt")
        assertFalse("独立搜索壳文件应已删除", shell.isFile)

        val manifest = manifestFile.readText()
        assertFalse("Manifest 不应再注册 InkSearchActivity",
            manifest.contains(".ui.main.search.InkSearchActivity"))
    }

    // 2. 外部分享：文本分支改道 router；系统入口 Activity 仍 exported 承接 PROCESS_TEXT / SEND。
    @Test
    fun `external share routes plain text to shelf overlay and stays exported`() {
        val receiver = readMain("receiver/SharedReceiverActivity.kt")
        assertTrue("分享文本应改道书架浮层 router",
            receiver.contains("ShelfSearchRouter.openShelfSearch(this, text)"))
        assertFalse("分享入口不应再启动独立搜索壳",
            receiver.contains("InkSearchActivity"))

        val manifest = manifestFile.readText()
        val receiverBlock = manifest.substringAfter(".receiver.SharedReceiverActivity")
            .substringBefore("</activity>")
        assertTrue("分享 Activity 必须保持 exported=true 承接系统入口",
            receiverBlock.contains("android:exported=\"true\""))
        assertTrue("保留 PROCESS_TEXT 入口", receiverBlock.contains("ACTION_PROCESS_TEXT"))
        assertTrue("保留 SEND 入口", receiverBlock.contains("ACTION_SEND"))

        val main = readMain("ui/main/MainActivity.kt")
        assertTrue("MainActivity 声明搜索词 extras 常量",
            main.contains("EXTRA_SEARCH_KEY = \"shelfSearchKey\""))
        assertTrue("MainActivity 声明作用域 extras 常量",
            main.contains("EXTRA_SEARCH_SCOPE = \"shelfSearchScope\""))
        assertTrue("MainActivity 消费 intent 并切回书架 Tab",
            main.contains("handleSearchIntent") && main.contains("switchTab(0)"))
    }

    // 3. 发现页两处现役入口改道浮层；书架 Fragment 公开投递口与画面请求接线齐备。
    @Test
    fun `discover page entries route to shelf overlay through request handoff`() {
        val explore = readMain("ui/main/explore/ExploreInkFragment.kt")
        val discoverRouteCount = explore.lines()
            .count { it.contains("ShelfSearchRouter.openShelfSearch(ctx, name)") }
        assertEquals("典籍卡与专题 Sheet 行两处都应改道 router", 2, discoverRouteCount)
        assertFalse("发现页不应再引用独立搜索壳", explore.contains("InkSearchActivity"))

        val shelf = readMain("ui/main/bookshelf/InkShelfFragment.kt")
        assertTrue("Fragment 暴露跨页请求投递口", shelf.contains("fun openSearchRequest(request: ShelfSearchRequest)"))
        assertTrue("Fragment 建栈时消费排队请求（冷启兜底）",
            shelf.contains("ShelfSearchHandoff.consume()?.let { openSearchRequest(it) }"))

        val screen = readMain("ui/main/bookshelf/BookshelfScreen.kt")
        assertTrue("画面层接收跨页请求", screen.contains("searchRequest: ShelfSearchRequest? = null"))
        assertTrue("画面层按请求序号打开浮层", screen.contains("LaunchedEffect(searchRequest?.seq)"))
    }

    // 4. 排队单源纯 JVM 语义：入队可消费、消费即清空、序号自增、后到覆盖先到。
    @Test
    fun `handoff enqueues consumes and sequences requests`() {
        ShelfSearchHandoff.consume() // 隔离前序用例残留

        ShelfSearchHandoff.enqueue("左传", null)
        val first = ShelfSearchHandoff.consume()
        assertEquals("左传", first?.key)
        assertNull(first?.scope)
        assertNull("消费后应清空排队", ShelfSearchHandoff.consume())

        ShelfSearchHandoff.enqueue("史记", "名称::url")
        ShelfSearchHandoff.enqueue("诗经", null)
        val latest = ShelfSearchHandoff.consume()
        assertEquals("诗经", latest?.key)
        assertNull(latest?.scope)

        ShelfSearchHandoff.enqueue("庄子", null)
        val seqAfterSecond = ShelfSearchHandoff.consume()?.seq
        ShelfSearchHandoff.enqueue("论语", null)
        val seqAfterThird = ShelfSearchHandoff.consume()?.seq
        assertTrue("序号应随入队自增", (seqAfterThird ?: 0) > (seqAfterSecond ?: 0))
        ShelfSearchHandoff.consume()
    }

    // 5. 单源 / 标签作用域检索经 router 透传 SearchScope 串；全书库入口只传关键词。
    @Test
    fun `scoped routes pass SearchScope string through router`() {
        val bookInfo = readMain("ui/book/info/BookInfoActivity.kt")
        assertTrue("书源标签检索带单源作用域",
            bookInfo.contains("SearchScope(source).toString()"))
        assertTrue("作者点按 / 长按走全书库 router",
            bookInfo.lines().count { it.contains("openShelfSearch(this@BookInfoActivity, book.author)") } >= 1)
        assertTrue("书名点按 / 长按走全书库 router",
            bookInfo.contains("openShelfSearch(this@BookInfoActivity, book.name)"))

        val sourceManage = readMain("ui/book/source/manage/BookSourceActivity.kt")
        assertTrue("书源管理单源检索带作用域",
            sourceManage.contains("SearchScope(source).toString()"))

        val sourceEdit = readMain("ui/book/source/edit/BookSourceEditActivity.kt")
        assertTrue("书源编辑测试检索带作用域",
            sourceEdit.contains("SearchScope(source).toString()"))

        val rssJs = readMain("ui/rss/read/RssJsExtensions.kt")
        assertTrue("RSS JS 桥通用检索透传 scope 串",
            rssJs.contains("openShelfSearch(it, key, searchScope)"))
        assertTrue("RSS JS 桥单源检索带作用域",
            rssJs.contains("SearchScope(source).toString()"))
    }
}
