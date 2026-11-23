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
import kotlinx.coroutines.flow.catch
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
    private var searchKey: String = ""
    private var searchID = 0L
    private val commandGate = SearchCommandGate()

    /** 上一次翻页触发时间戳（ms）；用于滚动到底连发的去抖窗口判定，0 = 从未触发。 */
    private var lastLoadMoreAt = 0L

    /**
     * 入口接线：Intent 透传的初始关键词 / 搜索范围在此落位；重复进入（onNewIntent 语义）
     * 只更新范围与初始词，不重建模型。
     */
    fun attach(initialKey: String?, initialScope: String?) {
        if (!this::searchScope.isInitialized) {
            searchScope = SearchScope(initialScope ?: AppConfig.searchScope)
            searchModel = SearchModel(viewModelScope, searchModelCallback)
            observeHistory()
        } else if (!initialScope.isNullOrBlank()) {
            searchScope.update(initialScope, postValue = false, save = false)
        }

        val key = capQuery(SearchMatcher.normalize(initialKey ?: ""))
        if (key.isNotEmpty()) {
            dispatch(SearchIntent.QueryChange(key))
            submit()
        }
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
     * 滚动到底触发下一页。三道守卫任一不满足即直接返回，不发请求：
     * 1. [SearchUiState.canLoadMore]：有内容、远端还有更多、无在途翻页（同页并发保护）；
     * 2. [SearchTokens.LOAD_MORE_DEBOUNCE_MS] 去抖窗口：连发只取首次；
     * 3. 协程体内再次比对闸门 / 网络 / 关键词，离线与空词回落分页错态而不是卡死。
     */
    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore) return
        val now = System.currentTimeMillis()
        if (SearchPagingPolicy.withinDebounce(lastLoadMoreAt, now, SearchTokens.LOAD_MORE_DEBOUNCE_MS)) return
        lastLoadMoreAt = now
        dispatch(SearchIntent.LoadMoreStart)
        execute {
            val model = searchModel
            if (model == null || searchKey.isBlank()) {
                dispatch(SearchIntent.LoadMoreFailure(SearchTokens.ERROR_MESSAGE))
                return@execute
            }
            if (!NetworkUtils.isAvailable()) {
                dispatch(SearchIntent.LoadMoreFailure(SearchTokens.OFFLINE_MESSAGE))
                return@execute
            }
            // 同 id 复调：SearchModel 内部识别为同词续页，searchPage++ 取下一页。
            model.search(searchID, searchKey)
        }
    }

    /** 分页失败脚注重试；仅分页错态可调，复用 [loadMore] 的守卫并绕过去抖。 */
    fun retryLoadMore() {
        if (_uiState.value.paging !is SearchPagingState.LoadMoreError) return
        lastLoadMoreAt = 0L
        loadMore()
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
            lastLoadMoreAt = 0L

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
                    // 翻页回填：按 id / 同名同作者轻量去重后追加；本地书架前置与排序合并归 D5。
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

    /** 本机历史词缓存：时间序（历史行）与频次序（热门行）两路流各自持有，任一刷新即重算热门。 */
    private var historyKeys: List<String> = emptyList()
    private var usageKeys: List<String> = emptyList()

    private fun observeHistory() {
        execute {
            appDb.searchKeywordDao.flowByTime()
                .catch { AppLog.put("搜索界面获取搜索历史数据失败\n${it.localizedMessage}", it) }
                .collect { keywords ->
                    historyKeys = SearchHistory.clean(keywords.map { it.word })
                    publishHot(historyKeys) { it.copy(history = historyKeys) }
                }
        }
        execute {
            appDb.searchKeywordDao.flowByUsage()
                .catch { AppLog.put("搜索界面获取高频搜索词失败\n${it.localizedMessage}", it) }
                .collect { keywords ->
                    usageKeys = keywords.map { it.word }
                    publishHot(historyKeys) { it }
                }
        }
    }

    /**
     * 按当前两路本地词表重算热门档并回填状态。
     * [statePatch] 允许历史流同时更新历史行；频次流只刷热门行，不动历史。
     */
    private fun publishHot(
        currentHistory: List<String>,
        statePatch: (SearchUiState) -> SearchUiState,
    ) {
        val hot = SearchHotResolver.resolve(usageKeys, currentHistory)
        _uiState.update { state ->
            statePatch(state).copy(hot = hot.keys, hotSource = hot.source)
        }
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
