package io.legado.app.ui.replace

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import io.legado.app.help.book.ContentProcessor
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.utils.ACache
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 落文 · 替换规则管理宿主（Compose 自绘壳）。
 *
 * 类名与 Manifest 入口保持不变；列表编排归 [ReplaceRuleScreen]，数据主控归
 * [ReplaceRuleListViewModel]。旧 XML / Binding / Adapter / 分组 Dialog 全部移除。
 * 页面销毁后刷新正文替换规则缓存（旧 `onDestroy` 语义保留）。
 */
class ReplaceRuleActivity : FragmentActivity() {

    private val viewModel: ReplaceRuleListViewModel by viewModels()
    private val importRecordKey = "replaceRuleRecordKey"

    private val qrCodeResult = registerForActivityResult(QrCodeResult()) { result ->
        result ?: return@registerForActivityResult
        showImportDialog(result)
    }

    private val importDoc = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { showImportDialog(it.toString()) }
    }

    private val exportResult = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            toastOnUi(io.legado.app.R.string.export_success)
            sendToClip(uri.toString())
        }
    }

    private val editActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) setResult(RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = viewModel.uiState.collectAsState().value
            ReplaceRuleScreen(
                state = state,
                onBack = { finish() },
                onQueryChange = viewModel::setQuery,
                onFilter = viewModel::applyFilter,
                onToggleSelect = viewModel::toggleSelect,
                onSelectAll = viewModel::selectAll,
                onRevert = viewModel::revertSelection,
                onToggleEnabled = viewModel::enable,
                onEdit = { openEdit(it.id) },
                onRequestDelete = viewModel::requestDelete,
                onDismissConfirm = viewModel::dismissConfirm,
                onExecuteDelete = viewModel::executeDelete,
                onDeleteSelection = viewModel::deleteSelection,
                onEnableSelection = viewModel::enableSelection,
                onDisableSelection = viewModel::disableSelection,
                onAddGroupSelection = viewModel::selectionAddToGroups,
                onRemoveGroupSelection = viewModel::selectionRemoveFromGroups,
                onTopSelection = viewModel::topSelection,
                onBottomSelection = viewModel::bottomSelection,
                onShareSelection = ::shareSelection,
                onExportSelection = ::exportSelection,
                onTop = viewModel::toTop,
                onBottom = viewModel::toBottom,
                onMove = viewModel::move,
                onAdd = { openEdit(-1) },
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
                    showImportDialog(source)
                },
                onImportQr = { qrCodeResult.launch() },
                onOpenGroupManage = viewModel::openGroupManage,
                onCloseGroupManage = viewModel::closeGroupManage,
                onGroupAdd = viewModel::addGroup,
                onGroupRename = viewModel::renameGroup,
                onGroupDelete = viewModel::deleteGroup,
                onToggleManual = viewModel::toggleManualReplace,
                onHelp = ::showHelp,
                onRetry = viewModel::retry,
                onDismissHelp = viewModel::dismissHelp,
            )
        }
    }

    private fun openEdit(id: Long) {
        editActivity.launch(ReplaceEditActivity.startIntent(this, id))
    }

    private fun showImportDialog(source: String) {
        if (source.isBlank()) {
            toastOnUi(io.legado.app.R.string.wrong_format)
            return
        }
        if (source.isAbsUrl()) {
            val cache = ACache.get(cacheDir = false)
            val urls = cache.getAsString(importRecordKey)?.splitNotBlank(",")?.toMutableList()
                ?: mutableListOf()
            if (!urls.contains(source)) {
                urls.add(0, source)
                cache.put(importRecordKey, urls.joinToString(","))
            }
        }
        ImportReplaceRuleDialog(source).show(supportFragmentManager, "ImportReplaceRuleDialog")
    }

    private fun shareSelection() {
        val json = viewModel.selectionJson()
        if (json == "[]") return
        runCatching {
            File.createTempFile("replaceRule_", ".json", cacheDir).apply { writeText(json) }
        }.onSuccess { share(it) }.onFailure { toastOnUi(it.localizedMessage) }
    }

    private fun exportSelection() {
        val json = viewModel.selectionJson()
        if (json == "[]") return
        exportResult.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "exportReplaceRule.json",
                json.toByteArray(),
                "application/json",
            )
        }
    }

    private fun showHelp() {
        runCatching {
            val md = assets.open("web/help/md/replaceRuleHelp.md").readBytes().decodeToString()
            viewModel.showHelp(md)
        }.onFailure { viewModel.showHelp("帮助文档暂不可用") }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch(Dispatchers.IO) { ContentProcessor.upReplaceRules() }
    }
}
