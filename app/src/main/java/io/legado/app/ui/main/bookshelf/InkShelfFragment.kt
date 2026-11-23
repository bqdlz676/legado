package io.legado.app.ui.main.bookshelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.main.MainFragmentInterface
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

    /** 回顶信号计数：每次 [gotoTop] 自增，画面层观察到变化即滚回首项。 */
    private var scrollTopSignal by mutableStateOf(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            BookshelfScreen(
                state = state,
                books = uiBooks,
                newBookCount = uiBooks.count { it.isNew },
                scrollTopSignal = scrollTopSignal,
                onOpenBook = ::openBook,
                onImport = ::openImport,
                onDeleteBook = ::deleteBook,
                onRetry = ::reload,
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
