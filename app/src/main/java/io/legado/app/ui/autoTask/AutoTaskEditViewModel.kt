package io.legado.app.ui.autoTask

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.AutoTaskRule
import io.legado.app.model.AutoTask
import io.legado.app.utils.CronSchedule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * 落文 · 自动任务编辑页状态模型。
 *
 * 草稿在 VM 内持有；保存前校验名称 / Cron / 脚本三项（旧口径）。
 * 校验失败给文案兜底，不落库；未保存退出走确认弹窗。
 */
data class AutoTaskEditState(
    val draft: AutoTaskRule = AutoTaskRule(),
    val origin: AutoTaskRule? = null,
    val confirmExit: Boolean = false,
    val helpText: String? = null,
) {
    val dirty: Boolean get() = origin?.let { it != draft } ?: true
}

class AutoTaskEditViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(AutoTaskEditState())
    val uiState: StateFlow<AutoTaskEditState> = _uiState.asStateFlow()

    fun load(id: String?) {
        execute {
            val loaded = if (id.isNullOrBlank()) null else AutoTask.get(id)
            _uiState.update { it.copy(draft = loaded ?: AutoTaskRule(), origin = loaded) }
        }
    }

    fun update(patch: (AutoTaskRule) -> AutoTaskRule) {
        _uiState.update { it.copy(draft = patch(it.draft)) }
    }

    fun validate(): AutoTaskRule? {
        val draft = _uiState.value.draft
        if (draft.name.isBlank()) {
            context.toastOnUi(io.legado.app.R.string.auto_task_name_required)
            return null
        }
        if (CronSchedule.parse(draft.cron.orEmpty()) == null) {
            context.toastOnUi(io.legado.app.R.string.auto_task_cron_invalid)
            return null
        }
        if (AutoTask.normalizeScript(draft.script).isBlank()) {
            context.toastOnUi(io.legado.app.R.string.auto_task_script_empty)
            return null
        }
        return draft
    }

    fun save(after: (AutoTaskRule) -> Unit) {
        val draft = validate() ?: return
        execute { AutoTask.upsert(draft, context) }
            .onSuccess { saved ->
                _uiState.update { it.copy(origin = saved) }
                after(saved)
            }
            .onError { context.toastOnUi(it.localizedMessage) }
    }

    fun copyRule() {
        execute {
            withContext(Dispatchers.Default) {
                AutoTask.exportJson(listOf(_uiState.value.draft))
            }
        }.onSuccess { context.sendToClip(it) }
    }

    fun pasteRule() {
        execute {
            val text = context.getClipText()?.takeIf { it.isNotBlank() }
                ?: throw io.legado.app.exception.NoStackTraceException("剪贴板为空")
            GSON.fromJsonObject<AutoTaskRule>(text).getOrNull()
                ?: GSON.fromJsonArray<AutoTaskRule>(text).getOrNull()?.singleOrNull()
                ?: throw io.legado.app.exception.NoStackTraceException("格式不对")
        }.onSuccess { pasted ->
            _uiState.update { it.copy(draft = pasted) }
        }.onError { context.toastOnUi(io.legado.app.R.string.wrong_format) }
    }

    fun requestExit() {
        if (_uiState.value.dirty) _uiState.update { it.copy(confirmExit = true) }
        else onExit?.invoke()
    }

    fun dismissConfirmExit() = _uiState.update { it.copy(confirmExit = false) }

    fun confirmExit() {
        _uiState.update { it.copy(confirmExit = false) }
        onExit?.invoke()
    }

    var onExit: (() -> Unit)? = null

    fun showHelp(mdText: String?) = _uiState.update { it.copy(helpText = mdText) }
    fun dismissHelp() = _uiState.update { it.copy(helpText = null) }
}
