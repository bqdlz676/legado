package io.legado.app.ui.file

// F-73 冻结：本模块无具体规格，暂停扩写与新派单；须先出 ≥2 份候选规格→独立选型→再重做（审计 表四#10，C2 工具页·file 域）

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import io.legado.app.constant.AppConst
import io.legado.app.utils.openFileUri
import io.legado.app.utils.toastOnUi
import java.io.File

/**
 * 落文 · 本机文件浏览宿主（Compose 自绘壳）。
 *
 * 类名与 Manifest 入口不变；列表编排归 [FileManageScreen]，目录扫描归 [FileManageViewModel]。
 * 旧 XML / Binding / 双 Adapter 全部移除。返回键优先回退上一级目录，再退才结束页面。
 */
class FileManageActivity : FragmentActivity() {

    private val viewModel: FileManageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.init()
        setContent {
            val state = viewModel.uiState.collectAsState().value
            FileManageScreen(
                state = state,
                visibleFiles = viewModel.visibleFiles(),
                onBack = ::onBackPressed,
                onQueryChange = viewModel::setQuery,
                onOpenDir = viewModel::navigate,
                onOpenFile = ::openFile,
                onPop = viewModel::pop,
                onRetry = viewModel::retry,
                onJump = viewModel::jumpTo,
                onRequestDelete = viewModel::requestDelete,
                onDismissConfirm = viewModel::dismissConfirm,
                onExecuteDelete = viewModel::executeDelete,
            )
        }
    }

    private fun openFile(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, AppConst.authority, file)
            openFileUri(uri)
        }.onFailure { toastOnUi(it.localizedMessage) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.uiState.value.subDocs.isNotEmpty()) {
            viewModel.pop()
        } else {
            super.onBackPressed()
        }
    }
}
