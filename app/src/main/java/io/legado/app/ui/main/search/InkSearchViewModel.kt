package io.legado.app.ui.main.search

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.SearchModel
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * 落文 · 搜索页数据主控（Compose 状态提升到这里）
 *
 * 职责：把 Legado 既有远程书源检索栈 [SearchModel] 与本地书架 / 历史关键词仓储接到
 * 纯 UI 状态 [SearchUiState] 上；本类只做编排与字段搬运，排版 / 高亮 / 动效归 Compose 层。
 *
 * 边界兜底（不空白、不崩溃）：
 * - 离线：仍检索本地书架，回填离线态缓存，可重试；
 * - 检索失败 / 取消：保留上一次结果作缓存，给可重试入口；
 * - 仓储异常：记日志并回落空列表，不把异常抛到 UI；
 * - 超长查询：检索 / 入库前按 [SearchTokens.QUERY_MAX_LEN] 截断，不让退化输入进 LIKE 与高亮。
 *
 * @see SearchReducer 纯状态归约；本类只把外部事件翻译成 [SearchIntent] 并执行数据读写。
 */
class InkSearchViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private lateinit var searchScope: SearchScope
    private var searchModel: SearchModel? = null

    /**
     * 历史 / 热门双路流接线器；持有引用是为了浮层关闭时能单独取消两路收集协程
     * （[detachOverlay]），而不必取消整个 viewModelScope。重开浮层时若已置空则重建。
     */
    private var hotBinder: SearchHotBinder? = null
    private var searchKey: String = ""
    private var searchID = 0L
    private val commandGate = SearchCommandGate()

    /** 翻页编排：闸门 / 去抖账 / 调用决策的纯 JVM 协作者，与单测共用同一实现。 */
    private val pagingOrchestrator = SearchPagingOrchestrator(commandGate)

    /**
     * 入口接线：Intent 透传的初始关键词 / 搜索范围在此落位；重复进入（onNewIntent 语义）
     * 只更新范围与初始词，不重建模型。
     */
    fun attach(initialKey: String?, initialScope: String?) {
        if (!this::searchScope.isInitialized) {
            searchScope = SearchScope(initialScope ?: AppConfig.searchScope)
            searchModel = SearchModel(viewModelScope, searchModelCallback)
            observeHistory()
        } else {
            // 重开浮层：上一次关闭走了 [detachOverlay]，双路收集已取消、检索模型已暂停；
            // 这里重建 binder 重新收集，并恢复模型，与既有 pause/resume 语义对齐。
            if (hotBinder == null) observeHistory()
            searchModel?.resume()
            if (!initialScope.isNullOrBlank()) {
                searchScope.update(initialScope, postValue = false, save = false)
            }
        }

        val key = capQuery(SearchMatcher.normalize(initialKey ?: ""))
        if (key.isNotEmpty()) {
            dispatch(SearchIntent.QueryChange(key))
            submit()
        }
    }

    /**
     * 浮层关闭释放收集（验收 D3 收口）：取消历史 / 热门双路 DAO 流收集协程，
     * 并暂停检索模型（与 [pause] 同一出口，不销毁模型本身）。VM 作用域仍存活，
     * 缓存的 [_uiState] 保留，重开时 [attach] 幂等重建收集并 resume 模型。
     *
     * 为什么只暂停而不 close 模型：模型内部持有书源分页状态，关闭浮层属临时离场，
     * 重开仍是同一次书架会话；彻底销毁归 [onCleared]。
     */
    fun detachOverlay() {
        hotBinder?.close()
        hotBinder = null
        searchModel?.pause()
    }

    // ─────────────────────────── 事件入口 ───────────────────────────

    fun dispatch(intent: SearchIntent) {
        _uiState.update { SearchReducer.reduce(it, intent) }
    }

    /** 提交检索（回车 / 点词条）；空词不检索、不写历史。 */
    fun submit() {
        dispatch(SearchIntent.Submit)
        val key = capQuery(SearchMatcher.normalize(_uiState.value.query))
        if (key.isNotEmpty()) runSearch(key)
    }

    /** 点选历史词条：回填关键词并直接检索。 */
    fun pickHistory(key: String) {
        dispatch(SearchIntent.PickHistory(key))
        val normalized = capQuery(SearchMatcher.normalize(key))
        if (normalized.isNotEmpty()) runSearch(normalized)
    }

    fun removeHistory(key: String) {
        dispatch(SearchIntent.RemoveHistory(key))
        execute {
            runCatching { appDb.searchKeywordDao.delete(SearchKeyword(key)) }
                .onFailure { AppLog.put("删除搜索历史失败\n${it.localizedMessage}", it) }
        }
    }

    fun clearHistory() {
        dispatch(SearchIntent.ClearHistory)
        execute {
            runCatching { appDb.searchKeywordDao.deleteAll() }
                .onFailure { AppLog.put("清空搜索历史失败\n${it.localizedMessage}", it) }
        }
    }

    /** 离线 / 失败态重试；空词回落空闲态。 */
    fun retry() {
        dispatch(SearchIntent.Retry)
        val key = capQuery(SearchMatcher.normalize(_uiState.value.query))
        if (key.isNotEmpty()) runSearch(key)
    }

    /**
     * 滚动到底触发下一页。守卫 / 去抖 / 归约二次闸门由 [SearchPagingOrchestrator] 统一收口：
     * 1. [SearchUiState.canLoadMore]：有内容、远端还有更多、无在途翻页（同页并发保护）；
     * 2. [SearchTokens.LOAD_MORE_DEBOUNCE_MS] 去抖窗口：连发只取首次；
     * 3. 协程体内再次复核命令序号 / 在途态 / 网络 / 关键词，离线与空词回落分页错态而不是卡死。
     */
    fun loadMore() {
        pagingOrchestrator.startAuto(
            snapshot = { _uiState.value },
            now = System.currentTimeMillis(),
            dispatch = ::dispatch,
            onSearch = ::launchPageSearch,
        )
    }

    /** 分页失败脚注重试；仅分页错态可调，绕开自动到底拦阻但仍过归约器闸门（D1-R3）。 */
    fun retryLoadMore() {
        pagingOrchestrator.startRetry(
            snapshot = { _uiState.value },
            now = System.currentTimeMillis(),
            dispatch = ::dispatch,
            onSearch = ::launchPageSearch,
        )
    }

    /**
     * 翻页协程体：编排器放行后在此复核命令序号与在途态（D1-R2），再做模型 / 关键词 /
     * 网络兜底；复核不通过直接退出，旧翻页体不得用新检索字段再调模型。
     */
    private fun launchPageSearch(command: Long) {
        execute {
            if (!pagingOrchestrator.bodyStillEligible(command, _uiState.value)) return@execute
            val model = searchModel
            if (model == null || searchKey.isBlank()) {
                dispatch(SearchIntent.LoadMoreFailure(SearchTokens.ERROR_MESSAGE))
                return@execute
            }
            if (!NetworkUtils.isAvailable()) {
                dispatch(SearchIntent.LoadMoreFailure(SearchTokens.OFFLINE_MESSAGE))
                return@execute
            }
            // 复核通过后再取字段快照，避免挂起期间被新检索覆盖。
            val id = searchID
            val key = searchKey
            // 同 id 复调：SearchModel 内部识别为同词续页，searchPage++ 取下一页。
            model.search(id, key)
        }
    }

    fun pause() {
        searchModel?.pause()
    }

    fun resume() {
        searchModel?.resume()
    }

    override fun onCleared() {
        super.onCleared()
        commandGate.invalidate { searchModel?.close() }
    }

    // ─────────────────────────── 检索编排 ───────────────────────────

    private fun runSearch(key: String) {
        val command = commandGate.next()
        execute {
            // 闸门比对在协程体内完成：编排体含挂起的本地书架检索 [queryLocal]，
            // 不能塞进闸门那个非挂起的 `() -> Unit` lambda，否则编译期报
            // “Suspension functions can only be called within coroutine body”。
            if (!commandGate.isCurrent(command)) return@execute
            searchModel?.cancelSearch()
            searchID = command
            searchKey = key
            // 新检索作废在途翻页命令（commandGate.next 已递增序号），清翻页去抖账（D1-R2）。
            pagingOrchestrator.onNewSearch()

            if (!NetworkUtils.isAvailable()) {
                // 离线兜底：本地书架照搜，结果作离线缓存展示，不阻断入口。
                val local = queryLocal(key)
                _uiState.update { it.copy(lastResults = local) }
                dispatch(SearchIntent.Offline)
                return@execute
            }

            saveSearchKey(key)
            searchModel?.search(searchID, key)
        }
    }

    private val searchModelCallback = object : SearchModel.CallBack {
        override fun getSearchScope(): SearchScope = searchScope

        override fun onSearchStart() {
            // 翻页在途时整页保持现有列表，不切全屏 Loading；仅首屏检索才切整页加载态。
            if (_uiState.value.paging is SearchPagingState.LoadingMore) return
            dispatch(SearchIntent.Loading)
        }

        override fun onSearchProgress(searched: Int, total: Int) {
            // 新搜索页只用三点加载指示，不落进度条；进度回调保留接口兼容。
        }

        override fun onSearchSuccess(searchBooks: List<SearchBook>) {
            val remote = searchBooks.map { it.toSearchItem() }
            execute {
                if (_uiState.value.paging is SearchPagingState.LoadingMore) {
                    // 翻页回填：多批 onSearchSuccess 都落在在途态（归约器保留在途到 Finish），
                    // 按 id / 同名同作者轻量去重后追加；本地书架前置与排序合并归 D5。
                    dispatch(SearchIntent.LoadMoreAppend(remote, hasMore = _uiState.value.hasMore))
                } else {
                    val local = queryLocal(searchKey)
                    val merged = SearchMerger.merge(local, remote)
                    dispatch(SearchIntent.ResultsReady(merged))
                }
            }
        }

        override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
            if (_uiState.value.paging is SearchPagingState.LoadingMore) {
                // 翻页收口：按远端标记回到空闲可翻页，或终态不再请求。
                dispatch(SearchIntent.LoadMoreFinish(hasMore))
                return
            }
            // 首屏空态收口。
            if (isEmpty && _uiState.value.loadState is SearchLoadState.Loading) {
                dispatch(SearchIntent.ResultsReady(emptyList()))
            }
        }

        override fun onSearchCancel(exception: Throwable?) {
            exception?.let { context.toastOnUi(it.localizedMessage) }
            if (_uiState.value.paging is SearchPagingState.LoadingMore) {
                // 翻页失败：保留现有列表，脚注给可重试入口，不切整页错态。
                dispatch(SearchIntent.LoadMoreFailure(exception?.localizedMessage ?: ""))
            } else {
                dispatch(SearchIntent.Failure(exception?.localizedMessage ?: ""))
            }
        }
    }

    /** 本地书架按书名 / 作者模糊匹配；仓储异常回落空列表。 */
    private suspend fun queryLocal(key: String): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            runCatching { appDb.bookDao.flowSearch(key).first() }
                .getOrDefault(emptyList())
                .map { it.toSearchItem() }
        }

    /**
     * 热门词双路流接线：构造 [SearchHotBinder] 并启动两路收集；首次初始化分支、
     * 或 [detachOverlay] 后重开浮层的幂等分支调用，不重复订阅。
     *
     * 生命周期与持有说明（验收 N1 / D3 收口）：两路收集协程挂在 [viewModelScope] 上，
     * `onCleared()` 取消作用域时子协程随之取消；浮层关闭则走 [detachOverlay] 单独
     * 取消 binder 内两路协程。binder 引用保留在 [hotBinder] 字段，唯一用途是关闭时
     * 取得到句柄做定向取消；热门档仍由 binder 内部收集循环经 `publishHot` 原子回填
     * 到 [_uiState]，VM 不回读 binder 内部状态。
     */
    private fun observeHistory() {
        hotBinder = SearchHotBinder(
            uiState = _uiState,
            timeFlow = appDb.searchKeywordDao.flowByTime(),
            usageFlow = appDb.searchKeywordDao.flowByUsage(),
            scope = viewModelScope,
        ).also { it.start() }
    }

    private fun saveSearchKey(key: String) {
        execute {
            runCatching {
                appDb.searchKeywordDao.get(key)?.let {
                    it.usage += 1
                    it.lastUseTime = System.currentTimeMillis()
                    appDb.searchKeywordDao.update(it)
                } ?: appDb.searchKeywordDao.insert(SearchKeyword(key, 1))
            }.onFailure { AppLog.put("保存搜索关键词失败\n${it.localizedMessage}", it) }
        }
    }

    /** 查询截断：归一化后超长只取前 [SearchTokens.QUERY_MAX_LEN] 个字符，空串原样回落。 */
    private fun capQuery(normalized: String): String =
        if (normalized.length > SearchTokens.QUERY_MAX_LEN) {
            normalized.substring(0, SearchTokens.QUERY_MAX_LEN)
        } else {
            normalized
        }
}

/**
 * 检索命令闸门：旧命令被新命令 / 停止操作作废后不得再写结果。
 * 纯 JVM 可单测（见 SearchCommandGateTest）。
 */
internal class SearchCommandGate {
    private val sequence = AtomicLong()
    private val lock = Any()

    fun next(): Long = sequence.incrementAndGet()

    /**
     * 仅比对命令是否仍为当前有效命令、不执行体；供协程体内的挂起编排前做闸门判定。
     * 与 [runIfCurrent] 共用同一把锁与同一个序号，作废语义一致。
     */
    fun isCurrent(command: Long): Boolean = synchronized(lock) {
        command == sequence.get()
    }

    fun runIfCurrent(command: Long, block: () -> Unit): Boolean = synchronized(lock) {
        if (command != sequence.get()) return false
        block()
        true
    }

    fun invalidate(block: () -> Unit) {
        sequence.incrementAndGet()
        synchronized(lock) { block() }
    }
}
