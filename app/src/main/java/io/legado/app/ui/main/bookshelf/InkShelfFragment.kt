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
                onOpenBook = ::openBook,
                onImport = ::openImport,
                onDeleteBook = ::deleteBook,
                onRetry = ::reload,
                searchUiState = searchUiState,
                onAttachSearch = { searchViewModel.attach(null, null) },
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
        super.onDestroyView()
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
     * 走书籍详情页，入参键（name / author / bookUrl）与旧独立搜索页 [InkSearchActivity] 一致。
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
    }

    override fun onResume() {
        super.onResume()
        searchViewModel.resume()
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
