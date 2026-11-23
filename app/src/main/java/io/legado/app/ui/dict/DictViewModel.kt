package io.legado.app.ui.dict

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 落文 · 阅读期取词弹窗状态模型。
 *
 * 数据仍走 [appDb.dictRuleDao] 与 [DictRule.search]；渲染编排归 Compose 层。
 * 兜底：无启用词典 → 空态文案；查询失败 → 错误文案 + 重试；任务切换时取消上一次请求。
 */
data class DictDialogState(
    val word: String = "",
    val rules: List<DictRule> = emptyList(),
    val activeIndex: Int = 0,
    val loading: Boolean = false,
    val content: String = "",
    val error: String? = null,
) {
    val activeRule: DictRule? get() = rules.getOrNull(activeIndex)
}

class DictViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(DictDialogState())
    val uiState: StateFlow<DictDialogState> = _uiState.asStateFlow()

    private var dictJob: Coroutine<String>? = null

    fun attach(word: String) {
        if (word.isBlank()) return
        _uiState.update { it.copy(word = word) }
        execute { appDb.dictRuleDao.enabled }
            .onSuccess { rules ->
                _uiState.update { it.copy(rules = rules, activeIndex = 0) }
                if (rules.isNotEmpty()) selectRule(0)
            }
            .onError { error ->
                _uiState.update { it.copy(error = error.localizedMessage) }
            }
    }

    fun selectRule(index: Int) {
        val state = _uiState.value
        val rule = state.rules.getOrNull(index) ?: return
        _uiState.update { it.copy(activeIndex = index, loading = true, error = null, content = "") }
        dictJob?.cancel()
        dictJob = execute { rule.search(state.word) }
            .onSuccess { result ->
                _uiState.update {
                    it.copy(loading = false, content = result.trimStart(), error = null)
                }
            }
            .onError { error ->
                _uiState.update {
                    it.copy(loading = false, error = error.localizedMessage ?: "ERROR")
                }
            }
    }

    fun retry() {
        val index = _uiState.value.activeIndex
        if (_uiState.value.rules.isNotEmpty()) selectRule(index)
    }

    fun onButtonClick(name: String, click: String) {
        if (click.isBlank()) return
        val rule = _uiState.value.activeRule ?: return
        execute { rule.buttonClick(name, click) }
            .onError { AppLog.put("$name click error\n${it.localizedMessage}", it) }
    }
}
