package io.legado.app.ui.main.bookshelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.sensor.GyroBridge
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.search.InkSearchViewModel
import io.legado.app.ui.main.search.SearchIntent
import io.legado.app.ui.main.search.SearchResultItem
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 落文 · U4 书架宿主 Fragment（切片-10）。
 *
 * 只借用排版引擎那一侧的书籍数据与阅读跳转，界面、列表、卡片、空态全部由 [BookshelfScreen]
 * Compose 自绘。数据库订阅 / 跳转动作在本类接线，画面层不碰 Room 实体。
 *
 * 类名与 `position` 构造口径由 [MainFragmentInterface] 与入口 Activity 反射引用，不可改名。
 */
class InkShelfFragment() : BaseFragment(0), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private var flowJob: Job? = null

    /** 槽位可见性口径（D1）：onHiddenChanged 回填；申领桥只在 resumed 且未 hidden 时进行。 */
    private var shelfHidden = false

    private var books: List<Book> = emptyList()
    private var state by mutableStateOf(ShelfState.INIT_LOADING)
    private var uiBooks by mutableStateOf(emptyList<ShelfBookUi>())

    /**
     * 浮层检索主控：复用既有搜索栈 ViewModel（联网书源检索 / 历史词 / 热门词双路流 /
     * 分页翻页全部归它编排），书架浮层只透传状态与回调，不另建第二套检索栈。
     * 作用域绑本 Fragment；与旧独立搜索 Activity 各自持有实例，互不干扰。
     */
    private val searchViewModel: InkSearchViewModel by viewModels()

    /** 回顶信号计数：每次 [gotoTop] 自增，画面层观察到变化即滚回首项。 */
    private var scrollTopSignal by mutableStateOf(0)

    /**
     * 跨页检索请求：非书架页（发现 / 书详情 / 书源管理 / 阅读记录 / 外部分享）经
     * [ShelfSearchRouter] 改道后投递到这里；画面层按序号打开浮层并回填初始词 / 作用域。
     * 冷启时由 [onFragmentCreated] 从 [ShelfSearchHandoff] 消费排队请求。
     */
    private var searchRequest by mutableStateOf<ShelfSearchRequest?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            val searchUiState by searchViewModel.uiState.collectAsState()
            BookshelfScreen(
                state = state,
                books = uiBooks,
                newBookCount = uiBooks.count { it.isNew },
                scrollTopSignal = scrollTopSignal,
                // Q441：视差开关偏好归宿主（Fragment 侧 appCtx 已初始化；Compose 屏自身不碰 AppConfig）。
                parallaxEnabled = AppConfig.shelfParallaxEnabled,
                onPersistParallax = ::onPersistParallaxPreference,
                onOpenBook = ::openBook,
                onImport = ::openImport,
                onDeleteBook = ::deleteBook,
                onRetry = ::reload,
                searchUiState = searchUiState,
                searchRequest = searchRequest,
                onAttachSearch = { key, scope -> searchViewModel.attach(key, scope) },
                onDetachSearch = { searchViewModel.detachOverlay() },
                onQueryChange = { searchViewModel.dispatch(SearchIntent.QueryChange(it)) },
                onSubmitSearch = { searchViewModel.submit() },
                onPickHistory = { searchViewModel.pickHistory(it) },
                onRemoveHistory = { searchViewModel.removeHistory(it) },
                onClearHistory = { searchViewModel.clearHistory() },
                onClearQuery = { searchViewModel.dispatch(SearchIntent.ClearQuery) },
                onOpenSearchItem = ::openSearchItem,
                onRetrySearch = { searchViewModel.retry() },
                onLoadMore = { searchViewModel.loadMore() },
                onRetryLoadMore = { searchViewModel.retryLoadMore() },
            )
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        observeBooks()
        // 冷启 / 首次切槽时 Fragment 晚于 MainActivity 建栈：消费主壳排队的跨页检索请求。
        ShelfSearchHandoff.consume()?.let { openSearchRequest(it) }
    }

    /** 主壳把跨页检索请求投递给本 Fragment（已在栈中时由 MainActivity 直调）。 */
    fun openSearchRequest(request: ShelfSearchRequest) {
        searchRequest = request
    }

    /**
     * 返回键消费：新书架是单组平铺，没有旧版「子分组退回根分组」层级，
     * 因此不拦截返回键，交由 Activity 走退出 / 退到后台流程。
     */
    fun back(): Boolean = false

    /** 双击底部书柜 tab：列表滚回首项（旧版 gotoTop 的等价能力）。 */
    fun gotoTop() {
        scrollTopSignal++
    }

    /** 配置变更后重排：重新订阅书籍流，按最近阅读时间刷新顺序（旧版 upSort 等价能力）。 */
    fun upSort() = observeBooks()

    override fun onDestroyView() {
        flowJob?.cancel()
        flowJob = null
        books = emptyList()
        // N1 补摘牌：视图销毁后不再保留持牌；release 只传身份串、不碰 Context/Activity，
        // 解挂后仍可安全调用（兜底写法见 releaseShelfBridge / acquireShelfBridge 注释）。
        syncShelfBridge()
        super.onDestroyView()
    }

    override fun onDestroy() {
        // N1 兜底：极端路径未走 onPause 即销毁时这里再裁一次；release 在桥侧按身份集合
        // 幂等去重，重复交还不会误拆其他宿主的牌（见报告 §3 序列四）。
        syncShelfBridge()
        super.onDestroy()
    }

    private fun observeBooks() {
        flowJob?.cancel()
        flowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowRoot()
                .map { list -> list.sortedByDescending { it.durChapterTime } }
                .flowWithLifecycleAndDatabaseChangeFirst(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_TABLE_NAME,
                )
                .catch {
                    AppLog.put("落文书架读取失败", it)
                    state = ShelfState.ERROR
                }
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { list ->
                    books = list
                    uiBooks = list.map { it.toShelfBookUi() }
                    state = when {
                        list.isEmpty() -> ShelfState.EMPTY
                        !NetworkUtils.isAvailable() -> ShelfState.OFFLINE
                        else -> ShelfState.CONTENT
                    }
                }
        }
    }

    private fun reload() {
        state = ShelfState.INIT_LOADING
        observeBooks()
    }

    private fun Book.toShelfBookUi(): ShelfBookUi = shelfBookUi(
        id = bookUrl.ifBlank { name },
        title = name,
        author = author,
        durChapterIndex = durChapterIndex,
        totalChapterNum = totalChapterNum,
        chapterTitle = durChapterTitle,
        latestChapterTime = durChapterTime,
    )

    private fun openBook(ui: ShelfBookUi) {
        val book = books.firstOrNull { it.bookUrl == ui.id }
            ?: books.firstOrNull { it.name == ui.title && it.author == ui.author }
            ?: return
        startActivityForBook(book)
    }

    /**
     * 点开一条浮层检索结果：本地书架已藏的书卷走阅读页入口；书源联网带回的未藏书卷
     * 走书籍详情页，入参键（name / author / bookUrl）沿用旧检索页透传口径。
     */
    private fun openSearchItem(item: SearchResultItem) {
        val local = books.firstOrNull { it.bookUrl == item.id }
            ?: books.firstOrNull { it.name == item.title && it.author == item.author }
        if (local != null) {
            startActivityForBook(local)
            return
        }
        startActivity<BookInfoActivity> {
            putExtra("name", item.title)
            putExtra("author", item.author)
            putExtra("bookUrl", item.id)
        }
    }

    override fun onPause() {
        super.onPause()
        searchViewModel.pause()
        // 切后台交还；裁决口径统一走 syncShelfBridge（PAUSED 态 visible 必为 false），不另写 if。
        syncShelfBridge()
    }

    override fun onResume() {
        super.onResume()
        searchViewModel.resume()
        // 回前台补申领 / 维持持牌；闸门口径与其他四处一致，走唯一裁决入口。
        syncShelfBridge()
    }

    /**
     * Q441：切到其他 Tab（Fragment 被 hide）即交还持牌；回到书架槽按开关口径重新申领。
     * 为什么不走 onPause：同屏 Tab 切换不触发 Fragment onPause，hide/show 才是槽位可见性口径。
     */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        shelfHidden = hidden
        // hidden=true 停桥（Q441 功耗要求，不放宽）；hidden=false 由裁决入口二次判断 RESUMED（N2 收口）。
        syncShelfBridge()
    }

    /**
     * 视差开关回调（D1）：先落偏好，再交裁决入口统一比对可见性——
     * 关 → 交还；开 → 仅 resumed 且未 hidden 时申领；不可见不抢传感器，
     * 回到可见由 onResume / onHiddenChanged 补申领。平滑值 / 阴影由 Compose 状态层
     * `setEnabled(false)` 回中、D2 守卫兜底归零。
     */
    private fun onPersistParallaxPreference(enabled: Boolean) {
        AppConfig.shelfParallaxEnabled = enabled
        syncShelfBridge()
    }

    /**
     * 桥持牌状态唯一裁决入口（Q-458，N1/N2 收口）：申领 / 交还判定只写这一处，
     * onResume、onPause、onHiddenChanged（双向）、开关回调、onDestroyView/onDestroy 全部走它。
     * 为什么收敛：此前判定散在四处各写一份 if——开关回调叠了 RESUMED 闸门、
     * onHiddenChanged(false) 漏叠，口径必然不齐（这正是 N2 的根因）；单点裁决后不可再造第二份闸门。
     *
     * 口径：申领仅当「偏好开启 && 生命周期至少 RESUMED && 未被藏槽」三者同时成立；
     * 其余一切路径——切后台、切 Tab、关开关、视图 / Fragment 销毁——一律交还。
     */
    private fun syncShelfBridge() {
        val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !shelfHidden
        if (AppConfig.shelfParallaxEnabled && visible) {
            acquireShelfBridge()
        } else {
            releaseShelfBridge()
        }
    }

    /**
     * 申领桥：先按当前屏幕方向写基准，再上牌。
     * 兜底：销毁路径之后 requireContext / requireActivity 可能已解挂抛 IllegalStateException；
     * 申领只在裁决入口判 visible=true 时到达（生命周期仍 attached），这里再用可空属性取一次，
     * 拿不到 context 就放弃本轮申领——不抛异常，也不静默降级成永久失桥：
     * 回到可见路径会由 onResume / onHiddenChanged 再走裁决补申领。
     */
    private fun acquireShelfBridge() {
        val ctx = context ?: return
        runCatching {
            val rotation = activity?.windowManager?.defaultDisplay?.rotation ?: 0
            GyroBridge.setDisplayRotation(rotation)
            GyroBridge.acquire(HOST_SHELF, ctx)
        }
    }

    /** 摘牌：只交宿主身份串，不触碰 Context / Activity；桥侧未上牌 release 为幂等空操作，可重复调用。 */
    private fun releaseShelfBridge() = GyroBridge.release(HOST_SHELF)

    companion object {
        /** N5 持牌标识：与地图 / 沙盘宿主区分，release 只摘自己的牌，不连带他人。 */
        private const val HOST_SHELF = "shelf"
    }

    private fun openImport() {
        startActivity<ImportBookActivity>()
    }

    private fun deleteBook(ui: ShelfBookUi) {
        val book = books.firstOrNull { it.bookUrl == ui.id } ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            appDb.bookDao.delete(book)
        }
    }
}
