package io.legado.app.ui.about

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.rememberInkGesture
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.delete
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.list
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileFilter

/**
 * 崩溃日志弹窗（新写 Compose 宿主，类名保留供跨域调用）。
 *
 * 文件扫描 / 清空 / 读取逻辑保留在 [CrashViewModel]；旧内部 LogAdapter 已移除。
 */
class CrashLogsDialog : DialogFragment() {

    private val viewModel by viewModels<CrashViewModel>()
    private var files by mutableStateOf<List<FileDoc>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent { CrashLogContent(files = files, onClear = { viewModel.clearCrashLog() }, onOpen = ::showLogFile) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.logLiveData.observe(viewLifecycleOwner) { files = it }
        viewModel.initData()
    }

    private fun showLogFile(fileDoc: FileDoc) {
        viewModel.readFile(fileDoc) { text ->
            if (isAdded) showDialogFragment(TextDialog(fileDoc.name, text))
        }
    }

    class CrashViewModel(application: Application) : BaseViewModel(application) {

        val logLiveData = MutableLiveData<List<FileDoc>>()

        fun initData() {
            execute {
                val list = arrayListOf<FileDoc>()
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.listFiles(FileFilter { it.isFile })
                    ?.forEach { list.add(FileDoc.fromFile(it)) }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true).find("crash")?.list { !it.isDir }?.let { list.addAll(it) }
                }
                list.sortedByDescending { it.name }.distinctBy { it.name }
            }.onSuccess { logLiveData.postValue(it) }
        }

        fun readFile(fileDoc: FileDoc, success: (String) -> Unit) {
            execute { String(fileDoc.readBytes()) }.onSuccess { success(it) }
                .onError { context.toastOnUi(it.localizedMessage) }
        }

        fun clearCrashLog() {
            execute {
                context.externalCacheDir?.getFile("crash")?.let { FileUtils.delete(it, false) }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    FileDoc.fromUri(Uri.parse(backupPath), true).find("crash")?.delete()
                }
            }.onError { context.toastOnUi(it.localizedMessage) }.onFinally { initData() }
        }
    }
}

@Composable
private fun CrashLogContent(
    files: List<FileDoc>,
    onClear: () -> Unit,
    onOpen: (FileDoc) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.text.BasicText(
                text = context.getString(R.string.crash_log),
                style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 18.sp18()),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            InkButton(context.getString(R.string.clear), variant = InkButtonVariant.GHOST, onClick = onClear)
        }
        LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files) { doc ->
                val gesture = rememberInkGesture(enabled = true, onClick = { onOpen(doc) })
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).then(gesture.gestureModifier),
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = doc.name,
                        style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 14.sp14()),
                    )
                }
            }
        }
    }
}

private fun Int.sp18() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp14() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
