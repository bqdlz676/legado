package io.legado.app.ui.autoTask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.ui.components.InkSwitch
import io.legado.app.ui.components.ToolAction
import io.legado.app.ui.components.ToolPageScaffold
import io.legado.app.ui.components.ToolRow
import io.legado.app.ui.components.ToolSectionLabel
import io.legado.app.ui.components.ToolTextField
import io.legado.app.ui.components.toolListArrangement
import io.legado.app.ui.config.settings.rememberSheetExitController
import io.legado.app.ui.sheet.HalfSheetExitHost

/**
 * 落文 · 自动任务管理页（Compose 自绘）。
 *
 * 功能对照旧 `AutoTaskActivity` + `AutoTaskAdapter`：搜索 / 启停 / 编辑 / 调试 / 日志查看与清除 /
 * 删除 / 上下移排序 / 新建 / 本地导入 / 在线导入 / 全部导出 / 帮助 / 多选批量
 * 启停·批量定时·导出所选·删除。
 *
 * 兜底：无任务 → 空态 + 新建；加载失败 → 错误文案 + 重试；Cron 非法 → 输入框错误提示。
 */
@Composable
fun AutoTaskScreen(
    state: AutoTaskListState,
    visibleRules: List<AutoTaskUi>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onRevert: () -> Unit,
    onToggleEnabled: (AutoTaskUi, Boolean) -> Unit,
    onEdit: (AutoTaskUi) -> Unit,
    onDebug: (AutoTaskUi) -> Unit,
    onShowLog: (AutoTaskUi) -> Unit,
    onRequestDelete: (AutoTaskUi) -> Unit,
    onDismissConfirm: () -> Unit,
    onExecuteDelete: (AutoTaskUi) -> Unit,
    onDeleteSelection: () -> Unit,
    onEnableSelection: () -> Unit,
    onDisableSelection: () -> Unit,
    onBatchCron: (String) -> Unit,
    onOpenBatchCron: () -> Unit,
    onCloseBatchCron: () -> Unit,
    onExportSelection: () -> Unit,
    onExportAll: () -> Unit,
    onMove: (AutoTaskUi, Boolean) -> Unit,
    onAdd: () -> Unit,
    onImportLocal: () -> Unit,
    onOpenImportOnline: () -> Unit,
    onCloseImportUrl: () -> Unit,
    onImportOnline: (String) -> Unit,
    onHelp: () -> Unit,
    onRetry: () -> Unit,
    onDismissHelp: () -> Unit,
    onClearLog: (String) -> Unit,
    onDismissLog: () -> Unit,
) {
    // B29：帮助半屏 / 运行日志半屏各持一份两段式离场控制器；程序化入口先过 canOpen 闸再 open()。
    val helpExit = rememberSheetExitController()
    val logExit = rememberSheetExitController()
    LaunchedEffect(state.helpText) {
        if (state.helpText != null && helpExit.canOpen) helpExit.open()
    }
    LaunchedEffect(state.logTarget) {
        if (state.logTarget != null && logExit.canOpen) logExit.open()
    }
    ToolPageScaffold(
        title = "自动任务",
        onBack = onBack,
        actions = listOf(
            ToolAction("帮助", onHelp),
            ToolAction("导出", onExportAll),
            ToolAction("在线", onOpenImportOnline),
            ToolAction("本地", onImportLocal),
            ToolAction("新建", onAdd),
        ),
    ) {
        SearchBox(query = state.query, onQueryChange = onQueryChange)
        when {
            state.loading -> LoadingBody()
            state.error != null -> ErrorBody(state.error ?: "", onRetry)
            visibleRules.isEmpty() -> EmptyBody(onAdd)
            else -> RuleList(
                rules = visibleRules,
                selection = state.selection,
                selectionMode = state.selectionMode,
                onToggleSelect = onToggleSelect,
                onToggleEnabled = onToggleEnabled,
                onEdit = onEdit,
                onDebug = onDebug,
                onShowLog = onShowLog,
                onRequestDelete = onRequestDelete,
                onMove = onMove,
            )
        }
    }

    if (state.selectionMode) {
        SelectionBar(
            count = state.selectionCount,
            total = visibleRules.size,
            onSelectAll = onSelectAll,
            onRevert = onRevert,
            onDelete = onDeleteSelection,
            onEnable = onEnableSelection,
            onDisable = onDisableSelection,
            onOpenBatchCron = onOpenBatchCron,
            onExport = onExportSelection,
        )
    }

    state.confirmDelete?.let { target ->
        ConfirmDialog(
            title = "删除自动任务",
            message = "确认删除「${target.name}」？",
            onConfirm = { onExecuteDelete(target) },
            onDismiss = onDismissConfirm,
        )
    }

    state.logTarget?.let { target ->
        HalfSheetExitHost(logExit, onFinished = onDismissLog) {
            LogSheet(
                task = target,
                visible = logExit.visible,
                onClear = { onClearLog(target.id) },
                onDismiss = { logExit.beginExit() },
            )
        }
    }

    if (state.batchCronOpen) {
        BatchCronDialog(
            onDismiss = onCloseBatchCron,
            onConfirm = onBatchCron,
        )
    }

    if (state.importUrlOpen) {
        ImportUrlDialog(
            onDismiss = onCloseImportUrl,
            onConfirm = onImportOnline,
        )
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
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(ComponentTokens.cardRadius))
            .background(ComponentTokens.bgSurface)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(ComponentTokens.textAccent),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        "搜索任务名称",
                        style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 14.sp),
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun LoadingBody() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.text.BasicText(
            "载入中…",
            style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 14.sp),
        )
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    InkEmptyState(
        message = message.ifBlank { "自动任务读取失败" },
        offline = true,
        actionLabel = "重试",
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun EmptyBody(onAdd: () -> Unit) {
    InkEmptyState(
        message = "还没有自动任务，新建一条定时任务",
        actionLabel = "新建",
        onAction = onAdd,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun RuleList(
    rules: List<AutoTaskUi>,
    selection: Set<String>,
    selectionMode: Boolean,
    onToggleSelect: (String) -> Unit,
    onToggleEnabled: (AutoTaskUi, Boolean) -> Unit,
    onEdit: (AutoTaskUi) -> Unit,
    onDebug: (AutoTaskUi) -> Unit,
    onShowLog: (AutoTaskUi) -> Unit,
    onRequestDelete: (AutoTaskUi) -> Unit,
    onMove: (AutoTaskUi, Boolean) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = toolListArrangement,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(rules, key = { it.id }) { rule ->
            ToolRow(
                onClick = { if (selectionMode) onToggleSelect(rule.id) else onEdit(rule) },
                selected = rule.id in selection,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            androidx.compose.foundation.text.BasicText(
                                rule.name,
                                style = TextStyle(
                                    color = ComponentTokens.textPrimary,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Serif,
                                ),
                            )
                            androidx.compose.foundation.text.BasicText(
                                // C2R6：末段为最近运行时间；从未运行显示"未运行"，配色沿用次级文本 token。
                                listOfNotNull(rule.cron, rule.comment, rule.lastRunLabel)
                                    .joinToString(" · "),
                                style = TextStyle(
                                    color = ComponentTokens.textTertiary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                ),
                            )
                        }
                        InkSwitch(checked = rule.enable, onToggle = { onToggleEnabled(rule, it) })
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MiniAction("调试", { onDebug(rule) })
                        MiniAction("日志", { onShowLog(rule) })
                        MiniAction("上移", { onMove(rule, false) })
                        MiniAction("下移", { onMove(rule, true) })
                        MiniAction("编辑", { onEdit(rule) })
                        MiniAction("删除", { onRequestDelete(rule) }, danger = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, onClick: () -> Unit, danger: Boolean = false) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            label,
            style = TextStyle(
                color = if (danger) ComponentTokens.textAccent else ComponentTokens.textSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
            ),
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    total: Int,
    onSelectAll: () -> Unit,
    onRevert: () -> Unit,
    onDelete: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onOpenBatchCron: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.foundation.text.BasicText(
                "已选 $count / $total",
                style = TextStyle(color = ComponentTokens.textPrimary, fontSize = 14.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("全选", onSelectAll, variant = InkButtonVariant.GHOST)
                InkButton("反选", onRevert, variant = InkButtonVariant.GHOST)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("启用", onEnable, variant = InkButtonVariant.SECONDARY)
                InkButton("停用", onDisable, variant = InkButtonVariant.SECONDARY)
                InkButton("定时", onOpenBatchCron, variant = InkButtonVariant.SECONDARY)
                InkButton("导出", onExport, variant = InkButtonVariant.SECONDARY)
                InkButton("删除", onDelete, variant = InkButtonVariant.PRIMARY)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.foundation.text.BasicText(
                title,
                style = TextStyle(color = ComponentTokens.textPrimary, fontSize = 16.sp, fontFamily = FontFamily.Serif),
            )
            androidx.compose.foundation.text.BasicText(
                message,
                style = TextStyle(color = ComponentTokens.textSecondary, fontSize = 14.sp),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton("取消", onDismiss, variant = InkButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
                InkButton("确认", onConfirm, variant = InkButtonVariant.PRIMARY)
            }
        }
    }
}

@Composable
private fun LogSheet(
    task: AutoTaskUi,
    visible: Boolean,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = task.lastLog ?: task.lastError ?: task.lastResult
    InkHalfSheet(visible = visible, onDismiss = onDismiss, title = task.name, content = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.text.BasicText(
                message ?: "暂无运行日志",
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 20.sp,
                ),
            )
            Row(horizontalArrangement = Arrangement.End) {
                InkButton("清除日志", { onClear(); onDismiss() }, variant = InkButtonVariant.GHOST)
            }
        }
    })
}

@Composable
private fun BatchCronDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var cron by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolSectionLabel("批量更新定时")
            ToolTextField("Cron 表达式", cron, { cron = it }, placeholder = "*/30 * * * *")
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton("取消", onDismiss, variant = InkButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
                InkButton("确定", { onConfirm(cron.trim()) }, variant = InkButtonVariant.PRIMARY)
            }
        }
    }
}

@Composable
private fun ImportUrlDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolSectionLabel("在线导入")
            ToolTextField("任务 URL", url, { url = it }, placeholder = "https://")
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton("取消", onDismiss, variant = InkButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
                InkButton("导入", { onConfirm(url.trim()) }, variant = InkButtonVariant.PRIMARY)
            }
        }
    }
}
