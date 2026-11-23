package io.legado.app.ui.book.source.edit

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeTextTransfer
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.search.InkSearchActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.code.EditSafety
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.UrlOptionDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.startActivity
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 落文 · 书源编辑页宿主（Compose 重写；类名保留，外部入口以 `sourceUrl` extra 打开）。
 *
 * 旧编排：ActivityBookSourceEditBinding + BookSourceEditAdapter + TabLayout + KeyboardToolPop，
 * 全部拆除；本类只承担：编辑器 / 扫码 / 文件契约、Fragment 对话框、保存前后跳转、未保存退出确认。
 */
class BookSourceEditActivity : FragmentActivity(), SourceEditActions, VariableDialog.Callback {

    private val vm by viewModels<SourceEditViewModel>()

    private var pendingEditTab: EditTab? = null
    private var pendingEditKey: String? = null
    private var pendingResultText: String? = null
    private var pendingResultCursor: Int = -1
    private var pendingEditTextPath: String? = null

    private var showExitConfirm by mutableStateOf(false)

    private val jsSourceEdit = registerForActivityResult(
        StartActivityContract(JsSourceEditActivity::class.java)
    ) { result ->
        setResult(result.resultCode, result.data)
        finish()
    }

    private val qrCodeResult = registerForActivityResult(QrCodeResult()) { text ->
        text ?: return@registerForActivityResult
        vm.importSource(text) { }
    }

    private val selectDoc = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                toastOnUi("读取文件失败")
            } else {
                vm.insertIntoActiveField(text)
            }
        }
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingEditTextPath?.let { CodeTextTransfer.delete(this, it) }
        pendingEditTextPath = null
        if (result.resultCode == RESULT_OK) {
            pendingResultText = result.data?.getStringExtra("text")
                ?: result.data?.getStringExtra("textFile")?.let { path ->
                    CodeTextTransfer.read(this, path).also { CodeTextTransfer.delete(this, path) }
                }
            pendingResultCursor = result.data?.getIntExtra("cursorPosition", -1) ?: -1
            applyPendingEditResult()
        } else {
            clearPendingEditResult()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirectJsSourceUrl = intent.getStringExtra("sourceUrl")
            ?.takeIf { appDb.bookSourceDao.hasJsSource(it) }

        setContent {
            val state by vm.uiState.collectAsState()
            val typeLabels = resources.getStringArray(R.array.book_type).toList()
            SourceEditScreen(state = state, vm = vm, actions = this, typeLabels = typeLabels)
            if (showExitConfirm) {
                ExitConfirmDialog(
                    onConfirm = { finish() },
                    onDismiss = { showExitConfirm = false },
                )
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (isDirty()) showExitConfirm = true else finish()
        }

        vm.initData(intent) {
            vm.uiState.value.source?.takeIf { it.isJsSource() }?.let { source ->
                jsSourceEdit.launch { putExtra("sourceUrl", source.bookSourceUrl) }
                return@initData
            }
        }

        if (redirectJsSourceUrl != null) {
            if (savedInstanceState == null) {
                jsSourceEdit.launch { putExtra("sourceUrl", redirectJsSourceUrl) }
            }
            return
        }

        if (!LocalConfig.ruleHelpVersionIsLast) {
            showHelpMd("ruleHelp")
        }
    }

    // ───────────────────────────── 动作实现 ─────────────────────────────

    override fun onBack() {
        if (isDirty()) showExitConfirm = true else finish()
    }

    override fun onSave() {
        val source = vm.assemble()
        val errors = vm.validate(source)
        if (errors.isNotEmpty()) {
            vm.postToast(io.legado.app.ui.components.ToastKind.FAILURE, getString(R.string.source_field_error))
            return
        }
        vm.save(source) { saved ->
            setResult(RESULT_OK, Intent().putExtra("origin", saved.bookSourceUrl))
            finish()
        }
    }

    override fun onDebug() = saveThen { source ->
        startActivity<BookSourceDebugActivity> { putExtra("key", source.bookSourceUrl) }
    }

    override fun onLogin() = saveThen { source ->
        startActivity<SourceLoginActivity> {
            putExtra("type", "bookSource")
            putExtra("key", source.bookSourceUrl)
        }
    }

    override fun onSearch() = saveThen { source -> InkSearchActivity.start(this, source) }

    override fun onClearCookie() {
        vm.clearCookie(vm.assemble().bookSourceUrl)
        toastOnUi(R.string.cookie)
    }

    override fun onCopy() = sendToClip(vm.toJson(vm.assemble()))

    override fun onPaste() = vm.pasteSource { }

    override fun onQr() = qrCodeResult.launch(null)

    override fun onShareText() = share(vm.toJson(vm.assemble()))

    override fun onShareQr() {
        shareWithQr(
            vm.toJson(vm.assemble()),
            getString(R.string.share_book_source),
            ErrorCorrectionLevel.L,
        )
    }

    override fun onShowLog() = showDialog(AppLogDialog())

    override fun onHelp() = showHelpMd("ruleHelp")

    override fun onSetVariable() = saveThen { source ->
        lifecycleScope.launch {
            val comment = source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(Dispatchers.IO) { source.getVariable() }
            showDialog(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment,
                ),
            )
        }
    }

    override fun onFullscreenEdit(tab: EditTab, field: EditField) {
        openTextEditor(tab, field, 0)
    }

    override fun onInsertUrlOption() {
        UrlOptionDialog(this) { vm.insertIntoActiveField(it) }.show()
    }

    override fun onInsertGroup() {
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) { appDb.bookSourceDao.allGroups() }
            selector(groups) { _, group, _ -> vm.insertIntoActiveField(group) }
        }
    }

    override fun onSelectFile() {
        selectDoc.launch { mode = HandleFileContract.FILE }
    }

    override fun onShowDoc(name: String) = showHelpMd(name)

    override fun setVariable(key: String, variable: String?) {
        vm.setVariable(variable)
    }

    // ───────────────────────────── 保存前后跳转 ─────────────────────────────

    private fun saveThen(onSuccess: (BookSource) -> Unit) {
        val source = vm.assemble()
        if (vm.validate(source).isNotEmpty()) {
            vm.postToast(io.legado.app.ui.components.ToastKind.FAILURE, getString(R.string.source_field_error))
            return
        }
        vm.save(source) { onSuccess(it) }
    }

    private fun isDirty(): Boolean {
        val original = vm.uiState.value.source ?: BookSource()
        return !vm.assemble().equal(original)
    }

    // ───────────────────────────── 全屏编辑器契约 ─────────────────────────────

    private fun openTextEditor(tab: EditTab, field: EditField, cursorPosition: Int) {
        pendingEditTab = tab
        pendingEditKey = field.key
        val text = field.value.orEmpty()
        val textPath = text.takeIf { it.length > EditSafety.MAX_INLINE_TEXT_LENGTH }
            ?.let { CodeTextTransfer.write(this, it) }
        pendingEditTextPath = textPath
        val intent = Intent(this, CodeEditActivity::class.java).apply {
            putExtra("useTextFile", true)
            if (textPath != null) putExtra("textFile", textPath) else putExtra("text", text)
            putExtra("title", getString(field.labelRes))
            putExtra("cursorPosition", cursorPosition)
        }
        runCatching { textEditLauncher.launch(intent) }
            .onFailure {
                CodeTextTransfer.delete(this, textPath)
                pendingEditTextPath = null
                throw it
            }
    }

    private fun applyPendingEditResult() {
        val tab = pendingEditTab ?: return
        val key = pendingEditKey ?: return
        pendingResultText?.let { vm.updateField(tab, key, it) }
        clearPendingEditResult()
    }

    private fun clearPendingEditResult() {
        pendingEditTab = null
        pendingEditKey = null
        pendingResultText = null
        pendingResultCursor = -1
    }

    // ───────────────────────────── 对话框 / 帮助 ─────────────────────────────

    private fun showDialog(dialog: DialogFragment) {
        dialog.show(supportFragmentManager, dialog::class.simpleName)
    }

    private fun showHelpMd(fileName: String) {
        val mdText = runCatching {
            String(assets.open("web/help/md/$fileName.md").readBytes())
        }.getOrNull() ?: "帮助文档缺失：$fileName"
        showDialog(TextDialog(getString(R.string.help), mdText, TextDialog.Mode.MD, showToc = true))
    }
}

/** 未保存退出确认（Compose 自绘；不引 Material AlertDialog）。 */
@Composable
private fun ExitConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(io.legado.app.ui.components.ComponentTokens.bgOverlay)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(io.legado.app.ui.components.ComponentTokens.bgPage)
                .padding(20.dp),
        ) {
            androidx.compose.foundation.text.BasicText(
                text = stringResource(R.string.source_exit_confirm),
                style = TextStyle(
                    color = io.legado.app.ui.components.ComponentTokens.textPrimary,
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                io.legado.app.ui.components.InkButton(
                    text = stringResource(R.string.no),
                    variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY,
                    onClick = onDismiss,
                )
                Spacer(Modifier.width(10.dp))
                io.legado.app.ui.components.InkButton(
                    text = stringResource(R.string.yes),
                    onClick = onConfirm,
                )
            }
        }
    }
}
