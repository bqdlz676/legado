package io.legado.app.ui.replace.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeTextTransfer
import io.legado.app.utils.toastOnUi

/**
 * 落文 · 替换规则编辑宿主（Compose 自绘壳）。
 *
 * 保留旧 [startIntent] 入口口径（列表页 / 阅读页净化弹窗 / 阅读页长按菜单均按此跳转）。
 * 表单编排归 [ReplaceEditScreen]，草稿与预览归 [ReplaceEditViewModel]。
 */
class ReplaceEditActivity : FragmentActivity() {

    companion object {
        private const val TRANSFER_THRESHOLD = 4000

        fun startIntent(
            context: Context,
            id: Long,
            pattern: String = "",
            isRegex: Boolean = true,
            scope: String? = null,
        ): Intent = Intent(context, ReplaceEditActivity::class.java).apply {
            putExtra("id", id)
            putExtra("pattern", pattern)
            putExtra("isRegex", isRegex)
            putExtra("scope", scope)
        }
    }

    private val viewModel: ReplaceEditViewModel by viewModels()

    private val codeEdit = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val text = data.getStringExtra("text")
            ?: data.getStringExtra("textFile")?.let { path ->
                CodeTextTransfer.read(this, path).also { CodeTextTransfer.delete(this, path) }
            }
        if (text != null) {
            viewModel.update { rule -> rule.copy(pattern = text) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onExit = {
            setResult(RESULT_OK)
            finish()
        }
        viewModel.load(
            id = intent.getLongExtra("id", -1L),
            pattern = intent.getStringExtra("pattern"),
            isRegex = intent.getBooleanExtra("isRegex", true),
            scope = intent.getStringExtra("scope"),
        )
        setContent {
            val state = viewModel.uiState.collectAsState().value
            ReplaceEditScreen(
                state = state,
                onBack = { viewModel.requestExit() },
                onPatch = { patch -> viewModel.update { patch } },
                onPreviewInput = viewModel::setPreviewInput,
                onSave = { viewModel.save { finish() } },
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

    private fun openFullEdit() {
        val current = viewModel.uiState.value.rule.pattern
        val intent = Intent(this, CodeEditActivity::class.java).apply {
            putExtra("title", "匹配模式")
            // 超长草稿走缓存文件，避开 Intent/Bundle 传输上限（旧 transfer 口径保留）。
            if (current.length > TRANSFER_THRESHOLD) {
                putExtra("textFile", CodeTextTransfer.write(this@ReplaceEditActivity, current))
            } else {
                putExtra("text", current)
            }
            putExtra("cursorPosition", current.length)
        }
        codeEdit.launch(intent)
    }

    private fun showHelp() {
        runCatching {
            val md = assets.open("web/help/md/replaceRuleHelp.md").readBytes().decodeToString()
            viewModel.showHelp(md)
        }.onFailure { viewModel.showHelp("帮助文档暂不可用") }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        viewModel.requestExit()
    }
}
