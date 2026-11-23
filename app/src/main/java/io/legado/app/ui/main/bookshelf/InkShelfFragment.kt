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
