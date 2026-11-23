package io.legado.app.ui.book.source.manage

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.toBookSource
import io.legado.app.help.SourceSharePassphrase
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.SourceHelp
import io.legado.app.ui.components.ToastKind
import io.legado.app.ui.components.ToastMessage
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.cnCompare
import io.legado.app.utils.moveRelativeTo
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.outputStream
import io.legado.app.utils.renameGroupExact
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * 落文 · 书源管理数据主控。
 *
 * 职责边界（对齐总纲「只留排版 / 数据内核，界面重写」）：
 * - 复用：Room DAO、[SourceHelp] 删源、偏好存取、书架引用数统计、导出序列化；
 * - 新写：UI 状态归约、多选集合、筛选 / 排序 / 归组的纯数据变换、错误与空态兜底。
 *
 * 本类不持有任何 View / Adapter / Binding；所有页面状态经 [uiState] 单向流出。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceManageViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(SourceManageUiState())
    val uiState: StateFlow<SourceManageUiState> = _uiState.asStateFlow()

    private val hostMap = HashMap<String, String>()
    private val importRecordKey = "bookSourceRecordKey"

    init {
        // 偏好回填（旧页启动时读取的两个开关）
        _uiState.update {
            it.copy(
                showCheckStatus = AppConfig.showSourceCheckStatus,
                showBookshelfCount = AppConfig.showSourceBookshelfCount,
            )
        }
        bindGroups()
        bindBookshelfCounts()
        bindSources()
    }

    // ───────────────────────────── 数据流 ─────────────────────────────

    private fun bindSources() {
        combine(
            sourceFlow(),
            _uiState.map { it.checkStatusFilter }.distinctUntilChanged(),
            _uiState.map { it.sort to it.ascending to it.groupByDomain }.distinctUntilChanged(),
        ) { sources, filterIndex, sortPack ->
            val (sortAscPack, groupByDomain) = sortPack
            val (sort, ascending) = sortAscPack
            val state = _uiState.value
            val stateFilter = if (state.showCheckStatus) checkStatusFilters[filterIndex] else ""
            val filtered = sources.filter { stateFilter.isEmpty() || it.checkStatus == stateFilter }
            sortSources(filtered, sort, ascending, groupByDomain)
        }.flowOn(Dispatchers.IO)
            .catch { AppLogPut("书源列表刷新出错", it) }
            .conflate()
            .onEach { items ->
                _uiState.update { it.copy(items = items, loading = false, error = null) }
            }
            .launchIn(viewModelScope)
    }

    private fun sourceFlow() = combine(
        _uiState.map { it.preset to it.groupName to it.query }.distinctUntilChanged(),
        _uiState.map { it.showCheckStatus }.distinctUntilChanged(),
    ) { pack, _ ->
        val (presetPack, query) = pack
        val (preset, groupName) = presetPack
        when (preset) {
            SourcePreset.ALL -> {
                if (query.isNullOrEmpty()) appDb.bookSourceDao.flowAll()
                else appDb.bookSourceDao.flowSearchWithCheckDetail(query)
            }
            SourcePreset.ENABLED -> appDb.bookSourceDao.flowEnabled()
            SourcePreset.DISABLED -> appDb.bookSourceDao.flowDisabled()
            SourcePreset.LOGIN -> appDb.bookSourceDao.flowLogin()
            SourcePreset.NO_GROUP -> appDb.bookSourceDao.flowNoGroup()
            SourcePreset.EXPLORE_ENABLED -> appDb.bookSourceDao.flowEnabledExplore()
            SourcePreset.EXPLORE_DISABLED -> appDb.bookSourceDao.flowDisabledExplore()
            SourcePreset.GROUP -> appDb.bookSourceDao.flowGroupSearch(groupName ?: "")
        }
    }.flowOn(Dispatchers.IO)
        .catch { AppLogPut("书源查询出错", it) }
        .conflate()
        .flatMapLatest { it }

    private fun bindGroups() {
        appDb.bookSourceDao.flowGroups()
            .flowOn(Dispatchers.IO)
            .conflate()
            .distinctUntilChanged()
            .catch { AppLogPut("分组刷新出错", it) }
            .onEach { groups ->
                _uiState.update { state ->
                    // 分组被删空后，旧筛选分组若已不存在则回落全部（旧页 isMissingBookSourceGroupFilter 兜底）
                    val preset = if (state.preset == SourcePreset.GROUP &&
                        state.groupName != null && state.groupName !in groups
                    ) SourcePreset.ALL else state.preset
                    state.copy(groups = groups, preset = preset, groupName = state.groupName)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun bindBookshelfCounts() {
        appDb.bookDao.flowBookshelfSourceOrigins()
            .map { origins -> origins.groupingBy { it }.eachCount() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .catch { AppLogPut("书架引用数刷新出错", it) }
            .onEach { counts -> _uiState.update { it.copy(bookshelfCounts = counts) } }
            .launchIn(viewModelScope)
    }

    private fun sortSources(
        data: List<BookSourcePart>,
        sort: BookSourceSort,
        ascending: Boolean,
        groupByDomain: Boolean,
    ): List<BookSourcePart> {
        if (groupByDomain) {
            return data.sortedWith(
                compareBy<BookSourcePart> { getSourceHost(it.bookSourceUrl) == "#" }
                    .thenBy { getSourceHost(it.bookSourceUrl) }
                    .thenByDescending { it.lastUpdateTime },
            )
        }
        return when (sort) {
            BookSourceSort.Default -> if (ascending) data else data.reversed()
            BookSourceSort.Weight -> if (ascending) data.sortedBy { it.weight } else data.sortedByDescending { it.weight }
            BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                val c = o1.bookSourceName.cnCompare(o2.bookSourceName)
                if (ascending) c else -c
            }
            BookSourceSort.Url -> if (ascending) data.sortedBy { it.bookSourceUrl } else data.sortedByDescending { it.bookSourceUrl }
            BookSourceSort.Update -> if (ascending) data.sortedByDescending { it.lastUpdateTime } else data.sortedBy { it.lastUpdateTime }
            BookSourceSort.Respond -> if (ascending) data.sortedBy { it.respondTime } else data.sortedByDescending { it.respondTime }
            BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                var c = -o1.enabled.compareTo(o2.enabled)
                if (c == 0) c = o1.bookSourceName.cnCompare(o2.bookSourceName)
                if (!ascending) c = -c
                c
            }
        }
    }

    internal fun getSourceHost(origin: String): String = hostMap.getOrPut(origin) {
        NetworkUtils.getSubDomainOrNull(origin) ?: "#"
    }

    // ───────────────────────────── 筛选 / 排序交互 ─────────────────────────────

    fun onQueryChange(text: String) {
        _uiState.update {
            it.copy(query = text, preset = SourcePreset.ALL, groupName = null)
        }
    }

    fun selectPreset(preset: SourcePreset, groupName: String? = null) {
        _uiState.update { it.copy(preset = preset, groupName = groupName, query = "") }
    }

    fun setSort(sort: BookSourceSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun toggleAscending() {
        _uiState.update { it.copy(ascending = !it.ascending) }
    }

    fun toggleGroupByDomain() {
        _uiState.update { it.copy(groupByDomain = !it.groupByDomain) }
    }

    fun setCheckStatusFilter(index: Int) {
        _uiState.update { it.copy(checkStatusFilter = index.coerceIn(0, checkStatusFilters.lastIndex)) }
    }

    fun toggleShowCheckStatus() {
        val next = !_uiState.value.showCheckStatus
        AppConfig.showSourceCheckStatus = next
        _uiState.update {
            it.copy(
                showCheckStatus = next,
                checkStatusFilter = if (next) it.checkStatusFilter else 0,
            )
        }
    }

    fun toggleShowBookshelfCount() {
        val next = !_uiState.value.showBookshelfCount
        AppConfig.showSourceBookshelfCount = next
        _uiState.update { it.copy(showBookshelfCount = next) }
    }

    fun toggleBlockSourceNavigation() {
        AppConfig.blockSourceNavigation = !AppConfig.blockSourceNavigation
    }

    // ───────────────────────────── 多选 ─────────────────────────────

    fun toggleSelect(sourceUrl: String) {
        _uiState.update { state ->
            val selected = state.selected.toMutableSet()
            if (selected.contains(sourceUrl)) selected.remove(sourceUrl) else selected.add(sourceUrl)
            state.copy(selected = selected)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selected = it.items.map { item -> item.bookSourceUrl }.toSet()) }
    }

    fun revertSelection() {
        _uiState.update { state ->
            val selected = state.selected.toMutableSet()
            state.items.forEach { item ->
                if (selected.contains(item.bookSourceUrl)) selected.remove(item.bookSourceUrl)
                else selected.add(item.bookSourceUrl)
            }
            state.copy(selected = selected)
        }
    }

    /** 选中区间全选（旧 checkSelectedInterval：最小下标到最大下标之间全部勾上）。 */
    fun selectCheckedInterval() {
        val state = _uiState.value
        val positions = state.items.mapIndexedNotNull { index, item ->
            index.takeIf { item.bookSourceUrl in state.selected }
        }
        if (positions.isEmpty()) return
        val min = positions.min()
        val max = positions.max()
        _uiState.update {
            val selected = it.selected.toMutableSet()
            for (i in min..max) it.items.getOrNull(i)?.let { s -> selected.add(s.bookSourceUrl) }
            it.copy(selected = selected)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selected = emptySet()) }
    }

    // ───────────────────────────── 单条 / 批量数据操作 ─────────────────────────────

    fun enable(enable: Boolean, source: BookSourcePart) {
        execute { appDb.bookSourceDao.enable(enable, listOf(source)) }
    }

    fun enableExplore(enable: Boolean, source: BookSourcePart) {
        execute { appDb.bookSourceDao.enableExplore(enable, listOf(source)) }
    }

    fun enableSelection() = execute { appDb.bookSourceDao.enable(true, _uiState.value.selection) }
    fun disableSelection() = execute { appDb.bookSourceDao.enable(false, _uiState.value.selection) }
    fun enableSelectExplore() = execute { appDb.bookSourceDao.enableExplore(true, _uiState.value.selection) }
    fun disableSelectExplore() = execute { appDb.bookSourceDao.enableExplore(false, _uiState.value.selection) }

    fun delete(selection: List<BookSourcePart>) {
        execute { SourceHelp.deleteBookSourceParts(selection) }
        _uiState.update { state ->
            val removed = selection.map { it.bookSourceUrl }.toSet()
            state.copy(selected = state.selected - removed)
        }
    }

    fun topSource(sources: List<BookSourcePart>) {
        execute {
            sources.sortedBy { it.customOrder }
            val minOrder = appDb.bookSourceDao.minOrder - 1
            val array = sources.mapIndexed { index, it ->
                it.copy(customOrder = minOrder - index)
            }
            appDb.bookSourceDao.upOrder(array)
        }
    }

    fun bottomSource(sources: List<BookSourcePart>) {
        execute {
            sources.sortedBy { it.customOrder }
            val maxOrder = appDb.bookSourceDao.maxOrder + 1
            val array = sources.mapIndexed { index, it ->
                it.copy(customOrder = maxOrder + index)
            }
            appDb.bookSourceDao.upOrder(array)
        }
    }

    fun move(sourceUrl: String, targetUrl: String, after: Boolean, onFinally: () -> Unit = {}) {
        executeLazy {
            appDb.runInTransaction {
                val current = appDb.bookSourceDao.allPart
                val reordered = moveRelativeTo(current, sourceUrl, targetUrl, after) { it.bookSourceUrl }
                if (reordered == current) return@runInTransaction
                appDb.bookSourceDao.upOrder(reordered.mapIndexed { index, source ->
                    source.copy(customOrder = index)
                })
            }
        }.onFinally { onFinally() }.start()
    }

    fun selectionAddToGroups(selection: List<BookSourcePart>, groups: String) {
        execute {
            val array = selection.map { it.copy().apply { addGroup(groups) } }
            appDb.bookSourceDao.upGroup(array)
        }
    }

    fun selectionRemoveFromGroups(selection: List<BookSourcePart>, groups: String) {
        execute {
            val array = selection.map { it.copy().apply { removeGroup(groups) } }
            appDb.bookSourceDao.upGroup(array)
        }
    }

    // ───────────────────────────── 分组管理（旧 GroupManageDialog） ─────────────────────────────

    fun addGroup(group: String) {
        execute {
            val sources = appDb.bookSourceDao.noGroup
            sources.forEach { source -> source.bookSourceGroup = group }
            appDb.bookSourceDao.update(*sources.toTypedArray())
        }
    }

    fun renameGroup(oldGroup: String, newGroup: String?) {
        execute {
            val sources = appDb.bookSourceDao.getByGroup(oldGroup).mapNotNull { source ->
                source.bookSourceGroup.renameGroupExact(oldGroup, newGroup)?.let { groups ->
                    source.apply { bookSourceGroup = groups }
                }
            }
            if (sources.isNotEmpty()) appDb.bookSourceDao.update(*sources.toTypedArray())
        }
    }

    fun deleteGroup(group: String) = renameGroup(group, null)

    // ───────────────────────────── 导出 / 分享 ─────────────────────────────

    /**
     * 把当前选中源序列化为导出文件；JS 源导出 `.js` 主脚本，其余导出 JSON 数组。
     * 成功回调在主线程抛出 (File, 文件名, 是否 JS)；失败以朱砂 Toast 兜底，不静默。
     */
    fun prepareExport(
        selection: List<BookSourcePart>,
        onReady: (file: File, name: String, isJs: Boolean) -> Unit,
    ) {
        execute {
            val sources = selection.toBookSource()
            val single = sources.singleOrNull()
            if (single != null && single.isJsSource()) {
                val outputName = "${single.bookSourceName.normalizeFileName()}.js"
                val file = File(context.cacheDir, outputName)
                file.writeText(single.mainJs.orEmpty())
                Triple(file, outputName, true)
            } else {
                val path = "${context.filesDir}/shareBookSource.json"
                FileUtils.delete(path)
                val file = FileUtils.createFileWithReplace(path)
                file.outputStream().buffered().use { GSON.writeToOutputStream(it, sources) }
                val name = if (sources.size == 1) {
                    "bookSource_${sources.first().bookSourceName.normalizeFileName()}.json"
                } else {
                    val stamp = java.text.SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault())
                        .format(Date())
                    "bookSource_$stamp.json"
                }
                Triple(file, name, false)
            }
        }.onSuccess { (file, name, isJs) -> onReady(file, name, isJs) }
            .onError { context.toastOnUi(it.stackTraceStr) }
    }

    /** 在线导入历史地址（旧 ACache 记录，最近在前，逗号分隔）。 */
    fun cachedImportUrls(): List<String> {
        val cache = ACache.get(cacheDir = false)
        return cache.getAsString(importRecordKey)?.splitNotBlank(",")?.toMutableList() ?: mutableListOf()
    }

    fun rememberImportUrl(url: String) {
        if (!url.isAbsUrlSafe()) return
        val cacheUrls = cachedImportUrls().toMutableList()
        cacheUrls.remove(url)
        cacheUrls.add(0, url)
        ACache.get(cacheDir = false).put(importRecordKey, cacheUrls.joinToString(","))
    }

    fun removeCachedImportUrl(url: String) {
        val cacheUrls = cachedImportUrls().toMutableList()
        cacheUrls.remove(url)
        ACache.get(cacheDir = false).put(importRecordKey, cacheUrls.joinToString(","))
    }

    // ───────────────────────────── 校验会话状态镜像 ─────────────────────────────

    fun markChecking(active: Boolean, message: String? = null, cancelable: Boolean = false) {
        _uiState.update { it.copy(checking = active, checkMessage = message, checkCancelable = cancelable) }
    }

    fun setCheckMessage(message: String?) {
        _uiState.update { it.copy(checkMessage = message) }
    }

    // ───────────────────────────── Toast ─────────────────────────────

    fun postToast(kind: ToastKind, text: String, actionLabel: String? = null) {
        val id = System.nanoTime()
        val factory = when (kind) {
            ToastKind.SUCCESS -> ToastMessage::success
            ToastKind.FAILURE -> ToastMessage::failure
            ToastKind.INFO -> ToastMessage::info
        }
        _uiState.update { it.copy(toasts = it.toasts + factory(id, text, actionLabel)) }
    }

    fun dismissToast(id: Long) {
        _uiState.update { it.copy(toasts = it.toasts.filterNot { t -> t.id == id }) }
    }

    fun openSheet(sheet: ManageSheet) = _uiState.update { it.copy(sheet = sheet) }
    fun closeSheet() = _uiState.update { it.copy(sheet = ManageSheet.None) }

    // ───────────────────────────── 兜底小工具 ─────────────────────────────

    private fun String.isAbsUrlSafe(): Boolean =
        startsWith("http://") || startsWith("https://")

    private fun AppLogPut(tag: String, throwable: Throwable) {
        io.legado.app.constant.AppLog.put(tag, throwable)
    }

    companion object {
        /** 导出后远程直链分享时的口令类型，与旧页一致。 */
        val sharePassphraseType = SourceSharePassphrase.Type.BOOK_SOURCE
    }
}
