package io.legado.app.ui.autoTask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.legado.app.data.entities.AutoTaskRule
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.config.settings.rememberSheetExitController
import io.legado.app.ui.sheet.HalfSheetExitHost
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.ui.components.InkSwitch
import io.legado.app.ui.components.ToolAction
import io.legado.app.ui.components.ToolPageScaffold
import io.legado.app.ui.components.ToolSectionLabel
import io.legado.app.ui.components.ToolTextField

/** 全屏代码编辑目标字段。 */
enum class AutoTaskField { SCRIPT, HEADER, JSLIB, LOGIN_URL, LOGIN_UI, LOGIN_CHECK_JS }

/**
 * 落文 · 自动任务编辑页（Compose 自绘）。
 *
 * 功能对照旧 `AutoTaskEditActivity`：启用 / CookieJar / 名称 / Cron / 备注 / 脚本 /
 * 响应头 / JS 库 / 并发限速 / 登录 URL / 登录 UI / 登录校验 JS / 保存 / 调试源 / 登录 /
 * 复制 / 粘贴 / 全屏编辑 / 帮助 / 未保存退出确认。
 */
@Composable
fun AutoTaskEditScreen(
    state: AutoTaskEditState,
    onBack: () -> Unit,
    onPatch: (AutoTaskRule) -> Unit,
    onSave: () -> Unit,
    onDebugSource: () -> Unit,
    onLogin: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onFullEdit: (AutoTaskField) -> Unit,
    onHelp: () -> Unit,
    onRequestExit: () -> Unit,
    onDismissConfirmExit: () -> Unit,
    onConfirmExit: () -> Unit,
    onDismissHelp: () -> Unit,
) {
    val rule = state.draft
    // B29：帮助半屏两段式离场控制器；程序化入口先过 canOpen 闸再 open()。
    val helpExit = rememberSheetExitController()
    LaunchedEffect(state.helpText) {
        if (state.helpText != null && helpExit.canOpen) helpExit.open()
    }
    ToolPageScaffold(
        title = if (state.origin == null) "新建自动任务" else "编辑自动任务",
        onBack = onRequestExit,
        actions = listOf(
            ToolAction("帮助", onHelp),
            ToolAction("复制", onCopy),
            ToolAction("粘贴", onPaste),
            ToolAction("全屏", { onFullEdit(AutoTaskField.SCRIPT) }),
            ToolAction("调试", onDebugSource),
            ToolAction("登录", onLogin),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkSwitch(checked = rule.enable, onToggle = { onPatch(rule.copy(enable = it)) })
                Spacer(Modifier.width(10.dp))
                Label("启用任务")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkSwitch(
                    checked = rule.enabledCookieJar,
                    onToggle = { onPatch(rule.copy(enabledCookieJar = it)) },
                )
                Spacer(Modifier.width(10.dp))
                Label("启用 CookieJar")
            }

            ToolTextField("任务名称", rule.name, { onPatch(rule.copy(name = it)) })
            ToolTextField("Cron 表达式", rule.cron.orEmpty(), { onPatch(rule.copy(cron = it)) })
            ToolTextField("备注", rule.comment.orEmpty(), { onPatch(rule.copy(comment = it.ifBlank { null })) })
            ToolTextField("任务脚本", rule.script, { onPatch(rule.copy(script = it)) }, multiLine = true)

            ToolSectionLabel("高级配置")
            ToolTextField(
                "响应头",
                rule.header.orEmpty(),
                { onPatch(rule.copy(header = it.ifBlank { null })) },
                multiLine = true,
            )
            ToolTextField(
                "JS 库",
                rule.jsLib.orEmpty(),
                { onPatch(rule.copy(jsLib = it.ifBlank { null })) },
                multiLine = true,
            )
            ToolTextField("并发限速", rule.concurrentRate.orEmpty(), {
                onPatch(rule.copy(concurrentRate = it.ifBlank { null }))
            })

            ToolSectionLabel("登录配置")
            ToolTextField(
                "登录 URL",
                rule.loginUrl.orEmpty(),
                { onPatch(rule.copy(loginUrl = it.ifBlank { null })) },
            )
            ToolTextField(
                "登录 UI",
                rule.loginUi.orEmpty(),
                { onPatch(rule.copy(loginUi = it.ifBlank { null })) },
                multiLine = true,
            )
            ToolTextField(
                "登录校验 JS",
                rule.loginCheckJs.orEmpty(),
                { onPatch(rule.copy(loginCheckJs = it.ifBlank { null })) },
                multiLine = true,
            )
        }
    }

    if (state.confirmExit) {
        Dialog(onDismissRequest = onDismissConfirmExit) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Label("放弃未保存的修改？")
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
private fun Label(text: String) {
    androidx.compose.foundation.text.BasicText(
        text,
        style = TextStyle(color = ComponentTokens.textSecondary, fontSize = 14.sp),
    )
}
