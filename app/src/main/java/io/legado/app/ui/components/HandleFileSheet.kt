package io.legado.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.lib.dialogs.SelectItem

/**
 * 落文 · 文件分发动作面板（透明宿主内的 Compose 弹层）。
 *
 * 列出系统 / 应用内选择器、上传、手动输入等动作；手动输入目录 / 图片链接走独立小弹窗。
 * 触达行 ≥ 48dp，颜色只取 [ComponentTokens]。
 */
@Composable
fun HandleFileSheet(
    title: String,
    actions: List<SelectItem<Int>>,
    onAction: (SelectItem<Int>) -> Unit,
    onCancel: () -> Unit,
    showManualDir: Boolean,
    showManualImg: Boolean,
    onDismissManualDir: () -> Unit,
    onDismissManualImg: () -> Unit,
    onConfirmDir: (String) -> Unit,
    onConfirmImg: (String) -> Unit,
) {
    InkHalfSheet(visible = true, onDismiss = onCancel, title = title, content = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(actions) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightInkRow()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onAction(item) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            item.title,
                            style = TextStyle(
                                color = ComponentTokens.textPrimary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Serif,
                            ),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton("取消", onCancel, variant = InkButtonVariant.GHOST)
            }
        }
    })

    if (showManualDir) {
        ManualInputDialog(
            hint = "目录路径",
            onDismiss = onDismissManualDir,
            onConfirm = onConfirmDir,
        )
    }
    if (showManualImg) {
        ManualInputDialog(
            hint = "图片链接或文件路径",
            onDismiss = onDismissManualImg,
            onConfirm = onConfirmImg,
        )
    }
}

private fun Modifier.heightInkRow(): Modifier = this.height(48.dp)

@Composable
private fun ManualInputDialog(
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolTextField(hint, input, { input = it })
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton("取消", onDismiss, variant = InkButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
                InkButton("确定", { onConfirm(input.trim()) }, variant = InkButtonVariant.PRIMARY)
            }
        }
    }
}
