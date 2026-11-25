package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.main.search.SearchLoadState
import io.legado.app.ui.main.search.SearchResultItem
import io.legado.app.ui.main.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 书架浮层分区行为 + 结构映射收口单测（纯 JVM，不依赖 Android / Compose 运行时）。
 *
 * 对应验收报告《验收-书架浮层搜索能力.md》挂账：
 * - D1：LOADING / EMPTY / ERROR / OFFLINE 四分区原先只有分区推导、没有「此时应显示什么」
 *   的行为锁；本文件前四条用例构造对应 [SearchLoadState]，断言落区、组件族、重试入口与
 *   缓存列口径，断的是状态 → 行为规格，不是回证源码字符串。
 * - D2：「复用哪些既有 Composable」改由 [ShelfSearch.sharedComponents] 结构注册表表达；
 *   本文件后两条用例锁注册表完备性与符号归属，替代对画面源码的精确子串匹配。
 *
 * 口径：历史 / 热门 / 联网结果 / 分页仍归既有 `ui.main.search` 栈；本文件只验浮层状态机
 * 与结构映射，不复制检索归约逻辑。
 */
class ShelfOverlayBodyBehaviorTest {

    private val openState get() = ShelfSearch.open(ShelfSearch.closed)

    private fun item(id: String, title: String) =
        SearchResultItem(id = id, title = title, author = "佚名")

    // ───────────────────────── D1 · 四分区行为断言 ─────────────────────────

    // 1. 检索中（Loading）→ LOADING 分区：挂三点加载指示族；不给重试入口、不挂缓存列。
    @Test
    fun `loading state renders loading dots without retry or cache`() {
        val search = SearchUiState(loadState = SearchLoadState.Loading)

        val body = ShelfSearch.bodyOf(openState, search)
        val spec = ShelfSearch.specOf(body)

        assertEquals(ShelfSearch.OverlayBody.LOADING, body)
        assertEquals(ShelfSearch.OverlayComponent.LOADING_DOTS, spec.component)
        assertFalse("加载态不挂重试入口", spec.hasRetryAction)
        assertFalse("加载态不挂缓存结果列", spec.showsCached)
    }

    // 2. 有词无命中（Empty）→ EMPTY 分区：挂空态引导族；不给重试入口、不挂缓存列。
    @Test
    fun `empty state renders empty guidance without retry`() {
        val search = SearchUiState(
            query = "庄子",
            loadState = SearchLoadState.Empty("庄子"),
        )

        val body = ShelfSearch.bodyOf(openState, search)
        val spec = ShelfSearch.specOf(body)

        assertEquals(ShelfSearch.OverlayBody.EMPTY, body)
        assertEquals(ShelfSearch.OverlayComponent.EMPTY_FALLBACK, spec.component)
        assertFalse("空态引导不挂重试入口", spec.hasRetryAction)
        assertFalse("空态不挂缓存结果列", spec.showsCached)
    }

    // 3. 检索失败（Error）→ ERROR 分区：挂失败兜底族，给重试入口；缓存列允许展示，
    //    且可见结果取错态缓存而不是空列表。
    @Test
    fun `error state renders fallback with retry entry and cached list`() {
        val cached = listOf(item("1", "春秋左传"), item("2", "史记"))
        val search = SearchUiState(
            query = "左传",
            loadState = SearchLoadState.Error("检索暂时不可用", cached = cached),
            lastResults = cached,
        )

        val body = ShelfSearch.bodyOf(openState, search)
        val spec = ShelfSearch.specOf(body)

        assertEquals(ShelfSearch.OverlayBody.ERROR, body)
        assertEquals(ShelfSearch.OverlayComponent.ERROR_FALLBACK, spec.component)
        assertTrue("失败态必须给可重试入口", spec.hasRetryAction)
        assertTrue("失败态允许挂缓存结果列", spec.showsCached)
        assertEquals("错态可见结果取缓存列", cached, search.visibleResults)
    }

    // 4. 网络断开（Offline）→ OFFLINE 分区：挂离线兜底族，给重试入口；本地缓存列展示。
    @Test
    fun `offline state renders offline fallback with retry and local cache`() {
        val cached = listOf(item("1", "三国志"))
        val search = SearchUiState(
            query = "三国",
            loadState = SearchLoadState.Offline(cached = cached),
            lastResults = cached,
        )

        val body = ShelfSearch.bodyOf(openState, search)
        val spec = ShelfSearch.specOf(body)

        assertEquals(ShelfSearch.OverlayBody.OFFLINE, body)
        assertEquals(ShelfSearch.OverlayComponent.OFFLINE_FALLBACK, spec.component)
        assertTrue("离线态必须给可重试入口", spec.hasRetryAction)
        assertTrue("离线态允许挂本地缓存列", spec.showsCached)
        assertEquals("离线态可见结果取本地缓存", cached, search.visibleResults)
    }

    // 补充行为锁：关闭态一律 HIDDEN，规格为 NONE——不挂载任何浮层组件族、无重试、无缓存。
    @Test
    fun `hidden closed body mounts no component family`() {
        val search = SearchUiState(loadState = SearchLoadState.Content(emptyList()))
        val spec = ShelfSearch.specOf(ShelfSearch.bodyOf(ShelfSearch.closed, search))

        assertEquals(ShelfSearch.OverlayComponent.NONE, spec.component)
        assertFalse(spec.hasRetryAction)
        assertFalse(spec.showsCached)
    }

    // ───────────────────────── D2 · 结构映射断言 ─────────────────────────

    // 5. 分区规格表穷举完备：七个 OverlayBody 各在册一次；specOf 与表一致；
    //    每个分区的组件族 / 重试 / 缓存口径与既有画面分支一一对应，新增分区缺表即在此报红。
    @Test
    fun `body spec table covers every overlay body exactly once`() {
        val expected = mapOf(
            ShelfSearch.OverlayBody.HIDDEN to ShelfSearch.OverlayComponent.NONE,
            ShelfSearch.OverlayBody.HISTORY_HOT to ShelfSearch.OverlayComponent.HISTORY_PANEL,
            ShelfSearch.OverlayBody.LOADING to ShelfSearch.OverlayComponent.LOADING_DOTS,
            ShelfSearch.OverlayBody.RESULTS to ShelfSearch.OverlayComponent.RESULT_LIST,
            ShelfSearch.OverlayBody.EMPTY to ShelfSearch.OverlayComponent.EMPTY_FALLBACK,
            ShelfSearch.OverlayBody.ERROR to ShelfSearch.OverlayComponent.ERROR_FALLBACK,
            ShelfSearch.OverlayBody.OFFLINE to ShelfSearch.OverlayComponent.OFFLINE_FALLBACK,
        )

        assertEquals(
            "分区表必须覆盖全部 OverlayBody 枚举项",
            expected.keys,
            ShelfSearch.bodySpecs.keys,
        )
        expected.forEach { (body, component) ->
            assertEquals(component, ShelfSearch.specOf(body).component)
            assertEquals(ShelfSearch.bodySpecs.getValue(body), ShelfSearch.specOf(body))
        }

        // 仅失败 / 离线两兜底态允许重试与缓存列；其余分区不得越权挂重试入口。
        ShelfSearch.bodySpecs.forEach { (body, spec) ->
            when (body) {
                ShelfSearch.OverlayBody.ERROR,
                ShelfSearch.OverlayBody.OFFLINE -> {
                    assertTrue("$body 应有重试入口", spec.hasRetryAction)
                    assertTrue("$body 应允许缓存列", spec.showsCached)
                }
                else -> {
                    assertFalse("$body 不应有重试入口", spec.hasRetryAction)
                    assertFalse("$body 不应挂缓存列", spec.showsCached)
                }
            }
        }
    }

    // 6. 组件族 → 既有搜索栈 Composable 符号注册表完备：可见族各登记 ≥1 个复用构件，
    //    NONE 不登记任何构件；注册符号集合与既有搜索栈构件族一致，浮层不新造面板 / 列表。
    @Test
    fun `shared component registry maps visible families to stack composables`() {
        val registry = ShelfSearch.sharedComponents

        assertEquals(
            "注册表必须覆盖全部 OverlayComponent 枚举项",
            ShelfSearch.OverlayComponent.entries.toSet(),
            registry.keys,
        )
        assertTrue("关闭族不登记任何构件", registry.getValue(ShelfSearch.OverlayComponent.NONE).isEmpty())

        ShelfSearch.OverlayComponent.entries
            .filterNot { it == ShelfSearch.OverlayComponent.NONE }
            .forEach { family ->
                assertTrue(
                    "$family 必须登记复用的既有 Composable 符号",
                    registry.getValue(family).isNotEmpty(),
                )
            }

        // 登记构件族：既有搜索栈面板 / 列表 / 加载 / 兜底正文 + 浮层本地兜底壳
        // （与 BookshelfScreen 浮层 when 分支一一对应）。
        val expectedSymbols = setOf(
            "SearchHistoryPanel",
            "SearchLoadingDots",
            "SearchResultList",
            "SearchFallbackBody",
            "OverlayFallbackWithCache",
        )
        val registered = registry.values.flatten().toSet()
        assertEquals(expectedSymbols, registered)
    }
}
