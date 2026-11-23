package io.legado.app.ui.autoTask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import io.legado.app.data.entities.AutoTaskRule
import io.legado.app.model.AutoTask
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeTextTransfer
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.toastOnUi

/**
 * 落文 · 自动任务编辑宿主（Compose 自绘壳）。
 *
 * 保留旧 [intent] 入口口径；表单编排归 [AutoTaskEditScreen]，草稿与校验归
 * [AutoTaskEditViewModel]。全屏编辑 / 调试源 / 登录跳转在本类接线。
 */
class AutoTaskEditActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_ID = "autoTaskId"
        private const val TRANSFER_THRESHOLD = 4000

        fun intent(context: Context, id: String): Intent =
            Intent(context, AutoTaskEditActivity::class.java).putExtra(EXTRA_ID, id)
    }

    private val viewModel: AutoTaskEditViewModel by viewModels()
    private var pendingField: AutoTaskField = AutoTaskField.SCRIPT

    private val codeEdit = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val text = result.data?.getStringExtra("text")
            ?: result.data?.getStringExtra("textFile")?.let { path ->
                CodeTextTransfer.read(this, path).also { CodeTextTransfer.delete(this, path) }
            }
        if (text != null) {
            viewModel.update { rule -> applyField(rule, pendingField, text) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onExit = {
            setResult(RESULT_OK)
            finish()
        }
        viewModel.load(intent.getStringExtra(EXTRA_ID))
        setContent {
            val state = viewModel.uiState.collectAsState().value
            AutoTaskEditScreen(
                state = state,
                onBack = { viewModel.requestExit() },
                onPatch = { patch -> viewModel.update { patch } },
                onSave = { viewModel.save { finish() } },
                onDebugSource = {
                    viewModel.save { saved ->
                        startActivity(AutoTaskDebugActivity.intent(this@AutoTaskEditActivity, saved.id))
                    }
                },
                onLogin = { openLogin() },
                onCopy = viewModel::copyRule,
                onPaste = viewModel::pasteRule,
                onFullEdit = ::openFullEdit,
                onHelp = ::showHelp,
                onRequestExit = viewModel::requestExit,
                onDismissConfirmExit = viewModel::dismissConfirmExit,
                onConfirmExit = viewModel::confirmExit,
                onDismissHelp = viewModel::dismissHelp,
            )
        }
    }

    private fun applyField(rule: AutoTaskRule, field: AutoTaskField, text: String): AutoTaskRule =
        when (field) {
            AutoTaskField.SCRIPT -> rule.copy(script = text)
            AutoTaskField.HEADER -> rule.copy(header = text.ifBlank { null })
            AutoTaskField.JSLIB -> rule.copy(jsLib = text.ifBlank { null })
            AutoTaskField.LOGIN_URL -> rule.copy(loginUrl = text.ifBlank { null })
            AutoTaskField.LOGIN_UI -> rule.copy(loginUi = text.ifBlank { null })
            AutoTaskField.LOGIN_CHECK_JS -> rule.copy(loginCheckJs = text.ifBlank { null })
        }

    private fun fieldValue(rule: AutoTaskRule, field: AutoTaskField): String = when (field) {
        AutoTaskField.SCRIPT -> rule.script
        AutoTaskField.HEADER -> rule.header.orEmpty()
        AutoTaskField.JSLIB -> rule.jsLib.orEmpty()
        AutoTaskField.LOGIN_URL -> rule.loginUrl.orEmpty()
        AutoTaskField.LOGIN_UI -> rule.loginUi.orEmpty()
        AutoTaskField.LOGIN_CHECK_JS -> rule.loginCheckJs.orEmpty()
    }

    private fun fieldTitle(field: AutoTaskField): String = when (field) {
        AutoTaskField.SCRIPT -> "任务脚本"
        AutoTaskField.HEADER -> "响应头"
        AutoTaskField.JSLIB -> "JS 库"
        AutoTaskField.LOGIN_URL -> "登录 URL"
        AutoTaskField.LOGIN_UI -> "登录 UI"
        AutoTaskField.LOGIN_CHECK_JS -> "登录校验 JS"
    }

    private fun openFullEdit(field: AutoTaskField) {
        pendingField = field
        val current = fieldValue(viewModel.uiState.value.draft, field)
        val intent = Intent(this, CodeEditActivity::class.java).apply {
            putExtra("title", fieldTitle(field))
            if (current.length > TRANSFER_THRESHOLD) {
                putExtra("textFile", CodeTextTransfer.write(this@AutoTaskEditActivity, current))
            } else {
                putExtra("text", current)
            }
            putExtra("cursorPosition", current.length)
        }
        codeEdit.launch(intent)
    }

    private fun openLogin() {
        viewModel.save { saved ->
            if (!AutoTask.buildSource(saved).hasLogin()) {
                toastOnUi(io.legado.app.R.string.source_no_login)
            } else {
                startActivity(Intent(this, SourceLoginActivity::class.java).apply {
                    putExtra("type", "autoTask")
                    putExtra("key", saved.id)
                })
            }
        }
    }

    private fun showHelp() {
        runCatching {
            val md = assets.open("web/help/md/autoTaskHelp.md").readBytes().decodeToString()
            viewModel.showHelp(md)
        }.onFailure { viewModel.showHelp("帮助文档暂不可用") }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        viewModel.requestExit()
    }
}
