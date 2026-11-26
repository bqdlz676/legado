package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.main.search.SearchHotResolver
import io.legado.app.ui.main.search.SearchHotSource
import io.legado.app.ui.main.search.SearchIntent
import io.legado.app.ui.main.search.SearchLoadState
import io.legado.app.ui.main.search.SearchPagingState
import io.legado.app.ui.main.search.SearchReducer
import io.legado.app.ui.main.search.SearchResultItem
import io.legado.app.ui.main.search.SearchTokens
import io.legado.app.ui.main.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 书架浮层搜索能力收口单测（纯 JVM，不依赖 Android / Compose 运行时）。
 *
 * 覆盖验收报告《验收-导航三Tab》判据 2/3/4/6/7 缺口在浮层侧的回填：
 * 1. 浮层开合状态机 + 面板分区推导（打开 → 输入 → 结果 → 关闭 全链路可测）；
 * 2. 有历史 → 空闲面板落历史/热门区且历史行非空；
 * 3. 历史与高频双空 → 热门经既有 [SearchHotResolver] 单源落到 SEED 兜底档并标记兜底；
 * 4. 结果第二批叠加而非覆盖（复用既有 [SearchReducer] 翻页归约，不复制翻页逻辑）；
 * 5. 【兜底回证，非主锁】源码文本检查：浮层复用既有面板 / 列表单源，未复制热门三元回落，
 *    结果列无 260dp 硬限高。组件族归属的结构主锁见 ShelfOverlayBodyBehaviorTest 注册表断言；
 *    本子串断言对折行 / 改名脆弱，仅作源码存在性兜底，不承担行为契约。
 * 6. 【兜底回证，非主锁】源码文本检查：Fragment 回调直连既有搜索 ViewModel 入口
 *    （联网 / 历史 / 热门 / 翻页同源）。回调签名对账属接线静态复核；结构级主锁见
 *    ShelfOverlayBodyBehaviorTest。
 *
 * 口径说明：热门三元回落（USAGE→HISTORY→SEED）与分页追加 / 收口的唯一实现仍在
 * `ui/main/search` 包内；本文件只经既有公开入口驱动与断言，不在书架包另立副本。
 */
class ShelfOverlaySearchTest {

    private val javaRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    // 1. 开合状态机：关闭 → 打开 → 落词 → 结果态 → 关闭，面板分区随开合与检索态推导；
    //    关闭后即便检索栈已有结果，浮层也不挂载（HIDDEN）。
    @Test
    fun `overlay machine opens closes and derives hidden body`() {
        var state = ShelfSearch.closed
        val idle = SearchUiState()
        assertEquals(ShelfSearch.OverlayBody.HIDDEN, ShelfSearch.bodyOf(state, idle))

        state = ShelfSearch.open(state)
        assertTrue("打开后浮层处于展开态", state.isOpen)
        assertEquals(ShelfSearch.OverlayBody.HISTORY_HOT, ShelfSearch.bodyOf(state, idle))

        state = ShelfSearch.updateQuery(state, "左传")
        assertEquals("左传", state.query)

        val results = listOf(SearchResultItem(id = "1", title = "春秋左传", author = "左丘明"))
        val content = SearchUiState(
            query = "左传",
            loadState = SearchLoadState.Content(results),
            lastResults = results,
        )
        assertEquals(ShelfSearch.OverlayBody.RESULTS, ShelfSearch.bodyOf(state, content))

        state = ShelfSearch.close(state)
        assertFalse("收起后浮层关闭", state.isOpen)
        assertEquals("收起即清空关键词", "", state.query)
        assertEquals(
            "关闭态不挂载浮层，即使检索栈仍有结果",
            ShelfSearch.OverlayBody.HIDDEN,
            ShelfSearch.bodyOf(state, content),
        )

        // 重新打开是一次新搜索：不带上次关键词，回落空闲面板。
        state = ShelfSearch.open(state)
        assertEquals("", state.query)
        assertEquals(ShelfSearch.OverlayBody.HISTORY_HOT, ShelfSearch.bodyOf(state, idle))
    }

    // 2. 有历史 → 空闲面板落 HISTORY_HOT 分区，历史行非空（面板数据源即检索栈 history）。
    @Test
    fun `history present renders history-hot panel with non-empty history`() {
        val search = SearchUiState(
            history = listOf("春秋", "左传"),
            loadState = SearchLoadState.Idle,
        )
        val body = ShelfSearch.bodyOf(ShelfSearch.open(ShelfSearch.closed), search)
        assertEquals(ShelfSearch.OverlayBody.HISTORY_HOT, body)
        assertTrue("历史区数据源不得为空", search.history.isNotEmpty())
        assertEquals(listOf("春秋", "左传"), search.history)
    }

    // 3. 高频与历史双空 → 热门经既有解析单源落 SEED 兜底档，来源标记为兜底（不冒充热搜）；
    //    双空空闲态仍落历史/热门面板，由面板自身兜底文案收口，不留白。
    @Test
    fun `empty usage and history resolve to seed fallback marked`() {
        val hot = SearchHotResolver.resolve(usage = emptyList(), history = emptyList())
        assertEquals(SearchHotSource.SEED, hot.source)
        assertTrue("精选兜底词不允许为空面板", hot.keys.isNotEmpty())
        assertEquals(SearchHotResolver.SEED_KEYS.take(SearchTokens.HOT_LIMIT), hot.keys)

        val search = SearchUiState(
            history = emptyList(),
            hot = hot.keys,
            hotSource = hot.source,
            loadState = SearchLoadState.Idle,
        )
        assertEquals(
            ShelfSearch.OverlayBody.HISTORY_HOT,
            ShelfSearch.bodyOf(ShelfSearch.open(ShelfSearch.closed), search),
        )
    }

    // 4. 第二批结果叠加而非覆盖：首批条目保留，按 id 去重合并；与旧 Activity 翻页口径一致。
    //    归约走既有 SearchReducer 单源，本断言不另写合并逻辑。
    @Test
    fun `second page appends keeping first batch and deduping by id`() {
        val page1 = listOf(SearchResultItem(id = "1", title = "春秋左传", author = "左丘明"))
        val page2 = listOf(SearchResultItem(id = "2", title = "史记", snippet = "春秋时期"))
        // 归约器单源语义（Q-179/D1-R1）：追加只在"翻页在途"时生效，故前置 paging=LoadingMore；
        // 追加批不提前收口，终态由 LoadMoreFinish 落定。
        val state = SearchUiState(
            query = "左传",
            loadState = SearchLoadState.Content(page1),
            lastResults = page1,
            paging = SearchPagingState.LoadingMore,
        )

        val appended = SearchReducer.reduce(
            state,
            SearchIntent.LoadMoreAppend(page1 + page2, hasMore = true),
        )

        val content = appended.loadState as SearchLoadState.Content
        assertEquals(listOf("1", "2"), content.results.map { it.id })
        assertTrue("首批条目不得被第二批覆盖", content.results.any { it.id == "1" })
        assertEquals(content.results, appended.lastResults)
        assertTrue("追加批不提前收口，仍在途", appended.paging is SearchPagingState.LoadingMore)
        val finished = SearchReducer.reduce(appended, SearchIntent.LoadMoreFinish(hasMore = true))
        assertTrue("Finish 是唯一的收口来源", finished.paging is SearchPagingState.Idle)
    }

    // 5.【兜底回证，非主锁】源码文本检查：浮层复用既有历史面板与结果列表单源；书架包不得
    //    复制热门三元回落词表；结果列不得保留 260dp 硬限高。组件族归属的结构主锁在
    //    ShelfOverlayBodyBehaviorTest 的 sharedComponents 注册表断言；本子串匹配仅兜底。
    @Test
    fun `overlay reuses shared search stack without copied fallback or height cap`() {
        val screen = File(
            javaRoot,
            "io/legado/app/ui/main/bookshelf/BookshelfScreen.kt",
        ).readText()

        assertTrue("浮层历史/热门面板必须复用 SearchHistoryPanel", screen.contains("SearchHistoryPanel("))
        assertTrue("浮层结果列表必须复用 SearchResultList", screen.contains("SearchResultList("))
        assertFalse("浮层不得复制精选兜底词表 SEED_KEYS", screen.contains("SEED_KEYS"))
        assertFalse("结果列不得保留 260dp 硬限高", screen.contains("260.dp"))

        // 书架包内不得另立热门三档回落枚举（USAGE / HISTORY / SEED 同源仅在 search 包）。
        val shelfDir = File(javaRoot, "io/legado/app/ui/main/bookshelf")
        val copied = shelfDir.walkTopDown()
            .filter { it.extension == "kt" }
            .map { it.readText() }
            .filter { it.contains("USAGE") && it.contains("HISTORY") && it.contains("SEED") }
        // Sequence 没有 isEmpty()（Kotlin 只在 Collection 上提供），必须用 count() 判空。
        assertTrue("书架包不得复制热门三档回落枚举", copied.count() == 0)
    }

    // 6.【兜底回证，非主锁】源码文本检查：Fragment 把浮层回调直连既有搜索 ViewModel 入口
    //    ——联网检索、历史增删清、提交 / 重试 / 翻页 / 翻页重试同源旧 Activity 栈，不另建
    //    检索出口。签名对账以静态逐行比对为准；本子串匹配对折行 / 具名参数改写脆弱，仅兜底。
    @Test
    fun `fragment wires overlay callbacks to shared search view model`() {
        val fragment = File(
            javaRoot,
            "io/legado/app/ui/main/bookshelf/InkShelfFragment.kt",
        ).readText()

        listOf(
            "InkSearchViewModel",
            "attach(key, scope)",
            "QueryChange",
            "submit()",
            "pickHistory",
            "removeHistory",
            "clearHistory",
            "ClearQuery",
            "retry()",
            "loadMore()",
            "retryLoadMore()",
        ).forEach { marker ->
            assertTrue("Fragment 缺既有搜索栈接线：$marker", fragment.contains(marker))
        }
    }
}
