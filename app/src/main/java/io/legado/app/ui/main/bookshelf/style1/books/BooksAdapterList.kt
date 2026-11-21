package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ItemBookshelfListBinding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.bookshelf.updateBookshelfReadProgress
import io.legado.app.ui.widget.InkCardMotion
import io.legado.app.utils.invisible
import io.legado.app.utils.toTimeAgo
import splitties.views.onLongClick

class BooksAdapterList(
    context: Context,
    private val fragment: Fragment,
    private val callBack: CallBack,
    private val lifecycle: Lifecycle
) : BaseBooksAdapter<ItemBookshelfListBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfListBinding {
        return ItemBookshelfListBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfListBinding,
        item: Book,
        payloads: MutableList<Any>
    ) = binding.run {
        InkCardMotion.reveal(root, holder.layoutPosition)
        if (payloads.isEmpty()) {
            tvName.text = verticalName(item.name)
            tvAuthor.text = authorText(item)
            tvRead.text = readText(item)
            tvLast.text = lastText(item)
            tvWatermark.text = watermarkText(item)
            ivCover.load(item, false)
            upRefresh(binding, item)
            upLastUpdateTime(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.text = verticalName(item.name)
                        "author" -> tvAuthor.text = authorText(item)
                        "dur" -> tvRead.text = readText(item)
                        "last" -> {
                            tvLast.text = lastText(item)
                            tvWatermark.text = watermarkText(item)
                        }
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

    /** 竖排书名：中文逐字换行，西文保持横排；超过 6 字留省略。 */
    private fun verticalName(name: String): String {
        val t = name.trim()
        if (t.isEmpty()) return t
        if (t.none { it in '\u4e00'..'\u9fff' }) return t
        val max = 4
        val body = if (t.length <= max) t else t.take(max - 1) + "…"
        return body.toCharArray().joinToString("\n")
    }

    private fun authorText(item: Book): String =
        item.author?.takeIf { it.isNotBlank() } ?: "佚名"

    private fun readText(item: Book): String {
        val t = item.durChapterTitle
        return when {
            !t.isNullOrBlank() -> t
            item.durChapterIndex > 0 -> "读至第 ${item.durChapterIndex + 1} 章"
            else -> "未读"
        }
    }

    /** 右缘竖排淡墨水印：进度百分比或未读。 */
    private fun watermarkText(item: Book): String {
        val total = item.totalChapterNum
        val raw = when {
            item.durChapterIndex <= 0 && item.durChapterTitle.isNullOrBlank() -> "未读"
            total > 0 -> {
                val p = ((item.durChapterIndex + 1).toFloat() / total * 100).toInt().coerceIn(1, 100)
                "$p%"
            }
            else -> "在\n读"
        }
        return raw.toCharArray().joinToString("\n")
    }

    private fun lastText(item: Book): String = when {
        !item.latestChapterTitle.isNullOrBlank() -> item.latestChapterTitle!!
        item.totalChapterNum > 0 -> "共 ${item.totalChapterNum} 章"
        else -> ""
    }

    private fun upRefresh(binding: ItemBookshelfListBinding, item: Book) {
        binding.pbReadProgress.updateBookshelfReadProgress(item, binding.tvReadPercent)
        binding.inkProgressLine.ratio = when {
            item.totalChapterNum > 0 ->
                (item.durChapterIndex + 1).toFloat() / item.totalChapterNum
            else -> 0f
        }
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

    private fun upLastUpdateTime(binding: ItemBookshelfListBinding, item: Book) {
        if (AppConfig.showLastUpdateTime && !item.isLocal) {
            val time = item.latestChapterTime.toTimeAgo()
            if (binding.tvLastUpdateTime.text != time) {
                binding.tvLastUpdateTime.text = time
            }
        } else {
            binding.tvLastUpdateTime.text = ""
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfListBinding) {
        holder.itemView.apply {
            setOnTouchListener { v, event ->
                InkCardMotion.touch(v, event)
                false
            }
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
