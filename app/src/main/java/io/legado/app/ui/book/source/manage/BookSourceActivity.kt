package io.legado.app.ui.book.source.manage

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.SourceSharePassphrase
import io.legado.app.help.config.LocalConfig
import io.legado.app.model.CheckSource
import io.legado.app.model.Debug
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.edit.JsSourceEditActivity
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.search.InkSearchActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.eventObservable
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import java.io.File

/**
 * 落文 · 书源管理页宿主（Compose 重写；类名保留以兼容外部入口 PendingIntent / startActivity）。
 *
 * 旧编排：ActivityBookSourceBinding + BookSourceAdapter + 旧菜单 + Snackbar + 多个 AlertDialog，
 * 全部拆除；本类只承担：契约注册、页面跳转、Fragment 对话框、校验会话生命周期、亮屏策略。
 * 列表状态与数据操作收口在 [SourceManageViewModel]，渲染收口在 [SourceManageScreen]。
 */
class BookSourceActivity : FragmentActivity(), SourceManageActions {

    private val vm by viewModels<SourceManageViewModel>()

    private var checkSessionId: Long? = null
    private var exportStage: ExportStage? = null

    private val importDoc = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri -> showImportDialog(uri.toString()) }
    }

    private val exportDoc = registerForActivityResult(HandleFileContract()) { result ->
        val stage = exportStage
        exportStage = null
        result.uri?.let { uri ->
            val url = uri.toString()
            val summary = if (url.isAbsUrl()) DirectLinkUpload.getSummary() else ""
            vm.openSheet(ManageSheet.ExportResult(stage?.name.orEmpty(), url, url.isAbsUrl(), summary))
        }
    }

    private val qrResult = registerForActivityResult(QrCodeResult()) { text ->
        text ?: return@registerForActivityResult
        showImportDialog(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.uiState.collectAsState()
            SourceManageScreen(state = state, vm = vm, actions = this@BookSourceActivity)
        }
        observeCheckBus()
        if (!LocalConfig.bookSourcesHelpVersionIsLast) {
            showHelpMd("SourceMBookHelp")
        }
    }

    override fun onResume() {
        super.onResume()
        if (Debug.isChecking) {
            keepScreenOn(true)
            CheckSource.resume(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!Debug.isChecking) {
            Debug.debugMessageMap.clear()
        }
    }

    // ───────────────────────────── 宿主动作 ─────────────────────────────

    override fun onBack() = finish()

    override fun onAddSource() {
        startActivity<BookSourceEditActivity>()
    }

    override fun onAddJsSource() {
        startActivity<JsSourceEditActivity>()
    }

    override fun onImportLocal() {
        importDoc.launch {
            mode = HandleFileContract.FILE
            allowExtensions = arrayOf("txt", "json", "js")
        }
    }

    override fun onImportOnline(url: String?) {
        url?.let { showImportDialog(it) }
    }

    override fun onImportQr() {
        qrResult.launch()
    }

    override fun onEdit(source: BookSourcePart) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", source.bookSourceUrl)
        }
    }

    override fun onDebug(source: BookSourcePart) {
        startActivity<BookSourceDebugActivity> {
            putExtra("key", source.bookSourceUrl)
        }
    }

    override fun onSearch(source: BookSourcePart) {
        InkSearchActivity.start(this, source)
    }

    override fun onLogin(source: BookSourcePart) {
        startActivity<SourceLoginActivity> {
            putExtra("type", "bookSource")
            putExtra("key", source.bookSourceUrl)
        }
    }

    override fun onExport(selection: List<BookSourcePart>) {
        vm.prepareExport(selection) { file, name, isJs ->
            exportStage = ExportStage(file, name, isJs)
            exportDoc.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    name,
                    file,
                    if (isJs) "text/javascript" else "application/json",
                )
            }
        }
    }

    override fun onShare(selection: List<BookSourcePart>) {
        vm.prepareExport(selection) { file, _, isJs ->
            share(file, if (isJs) "text/javascript" else "text/*")
        }
    }

    override fun onCheckSource(keyword: String) {
        startCheck(keyword)
    }

    override fun onStopCheck() {
        checkSessionId?.let { CheckSource.stop(this, it) }
    }

    override fun onShowCheckConfig() {
        showDialog(CheckSourceConfig())
    }

    override fun onHelp() {
        showHelpMd("SourceMBookHelp")
    }

    override fun onCopyText(text: String) {
        sendToClip(text)
        toastOnUi(R.string.source_copied)
    }

    override fun onPassphraseShare(url: String) {
        if (!SourceSharePassphrase.canEncode(url)) {
            toastOnUi(R.string.source_passphrase_unsuitable)
            return
        }
        val passphrase = SourceSharePassphrase.encode(
            url = url,
            type = SourceSharePassphrase.Type.BOOK_SOURCE,
            expiryDays = DirectLinkUpload.getExpiryDate(),
        )
        sendToClip(passphrase)
        toastOnUi(R.string.source_passphrase_copied)
    }

    // ───────────────────────────── 校验会话 ─────────────────────────────

    private fun startCheck(keyword: String) {
        val selection = vm.uiState.value.selection
        if (selection.isEmpty()) {
            toastOnUi(R.string.source_check_select_first)
            return
        }
        if (keyword.isNotBlank()) {
            CheckSource.keyword = keyword
        }
        val sessionId = Debug.tryStartCheckSession()
        if (sessionId == null) {
            toastOnUi(R.string.source_check_busy)
            return
        }
        keepScreenOn(true)
        lifecycleScope.launch {
            try {
                CheckSource.start(this@BookSourceActivity, selection, sessionId)
                checkSessionId = sessionId
                vm.markChecking(true)
            } catch (error: Exception) {
                keepScreenOn(false)
                vm.markChecking(false)
                toastOnUi(error.localizedMessage ?: getString(R.string.source_check_start_failed))
            }
        }
    }

    private fun observeCheckBus() {
        eventObservable<Pair<Long, String>>(EventBus.CHECK_SOURCE).observe(this, Observer { payload ->
            val (sessionId, msg) = payload ?: return@Observer
            if (!Debug.isChecking(sessionId)) return@Observer
            checkSessionId = sessionId
            vm.markChecking(true, msg, cancelable = true)
        })
        eventObservable<Long>(EventBus.CHECK_SOURCE_DONE).observe(this, Observer { sessionId ->
            if (checkSessionId != null && checkSessionId != sessionId) return@Observer
            checkSessionId = null
            keepScreenOn(false)
            vm.markChecking(false)
        })
    }

    private fun keepScreenOn(on: Boolean) {
        val isScreenOn =
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == isScreenOn) return
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ───────────────────────────── 对话框 / 帮助 ─────────────────────────────

    private fun showImportDialog(source: String) {
        showDialog(ImportBookSourceDialog(source))
    }

    private fun showDialog(dialog: DialogFragment) {
        dialog.show(supportFragmentManager, dialog::class.simpleName)
    }

    private fun showHelpMd(fileName: String) {
        val mdText = runCatching {
            String(assets.open("web/help/md/$fileName.md").readBytes())
        }.getOrNull() ?: "帮助文档缺失：$fileName"
        showDialog(TextDialog(getString(R.string.help), mdText, TextDialog.Mode.MD, showToc = true))
    }

    private data class ExportStage(val file: File, val name: String, val isJs: Boolean)
}
