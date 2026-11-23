package io.legado.app.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.update.AppUpdate
import io.legado.app.model.Download
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.openUrl
import io.legado.app.utils.toastOnUi
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 更新提示弹窗（新写 Compose 宿主，保留无参构造与 [AppUpdate.UpdateInfo] 构造、arguments 键不变）。
 *
 * 更新日志用 Markwon 渲染到 AndroidView 承载的 TextView；下载 / 镜像 / 浏览器 / 忽略版本动作逐条保留。
 */
class UpdateDialog() : DialogFragment() {

    constructor(updateInfo: AppUpdate.UpdateInfo) : this() {
        arguments = Bundle().apply {
            putString("newVersion", updateInfo.tagName)
            putString("updateBody", updateInfo.updateLog)
            putString("url", updateInfo.downloadUrl)
            putString("name", updateInfo.fileName)
            putString("backupUrl", updateInfo.backupDownloadUrl)
            putString("mirrorUrl", updateInfo.mirrorDownloadUrl)
            putString("alternateMirrorUrl", updateInfo.alternateMirrorDownloadUrl)
            putLong("size", updateInfo.size)
            putLong("createdAt", updateInfo.createdAt)
            putBoolean("isBeta", updateInfo.isBeta)
        }
    }

    private val isBetaUpdate: Boolean
        get() = arguments?.getBoolean("isBeta") == true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            val args = arguments
            UpdateContent(
                newVersion = args?.getString("newVersion").orEmpty(),
                updateBody = args?.getString("updateBody"),
                size = args?.getLong("size") ?: 0L,
                createdAt = args?.getLong("createdAt") ?: 0L,
                isBeta = isBetaUpdate,
                onDownload = { startDownload(args?.getString("url"), args?.getString("name")) },
                onDownloadBackup = { startDownload(args?.getString("backupUrl"), args?.getString("name")) },
                onDownloadMirror = { startDownload(args?.getString("mirrorUrl"), args?.getString("name")) },
                onDownloadAlternate = { startDownload(args?.getString("alternateMirrorUrl"), args?.getString("name")) },
                onOpenBrowser = {
                    val url = args?.getString("backupUrl").orEmpty()
                        .ifBlank { args?.getString("url").orEmpty() }
                    if (url.isNotBlank()) requireContext().openUrl(url)
                },
                onIgnore = {
                    LocalConfig.ignoreUpdateVersion = args?.getString("newVersion")
                    toastOnUi(R.string.ignore_this_version)
                    dismiss()
                },
                onCancel = { dismiss() },
            )
        }
    }

    private fun startDownload(url: String?, name: String?) {
        if (url.isNullOrBlank() || name.isNullOrBlank()) return
        Download.start(requireContext(), url, name, isAppUpdate = true)
        toastOnUi(R.string.download_start)
        dismiss()
    }
}

@Composable
private fun UpdateContent(
    newVersion: String,
    updateBody: String?,
    size: Long,
    createdAt: Long,
    isBeta: Boolean,
    onDownload: () -> Unit,
    onDownloadBackup: () -> Unit,
    onDownloadMirror: () -> Unit,
    onDownloadAlternate: () -> Unit,
    onOpenBrowser: () -> Unit,
    onIgnore: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    if (updateBody == null) {
        context.toastOnUi("没有数据")
        return
    }
    val metadata = remember(size, createdAt) {
        listOf(size, createdAt).let {
            val m = mutableListOf<String>()
            if (size > 0) m += ConvertUtils.formatFileSize(size)
            if (createdAt > 0) m += Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            m.joinToString(" · ")
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = newVersion,
            style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 20.sp20()),
        )
        if (metadata.isNotBlank()) {
            androidx.compose.foundation.text.BasicText(
                text = metadata,
                style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textSecondary, fontSize = 12.sp12()),
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).verticalScroll(rememberScrollState()),
            factory = { ctx -> TextView(ctx) },
            update = { textView ->
                Markwon.builder(context)
                    .usePlugin(GlideImagesPlugin.create(context))
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(TablePlugin.create(context))
                    .build()
                    .setMarkdown(textView, updateBody)
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (isBeta) {
                InkButton(context.getString(R.string.cancel), variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f), onClick = onCancel)
                InkButton(context.getString(R.string.beta_update_now), modifier = Modifier.weight(1f), onClick = onDownload)
            } else {
                InkButton(context.getString(R.string.action_download), modifier = Modifier.weight(1f), onClick = onDownload)
            }
        }
        if (!isBeta) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                InkButton(context.getString(R.string.action_download), variant = InkButtonVariant.GHOST, modifier = Modifier.weight(1f), onClick = onDownloadBackup)
                InkButton(context.getString(R.string.action_download), variant = InkButtonVariant.GHOST, modifier = Modifier.weight(1f), onClick = onDownloadMirror)
                InkButton(context.getString(R.string.action_download), variant = InkButtonVariant.GHOST, modifier = Modifier.weight(1f), onClick = onDownloadAlternate)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InkButton(context.getString(R.string.open_in_browser), variant = InkButtonVariant.GHOST, modifier = Modifier.weight(1f), onClick = onOpenBrowser)
            if (!isBeta) {
                InkButton(context.getString(R.string.ignore_this_version), variant = InkButtonVariant.GHOST, modifier = Modifier.weight(1f), onClick = onIgnore)
            }
        }
    }
}

private fun Int.sp20() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp12() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
