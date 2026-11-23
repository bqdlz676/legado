package io.legado.app.ui.dict.rule

import androidx.compose.foundation.clickable
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

/**
 * 落文 · 词典规则管理页（Compose 自绘，切片-03 / 切片-04 / 切片-15）。
 *
 * 功能对照旧 `DictRuleActivity` + `DictRuleAdapter`：
 * 列表 / 启用开关 / 单选多选 / 全选反选 / 批量删除启用禁用 / 分享 / 导出 /
 * 新建 / 编辑 / 删除 / 上下移排序 / 本地导入 / 在线导入 / 二维码导入 / 默认规则 / 帮助。
 *
 * 兜底：空规则集 → 空态引导 + 新建入口；读取失败 → 错误文案 + 重试；选择集随数据清空回收。
 */
@Composable
fun DictRuleScreen(
    state: DictRuleListState,
    onBack: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onRevert: () -> Unit,
    onToggleEnabled: (DictRuleUi) -> Unit,
    onEdit: (DictRuleUi) -> Unit,
    onRequestDelete: (DictRuleUi) -> Unit,
    onDismissConfirm: () -> Unit,
    onExecuteDelete: (DictRuleUi) -> Unit,
    onDeleteSelection: () -> Unit,
    onEnableSelection: () -> Unit,
    onDisableSelection: () -> Unit,
    onShareSelection: () -> Unit,
    onExportSelection: () -> Unit,
    onMove: (DictRuleUi, Boolean) -> Unit,
    onAdd: () -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: (String) -> Unit,
    onImportQr: () -> Unit,
    onImportDefault: () -> Unit,
    onHelp: () -> Unit,
    onRetry: () -> Unit,
    onSaveEdit: (DictRuleUi) -> Unit,
    onDismissEdit: () -> Unit,
    onCopyEdit: () -> Unit,
    onPasteEdit: () -> Unit,
    onDismissHelp: () -> Unit,
) {
    var showUrlDialog by remember { mutableStateOf(false) }

    ToolPageScaffold(
        title = "词典规则",
        onBack = onBack,
        actions = buildList {
            add(ToolAction("帮助", onHelp))
            add(ToolAction("默认", onImportDefault))
            add(ToolAction("二维码", onImportQr))
            add(ToolAction("在线", { showUrlDialog = true }))
            add(ToolAction("本地", onImportLocal))
            add(ToolAction("新建", onAdd))
        },
    ) {
        when {
            state.loading -> LoadingBody()
            state.error != null -> ErrorBody(state.error ?: "", onRetry)
            state.rules.isEmpty() -> EmptyBody(onAdd, onImportDefault)
            else -> RuleList(
                state = state,
                onToggleSelect = onToggleSelect,
                onToggleEnabled = onToggleEnabled,
                onEdit = onEdit,
                onRequestDelete = onRequestDelete,
                onMove = onMove,
            )
        }
    }

    if (state.selectionMode) {
        SelectionBar(
            count = state.selectionCount,
            total = state.rules.size,
            onSelectAll = onSelectAll,
            onRevert = onRevert,
            onDelete = onDeleteSelection,
            onEnable = onEnableSelection,
            onDisable = onDisableSelection,
            onShare = onShareSelection,
            onExport = onExportSelection,
        )
    }

    // 单条删除确认
    state.confirmDelete?.let { target ->
        ConfirmDialog(
            title = "删除词典规则",
            message = "确认删除「${target.name}」？",
            onConfirm = { onExecuteDelete(target) },
            onDismiss = onDismissConfirm,
        )
    }

    // 编辑 / 新建 Sheet
    state.editTarget?.let { draft ->
        EditSheet(
            initial = draft,
            creating = state.creating,
            onSave = onSaveEdit,
            onDismiss = onDismissEdit,
            onCopy = onCopyEdit,
            onPaste = onPasteEdit,
        )
    }

    // 在线导入 URL
    if (showUrlDialog) {
        ImportUrlDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url ->
                showUrlDialog = false
                onImportOnline(url)
            },
        )
    }

    // 帮助文档
    state.helpText?.let { md ->
        HelpSheet(mdText = md, onDismiss = onDismissHelp)
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
        message = message.ifBlank { "词典规则读取失败" },
        offline = true,
        actionLabel = "重试",
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun EmptyBody(onAdd: () -> Unit, onImportDefault: () -> Unit) {
    InkEmptyState(
        message = "还没有词典规则，新建一条或导入默认规则",
        actionLabel = "新建",
        onAction = onAdd,
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().padding(bottom = 72.dp), contentAlignment = Alignment.BottomCenter) {
        InkButton("导入默认规则", onImportDefault, variant = InkButtonVariant.GHOST)
    }
}

@Composable
private fun RuleList(
    state: DictRuleListState,
    onToggleSelect: (String) -> Unit,
    onToggleEnabled: (DictRuleUi) -> Unit,
    onEdit: (DictRuleUi) -> Unit,
    onRequestDelete: (DictRuleUi) -> Unit,
    onMove: (DictRuleUi, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = toolListArrangement,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(state.rules, key = { it.name }) { rule ->
            val selected = rule.name in state.selection
            ToolRow(
                onClick = { if (state.selectionMode) onToggleSelect(rule.name) else onEdit(rule) },
                selected = selected,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            androidx.compose.foundation.text.BasicText(
                                rule.name.ifBlank { "未命名规则" },
                                style = TextStyle(
                                    color = ComponentTokens.textPrimary,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Serif,
                                ),
                            )
                            if (rule.urlRule.isNotBlank()) {
                                androidx.compose.foundation.text.BasicText(
                                    rule.urlRule,
                                    style = TextStyle(
                                        color = ComponentTokens.textTertiary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.SansSerif,
                                    ),
                                )
                            }
                        }
                        InkSwitch(checked = rule.enabled, onToggle = { onToggleEnabled(rule) })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
}

@Composable
private fun MiniAction(label: String, onClick: () -> Unit, danger: Boolean = false) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .height(32.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
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
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                InkButton("分享", onShare, variant = InkButtonVariant.SECONDARY)
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.foundation.text.BasicText(
                title,
                style = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                ),
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
private fun EditSheet(
    initial: DictRuleUi,
    creating: Boolean,
    onSave: (DictRuleUi) -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var urlRule by remember { mutableStateOf(initial.urlRule) }
    var showRule by remember { mutableStateOf(initial.showRule) }
    LaunchedEffect(initial.name) {
        name = initial.name
        urlRule = initial.urlRule
        showRule = initial.showRule
    }
    InkHalfSheet(visible = true, onDismiss = onDismiss, title = if (creating) "新建词典规则" else "编辑词典规则", content = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolTextField("规则名称", name, { name = it })
            ToolTextField("URL 规则", urlRule, { urlRule = it }, multiLine = true)
            ToolTextField("展示规则", showRule, { showRule = it }, multiLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("复制", onCopy, variant = InkButtonVariant.GHOST)
                InkButton("粘贴", onPaste, variant = InkButtonVariant.GHOST)
                InkButton("保存", {
                    onSave(
                        initial.copy(name = name.trim(), urlRule = urlRule, showRule = showRule),
                    )
                }, variant = InkButtonVariant.PRIMARY)
            }
        }
    })
}

@Composable
private fun ImportUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolSectionLabel("在线导入")
            ToolTextField("规则 URL", url, { url = it }, placeholder = "https://")
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton("取消", onDismiss, variant = InkButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
                InkButton("导入", { onConfirm(url.trim()) }, variant = InkButtonVariant.PRIMARY)
            }
        }
    }
}

@Composable
private fun HelpSheet(mdText: String, onDismiss: () -> Unit) {
    InkHalfSheet(visible = true, onDismiss = onDismiss, title = "帮助", content = {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            androidx.compose.foundation.text.BasicText(
                mdText,
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 20.sp,
                ),
            )
        }
    })
}

