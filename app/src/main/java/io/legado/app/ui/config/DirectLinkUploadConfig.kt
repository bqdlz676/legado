package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.SourceSharePassphrase
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.config.settings.InkSwitch
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 直链上传规则配置弹窗（新写 Compose 宿主，保留类名供其它设置项调用）。
 *
 * 规则读写复用 [DirectLinkUpload]；表单字段与旧版一致：上传 Url / 下载规则 / 注释 / 压缩 / 有效期。
 */
class DirectLinkUploadConfig : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent { DirectLinkUploadDialog(onDismiss = { dismissAllowingStateLoss() }) }
    }

    @Composable
    private fun DirectLinkUploadDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current
        val initial = remember { DirectLinkUpload.getRule() }
        var uploadUrl by remember { mutableStateOf(initial.uploadUrl) }
        var downloadRule by remember { mutableStateOf(initial.downloadUrlRule) }
        var summary by remember { mutableStateOf(initial.summary) }
        var expiry by remember { mutableStateOf(initial.expiryDate.toString()) }
        var compress by remember { mutableStateOf(initial.compress) }

        fun buildRule(): DirectLinkUpload.Rule? {
            if (uploadUrl.isBlank()) { context.toastOnUi("上传Url不能为空"); return null }
            if (downloadRule.isBlank()) { context.toastOnUi("下载Url规则不能为空"); return null }
            if (summary.isBlank()) { context.toastOnUi("注释不能为空"); return null }
            val days = expiry.toIntOrNull()
            if (days == null || days !in 0..SourceSharePassphrase.MAX_EXPIRY_DAYS) {
                context.toastOnUi(R.string.invalid_share_expiry_days); return null
            }
            return DirectLinkUpload.Rule(uploadUrl, downloadRule, summary, compress, days)
        }

        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(stringRes(R.string.upload_url), uploadUrl) { uploadUrl = it }
            FormField(stringRes(R.string.download_url_rule), downloadRule) { downloadRule = it }
            FormField(stringRes(R.string.summary), summary) { summary = it }
            FormField(stringRes(R.string.share_expiry_days), expiry) { expiry = it.filter(Char::isDigit) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    text = stringRes(R.string.is_compress),
                    modifier = Modifier.weight(1f),
                    style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
                )
                InkSwitch(checked = compress, enabled = true, onCheckedChange = { compress = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                InkButton(stringRes(R.string.import_default_rule), variant = InkButtonVariant.GHOST) {
                    DirectLinkUpload.defaultRules.firstOrNull()?.let {
                        uploadUrl = it.uploadUrl; downloadRule = it.downloadUrlRule
                        summary = it.summary; compress = it.compress; expiry = it.expiryDate.toString()
                    }
                }
                InkButton(stringRes(R.string.copy_rule), variant = InkButtonVariant.GHOST) {
                    buildRule()?.let { context.sendToClip(GSON.toJson(it)) }
                }
                InkButton(stringRes(R.string.paste_rule), variant = InkButtonVariant.GHOST) {
                    runCatching {
                        context.getClipText()?.let {
                            val rule = GSON.fromJsonObject<DirectLinkUpload.Rule>(it).getOrThrow()
                            uploadUrl = rule.uploadUrl; downloadRule = rule.downloadUrlRule
                            summary = rule.summary; compress = rule.compress; expiry = rule.expiryDate.toString()
                        }
                    }.onFailure { context.toastOnUi("剪贴板为空或格式不对") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InkButton(stringRes(R.string.test), variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f)) {
                    val rule = buildRule() ?: return@InkButton
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { DirectLinkUpload.upLoad("test.json", "{}", "application/json", rule) }
                                .getOrElse { it.localizedMessage ?: "ERROR" }
                        }
                        context.toastOnUi(result)
                    }
                }
                InkButton(stringRes(R.string.cancel), variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f), onClick = onDismiss)
                InkButton(stringRes(R.string.ok), modifier = Modifier.weight(1f)) {
                    buildRule()?.let { DirectLinkUpload.putConfig(it); onDismiss() }
                }
            }
        }
    }

    @Composable
    private fun FormField(label: String, value: String, onChange: (String) -> Unit) {
        Column {
            androidx.compose.foundation.text.BasicText(
                text = label,
                style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textSecondary, fontSize = 13.sp13()),
            )
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
            )
        }
    }

    @Composable
    private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
    private fun Int.sp15() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
    private fun Int.sp13() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
}
