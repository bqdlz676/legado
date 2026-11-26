package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // ───────────────────── D5 解析辅助：方法块提取 / 调用点实参读取 ─────────────────────
    // 为什么不用整文件子串：整文件 contains 抓不住“该实参是否真透传”，也抓不住委托发生在
    // 哪个方法块内。下面两个辅助按括号配对切出方法体与调用点实参，断言对象是块内调用序 /
    // 第三实参表达式，不是源码全文出现过某段文字。

    /** 按签名定位方法，花括号配对取出方法体；找不到签名返回 null。 */
    private fun methodBody(source: String, signature: String): String? {
        val sigIdx = source.indexOf(signature).takeIf { it >= 0 } ?: return null
        val braceIdx = source.indexOf('{', sigIdx).takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var inChar = false
        var i = braceIdx
        while (i < source.length) {
            val c = source[i]
            when {
                c == '"' && !inChar -> inString = !inString
                c == '\'' && !inString -> inChar = !inChar
                !inString && !inChar -> {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) break
                    }
                }
            }
            i++
        }
        return source.substring(braceIdx + 1, i)
    }

    /** 顶层逗号切分实参；括号 / 方括号 / 尖括号内层与字符串字面量内的逗号不切。 */
    private fun splitTopLevelArgs(text: String): List<String> {
        val args = mutableListOf<String>()
        val sb = StringBuilder()
        var paren = 0
        var bracket = 0
        var angle = 0
        var inString = false
        var inChar = false
        for (c in text) {
            when {
                c == '"' && !inChar -> inString = !inString
                c == '\'' && !inString -> inChar = !inChar
                inString || inChar -> sb.append(c)
                c == '(' -> { paren++; sb.append(c) }
                c == ')' -> { paren--; sb.append(c) }
                c == '[' -> { bracket++; sb.append(c) }
                c == ']' -> { bracket--; sb.append(c) }
                c == '<' -> { angle++; sb.append(c) }
                c == '>' -> { angle--; sb.append(c) }
                c == ',' && paren == 0 && bracket == 0 && angle == 0 -> {
                    args.add(normalizeExpr(sb.toString())); sb.clear()
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotBlank() || args.isNotEmpty()) args.add(normalizeExpr(sb.toString()))
        return args.filter { it.isNotBlank() }
    }

    /** 读出源码内全部 `ShelfSearchRouter.openShelfSearch(...)` 调用点的实参列表。 */
    private fun openShelfSearchCalls(source: String): List<List<String>> {
        val marker = "ShelfSearchRouter.openShelfSearch("
        val calls = mutableListOf<List<String>>()
        var index = 0
        while (true) {
            val start = source.indexOf(marker, index).takeIf { it >= 0 } ?: break
            val openParen = start + marker.length - 1
            var depth = 0
            var inString = false
            var inChar = false
            var end = openParen
            while (end < source.length) {
                val c = source[end]
                when {
                    c == '"' && !inChar -> inString = !inString
                    c == '\'' && !inString -> inChar = !inChar
                    !inString && !inChar -> {
                        if (c == '(') depth++
                        else if (c == ')') {
                            depth--
                            if (depth == 0) break
                        }
                    }
                }
                end++
            }
            calls.add(splitTopLevelArgs(source.substring(openParen + 1, end)))
            index = end + 1
        }
        return calls
    }

    private fun normalizeExpr(expr: String): String =
        expr.replace("""\s+""".toRegex(), " ").trim().removeSuffix(",").trim()

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

    // 2a. D5(a) 热启路径锁：MainActivity 必须真覆写 onNewIntent，且方法块内按
    //     super → setIntent → handleSearchIntent 序委托；删掉覆写或截断委托即红。
    //     口径：纯 JVM 无法实例化 Activity，此处以方法块调用序桩记录替代运行时桩，
    //     运行时投递时序仍属未验证项。
    @Test
    fun `onNewIntent override exists and delegates search intent to handleSearchIntent`() {
        val main = readMain("ui/main/MainActivity.kt")
        val onNewIntent = methodBody(main, "override fun onNewIntent(intent: Intent)")
        assertNotNull("MainActivity 必须覆写 onNewIntent（singleTask 热启投递路径）", onNewIntent)
        val body = onNewIntent!!

        val superIdx = body.indexOf("super.onNewIntent(intent)")
        val setIdx = body.indexOf("setIntent(intent)")
        val handleIdx = body.indexOf("handleSearchIntent(intent)")
        assertTrue("onNewIntent 必须先回调 super.onNewIntent", superIdx >= 0)
        assertTrue("onNewIntent 必须 setIntent 落新 intent", setIdx >= 0)
        assertTrue("onNewIntent 必须委托 handleSearchIntent 承接 extras", handleIdx >= 0)
        assertTrue(
            "委托序必须为 super → setIntent → handleSearchIntent",
            superIdx < setIdx && setIdx < handleIdx,
        )
        assertTrue(
            "handleSearchIntent 必须是 MainActivity 成员方法（非外部旁路）",
            main.contains("private fun handleSearchIntent(intent: Intent?)"),
        )
    }

    // 2b. D5(b) extras 常量同符号锁：router 写入 extras 必须引用 MainActivity companion
    //     的常量符号，不得复制字面量；常量声明与取值在 MainActivity 一侧回证。
    @Test
    fun `router writes extras through MainActivity constants not duplicated literals`() {
        val main = readMain("ui/main/MainActivity.kt")
        val handoff = readMain("ui/main/bookshelf/ShelfSearchHandoff.kt")
        val routerBody = methodBody(handoff, "fun openShelfSearch(")
        assertNotNull("router 入口 openShelfSearch 必须存在", routerBody)
        val body = routerBody!!

        assertTrue(
            "router 必须引用 MainActivity.EXTRA_SEARCH_KEY 同一符号",
            body.contains("putExtra(MainActivity.EXTRA_SEARCH_KEY, key)"),
        )
        assertTrue(
            "router 必须引用 MainActivity.EXTRA_SEARCH_SCOPE 同一符号",
            body.contains("putExtra(MainActivity.EXTRA_SEARCH_SCOPE, scope)"),
        )
        assertFalse(
            "router 不得复制 extras 字面量（须与 MainActivity 常量同符号，避免脱节）",
            body.contains("\"shelfSearchKey\"") || body.contains("\"shelfSearchScope\""),
        )
        assertTrue(
            "router 必须 import MainActivity 以解析常量符号",
            handoff.contains("import io.legado.app.ui.main.MainActivity"),
        )
        assertTrue("MainActivity companion 声明关键词 extras 常量",
            main.contains("EXTRA_SEARCH_KEY = \"shelfSearchKey\""))
        assertTrue("MainActivity companion 声明作用域 extras 常量",
            main.contains("EXTRA_SEARCH_SCOPE = \"shelfSearchScope\""))
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

    // 5. D5(c) B 类五处作用域透传：逐调用点读出 router 第三实参（scope 表达式），
    //    按调用点配对断言；不再做整文件子串存在检查——文件别处出现表达式不算该点透传。
    //    A 类全书库入口按实参个数锁：只传 context + 关键词，不带第三实参。
    @Test
    fun `scoped routes pass SearchScope string through router per call site`() {
        val bookInfo = readMain("ui/book/info/BookInfoActivity.kt")
        val bookInfoCalls = openShelfSearchCalls(bookInfo)
        val infoScoped = bookInfoCalls.filter { it.size >= 3 }
        assertEquals("书详情标签检索应只有一处单源作用域调用", 1, infoScoped.size)
        assertEquals("标签检索第三实参必须是 SearchScope(source).toString()",
            "SearchScope(source).toString()", infoScoped.first()[2])
        val infoPlain = bookInfoCalls.count { it.size == 2 }
        assertEquals("作者 / 书名四处全书库入口只传关键词（无第三实参）", 4, infoPlain)
        assertTrue("作者两处全书库入口关键词取 book.author",
            bookInfoCalls.count { it.size == 2 && it[1] == "book.author" } == 2)
        assertTrue("书名两处全书库入口关键词取 book.name",
            bookInfoCalls.count { it.size == 2 && it[1] == "book.name" } == 2)

        val sourceManage = readMain("ui/book/source/manage/BookSourceActivity.kt")
        val manageCalls = openShelfSearchCalls(sourceManage)
        assertEquals("书源管理应只有一处 router 调用", 1, manageCalls.size)
        assertEquals("书源管理第三实参必须是 SearchScope(source).toString()",
            "SearchScope(source).toString()", manageCalls.first()[2])

        val sourceEdit = readMain("ui/book/source/edit/BookSourceEditActivity.kt")
        val editCalls = openShelfSearchCalls(sourceEdit)
        assertEquals("书源编辑应只有一处 router 调用", 1, editCalls.size)
        assertEquals("书源编辑第三实参必须是 SearchScope(source).toString()",
            "SearchScope(source).toString()", editCalls.first()[2])

        val rssJs = readMain("ui/rss/read/RssJsExtensions.kt")
        val rssCalls = openShelfSearchCalls(rssJs)
        assertEquals("RSS JS 桥两个重载都必须透传第三实参", 2, rssCalls.size)
        val rssScopeArgs = rssCalls.map { it[2] }
        assertEquals("通用重载第三实参必须透传 JS 传入的 searchScope",
            "searchScope", rssScopeArgs.first())
        assertEquals("单源重载第三实参必须是 SearchScope(source).toString()",
            "SearchScope(source).toString()", rssScopeArgs.last())
    }
}
