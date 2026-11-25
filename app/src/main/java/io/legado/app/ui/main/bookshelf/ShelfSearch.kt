package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.main.search.SearchLoadState
import io.legado.app.ui.main.search.SearchUiState

/**
 * D25 · 书架页下拉搜索浮层状态机（纯 JVM，不依赖 Compose / Android 框架，便于单测）。
 *
 * 口径（F-13）：搜索只做书架页下拉浮层，不再起独立搜索页。
 * 开合态与面板分区推导在本状态机；历史 / 热门 / 联网检索 / 分页翻页复用既有
 * `ui.main.search` 栈（[SearchUiState] + InkSearchViewModel），本状态机不碰网络、
 * 不复制热门三元回落或翻页逻辑。
 *
 * 死代码清剿留档：早期本地书架三路投影纯函数（书名 / 作者 / 正文片段过滤，旧
 * `filter` / `search` / `SearchOutcome` / `toLite` 与 `bookshelf/search/ShelfSearchContract.kt`
 * 内的 `searchBooks` / `ShelfItemLite` / `HitSegment` 等）在浮层结果主源切到检索栈合并结果后，
 * 主源码已无调用方，仅被旧测试锁定。本单一并移除：能力已由既有搜索栈
 * （`SearchMatcher` / `SearchMerger` / `SearchReducer` 与 DAO 真实流）承接，残留旧投影
 * 只会误导接手者以为浮层存在第二套本地过滤口径。
 */
object ShelfSearch {

    enum class Stage { CLOSED, OPENED }

    data class State(
        val stage: Stage,
        val query: String,
    ) {
        val isOpen: Boolean get() = stage == Stage.OPENED
    }

    /**
     * 浮层面板分区（纯 JVM 推导，不碰 Compose / Android）：开合态 + 检索整页态 → 应渲染哪个区。
     *
     * 为什么抽到状态机层：开合与「输入 → 结果 → 关闭」的面板切换若埋在 Compose 分支里，
     * 纯 JVM 单测无法锁定；这里把推导收成纯函数，画面层只读本枚举挂载分支，不另写第二套判断。
     * 检索整页态复用既有搜索栈 [SearchUiState]（空闲 / 加载 / 内容 / 空 / 错 / 离线），
     * 不在书架包内复制一套整页状态。
     */
    enum class OverlayBody {
        /** 浮层关闭：不挂载浮层组合树。 */
        HIDDEN,

        /** 空闲：历史词条 + 热门词面板（双空由面板自身兜底文案收口，不留白）。 */
        HISTORY_HOT,

        /** 首屏检索中：三点加载指示。 */
        LOADING,

        /** 有结果：可滚动结果列表 + 分页脚注。 */
        RESULTS,

        /** 有查询词但无结果：空态引导。 */
        EMPTY,

        /** 检索失败：缓存结果 + 可重试入口。 */
        ERROR,

        /** 网络断开：本地缓存 + 离线文案 + 可重试入口。 */
        OFFLINE,
    }

    /**
     * 分区挂载的组件族（结构级分类，纯 JVM 可断言）。画面层 `when (body)` 分支只允许落到
     * 本枚举登记的族；组件族到既有搜索栈 Composable 符号的对应关系见 [sharedComponents]。
     *
     * 为什么不直接在状态机层引用 Composable 类型：状态机必须保持纯 JVM，不能 import
     * Compose；用枚举族 + 符号注册表做结构映射，单测断映射而不是 grep 源码字符串。
     */
    enum class OverlayComponent {
        /** 关闭：不挂载任何浮层组合树。 */
        NONE,

        /** 历史 / 热门词条面板（复用既有 `SearchHistoryPanel`）。 */
        HISTORY_PANEL,

        /** 三点加载指示（复用既有 `SearchLoadingDots`）。 */
        LOADING_DOTS,

        /** 可滚动结果列表（复用既有 `SearchResultList`）。 */
        RESULT_LIST,

        /** 空态引导（复用既有 `SearchFallbackBody`）。 */
        EMPTY_FALLBACK,

        /** 失败兜底：缓存列 + 重试入口（复用既有浮层兜底壳与 `SearchFallbackBody`）。 */
        ERROR_FALLBACK,

        /** 离线兜底：本地缓存 + 离线文案 + 重试入口。 */
        OFFLINE_FALLBACK,
    }

    /**
     * 分区行为规格：该分区挂哪个组件族、是否给可重试入口、是否允许挂缓存结果列。
     * 画面层分支与本规格一一对应；纯 JVM 单测据此锁「某状态下应显示什么」，
     * 而不是回证源码里出现某个文案或组件名。
     */
    data class OverlayBodySpec(
        val component: OverlayComponent,
        val hasRetryAction: Boolean,
        val showsCached: Boolean,
    )

    /**
     * 分区 → 组件族 / 行为能力结构映射表。七个分区穷举在册；新增分区必须补表，
     * 否则 [specOf] 取值时直接抛异常，编译期 / 单测期暴露缺漏。
     */
    val bodySpecs: Map<OverlayBody, OverlayBodySpec> = mapOf(
        OverlayBody.HIDDEN to OverlayBodySpec(
            component = OverlayComponent.NONE,
            hasRetryAction = false,
            showsCached = false,
        ),
        OverlayBody.HISTORY_HOT to OverlayBodySpec(
            component = OverlayComponent.HISTORY_PANEL,
            hasRetryAction = false,
            showsCached = false,
        ),
        OverlayBody.LOADING to OverlayBodySpec(
            component = OverlayComponent.LOADING_DOTS,
            hasRetryAction = false,
            showsCached = false,
        ),
        OverlayBody.RESULTS to OverlayBodySpec(
            component = OverlayComponent.RESULT_LIST,
            hasRetryAction = false,
            showsCached = false,
        ),
        OverlayBody.EMPTY to OverlayBodySpec(
            component = OverlayComponent.EMPTY_FALLBACK,
            hasRetryAction = false,
            showsCached = false,
        ),
        OverlayBody.ERROR to OverlayBodySpec(
            component = OverlayComponent.ERROR_FALLBACK,
            hasRetryAction = true,
            showsCached = true,
        ),
        OverlayBody.OFFLINE to OverlayBodySpec(
            component = OverlayComponent.OFFLINE_FALLBACK,
            hasRetryAction = true,
            showsCached = true,
        ),
    )

    /** 取分区行为规格；表内缺漏直接抛异常，不留静默默认分支。 */
    fun specOf(body: OverlayBody): OverlayBodySpec = bodySpecs.getValue(body)

    /**
     * 组件族 → 构件符号名注册表。面板 / 列表 / 加载 / 兜底正文复用 `ui.main.search`
     * 既有族；`OverlayFallbackWithCache` 是浮层本地兜底壳（内部包既有 `SearchFallbackBody`
     * 与既有 `SearchResultList`），一并登记以便结构断言穷举。本注册表是「复用哪些构件」
     * 的结构级表达，单测断本表完整性与符号归属，不再对画面源码做精确子串匹配
     * （源码折行 / 构件改名不再直接击穿断言；改名时只需改这一处注册表）。
     */
    val sharedComponents: Map<OverlayComponent, Set<String>> = mapOf(
        OverlayComponent.NONE to emptySet(),
        OverlayComponent.HISTORY_PANEL to setOf("SearchHistoryPanel"),
        OverlayComponent.LOADING_DOTS to setOf("SearchLoadingDots"),
        OverlayComponent.RESULT_LIST to setOf("SearchResultList"),
        OverlayComponent.EMPTY_FALLBACK to setOf("SearchFallbackBody"),
        OverlayComponent.ERROR_FALLBACK to setOf("OverlayFallbackWithCache", "SearchFallbackBody"),
        OverlayComponent.OFFLINE_FALLBACK to setOf("OverlayFallbackWithCache", "SearchFallbackBody"),
    )

    /**
     * 推导浮层当前应渲染的分区。浮层关闭时一律 [OverlayBody.HIDDEN]，与检索态无关；
     * 打开后按 [SearchUiState.loadState] 映射，分支口径与旧独立搜索页一致。
     */
    fun bodyOf(overlay: State, search: SearchUiState): OverlayBody =
        if (!overlay.isOpen) OverlayBody.HIDDEN
        else when (search.loadState) {
            is SearchLoadState.Idle -> OverlayBody.HISTORY_HOT
            is SearchLoadState.Loading -> OverlayBody.LOADING
            is SearchLoadState.Content -> OverlayBody.RESULTS
            is SearchLoadState.Empty -> OverlayBody.EMPTY
            is SearchLoadState.Error -> OverlayBody.ERROR
            is SearchLoadState.Offline -> OverlayBody.OFFLINE
        }

    /**
     * 开合 effect 下一步动作（纯 JVM 判定结果）。画面层只按本枚举派发回调，不再在
     * Composable 内手写守卫分支，守卫口径因此可被单测直接锁定（验收 N3 收口）。
     */
    enum class LifecycleAction {
        /** 打开浮层：挂接搜索 ViewModel（首建模型 / 重建双路收集由 VM 幂等分支承接）。 */
        Attach,

        /** 关闭浮层且此前打开过：释放双路收集并暂停检索模型。 */
        Detach,

        /** 首帧关闭态：从未打开过，不触发 detach，避免冷启空挂接后立刻被取消。 */
        Idle,
    }

    /**
     * 一次开合 effect 触发的纯判定结果：新的「曾打开过」守卫位 + 应派发的动作。
     * 为什么抽成纯函数：守卫原本埋在 Compose `LaunchedEffect` 内，纯 JVM 测试无法驱动
     * 重组，去掉 `hasOpenedOverlay` 守卫不会让任何既有用例变红；收成函数后，画面层
     * 只读本结果派发，判定逻辑可直接断。
     */
    data class LifecycleTransition(
        val hasOpened: Boolean,
        val action: LifecycleAction,
    )

    /**
     * 开合守卫判定：给定当前开合态 [isOpen] 与此前是否打开过 [hasOpened]，产出下一步
     * 动作与新守卫位。
     * - 打开 → 守卫位置 true，动作 [LifecycleAction.Attach]；
     * - 关闭且曾打开 → 守卫位复位 false，动作 [LifecycleAction.Detach]；
     * - 关闭且首帧从未打开 → 守卫位保持 false，动作 [LifecycleAction.Idle]（不 detach）。
     */
    fun overlayTransition(isOpen: Boolean, hasOpened: Boolean): LifecycleTransition = when {
        isOpen -> LifecycleTransition(hasOpened = true, action = LifecycleAction.Attach)
        hasOpened -> LifecycleTransition(hasOpened = false, action = LifecycleAction.Detach)
        else -> LifecycleTransition(hasOpened = false, action = LifecycleAction.Idle)
    }

    /** 初始态：收起、无关键词。 */
    val closed = State(Stage.CLOSED, "")

    /** 展开浮层；保留上次关键词，便于接续输入。 */
    fun open(state: State): State = state.copy(stage = Stage.OPENED)

    /** 收起浮层：清空关键词，下次打开是一次新搜索，不留陈旧查询。 */
    fun close(state: State): State = State(Stage.CLOSED, "")

    /** 输入即查：关键词原样落状态；结果由既有搜索栈归约产出，状态机不做本地过滤。 */
    fun updateQuery(state: State, query: String): State = state.copy(query = query)
}
