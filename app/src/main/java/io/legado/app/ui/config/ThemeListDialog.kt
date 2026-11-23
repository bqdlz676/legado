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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.config.settings.ConfirmSheet
import io.legado.app.utils.GSON
import io.legado.app.utils.getClipText
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi

/**
 * 主题列表弹窗（新写 Compose 宿主，保留类名供主题分区调用）。
 *
 * 主题读写 / 应用复用 [ThemeConfig]；列表行支持应用、分享、删除，顶栏从剪贴板导入主题配置。
 */
class ThemeListDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent { ThemeListDialogContent(onDismiss = { dismissAllowingStateLoss() }) }
    }

    @Composable
    private fun ThemeListDialogContent(onDismiss: () -> Unit) {
        val context = LocalContext.current
        var items by remember { mutableStateOf(ThemeConfig.configList.toList()) }
        var deleteIndex by remember { mutableStateOf<Int?>(null) }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.text.BasicText(
                    text = context.getString(R.string.theme_list),
                    style = androidx.compose.ui.text.TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 18.sp18(),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    ),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                InkButton("剪贴板导入", variant = InkButtonVariant.GHOST) {
                    context.getClipText()?.let { json ->
                        if (ThemeConfig.addConfig(json)) {
                            items = ThemeConfig.configList.toList()
                        } else {
                            context.toastOnUi("格式不对,添加失败")
                        }
                    }
                }
            }
            LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(items) { index, config ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = config.themeName,
                            modifier = Modifier.weight(1f),
                            style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
                        )
                        InkButton(context.getString(R.string.share), variant = InkButtonVariant.GHOST) {
                            context.share(GSON.toJson(config), "主题分享")
                        }
                        InkButton(context.getString(R.string.delete), variant = InkButtonVariant.GHOST) {
                            deleteIndex = index
                        }
                    }
                }
            }
        }

        deleteIndex?.let { index ->
            ConfirmSheet(
                visible = true,
                title = context.getString(R.string.delete),
                message = context.getString(R.string.sure_del),
                danger = true,
                onConfirm = {
                    ThemeConfig.delConfig(index)
                    items = ThemeConfig.configList.toList()
                },
                onDismiss = { deleteIndex = null },
            )
        }
    }

    private fun Int.sp18() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
    private fun Int.sp15() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
}
