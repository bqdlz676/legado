package io.legado.app.ui.autoTask

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.ACache
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.toastOnUi

/**
 * 落文 · 自动任务管理宿主（Compose 自绘壳）。
 *
 * 类名与 Manifest 入口不变；列表编排归 [AutoTaskScreen]，数据主控归 [AutoTaskListViewModel]。
 * 旧 XML / Binding / Adapter / SelectActionBar 全部移除。
 */
class AutoTaskActivity : FragmentActivity() {

    private val viewModel: AutoTaskListViewModel by viewModels()
    private val importRecordKey = "autoTaskRecordKey"

    private val importDoc = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { showImportDialog(it.toString()) }
    }

    private val exportDoc = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            toastOnUi(io.legado.app.R.string.export_success)
            sendToClip(uri.toString())
        }
    }

    private val editActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* 列表由 Flow 自动回推，结果无需额外处理 */ }

    private val debugActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = viewModel.uiState.collectAsState().value
            AutoTaskScreen(
                state = state,
                visibleRules = viewModel.visibleRules(),
                onBack = { finish() },
                onQueryChange = viewModel::setQuery,
                onToggleSelect = viewModel::toggleSelect,
                onSelectAll = viewModel::selectAll,
                onRevert = viewModel::revertSelection,
                onToggleEnabled = viewModel::toggleEnabled,
                onEdit = { editActivity.launch(AutoTaskEditActivity.intent(this, it.id)) },
                onDebug = { debugActivity.launch(AutoTaskDebugActivity.intent(this, it.id)) },
                onShowLog = viewModel::showLog,
                onRequestDelete = viewModel::requestDelete,
                onDismissConfirm = viewModel::dismissConfirm,
                onExecuteDelete = viewModel::executeDelete,
                onDeleteSelection = viewModel::deleteSelection,
                onEnableSelection = viewModel::enableSelection,
                onDisableSelection = viewModel::disableSelection,
                onBatchCron = { cron ->
                    if (viewModel.applyBatchCron(cron)) viewModel.closeBatchCron()
                },
                onOpenBatchCron = viewModel::openBatchCron,
                onCloseBatchCron = viewModel::closeBatchCron,
                onExportSelection = { exportJson(viewModel.selectionJson()) },
                onExportAll = { exportJson(viewModel.allJson()) },
                onMove = viewModel::move,
                onAdd = { editActivity.launch(AutoTaskEditActivity.intent(this, "")) },
                onImportLocal = {
                    importDoc.launch {
                        mode = HandleFileContract.FILE
                        allowExtensions = arrayOf("txt", "json")
                    }
                },
                onOpenImportOnline = viewModel::openImportUrl,
                onCloseImportUrl = viewModel::closeImportUrl,
                onImportOnline = { source ->
                    viewModel.closeImportUrl()
                    if (source.isBlank()) toastOnUi(io.legado.app.R.string.wrong_format)
                    else showImportDialog(source)
                },
                onHelp = ::showHelp,
                onRetry = viewModel::retry,
                onDismissHelp = viewModel::dismissHelp,
                onClearLog = viewModel::clearLog,
                onDismissLog = viewModel::dismissLog,
            )
        }
    }

    private fun exportJson(json: String) {
        if (json == "[]") return
        exportDoc.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "exportAutoTask.json",
                json.toByteArray(),
                "application/json",
            )
        }
    }

    private fun showImportDialog(source: String) {
        if (source.isAbsUrl()) {
            val cache = ACache.get(cacheDir = false)
            val urls = cache.getAsString(importRecordKey)?.splitNotBlank(",")?.toMutableList()
                ?: mutableListOf()
            if (!urls.contains(source)) {
                urls.add(0, source)
                cache.put(importRecordKey, urls.joinToString(","))
            }
        }
        ImportAutoTaskDialog(source).show(supportFragmentManager, "ImportAutoTaskDialog")
    }

    private fun showHelp() {
        runCatching {
            val md = assets.open("web/help/md/autoTaskHelp.md").readBytes().decodeToString()
            viewModel.showHelp(md)
        }.onFailure { viewModel.showHelp("帮助文档暂不可用") }
    }
}
