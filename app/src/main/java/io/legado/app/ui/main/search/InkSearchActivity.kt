package io.legado.app.ui.main.search

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.startActivity

/**
 * 落文 · 搜索页壳宿主（切片-13，Compose 自绘）
 *
 * 本类只做生命周期、返回与 Intent 传参；检索编排归 [InkSearchViewModel]，
 * 排版 / 高亮 / 动效归 [InkSearchScreen]。不引旧 XML 搜索布局、不挂 Material 组件。
 *
 * 入参语义与旧搜索页逐点对齐：
 * - [EXTRA_KEY] 初始搜索词（可空：空则落历史空闲面板）；
 * - [EXTRA_SCOPE] 搜索范围（单书源 `名称::url` 或分组逗号串；可空 = 全部启用书源）。
 */
class InkSearchActivity : ComponentActivity() {

    private val viewModel: InkSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.attach(
            initialKey = intent.getStringExtra(EXTRA_KEY),
            initialScope = intent.getStringExtra(EXTRA_SCOPE),
        )
        setContent {
            val state by viewModel.uiState.collectAsState()
            InkSearchScreen(
                state = state,
                onQueryChange = { viewModel.dispatch(SearchIntent.QueryChange(it)) },
                onSubmit = { viewModel.submit() },
                onFocusChange = {
                    viewModel.dispatch(
                        if (it) SearchIntent.FocusGained else SearchIntent.FocusLost,
                    )
                },
                onClearQuery = { viewModel.dispatch(SearchIntent.ClearQuery) },
                onPickHistory = { viewModel.pickHistory(it) },
                onRemoveHistory = { viewModel.removeHistory(it) },
                onClearHistory = { viewModel.clearHistory() },
                onItemClick = { openBookInfo(it) },
                onRetry = { viewModel.retry() },
                onBack = { finish() },
            )
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resume()
    }

    /** 点开一条结果：书名 / 作者 / bookUrl 透传书籍详情，参数键与旧搜索页一致。 */
    private fun openBookInfo(item: SearchResultItem) {
        startActivity<BookInfoActivity> {
            putExtra("name", item.title)
            putExtra("author", item.author)
            putExtra("bookUrl", item.id)
        }
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_SCOPE = "searchScope"

        /** 通用入口：关键词 + 可选搜索范围（分组串 / 单书源串）。 */
        fun start(context: Context, key: String?, searchScope: String? = null) {
            context.startActivity<InkSearchActivity> {
                putExtra(EXTRA_KEY, key)
                putExtra(EXTRA_SCOPE, searchScope)
            }
        }

        /** 单书源入口：以 [source] 构造范围串，关键词可空。 */
        fun start(context: Context, source: BookSource, key: String? = null) {
            context.startActivity<InkSearchActivity> {
                putExtra(EXTRA_KEY, key)
                putExtra(EXTRA_SCOPE, SearchScope(source).toString())
            }
        }

        /** 单书源入口（书源分部实体）：参数语义同上。 */
        fun start(context: Context, source: BookSourcePart, key: String? = null) {
            context.startActivity<InkSearchActivity> {
                putExtra(EXTRA_KEY, key)
                putExtra(EXTRA_SCOPE, SearchScope(source).toString())
            }
        }
    }
}
