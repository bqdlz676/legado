package io.legado.app.ui.config.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkBottomSheet
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant

/**
 * 设置页弹层：数字步进盘 / 文本编辑 / 列表单选 / 多选 / 确认框。
 *
 * 统一挂在 [InkBottomSheet] 茶玻毛玻璃之上；旧版的 NumberPickerDialog / EditTextPreference
 * 弹窗 / selector 全部替换为这里的自绘弹层。弹层内不引 Material 组件。
 */

/** 数字步进盘：[min]–[max]，[unit] 为单位文案；[onDefault] 非空时提供「恢复默认」。 */
@Composable
fun NumberPickerSheet(
    visible: Boolean,
    title: String,
    min: Int,
    max: Int,
    current: Int,
    unit: String? = null,
    defaultLabel: String? = null,
    onDefault: (() -> Unit)? = null,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // B29：不按 visible 早退摘子树——离场段 visible=false 时子树保留组合，由 InkBottomSheet
    // 内部保留帧条件（progress 归 0 且 !visible）收束；宿主在动画收束后才清空槽位摘子树。
    var value by remember(current) { mutableIntStateOf(current.coerceIn(min, max)) }
    InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetStepperButton("−") { if (value > min) value-- }
                Spacer(Modifier.width(28.dp))
                BasicText(
                    text = value.toString() + (unit?.let { " $it" } ?: ""),
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                Spacer(Modifier.width(28.dp))
                SheetStepperButton("+") { if (value < max) value++ }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDefault != null && defaultLabel != null) {
                    InkButton(defaultLabel, variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f)) {
                        onDefault()
                        onDismiss()
                    }
                }
                InkButton("确定", modifier = Modifier.weight(1f)) {
                    onConfirm(value)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun SheetStepperButton(label: String, onClick: () -> Unit) {
    InkButton(label, variant = InkButtonVariant.SECONDARY, onClick = onClick)
}

/** 文本编辑弹层：[password] 时不显示明文（落盘口径与旧 EditTextPreference 一致）。 */
@Composable
fun TextEditSheet(
    visible: Boolean,
    title: String,
    initial: String?,
    hint: String,
    password: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // B29：离场段不早退，子树保留播离场动画（口径见 NumberPickerSheet 注释）。
    var text by remember(initial) { mutableStateOf(initial ?: "") }
    InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                decorationBox = { inner ->
                    if (text.isBlank()) {
                        BasicText(hint, TextStyle(color = ComponentTokens.textTertiary, fontSize = 14.sp))
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton("取消", variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f), onClick = onDismiss)
                InkButton("确定", modifier = Modifier.weight(1f)) {
                    onConfirm(text.trim())
                    onDismiss()
                }
            }
        }
    }
}

/** 列表单选弹层：[entries] 展示，[values] 落盘；[current] 为当前值。 */
@Composable
fun ListPickSheet(
    visible: Boolean,
    title: String,
    entries: List<String>,
    values: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // B29：离场段不早退，子树保留播离场动画（口径见 NumberPickerSheet 注释）。
    InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {
        Column(
            Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            entries.forEachIndexed { index, label ->
                val value = values.getOrElse(index) { label }
                val selected = value == current
                SettingRowShell(onClick = { onPick(value); onDismiss() }, enabled = true) {
                    BasicText(
                        text = label,
                        style = TextStyle(
                            color = if (selected) ComponentTokens.textAccent else ComponentTokens.textPrimary,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Serif,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    if (selected) {
                        BasicText("·", TextStyle(color = ComponentTokens.brandGold, fontSize = 18.sp))
                    }
                }
            }
        }
    }
}

/** 多选弹层：[items] 文案，[checked] 初始勾选；确认时回传最终勾选布尔数组。 */
@Composable
fun MultiChoiceSheet(
    visible: Boolean,
    title: String,
    items: List<String>,
    checked: BooleanArray,
    onConfirm: (BooleanArray) -> Unit,
    onDismiss: () -> Unit,
) {
    // B29：离场段不早退，子树保留播离场动画（口径见 NumberPickerSheet 注释）。
    val state = remember(checked) { checked.copyOf() }
    InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            items.forEachIndexed { index, label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = label,
                        style = TextStyle(color = ComponentTokens.textPrimary, fontSize = 16.sp, fontFamily = FontFamily.Serif),
                    )
                    Spacer(Modifier.weight(1f))
                    InkSwitch(checked = state[index], enabled = true, onCheckedChange = { state[index] = it })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton("取消", variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f), onClick = onDismiss)
                InkButton("确定", modifier = Modifier.weight(1f)) {
                    onConfirm(state.copyOf())
                    onDismiss()
                }
            }
        }
    }
}

/** 确认弹层：危险动作用朱砂标题。 */
@Composable
fun ConfirmSheet(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // B29：离场段不早退，子树保留播离场动画（口径见 NumberPickerSheet 注释）。
    InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            BasicText(
                text = message,
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton("取消", variant = InkButtonVariant.SECONDARY, modifier = Modifier.weight(1f), onClick = onDismiss)
                InkButton(
                    confirmLabel,
                    variant = if (danger) InkButtonVariant.GHOST else InkButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f),
                ) {
                    onConfirm()
                    onDismiss()
                }
            }
        }
    }
}
