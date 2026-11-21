package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ItemBookshelfList2Binding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.bookshelf.updateBookshelfReadProgress
import io.legado.app.utils.invisible
import io.legado.app.utils.toTimeAgo
import splitties.views.onLongClick

/**
紧凑列表布局
*/
class BooksAdapterList2(
    context: Context,
    private val fragment: Fragment,
    private val callBack: CallBack,
    private val lifecycle: Lifecycle
) : BaseBooksAdapter<ItemBookshelfList2Binding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfList2Binding {
        return ItemBookshelfList2Binding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfList2Binding,
        item: Book,
        payloads: MutableList<Any>
    ) = binding.run {
        if (payloads.isEmpty()) {
            tvName.text = item.name
            tvAuthor.text = authorText(item)
            tvRead.text = readText(item)
            tvLast.text = lastText(item)
            ivCover.load(item, false)
            upRefresh(binding, item)
            upLastUpdateTime(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.text = item.name
                        "author" -> tvAuthor.text = authorText(item)
                        "dur" -> tvRead.text = readText(item)
                        "last" -> tvLast.text = lastText(item)
                        "cover" -> ivCover.load(
                            item,
                            false,
                            fragment,
                            lifecycle
                        )

                        "refresh" -> upRefresh(binding, item)
                        "lastUpdateTime" -> upLastUpdateTime(binding, item)
                    }
                }
            }
        }
    }

    /** 本地古籍没有作者，落"佚名"，别留空行 */
    private fun authorText(item: Book): String =
        item.author.takeIf { it.isNotBlank() } ?: "佚名"

    /** 没读过就写"未读"，不空着 */
    private fun readText(item: Book): String = when {
        item.durChapterTitle.isNotBlank() -> item.durChapterTitle
        item.durChapterIndex > 0 -> "读至第 ${item.durChapterIndex + 1} 章"
        else -> "未读"
    }

    /** 本地书没有 latestChapterTitle，退成总章数 */
    private fun lastText(item: Book): String = when {
        !item.latestChapterTitle.isNullOrBlank() -> item.latestChapterTitle!!
        item.totalChapterNum > 0 -> "共 ${item.totalChapterNum} 章"
        else -> ""
    }

    private fun upRefresh(binding: ItemBookshelfList2Binding, item: Book) {
        binding.pbReadProgress.updateBookshelfReadProgress(item, binding.tvReadPercent)
        if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
            binding.bvUnread.invisible()
            binding.rlLoading.visible()
        } else {
            binding.rlLoading.gone()
            if (AppConfig.showUnread) {
                binding.bvUnread.setHighlight(item.lastCheckCount > 0)
                binding.bvUnread.setBadgeCount(item.getUnreadChapterNum())
            } else {
                binding.bvUnread.invisible()
            }
        }
    }

    private fun upLastUpdateTime(binding: ItemBookshelfList2Binding, item: Book) {
        if (AppConfig.showLastUpdateTime && !item.isLocal) {
            val time = item.latestChapterTime.toTimeAgo()
            if (binding.tvLastUpdateTime.text != time) {
                binding.tvLastUpdateTime.text = time
            }
        } else {
            binding.tvLastUpdateTime.text = ""
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfList2Binding) {
        holder.itemView.apply {
            setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.open(it)
                }
            }

            onLongClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.openBookInfo(it)
                }
            }
        }
    }
}
