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
import io.legado.app.model.BookCover
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.config.settings.InkSwitch
import io.legado.app.utils.toastOnUi

/**
 * 封面规则配置弹窗（新写 Compose 宿主，保留类名供封面分区调用）。
 *
 * 规则读写复用 [BookCover.getCoverRule] / [BookCover.saveCoverRule] / [BookCover.delCoverRule]。
 */
class CoverRuleConfigDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent { CoverRuleDialog(onDismiss = { dismissAllowingStateLoss() }) }
    }

    @Composable
    private fun CoverRuleDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current
        val initial = remember { runCatching { BookCover.getCoverRule() }.getOrNull() }
        var enable by remember { mutableStateOf(initial?.enable ?: true) }
        var searchUrl by remember { mutableStateOf(initial?.searchUrl.orEmpty()) }
        var coverRule by remember { mutableStateOf(initial?.coverRule.orEmpty()) }

        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    text = stringRes(R.string.enable),
                    modifier = Modifier.weight(1f),
                    style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
                )
                InkSwitch(checked = enable, enabled = true) { enable = it }
            }
            FormField(stringRes(R.string.r_search_url), searchUrl) { searchUrl = it }
            FormField(stringRes(R.string.rule_cover_url), coverRule) { coverRule = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InkButton(stringRes(R.string.btn_default_s), variant = InkButtonVariant.GHOST, modifier = Modifier.weight(1f)) {
                    BookCover.delCoverRule()
                    onDismiss()
                }
                InkButton(stringRes(R.string.cancel), variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f), onClick = onDismiss)
                InkButton(stringRes(R.string.ok), modifier = Modifier.weight(1f)) {
                    if (searchUrl.isBlank() || coverRule.isBlank()) {
                        context.toastOnUi("搜索url和cover规则不能为空")
                    } else {
                        BookCover.saveCoverRule(BookCover.CoverRule(enable, searchUrl, coverRule))
                        onDismiss()
                    }
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
