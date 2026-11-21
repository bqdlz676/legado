package io.legado.app.ui.main.bookshelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.databinding.FragmentInkShelfBinding
import io.legado.app.databinding.ItemInkBookBinding
import io.legado.app.constant.AppLog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.PlaceMapActivity
import io.legado.app.ui.widget.inkPress
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 落文 · 书架（从零）
 *
 * 只借用排版引擎那一侧的书籍数据与阅读跳转，界面、列表、卡片、空态全部自己画。
 */
class InkShelfFragment() : BaseFragment(R.layout.fragment_ink_shelf), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentInkShelfBinding::bind)
    private val ctx get() = requireContext()
    private var flowJob: Job? = null
    private val adapter = BookAdapter()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvSearch.setOnClickListener { SearchActivity.start(ctx, "") }
        binding.tvMap.setOnClickListener {
            startActivity(android.content.Intent(ctx, PlaceMapActivity::class.java).putExtra("name", "长安").putExtra("year", -202))
        }
        binding.rvBooks.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.rvBooks.adapter = adapter
        observeBooks()
    }

    private fun observeBooks() {
        flowJob?.cancel()
        flowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowRoot()
                .map { list -> list.sortedByDescending { it.durChapterTime } }
                .flowWithLifecycleAndDatabaseChangeFirst(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_TABLE_NAME
                )
                .catch { AppLog.put("落文书架读取失败", it) }
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { list ->
                    adapter.items = list
                    adapter.notifyDataSetChanged()
                    binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    delay(60)
                }
        }
    }

    private inner class BookAdapter : RecyclerView.Adapter<BookAdapter.H>() {

        var items: List<Book> = emptyList()

        inner class H(val b: ItemInkBookBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
            val v = LayoutInflater.from(ctx).inflate(R.layout.item_ink_book, parent, false)
            return H(ItemInkBookBinding.bind(v))
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: H, position: Int) {
            val book = items[position]
            val b = holder.b
            b.tvName.text = verticalName(book.name)
            b.tvAuthor.text = book.author.ifEmpty { "佚名" }
            val total = book.totalChapterNum
            val progress = if (total > 0) {
                ((book.durChapterIndex + 1).toFloat() / total).coerceIn(0f, 1f)
            } else 0f
            b.ivProgress.ratio = progress
            b.tvPercent.text = if (progress <= 0f) "未读" else "${(progress * 100).toInt()}%"
            b.root.inkPress(radiusDp = 6f)
            b.root.setOnClickListener { startActivityForBook(book) }
            b.root.setOnLongClickListener {
                startActivity(android.content.Intent(ctx, BookInfoActivity::class.java)
                    .putExtra("name", book.name)
                    .putExtra("author", book.author))
                true
            }
            io.legado.app.ui.widget.InkCardMotion.reveal(b.root, position)
        }
    }

    private fun verticalName(name: String): String {
        val chars = name.toCharArray()
        return if (chars.size <= 6) chars.joinToString("\n")
        else chars.take(5).joinToString("\n") + "\n…"
    }
}
