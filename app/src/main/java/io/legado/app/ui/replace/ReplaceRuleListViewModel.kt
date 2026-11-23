package io.legado.app.ui.replace

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReplacePreviewConfig
import io.legado.app.utils.moveRelativeTo
import io.legado.app.utils.renameGroupExact
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 落文 · 替换规则列表主控。
 *
 * 订阅 Room Flow 按筛选口径分流；写操作复制实体后落库（旧口径：直接改实体界面不刷新）。
 * 兜底：读取异常保留文案 + 重试；空集走空态；选择集随数据回收。
 */
class ReplaceRuleListViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(ReplaceRuleListState())
    val uiState: kotlinx.coroutines.flow.StateFlow<ReplaceRuleListState> = _uiState.asStateFlow()

    init {
        observeGroups()
        observeRules()
        _uiState.update { it.copy(manualReplace = AppConfig.manualReplaceRule) }
    }

    private fun currentFilter(query: String): ReplaceFilter {
        val enabled = context.getString(io.legado.app.R.string.enabled)
        val disabled = context.getString(io.legado.app.R.string.disabled)
        val noGroup = context.getString(io.legado.app.R.string.no_group)
        return when {
            query.isEmpty() -> ReplaceFilter.All
            query == enabled -> ReplaceFilter.Enabled
            query == disabled -> ReplaceFilter.Disabled
            query == noGroup -> ReplaceFilter.NoGroup
            query.startsWith("group:") -> ReplaceFilter.Group(query.substringAfter("group:"))
            else -> ReplaceFilter.Text(query)
        }
    }

    private fun observeRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val filter = currentFilter(_uiState.value.query)
            val flow = when (filter) {
                ReplaceFilter.All -> appDb.replaceRuleDao.flowAll()
                ReplaceFilter.Enabled -> appDb.replaceRuleDao.flowEnabled()
                ReplaceFilter.Disabled -> appDb.replaceRuleDao.flowDisabled()
                ReplaceFilter.NoGroup -> appDb.replaceRuleDao.flowNoGroup()
                is ReplaceFilter.Group -> appDb.replaceRuleDao.flowGroupSearch((filter as ReplaceFilter.Group).name)
                is ReplaceFilter.Text -> appDb.replaceRuleDao.flowSearch("%${(filter as ReplaceFilter.Text).key}%")
            }
            flow.flowOn(Dispatchers.IO)
                .conflate()
                .catch { error ->
                    AppLog.put("替换规则管理界面更新数据出错", error)
                    _uiState.update { it.copy(loading = false, error = error.localizedMessage) }
                }
                .collect { rules ->
                    _uiState.update { state ->
                        val retained = rules.map { it.id }.toHashSet()
                        state.copy(
                            rules = rules.map(::ReplaceRuleUi),
                            selection = state.selection.intersect(retained),
                            loading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            appDb.replaceRuleDao.flowGroups()
                .flowOn(Dispatchers.IO)
                .collect { groups -> _uiState.update { it.copy(groups = groups) } }
        }
    }

    fun retry() = observeRules()

    fun setQuery(query: String) {
        val filter = currentFilter(query)
        _uiState.update { it.copy(query = query, activeFilter = filter) }
        observeRules()
    }

    fun applyFilter(filter: ReplaceFilter) {
        val query = when (filter) {
            ReplaceFilter.All -> ""
            ReplaceFilter.Enabled -> context.getString(io.legado.app.R.string.enabled)
            ReplaceFilter.Disabled -> context.getString(io.legado.app.R.string.disabled)
            ReplaceFilter.NoGroup -> context.getString(io.legado.app.R.string.no_group)
            is ReplaceFilter.Group -> "group:${filter.name}"
            is ReplaceFilter.Text -> filter.key
        }
        _uiState.update { it.copy(query = query, activeFilter = filter) }
        observeRules()
    }

    // ─────────────────────────── 选择 ───────────────────────────

    fun toggleSelect(id: Long) {
        _uiState.update { state ->
            val next = state.selection.toMutableSet()
            if (!next.add(id)) next.remove(id)
            state.copy(selection = next)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selection = it.rules.map { rule -> rule.id }.toSet()) }
    }

    fun revertSelection() {
        _uiState.update { state ->
            val next = state.rules.map { it.id }.toSet() - state.selection
            state.copy(selection = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = emptySet()) }
    }

    private fun selected(): List<ReplaceRule> = _uiState.value.let { state ->
        state.rules.filter { it.id in state.selection }.map { it.toEntity() }
    }

    // ─────────────────────────── 单条 ───────────────────────────

    fun enable(rule: ReplaceRuleUi, enabled: Boolean) {
        execute { appDb.replaceRuleDao.enable(rule.id, enabled) }
    }

    fun requestDelete(rule: ReplaceRuleUi) {
        _uiState.update { it.copy(confirmDelete = rule) }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun executeDelete(rule: ReplaceRuleUi) {
        execute {
            appDb.replaceRuleDao.delete(rule.toEntity())
            ReplacePreviewConfig.removeSample(rule.id)
        }
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun toTop(rule: ReplaceRuleUi) {
        execute {
            val entity = rule.toEntity()
            entity.order = appDb.replaceRuleDao.minOrder - 1
            appDb.replaceRuleDao.update(entity)
        }
    }

    fun toBottom(rule: ReplaceRuleUi) {
        execute {
            val entity = rule.toEntity()
            entity.order = appDb.replaceRuleDao.maxOrder + 1
            appDb.replaceRuleDao.update(entity)
        }
    }

    /** 上移 / 下移：与相邻规则交换相对顺序。 */
    fun move(rule: ReplaceRuleUi, down: Boolean) {
        val rules = _uiState.value.rules
        val index = rules.indexOfFirst { it.id == rule.id }
        val target = if (down) index + 1 else index - 1
        if (index < 0 || target !in rules.indices) return
        val neighbor = rules[target]
        execute {
            appDb.runInTransaction {
                val current = appDb.replaceRuleDao.all
                val reordered = moveRelativeTo(current, rule.id, neighbor.id, after = down) { it.id }
                if (reordered == current) return@runInTransaction
                appDb.replaceRuleDao.update(
                    *reordered.mapIndexed { idx, item -> item.copy(order = idx) }.toTypedArray(),
                )
            }
        }
    }

    // ─────────────────────────── 批量 ───────────────────────────

    fun deleteSelection() {
        val selected = selected()
        if (selected.isEmpty()) return
        execute {
            appDb.replaceRuleDao.delete(*selected.toTypedArray())
            selected.forEach { ReplacePreviewConfig.removeSample(it.id) }
        }
        clearSelection()
    }

    fun enableSelection() {
        execute { appDb.replaceRuleDao.enable(true, selected()) }
    }

    fun disableSelection() {
        execute { appDb.replaceRuleDao.enable(false, selected()) }
    }

    fun selectionAddToGroups(group: String) {
        execute {
            val array = selected().map { it.copy().addGroup(group) }.toTypedArray()
            appDb.replaceRuleDao.update(*array)
        }
    }

    fun selectionRemoveFromGroups(group: String) {
        execute {
            val array = selected().map { it.copy().removeGroup(group) }.toTypedArray()
            appDb.replaceRuleDao.update(*array)
        }
    }

    fun topSelection() {
        execute {
            var minOrder = appDb.replaceRuleDao.minOrder - selected().size
            val array = selected().onEach { it.order = minOrder++ }.toTypedArray()
            appDb.replaceRuleDao.update(*array)
        }
    }

    fun bottomSelection() {
        execute {
            var maxOrder = appDb.replaceRuleDao.maxOrder + 1
            val array = selected().onEach { it.order = maxOrder++ }.toTypedArray()
            appDb.replaceRuleDao.update(*array)
        }
    }

    fun selectionJson(): String =
        io.legado.app.utils.GSON.toJson(ReplacePreviewConfig.withSamples(selected()))

    // ─────────────────────────── 分组管理 ───────────────────────────

    fun openGroupManage() = _uiState.update { it.copy(groupManageOpen = true) }
    fun closeGroupManage() = _uiState.update { it.copy(groupManageOpen = false) }

    fun addGroup(group: String) {
        execute {
            val sources = appDb.replaceRuleDao.noGroup
            sources.forEach { it.group = group }
            appDb.replaceRuleDao.update(*sources.toTypedArray())
        }
    }

    fun renameGroup(oldGroup: String, newGroup: String?) {
        execute {
            val sources = appDb.replaceRuleDao.getByGroup(oldGroup).mapNotNull { source ->
                source.group.renameGroupExact(oldGroup, newGroup)?.let { groups ->
                    source.apply { group = groups }
                }
            }
            if (sources.isNotEmpty()) appDb.replaceRuleDao.update(*sources.toTypedArray())
        }
    }

    fun deleteGroup(group: String) = renameGroup(group, null)

    // ─────────────────────────── 其它开关 ───────────────────────────

    fun toggleManualReplace() {
        AppConfig.manualReplaceRule = !AppConfig.manualReplaceRule
        _uiState.update { it.copy(manualReplace = AppConfig.manualReplaceRule) }
    }

    fun openImportUrl() = _uiState.update { it.copy(importUrlOpen = true) }
    fun closeImportUrl() = _uiState.update { it.copy(importUrlOpen = false) }

    fun showHelp(mdText: String?) = _uiState.update { it.copy(helpText = mdText) }
    fun dismissHelp() = _uiState.update { it.copy(helpText = null) }

    fun reportError(message: String) = context.toastOnUi(message)
}
