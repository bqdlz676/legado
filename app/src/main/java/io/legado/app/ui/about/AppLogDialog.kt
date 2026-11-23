package io.legado.app.ui.about

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.help.http.HttpLogRecord
import io.legado.app.help.http.HttpLogStore
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.rememberInkGesture
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.LogUtils
import io.legado.app.utils.eventObservable
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import java.io.File
import java.util.Date

/**
 * 应用日志弹窗（新写 Compose 宿主，类名保留供跨域 `showDialogFragment<AppLogDialog>()` 调用）。
 *
 * 日志读写复用 [AppLog] / [HttpLogStore]；行点击展开 HTTP 详情或异常堆栈；
 * 顶栏支持清空、导出分享。旧内部 LogAdapter 已移除。
 */
class AppLogDialog : DialogFragment() {

    companion object {
        private const val MAX_SHARE_TEXT = 64_000
    }

    private var logs by mutableStateOf(AppLog.logs)

    override fun onStart() {
        super.onStart()
        eventObservable<Boolean>(EventBus.APP_LOG_UPDATED)
            .observe(this, Observer { logs = AppLog.logs })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent { AppLogContent(logs = logs, onClear = ::confirmClear, onExport = ::exportLogs, onOpen = ::openEntry) }
    }

    private fun confirmClear() {
        alert(R.string.clear, R.string.clear_log_confirm) {
            yesButton {
                AppLog.clear()
                HttpLogStore.clear()
                logs = AppLog.logs
            }
            noButton()
        }
    }

    private fun exportLogs() {
        val text = AppLog.exportText(logs)
        if (text.isBlank()) {
            toastOnUi(R.string.no_log); return
        }
        if (text.length <= MAX_SHARE_TEXT) {
            requireContext().share(text, getString(R.string.log)); return
        }
        runCatching {
            val file = File(requireContext().cacheDir, "applog.txt")
            file.writeText(text)
            requireContext().share(file, "text/plain")
        }.onFailure { toastOnUi(R.string.can_not_share) }
    }

    private fun openEntry(item: Triple<Long, String, Throwable?>) {
        val httpId = HttpLogRecord.parseId(item.second)
        val httpRecord = httpId?.let(HttpLogStore::get)
        when {
            httpId != null -> showDialogFragment(TextDialog("HTTP", httpRecord?.detail ?: item.second))
            else -> item.third?.let { showDialogFragment(TextDialog("Log", it.stackTraceToString())) }
        }
    }
}

@Composable
private fun AppLogContent(
    logs: List<Triple<Long, String, Throwable?>>,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onOpen: (Triple<Long, String, Throwable?>) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.text.BasicText(
                text = context.getString(R.string.log),
                style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 18.sp18()),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            InkButton(context.getString(R.string.clear), variant = InkButtonVariant.GHOST, onClick = onClear)
            InkButton(context.getString(R.string.share), variant = InkButtonVariant.GHOST, onClick = onExport)
        }
        LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs) { item ->
                val gesture = rememberInkGesture(enabled = true, onClick = { onOpen(item) })
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).then(gesture.gestureModifier),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = LogUtils.logTimeFormat.format(Date(item.first)),
                        style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textTertiary, fontSize = 12.sp12()),
                    )
                    androidx.compose.foundation.text.BasicText(
                        text = item.second,
                        style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 14.sp14()),
                    )
                }
            }
        }
    }
}

private fun Int.sp18() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp14() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp12() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
