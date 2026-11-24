package io.legado.app.ui.dict.rule

// F-73 冻结：本模块无具体规格，暂停扩写与新派单；须先出 ≥2 份候选规格→独立选型→再重做（审计 表四#10，C2 工具页·dict 域）

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import io.legado.app.ui.association.ImportDictRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.toastOnUi
import java.io.File

/**
 * 落文 · 词典规则管理宿主（Compose 自绘壳）。
 *
 * 类名保持旧 [DictRuleActivity] 不变，入口（我的页面 / Intent 直跳）与 Manifest 无需改动；
 * 本类只做生命周期、ActivityResult 接线与跨页跳转，列表编排归 [DictRuleScreen]，
 * 数据主控归 [DictRuleListViewModel]。旧 XML / Binding / Adapter / ViewHolder 全部移除。
 */
class DictRuleActivity : FragmentActivity() {

    private val viewModel: DictRuleListViewModel by viewModels()
    private val importRecordKey = "dictRuleUrls"

    private val qrCodeResult = registerForActivityResult(QrCodeResult()) { result ->
        result ?: return@registerForActivityResult
        showImportDialog(result)
    }

    private val importDoc = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { showImportDialog(it.toString()) }
    }

    private val exportResult = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val url = uri.toString()
            toastOnUi(io.legado.app.R.string.export_success)
            sendToClip(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = viewModel.uiState.collectAsState().value
            DictRuleScreen(
                state = state,
                onBack = { finish() },
                onToggleSelect = viewModel::toggleSelect,
                onSelectAll = viewModel::selectAll,
                onRevert = viewModel::revertSelection,
                onToggleEnabled = viewModel::toggleEnabled,
                onEdit = viewModel::openEdit,
                onRequestDelete = viewModel::requestDelete,
                onDismissConfirm = viewModel::dismissConfirm,
                onExecuteDelete = viewModel::executeDelete,
                onDeleteSelection = viewModel::deleteSelection,
                onEnableSelection = viewModel::enableSelection,
                onDisableSelection = viewModel::disableSelection,
                onShareSelection = ::shareSelection,
                onExportSelection = ::exportSelection,
                onMove = viewModel::move,
                onAdd = viewModel::openCreate,
                onImportLocal = {
                    importDoc.launch {
                        mode = HandleFileContract.FILE
                        allowExtensions = arrayOf("txt", "json")
                    }
                },
                onImportOnline = ::showImportDialog,
                onImportQr = { qrCodeResult.launch() },
                onImportDefault = viewModel::importDefault,
                onHelp = ::showHelp,
                onRetry = viewModel::retry,
                onSaveEdit = viewModel::save,
                onDismissEdit = viewModel::dismissEdit,
                onCopyEdit = viewModel::copyCurrentDraft,
                onPasteEdit = viewModel::pasteRule,
                onDismissHelp = viewModel::dismissHelp,
            )
        }
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
        ImportDictRuleDialog(source).show(supportFragmentManager, "ImportDictRuleDialog")
    }

    private fun shareSelection() {
        val json = viewModel.selectionJson()
        if (json == "[]") return
        executeTempFile("dictRule_", ".json", json)?.let { file -> share(file) }
    }

    private fun exportSelection() {
        val json = viewModel.selectionJson()
        if (json == "[]") return
        exportResult.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "exportDictRule.json",
                json.toByteArray(),
                "application/json",
            )
        }
    }

    private fun executeTempFile(prefix: String, suffix: String, content: String): File? =
        runCatching {
            File.createTempFile(prefix, suffix, cacheDir).apply { writeText(content) }
        }.onFailure { toastOnUi(it.localizedMessage) }.getOrNull()

    private fun showHelp() {
        runCatching {
            val md = assets.open("web/help/md/dictRuleHelp.md").readBytes().decodeToString()
            viewModel.showHelp(md)
        }.onFailure { viewModel.showHelp("帮助文档暂不可用") }
    }

}
