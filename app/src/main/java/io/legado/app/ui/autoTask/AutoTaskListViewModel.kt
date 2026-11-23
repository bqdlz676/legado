package io.legado.app.ui.autoTask

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.model.AutoTask
import io.legado.app.utils.CronSchedule
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 落文 · 自动任务列表主控。
 *
 * 订阅 Room Flow；启停 / 批量定时 / 删除 / 排序 / 清日志委托 [AutoTask] 模型层。
 * 兜底：读取异常 → 文案 + 重试；空任务 → 空态；Cron 非法 → 校验文案，不落库。
 */
class AutoTaskListViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(AutoTaskListState())
    val uiState: kotlinx.coroutines.flow.StateFlow<AutoTaskListState> = _uiState.asStateFlow()

    init {
        observeRules()
    }

    private fun observeRules() {
        viewModelScope.launch {
            appDb.autoTaskRuleDao.flowAll()
                .flowOn(Dispatchers.IO)
                .conflate()
                .catch { error ->
                    AppLog.put("自动任务读取失败\n${error.localizedMessage}", error)
                    _uiState.update { it.copy(loading = false, error = error.localizedMessage) }
                }
                .collect { rules ->
                    _uiState.update { state ->
                        val retained = rules.map { it.id }.toHashSet()
                        state.copy(
                            rules = rules.map(::AutoTaskUi),
                            selection = state.selection.intersect(retained),
                            loading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun retry() = observeRules()

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun visibleRules(): List<AutoTaskUi> {
        val query = _uiState.value.query.trim()
        return if (query.isEmpty()) _uiState.value.rules
        else _uiState.value.rules.filter { it.name.contains(query, ignoreCase = true) }
    }

    // ─────────────────────────── 选择 ───────────────────────────

    fun toggleSelect(id: String) {
        _uiState.update { state ->
            val next = state.selection.toMutableSet()
            if (!next.add(id)) next.remove(id)
            state.copy(selection = next)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selection = visibleRules().map { rule -> rule.id }.toSet()) }
    }

    fun revertSelection() {
        _uiState.update { state ->
            val next = visibleRules().map { it.id }.toSet() - state.selection
            state.copy(selection = next)
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selection = emptySet()) }

    private fun selectedIds(): List<String> =
        _uiState.value.rules.filter { it.id in _uiState.value.selection }.map { it.id }

    // ─────────────────────────── 单条 ───────────────────────────

    fun toggleEnabled(rule: AutoTaskUi, enabled: Boolean) {
        execute { AutoTask.upsert(rule.toEntity().copy(enable = enabled), context) }
    }

    fun requestDelete(rule: AutoTaskUi) = _uiState.update { it.copy(confirmDelete = rule) }
    fun dismissConfirm() = _uiState.update { it.copy(confirmDelete = null) }

    fun executeDelete(rule: AutoTaskUi) {
        execute { AutoTask.delete(listOf(rule.id), context) }
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun showLog(rule: AutoTaskUi) = _uiState.update { it.copy(logTarget = rule) }
    fun dismissLog() = _uiState.update { it.copy(logTarget = null) }

    fun clearLog(id: String) {
        execute { AutoTask.clearRunLog(id) }
        _uiState.update { it.copy(logTarget = null) }
    }

    fun move(rule: AutoTaskUi, down: Boolean) {
        val rules = visibleRules()
        val index = rules.indexOfFirst { it.id == rule.id }
        val target = if (down) index + 1 else index - 1
        if (index < 0 || target !in rules.indices) return
        val reordered = rules.toMutableList().apply { add(target, removeAt(index)) }
        execute { AutoTask.reorder(reordered.map { it.id }, context) }
    }

    // ─────────────────────────── 批量 ───────────────────────────

    fun deleteSelection() {
        val ids = selectedIds()
        if (ids.isEmpty()) return
        execute { AutoTask.delete(ids, context) }
        clearSelection()
    }

    fun enableSelection() {
        execute { AutoTask.updateEnabled(selectedIds(), true, context) }
    }

    fun disableSelection() {
        execute { AutoTask.updateEnabled(selectedIds(), false, context) }
    }

    fun applyBatchCron(cron: String): Boolean {
        if (CronSchedule.parse(cron) == null) {
            context.toastOnUi(io.legado.app.R.string.auto_task_cron_invalid)
            return false
        }
        execute { AutoTask.updateCron(selectedIds(), cron, context) }
        return true
    }

    fun selectionJson(): String =
        AutoTask.exportJson(_uiState.value.rules.filter { it.id in _uiState.value.selection }
            .map(AutoTaskUi::toEntity))

    fun allJson(): String = AutoTask.exportJson()

    // ─────────────────────────── 弹窗路由 ───────────────────────────

    fun openBatchCron() = _uiState.update { it.copy(batchCronOpen = true) }
    fun closeBatchCron() = _uiState.update { it.copy(batchCronOpen = false) }
    fun openImportUrl() = _uiState.update { it.copy(importUrlOpen = true) }
    fun closeImportUrl() = _uiState.update { it.copy(importUrlOpen = false) }
    fun showHelp(mdText: String?) = _uiState.update { it.copy(helpText = mdText) }
    fun dismissHelp() = _uiState.update { it.copy(helpText = null) }

    fun validateCron(cron: String): Boolean = CronSchedule.parse(cron) != null
}
