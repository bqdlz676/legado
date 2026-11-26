package io.legado.app.ui.file

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.ToolRow
import io.legado.app.ui.components.ToolSectionLabel
import io.legado.app.ui.components.ToolTextField
import io.legado.app.ui.components.contract.ComponentVisualState
import java.io.File

/**
 * 落文 · 文件 / 目录选择弹窗（Compose 自绘壳）。
 *
 * 保留旧 [show] 静态口径与 [CallBack] 回调契约，宿主 [HandleFileActivity] 接入方式不变。
 * 目录扫描归 [FilePickerViewModel]。兜底：根路径不可用 → 错误文案；未选择文件 → 确认禁用；
 * 非法文件夹名 → 校验提示。
 */
class FilePickerDialog : DialogFragment() {

    companion object {
        const val tag = "FileChooserDialog"

        fun show(
            manager: FragmentManager,
            mode: Int = HandleFileContract.FILE,
            title: String? = null,
            initPath: String? = null,
            isShowHideDir: Boolean = false,
            allowExtensions: Array<String>? = null,
        ) {
            FilePickerDialog().apply {
                val bundle = Bundle()
                bundle.putInt("mode", mode)
                bundle.putString("title", title)
                bundle.putBoolean("isShowHideDir", isShowHideDir)
                bundle.putString("initPath", initPath)
                bundle.putStringArray("allowExtensions", allowExtensions)
                arguments = bundle
            }.show(manager, tag)
        }
    }

    private val viewModel by viewModels<FilePickerViewModel>()

    interface CallBack {
        fun onResult(data: Intent)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel.initData(arguments)
        return ComposeView(requireContext()).apply {
            setContent {
                val state = viewModel.uiState.collectAsState().value
                var showCreateFolder by remember { mutableStateOf(false) }
                val title = arguments?.getString("title") ?: if (viewModel.isSelectDir) {
                    getString(io.legado.app.R.string.folder_chooser)
                } else {
                    getString(io.legado.app.R.string.file_chooser)
                }
                PickerContent(
                    title = title,
                    state = state,
                    isSelectDir = viewModel.isSelectDir,
                    onCreateFolder = { showCreateFolder = true },
                    onOpenDir = viewModel::navigate,
                    onPop = viewModel::pop,
                    onJump = viewModel::jumpTo,
                    onSelectFile = viewModel::select,
                    onCancel = { dismissAllowingStateLoss() },
                    onOk = ::submit,
                )
                if (showCreateFolder) {
                    CreateFolderDialog(
                        onDismiss = { showCreateFolder = false },
                        onConfirm = { name ->
                            viewModel.createFolder(name)
                            showCreateFolder = false
                        },
                    )
                }
            }
        }
    }

    private fun submit() {
        val path = when {
            viewModel.isSelectDir -> viewModel.lastDir?.path
            else -> stateSelectFile()?.path
        }
        if (path == null) return
        val data = Intent().setData(Uri.fromFile(File(path)))
        (parentFragment as? CallBack)?.onResult(data)
        (activity as? CallBack)?.onResult(data)
        dismissAllowingStateLoss()
    }

    private fun stateSelectFile(): File? = viewModel.uiState.value.selectFile

    @Composable
    private fun PickerContent(
        title: String,
        state: FilePickerState,
        isSelectDir: Boolean,
        onCreateFolder: () -> Unit,
        onOpenDir: (File) -> Unit,
        onPop: () -> Unit,
        onJump: (Int) -> Unit,
        onSelectFile: (File) -> Unit,
        onCancel: () -> Unit,
        onOk: () -> Unit,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    title,
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    modifier = Modifier.weight(1f),
                )
                InkButton("新建目录", onCreateFolder, variant = InkButtonVariant.GHOST)
            }

            Breadcrumb(state.subDocs, onPop, onJump)

            when {
                state.loading -> Box(Modifier.height(120.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.text.BasicText(
                        "载入中…",
                        style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 14.sp),
                    )
                }

                state.error != null -> InkEmptyState(
                    message = state.error ?: "目录读取失败",
                    offline = true,
                    state = ComponentVisualState.OFFLINE,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.files.isEmpty() -> InkEmptyState(
                    message = "当前目录为空",
                    state = ComponentVisualState.EMPTY,
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.files, key = { it.absolutePath }) { file ->
                        val isParent = state.subDocs.isNotEmpty() && file == state.subDocs.firstOrNull()
                        val selected = file == state.selectFile
                        ToolRow(
                            selected = selected,
                            onClick = {
                                when {
                                    isParent -> onPop()
                                    file.isDirectory -> onOpenDir(file)
                                    else -> onSelectFile(file)
                                }
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.text.BasicText(
                                    when {
                                        isParent -> "←"
                                        file.isDirectory -> "▸"
                                        else -> "▤"
                                    },
                                    style = TextStyle(
                                        color = ComponentTokens.brandGold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Serif,
                                    ),
                                    modifier = Modifier.width(24.dp),
                                )
                                androidx.compose.foundation.text.BasicText(
                                    if (isParent) ".." else file.name,
                                    style = TextStyle(
                                        color = ComponentTokens.textPrimary,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Serif,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton("取消", onCancel, variant = InkButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
                InkButton(
                    "确定",
                    onOk,
                    variant = InkButtonVariant.PRIMARY,
                    enabled = isSelectDir || state.selectFile != null,
                )
            }
        }
    }

    @Composable
    private fun Breadcrumb(segments: List<File>, onRoot: () -> Unit, onJump: (Int) -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crumb("root", onRoot)
            segments.forEachIndexed { index, file ->
                androidx.compose.foundation.text.BasicText(
                    "/",
                    style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 13.sp),
                )
                Crumb(file.name, { onJump(index) })
            }
        }
    }

    @Composable
    private fun Crumb(label: String, onClick: () -> Unit) {
        Box(
            Modifier
                .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            androidx.compose.foundation.text.BasicText(
                label,
                style = TextStyle(color = ComponentTokens.stoneBlue, fontSize = 13.sp),
            )
        }
    }

    @Composable
    private fun CreateFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
        var name by remember { mutableStateOf("") }
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolSectionLabel("新建目录")
                ToolTextField("文件夹名", name, { name = it })
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    InkButton("取消", onDismiss, variant = InkButtonVariant.GHOST)
                    Spacer(Modifier.width(8.dp))
                    InkButton(
                        "创建",
                        { if (name.isNotBlank()) onConfirm(name.trim()) },
                        variant = InkButtonVariant.PRIMARY,
                    )
                }
            }
        }
    }
}
