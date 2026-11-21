package io.legado.app.ui.main.rss

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.FragmentRssInkBinding
import io.legado.app.databinding.ItemRssInkBinding
import io.legado.app.help.AppLog
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.rss.favorites.RssFavoritesActivity
import io.legado.app.ui.rss.source.RssSourceActivity
import io.legado.app.ui.rss.sort.RssSortActivity
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 落文 · 订阅
 *
 * 横排列表：图标、名字、分组；搜索框走同一条 flow。整页自绘，不用 Legado 的网格与卡片。
 */
class RssInkFragment() : VMBaseFragment<RssViewModel>(R.layout.fragment_rss_ink),
    MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentRssInkBinding::bind)
    override val viewModel by viewModels<RssViewModel>()
    private val adapter = InkRssAdapter()
    private var job: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.tvConfig.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), RssSourceActivity::class.java))
        }
        binding.tvStar.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), RssFavoritesActivity::class.java))
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = load(s?.toString())
        })
        load(null)
    }

    private fun load(key: String?) {
        job?.cancel()
        job = viewLifecycleOwner.lifecycleScope.launch {
            val flow = when {
                key.isNullOrEmpty() -> appDb.rssSourceDao.flowEnabled()
                key.startsWith("group:") ->
                    appDb.rssSourceDao.flowEnabledByGroup(key.substringAfter("group:"))
                else -> appDb.rssSourceDao.flowEnabled(key)
            }
            flow.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.RSS_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("订阅界面更新数据出错", it)
            }.flowOn(IO).conflate().collect { list ->
                adapter.items = list
                adapter.notifyDataSetChanged()
                binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                binding.tvEmptyMsg.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openRss(s: RssSource) {
        if (s.singleUrl) {
            viewModel.getSingleUrl(s) { url ->
                if (url.startsWith("http", true)) {
                    ReadRssActivity.start(
                        requireContext(), true, s.sourceUrl, s.sourceName, url
                    )
                } else context?.let { io.legado.app.utils.openUrl(it, url) }
            }
        } else {
            viewModel.launchRssWithHtml(s, {
                startActivity(android.content.Intent(requireContext(), RssSortActivity::class.java)
                    .putExtra("sourceUrl", s.sourceUrl))
            }) { html ->
                ReadRssActivity.start(
                    requireContext(), true, s.sourceUrl, s.sourceName, startHtml = html
                )
            }
        }
    }

    private inner class InkRssAdapter : RecyclerView.Adapter<InkRssAdapter.H>() {

        var items: List<RssSource> = emptyList()

        inner class H(val b: ItemRssInkBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H =
            H(ItemRssInkBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: H, position: Int) {
            val s = items[position]
            holder.b.tvName.text = s.sourceName
            val g = s.sourceGroup
            holder.b.tvGroup.text = if (g.isNullOrBlank()) s.sourceUrl else "$g · ${s.sourceUrl}"
            holder.b.root.setOnClickListener { openRss(s) }
        }

        override fun getItemCount(): Int = items.size
    }
}
