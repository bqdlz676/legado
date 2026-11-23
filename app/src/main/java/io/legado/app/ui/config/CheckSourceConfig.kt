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
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.model.CheckSource
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.config.settings.InkSwitch
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi

/**
 * 书源校验配置弹窗（新写 Compose 宿主，保留类名供书源域 showDialogFragment 调用）。
 *
 * 校验项 / 超时仍落在 [CheckSource] 单例，保存后回写偏好摘要；旧版 Binding 校验逻辑不再复用。
 */
class CheckSourceConfig : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent { CheckSourceDialog(onDismiss = { dismissAllowingStateLoss() }) }
    }
}

@Composable
private fun CheckSourceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var timeoutSec by remember { mutableStateOf((CheckSource.timeout / 1000).toString()) }
    var wComment by remember { mutableStateOf(CheckSource.wSourceComment) }
    var domain by remember { mutableStateOf(CheckSource.checkDomain) }
    var search by remember { mutableStateOf(CheckSource.checkSearch) }
    var discovery by remember { mutableStateOf(CheckSource.checkDiscovery) }
    var info by remember { mutableStateOf(CheckSource.checkInfo) }
    var category by remember { mutableStateOf(CheckSource.checkCategory) }
    var content by remember { mutableStateOf(CheckSource.checkContent) }

    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = context.getString(R.string.check_source_config),
            style = androidx.compose.ui.text.TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 18.sp18(),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            ),
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                text = context.getString(R.string.timeout),
                style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
            )
            BasicTextField(
                value = timeoutSec,
                onValueChange = { timeoutSec = it.filter(Char::isDigit) },
                modifier = Modifier.padding(start = 12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
            )
        }
        SwitchRow(context.getString(R.string.domain), domain) { domain = it }
        SwitchRow(context.getString(R.string.search), search) { search = it }
        SwitchRow(context.getString(R.string.discovery), discovery) { discovery = it }
        SwitchRow(context.getString(R.string.source_tab_info), info, enabled = search || discovery) { info = it }
        SwitchRow(context.getString(R.string.chapter_list), category, enabled = info) { category = it }
        SwitchRow(context.getString(R.string.main_body), content, enabled = category) { content = it }
        SwitchRow(context.getString(R.string.comment), wComment) { wComment = it }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            InkButton(context.getString(R.string.cancel), variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f), onClick = onDismiss)
            InkButton(context.getString(R.string.ok), modifier = Modifier.weight(1f)) {
                val sec = timeoutSec.toLongOrNull()
                if (sec == null || sec <= 0L) {
                    context.toastOnUi("${context.getString(R.string.timeout)}${context.getString(R.string.less_than)}0${context.getString(R.string.seconds)}")
                    return@InkButton
                }
                CheckSource.timeout = sec * 1000
                CheckSource.wSourceComment = wComment
                CheckSource.checkDomain = domain
                CheckSource.checkSearch = search
                CheckSource.checkDiscovery = discovery
                CheckSource.checkInfo = info
                CheckSource.checkCategory = category
                CheckSource.checkContent = content
                CheckSource.putConfig()
                context.putPrefString(PreferKey.checkSource, CheckSource.summary)
                onDismiss()
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
        )
        InkSwitch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

private fun Int.sp18() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp15() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
