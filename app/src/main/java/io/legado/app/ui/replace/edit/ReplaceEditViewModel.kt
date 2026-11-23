package io.legado.app.ui.replace.edit

import android.app.Application
import com.script.rhino.RhinoInterruptError
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.config.ReplacePreviewConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 落文 · 替换规则编辑页状态模型。
 *
 * 草稿 [ReplaceRule] 在 VM 内持有，字段修改即标记 dirty；预览走纯逻辑 [ReplacePreview]，
 * 异常分类映射到对应文案兜底。保存时复制草稿落库，预览样本持久化到 [ReplacePreviewConfig]。
 */
data class ReplaceEditState(
    val rule: ReplaceRule = ReplaceRule(),
    val previewInput: String = "",
    val previewOutput: String = "",
    val previewError: String? = null,
    val loading: Boolean = false,
    val dirty: Boolean = false,
    val confirmExit: Boolean = false,
    val helpText: String? = null,
)

class ReplaceEditViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ReplaceEditState())
    val uiState: StateFlow<ReplaceEditState> = _uiState.asStateFlow()

    private var snapshot: ReplaceRule = ReplaceRule()
    private var previewJob: Job? = null

    /** 入口装载：优先按 id 取库内规则；否则用 extras 拼一条草稿。 */
    fun load(id: Long, pattern: String?, isRegex: Boolean?, scope: String?) {
        execute {
            val entity = if (id > 0) appDb.replaceRuleDao.findById(id) else null
            val draft = entity ?: ReplaceRule(
                pattern = pattern.orEmpty(),
                isRegex = isRegex ?: true,
                scope = scope,
            )
            snapshot = draft.copy()
            val sample = ReplacePreviewConfig.sample(draft.id)
            _uiState.update {
                it.copy(
                    rule = draft,
                    previewInput = sample,
                    previewOutput = if (sample.isEmpty()) "" else "—",
                )
            }
            runPreview()
        }
    }

    fun update(patch: (ReplaceRule) -> ReplaceRule) {
        _uiState.update { state ->
            val next = patch(state.rule)
            state.copy(rule = next, dirty = next != snapshot)
        }
        schedulePreview()
    }

    fun setPreviewInput(text: String) {
        _uiState.update { it.copy(previewInput = text) }
        schedulePreview()
    }

    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            delay(250)
            runPreview()
        }
    }

    fun runPreview() {
        val state = _uiState.value
        if (state.previewInput.isBlank() || state.rule.pattern.isBlank()) {
            _uiState.update { it.copy(previewOutput = "", previewError = null) }
            return
        }
        previewJob?.cancel()
        _uiState.update { it.copy(loading = true, previewError = null) }
        execute {
            val sample = ReplacePreview.normalizeSample(state.previewInput)
            ReplacePreview.apply(state.rule, sample)
        }.onSuccess { output ->
            _uiState.update {
                it.copy(
                    loading = false,
                    previewOutput = output,
                    previewError = if (output.isEmpty())
                        context.getString(io.legado.app.R.string.replace_preview_truncated)
                    else null,
                )
            }
        }.onError { error ->
            val message = when (error) {
                is ReplacePreviewException -> when (error.reason) {
                    ReplacePreviewException.Reason.TIMEOUT ->
                        context.getString(io.legado.app.R.string.replace_preview_timeout)
                    ReplacePreviewException.Reason.CONTEXT_UNAVAILABLE ->
                        context.getString(io.legado.app.R.string.replace_preview_context_unavailable)
                    ReplacePreviewException.Reason.JS_EVALUATION ->
                        context.getString(io.legado.app.R.string.replace_preview_js_error)
                }
                is RhinoInterruptError ->
                    context.getString(io.legado.app.R.string.replace_preview_context_unavailable)
                else ->
                    context.getString(io.legado.app.R.string.replace_preview_error)
            }
            _uiState.update { it.copy(loading = false, previewError = message) }
        }
    }

    fun save(onSaved: () -> Unit) {
        val draft = _uiState.value.rule
        if (draft.name.isBlank() || draft.pattern.isBlank()) {
            context.toastOnUi(context.getString(io.legado.app.R.string.replace_rule_invalid))
            return
        }
        execute {
            ReplacePreviewConfig.saveSample(draft.id, _uiState.value.previewInput)
            appDb.replaceRuleDao.insert(draft)
        }.onSuccess { onSaved() }
            .onError { context.toastOnUi(it.localizedMessage) }
    }

    fun copyRule() {
        context.sendToClip(GSON.toJson(_uiState.value.rule))
    }

    fun pasteRule() {
        execute {
            val text = context.getClipText()?.takeIf { it.isNotBlank() }
                ?: throw io.legado.app.exception.NoStackTraceException("剪贴板为空")
            GSON.fromJsonObject<ReplaceRule>(text).getOrThrow()
        }.onSuccess { pasted ->
            _uiState.update {
                it.copy(rule = pasted, dirty = true, previewInput = "")
            }
            schedulePreview()
        }.onError { context.toastOnUi(context.getString(io.legado.app.R.string.wrong_format)) }
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
