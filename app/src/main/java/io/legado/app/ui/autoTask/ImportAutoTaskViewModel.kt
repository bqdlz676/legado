package io.legado.app.ui.autoTask

import android.app.Application
import androidx.core.net.toUri
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.AutoTaskRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.AutoTask
import io.legado.app.utils.CronSchedule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isUri
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import splitties.init.appCtx

/**
 * 落文 · 自动任务导入弹窗状态模型。
 *
 * 解析 / 校验 / 本地比对逻辑保留（URL / URI / JSON 对象 / JSON 数组四入口），
 * 状态汇总到 [uiState]。解析失败给错误文案兜底；重复条目按 新增 / 更新 / 已存在 标注。
 */
data class ImportAutoTaskState(
    val tasks: List<AutoTaskRule> = emptyList(),
    val local: List<AutoTaskRule?> = emptyList(),
    val selected: List<Boolean> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val selectCount: Int get() = selected.count { it }
    val isSelectAll: Boolean get() = selected.isNotEmpty() && selected.all { it }
}

class ImportAutoTaskViewModel(app: Application) : BaseViewModel(app) {

    private val _uiState = MutableStateFlow(ImportAutoTaskState())
    val uiState: StateFlow<ImportAutoTaskState> = _uiState.asStateFlow()

    @Volatile
    private var localTasksById: Map<String, AutoTaskRule> = emptyMap()
    private var importStarted = false

    fun importSelect(finally: () -> Unit) {
        execute {
            val selected = _uiState.value.selected.mapIndexedNotNull { index, checked ->
                if (checked) _uiState.value.tasks.getOrNull(index) else null
            }.map(::validateImportedTask)
            AutoTask.importRules(selected, context)
        }.onError { error ->
            _uiState.update { it.copy(error = "ImportError:${error.localizedMessage}") }
            AppLog.put("ImportError:${error.localizedMessage}", error)
        }.onSuccess { finally() }
    }

    fun importSource(text: String?) {
        if (importStarted) return
        val sourceText = text?.trim().orEmpty()
        if (sourceText.isBlank()) {
            _uiState.update { it.copy(error = "ImportError:${context.getString(io.legado.app.R.string.wrong_format)}") }
            return
        }
        importStarted = true
        _uiState.update { it.copy(loading = true, error = null) }
        execute { importSourceAwait(sourceText) }
            .onError { error ->
                importStarted = false
                _uiState.update {
                    it.copy(loading = false, tasks = emptyList(), local = emptyList(),
                        selected = emptyList(), error = "ImportError:${error.localizedMessage}")
                }
                AppLog.put("ImportError:${error.localizedMessage}", error)
            }
            .onSuccess { tasks ->
                _uiState.update { it.copy(tasks = tasks) }
                comparisonSource()
            }
    }

    fun toggle(index: Int) {
        _uiState.update { state ->
            val next = state.selected.toMutableList()
            if (index in next.indices) next[index] = !next[index]
            state.copy(selected = next)
        }
    }

    fun toggleAll() {
        _uiState.update { state ->
            val next = List(state.tasks.size) { !state.isSelectAll }
            state.copy(selected = next)
        }
    }

    fun updateTaskAt(index: Int, task: AutoTaskRule): AutoTaskRule? {
        val state = _uiState.value
        if (index !in state.tasks.indices) return null
        val validated = try {
            validateImportedTask(task)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(error = "ImportError:${e.localizedMessage ?: context.getString(io.legado.app.R.string.wrong_format)}")
            }
            return null
        }
        val localTask = localTasksById[validated.id]
        val tasks = state.tasks.toMutableList().also { it[index] = validated }
        val locals = state.local.toMutableList().also { if (index < it.size) it[index] = localTask }
        val selected = state.selected.toMutableList().also {
            if (index < it.size) it[index] = localTask == null || !sameAutoTaskForImport(validated, localTask)
        }
        _uiState.update { it.copy(tasks = tasks, local = locals, selected = selected, error = null) }
        return validated
    }

    private suspend fun importSourceAwait(text: String): List<AutoTaskRule> = when {
        text.isJsonObject() -> listOf(
            validateImportedTask(GSON.fromJsonObject<AutoTaskRule>(text).getOrThrow()),
        )

        text.isJsonArray() -> GSON.fromJsonArray<AutoTaskRule>(text)
            .getOrThrow().map(::validateImportedTask)

        text.isAbsUrl() -> importSourceUrl(text)
        text.isUri() -> importSourceAwait(text.toUri().readText(appCtx))
        else -> throw NoStackTraceException(context.getString(io.legado.app.R.string.wrong_format))
    }

    private suspend fun importSourceUrl(url: String): List<AutoTaskRule> {
        val body = okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().text()
        return importSourceAwait(body)
    }

    private fun validateImportedTask(task: AutoTaskRule): AutoTaskRule {
        val normalized = task.copy(
            name = task.name.orEmpty().trim(),
            cron = task.cron?.trim().orEmpty().ifBlank { AutoTask.DEFAULT_CRON },
            script = task.script.orEmpty(),
        )
        if (normalized.name.isBlank()) {
            throw NoStackTraceException(context.getString(io.legado.app.R.string.auto_task_name_required))
        }
        if (CronSchedule.parse(normalized.cron.orEmpty()) == null) {
            throw NoStackTraceException(context.getString(io.legado.app.R.string.auto_task_cron_invalid))
        }
        if (AutoTask.normalizeScript(normalized.script).isBlank()) {
            throw NoStackTraceException(context.getString(io.legado.app.R.string.auto_task_script_empty))
        }
        return normalized
    }

    private fun comparisonSource() {
        execute {
            val localMap = AutoTask.all().associateBy { it.id }
            localTasksById = localMap
            val tasks = _uiState.value.tasks
            val locals = tasks.map { localMap[it.id] }
            val selected = tasks.map { task ->
                val local = localMap[task.id]
                local == null || !sameAutoTaskForImport(task, local)
            }
            _uiState.update {
                it.copy(loading = false, local = locals, selected = selected)
            }
        }
    }

    fun reportError(message: String) = context.toastOnUi(message)
}

internal fun sameAutoTaskForImport(left: AutoTaskRule, right: AutoTaskRule): Boolean {
    fun AutoTaskRule.configuration() = copy(
        customOrder = 0,
        lastRunAt = 0L,
        lastResult = null,
        lastError = null,
        lastLog = null,
    )
    return left.configuration() == right.configuration()
}
