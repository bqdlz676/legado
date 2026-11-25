package io.legado.app.ui.dict.rule

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.help.DefaultData
import io.legado.app.utils.getClipText
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 落文 · 词典规则列表主控。
 *
 * 数据读写只走 [appDb.dictRuleDao] 与 [DefaultData]；本类把 UI 事件翻译成 DAO 调用，
 * 状态汇总到 [uiState]。空集 / 读取异常都有文案与重试路径，不向 UI 抛异常。
 */
class DictRuleListViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(DictRuleListState())
    val uiState: kotlinx.coroutines.flow.StateFlow<DictRuleListState> = _uiState.asStateFlow()

    init {
        observeRules()
    }

    private fun observeRules() {
        viewModelScope.launch {
            appDb.dictRuleDao.flowAll()
                .flowOn(Dispatchers.IO)
                .catch { error ->
                    AppLog.put("字典规则获取数据失败\n${error.localizedMessage}", error)
                    _uiState.update { it.copy(loading = false, error = error.localizedMessage) }
                }
                .collect { rules ->
                    _uiState.update { state ->
                        val retained = rules.map { it.name }.toHashSet()
                        state.copy(
                            rules = rules.map(::DictRuleUi),
                            selection = state.selection.intersect(retained),
                            loading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun retry() {
        _uiState.update { it.copy(loading = true, error = null) }
        observeRules()
    }

    // ─────────────────────────── 选择 ───────────────────────────

    fun toggleSelect(name: String) {
        _uiState.update { state ->
            val next = state.selection.toMutableSet()
            if (!next.add(name)) next.remove(name)
            state.copy(selection = next)
        }
    }

    fun selectAll() {
        _uiState.update { state -> state.copy(selection = state.rules.map { it.name }.toSet()) }
    }

    fun revertSelection() {
        _uiState.update { state ->
            val next = state.rules.map { it.name }.toSet() - state.selection
            state.copy(selection = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = emptySet()) }
    }

    private fun selectedEntities(): List<DictRule> = _uiState.value.let { state ->
        state.rules.filter { it.name in state.selection }.map { it.toEntity() }
    }

    // ─────────────────────────── 单条操作 ───────────────────────────

    fun toggleEnabled(rule: DictRuleUi) {
        execute {
            val updated = rule.toEntity().copy(enabled = !rule.enabled)
            appDb.dictRuleDao.update(updated)
        }.onError { error ->
            context.toastOnUi("更新字典规则出错\n${error.localizedMessage}")
        }
    }

    fun requestDelete(rule: DictRuleUi) {
        _uiState.update { it.copy(confirmDelete = rule) }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun executeDelete(rule: DictRuleUi) {
        execute {
            appDb.dictRuleDao.delete(rule.toEntity())
        }.onError { error ->
            context.toastOnUi("删除字典规则出错\n${error.localizedMessage}")
        }
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun deleteSelection() {
        val selected = selectedEntities()
        if (selected.isEmpty()) return
        execute { appDb.dictRuleDao.delete(*selected.toTypedArray()) }
            .onError { error -> context.toastOnUi("删除字典规则出错\n${error.localizedMessage}") }
        clearSelection()
    }

    fun enableSelection() {
        execute {
            val array = selectedEntities().map { it.copy(enabled = true) }.toTypedArray()
            appDb.dictRuleDao.insert(*array)
        }
    }

    fun disableSelection() {
        execute {
            val array = selectedEntities().map { it.copy(enabled = false) }.toTypedArray()
            appDb.dictRuleDao.insert(*array)
        }
    }

    /** 上移 / 下移：交换相邻 sortNumber，落库后由 flow 回推顺序。 */
    fun move(rule: DictRuleUi, down: Boolean) {
        val rules = _uiState.value.rules
        val index = rules.indexOfFirst { it.name == rule.name }
        val target = if (down) index + 1 else index - 1
        if (index < 0 || target !in rules.indices) return
        val other = rules[target]
        execute {
            val a = rule.toEntity().copy(sortNumber = other.sortNumber)
            val b = other.toEntity().copy(sortNumber = rule.sortNumber)
            appDb.dictRuleDao.update(a, b)
        }
    }

    // ─────────────────────────── 编辑 ───────────────────────────

    fun openCreate() {
        _uiState.update {
            it.copy(editTarget = DictRuleUi("", "", "", true, 0), creating = true)
        }
    }

    fun openEdit(rule: DictRuleUi) {
        _uiState.update { it.copy(editTarget = rule, creating = false) }
    }

    fun dismissEdit() {
        _uiState.update { it.copy(editTarget = null, creating = false) }
    }

    fun save(rule: DictRuleUi) {
        val creating = _uiState.value.creating
        execute {
            if (!creating) {
                // 主键是 name：改名时先删旧记录，等价旧编辑弹窗的 delete + insert。
                _uiState.value.editTarget?.name?.takeIf { it != rule.name }?.let { oldName ->
                    appDb.dictRuleDao.delete(appDb.dictRuleDao.getByName(oldName) ?: return@execute)
                }
            }
            appDb.dictRuleDao.insert(rule.toEntity())
        }.onError { error ->
            context.toastOnUi("保存字典规则出错\n${error.localizedMessage}")
        }
        // B29：入库副作用在决策时刻执行；槽位摘除交 HalfSheetExitHost 离场收束回调
        // （dismissEdit），不再在此直清 editTarget 跳过离场动画。
    }

    // ─────────────────────────── 批量 / 导入 ───────────────────────────

    fun importDefault() {
        execute { DefaultData.importDefaultDictRules() }
            .onError { error -> context.toastOnUi(error.localizedMessage) }
    }

    /** 供分享 / 导出取用选中规则的 JSON 文本。 */
    fun selectionJson(): String =
        io.legado.app.utils.GSON.toJson(selectedEntities())

    /** 复制当前草稿到剪贴板（旧编辑弹窗「复制规则」）。 */
    fun copyRule(rule: DictRuleUi) {
        context.sendToClip(io.legado.app.utils.GSON.toJson(rule.toEntity()))
    }

    /** 编辑 Sheet 内「复制」：取当前草稿。 */
    fun copyCurrentDraft() {
        _uiState.value.editTarget?.let { copyRule(it) }
    }

    /** 从剪贴板粘贴规则；解析失败给文案兜底，不崩。 */
    fun pasteRule() {
        execute {
            val text = context.getClipText()?.takeIf { it.isNotBlank() }
                ?: throw io.legado.app.exception.NoStackTraceException("剪贴板没有内容")
            io.legado.app.utils.GSON.fromJsonObject<DictRule>(text).getOrThrow()
        }.onSuccess { pasted ->
            _uiState.update { it.copy(editTarget = DictRuleUi(pasted)) }
        }.onError {
            context.toastOnUi("格式不对")
        }
    }

    fun showHelp(mdText: String?) {
        _uiState.update { it.copy(helpText = mdText) }
    }

    fun dismissHelp() {
        _uiState.update { it.copy(helpText = null) }
    }
}
