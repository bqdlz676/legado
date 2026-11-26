package io.legado.app.ui.book.read.ink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.rememberInkGesture

/**
 * 内容编辑面板 · 底部动作区。
 *
 * 〔职责边界〕本件只装次级文字动作（搜索 / 复制全部 / 仅显示正文 / 重置）与
 * 取消 / 保存主按钮一行；保存按钮可用性由状态层 [InkContentEditState.contentValidation]
 * 加加载 / 保存态共同裁决，本件只做展示与回调搬运。
 * 切分理由：动作区与编辑器主体无共享滚动态，仅依赖状态与回调签名，独立成件。
 */

/** 底部动作区：次级动作（搜索 / 复制全部 / 仅显示正文 / 重置）一行；取消 / 保存主按钮一行。 */
@Composable
internal fun FooterActions(
    state: InkContentEditState,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onCopyAll: () -> Unit,
    onTogglePlainText: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val saveEnabled = !state.loading && !state.saving &&
        state.contentValidation is ContentValidation.Valid

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterLink(context.getString(R.string.search)) {
                state.searchVisible = !state.searchVisible
            }
            FooterLink(context.getString(R.string.copy_all), onCopyAll)
            FooterLink(context.getString(R.string.content_edit_plain_text)) {
                onTogglePlainText(!state.plainText)
            }
            FooterLink(context.getString(R.string.reset), onReset)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InkButton(
                text = context.getString(R.string.cancel),
                variant = InkButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
                enabled = !state.saving,
                onClick = onDismiss,
            )
            InkButton(
                text = context.getString(R.string.action_save),
                variant = InkButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f),
                enabled = saveEnabled,
                loading = state.saving,
                onClick = onSave,
            )
        }
    }
}

/** 次级文字动作：茶褐色墨触文字钮，不抢主按钮视觉。 */
@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .then(rememberInkGesture(enabled = true, onClick = onClick).gestureModifier)
            .padding(vertical = 8.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = ComponentTokens.textSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}
