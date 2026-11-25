package io.legado.app.ui.replace.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.ui.components.InkSwitch
import io.legado.app.ui.components.ToolAction
import io.legado.app.ui.components.ToolPageScaffold
import io.legado.app.ui.components.ToolSectionLabel
import io.legado.app.ui.components.ToolTextField
import io.legado.app.ui.config.settings.rememberSheetExitController
import io.legado.app.ui.sheet.HalfSheetExitHost

/**
 * 落文 · 替换规则编辑页（Compose 自绘）。
 *
 * 功能对照旧 `ReplaceEditActivity`：名称 / 分组 / 正则开关 / 匹配模式 / 替换文本 /
 * 作用范围（标题·来源·正文）/ 范围筛选 / 排除范围 / 超时毫秒 / 实时预览（输入·输出·错误分类）/
 * 复制规则 / 粘贴规则 / 全屏代码编辑 / 帮助 / 未保存退出确认。
 */
@Composable
fun ReplaceEditScreen(
    state: ReplaceEditState,
    onBack: () -> Unit,
    onPatch: (ReplaceRule) -> Unit,
    onPreviewInput: (String) -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onFullEdit: () -> Unit,
    onHelp: () -> Unit,
    onRequestExit: () -> Unit,
    onDismissConfirmExit: () -> Unit,
    onConfirmExit: () -> Unit,
    onDismissHelp: () -> Unit,
) {
    val rule = state.rule
    // B29：帮助半屏两段式离场控制器；程序化入口先过 canOpen 闸再 open()。
    val helpExit = rememberSheetExitController()
    LaunchedEffect(state.helpText) {
        if (state.helpText != null && helpExit.canOpen) helpExit.open()
    }
    ToolPageScaffold(
        title = if (rule.id > 0) "编辑替换规则" else "新建替换规则",
        onBack = onRequestExit,
        actions = listOf(
            ToolAction("帮助", onHelp),
            ToolAction("复制", onCopy),
            ToolAction("粘贴", onPaste),
            ToolAction("全屏", onFullEdit),
            ToolAction("保存", onSave),
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolTextField("规则名称", rule.name, { onPatch(rule.copy(name = it)) })
            ToolTextField("所属分组", rule.group.orEmpty(), { onPatch(rule.copy(group = it.ifBlank { null })) })

            Row(verticalAlignment = Alignment.CenterVertically) {
                InkSwitch(checked = rule.isRegex, onToggle = { onPatch(rule.copy(isRegex = it)) })
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicText(
                    "正则表达式",
                    style = TextStyle(color = ComponentTokens.textSecondary, fontSize = 14.sp),
                )
            }

            ToolTextField(
                "匹配模式",
                rule.pattern,
                { onPatch(rule.copy(pattern = it)) },
                multiLine = true,
            )
            ToolTextField(
                "替换文本",
                rule.replacement,
                { onPatch(rule.copy(replacement = it)) },
                multiLine = true,
            )

            ToolSectionLabel("作用范围")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScopeCheck("标题", rule.scopeTitle) { onPatch(rule.copy(scopeTitle = it)) }
                ScopeCheck("来源", rule.scopeSource) { onPatch(rule.copy(scopeSource = it)) }
                ScopeCheck("正文", rule.scopeContent) { onPatch(rule.copy(scopeContent = it)) }
            }
            ToolTextField(
                "范围筛选",
                rule.scope.orEmpty(),
                { onPatch(rule.copy(scope = it.ifBlank { null })) },
                placeholder = "书名 / 分组 / 来源规则",
            )
            ToolTextField(
                "排除范围",
                rule.excludeScope.orEmpty(),
                { onPatch(rule.copy(excludeScope = it.ifBlank { null })) },
            )
            ToolTextField(
                "超时（毫秒）",
                rule.timeoutMillisecond.toString(),
                { value ->
                    onPatch(rule.copy(timeoutMillisecond = value.trim().toLongOrNull() ?: 3000L))
                },
            )

            ToolSectionLabel("效果预览")
            ToolTextField(
                "样本文本",
                state.previewInput,
                onPreviewInput,
                multiLine = true,
                placeholder = "粘贴一段待净化的正文",
            )
            when {
                state.previewError != null -> androidx.compose.foundation.text.BasicText(
                    state.previewError,
                    style = TextStyle(color = ComponentTokens.textAccent, fontSize = 13.sp),
                )

                state.loading -> androidx.compose.foundation.text.BasicText(
                    "预览计算中…",
                    style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 13.sp),
                )

                state.previewOutput.isNotEmpty() -> Column(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    androidx.compose.foundation.text.BasicText(
                        state.previewOutput,
                        style = TextStyle(
                            color = ComponentTokens.textPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 22.sp,
                        ),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }

    if (state.confirmExit) {
        Dialog(onDismissRequest = onDismissConfirmExit) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                androidx.compose.foundation.text.BasicText(
                    "放弃未保存的修改？",
                    style = TextStyle(color = ComponentTokens.textPrimary, fontSize = 16.sp, fontFamily = FontFamily.Serif),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    InkButton("继续编辑", onDismissConfirmExit, variant = InkButtonVariant.GHOST)
                    Spacer(Modifier.width(8.dp))
                    InkButton("退出", onConfirmExit, variant = InkButtonVariant.PRIMARY)
                }
            }
        }
    }

    state.helpText?.let { md ->
        HalfSheetExitHost(helpExit, onFinished = { onDismissHelp() }) {
            InkHalfSheet(
                visible = helpExit.visible,
                onDismiss = { helpExit.beginExit() },
                title = "帮助",
                content = {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        androidx.compose.foundation.text.BasicText(
                            md,
                            style = TextStyle(
                                color = ComponentTokens.textSecondary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 20.sp,
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ScopeCheck(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSwitch(checked = checked, onToggle = onToggle)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(
            label,
            style = TextStyle(color = ComponentTokens.textSecondary, fontSize = 14.sp),
        )
    }
}
