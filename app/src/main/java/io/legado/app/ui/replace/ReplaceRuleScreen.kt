package io.legado.app.ui.replace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
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
 * 落文 · 替换规则管理页（Compose 自绘）。
 *
 * 功能对照旧 `ReplaceRuleActivity` + `ReplaceRuleAdapter` + `GroupManageDialog`：
 * 搜索 / 启用·停用·未分组·分组筛选 / 分组管理（新建·重命名·删除）/ 多选批量
 * 启用·停用·加入分组·移出分组·置顶·置底·分享·导出·删除 / 单条启停·编辑·置顶·置底·上下移 /
 * 本地·在线·二维码导入 / 默认手动替换开关 / 帮助。
 *
 * 兜底：空规则集 → 空态 + 新建；读取失败 → 错误文案 + 重试；非法分组名 → 输入校验文案。
 */
@Composable
fun ReplaceRuleScreen(
    state: ReplaceRuleListState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilter: (ReplaceFilter) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onRevert: () -> Unit,
    onToggleEnabled: (ReplaceRuleUi, Boolean) -> Unit,
    onEdit: (ReplaceRuleUi) -> Unit,
    onRequestDelete: (ReplaceRuleUi) -> Unit,
    onDismissConfirm: () -> Unit,
    onExecuteDelete: (ReplaceRuleUi) -> Unit,
    onDeleteSelection: () -> Unit,
    onEnableSelection: () -> Unit,
    onDisableSelection: () -> Unit,
    onAddGroupSelection: (String) -> Unit,
    onRemoveGroupSelection: (String) -> Unit,
    onTopSelection: () -> Unit,
    onBottomSelection: () -> Unit,
    onShareSelection: () -> Unit,
    onExportSelection: () -> Unit,
    onTop: (ReplaceRuleUi) -> Unit,
    onBottom: (ReplaceRuleUi) -> Unit,
    onMove: (ReplaceRuleUi, Boolean) -> Unit,
    onAdd: () -> Unit,
    onImportLocal: () -> Unit,
    onOpenImportOnline: () -> Unit,
    onCloseImportUrl: () -> Unit,
    onImportOnline: (String) -> Unit,
    onImportQr: () -> Unit,
    onOpenGroupManage: () -> Unit,
    onCloseGroupManage: () -> Unit,
    onGroupAdd: (String) -> Unit,
    onGroupRename: (String, String) -> Unit,
    onGroupDelete: (String) -> Unit,
    onToggleManual: () -> Unit,
    onHelp: () -> Unit,
    onRetry: () -> Unit,
    onDismissHelp: () -> Unit,
) {
    var batchGroupAction by remember { mutableStateOf<BatchGroup?>(null) }
    var groupEditTarget by remember { mutableStateOf<String?>(null) }

    ToolPageScaffold(
        title = "替换规则",
        onBack = onBack,
        actions = buildList {
            add(ToolAction("帮助", onHelp))
            add(ToolAction("分组", onOpenGroupManage))
            add(ToolAction(if (state.manualReplace) "手动·开" else "手动·关", onToggleManual))
            add(ToolAction("二维码", onImportQr))
            add(ToolAction("在线", onOpenImportOnline))
            add(ToolAction("本地", onImportLocal))
            add(ToolAction("新建", onAdd))
        },
    ) {
        SearchBox(query = state.query, onQueryChange = onQueryChange)
        FilterChips(
            groups = state.groups,
            active = state.activeFilter,
            onFilter = onFilter,
        )
        when {
            state.loading -> LoadingBody()
            state.error != null -> ErrorBody(state.error ?: "", onRetry)
            state.rules.isEmpty() -> EmptyBody(onAdd)
            else -> RuleList(
                state = state,
                onToggleSelect = onToggleSelect,
                onToggleEnabled = onToggleEnabled,
                onEdit = onEdit,
                onRequestDelete = onRequestDelete,
                onTop = onTop,
                onBottom = onBottom,
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
            onAddGroup = { batchGroupAction = BatchGroup.ADD },
            onRemoveGroup = { batchGroupAction = BatchGroup.REMOVE },
            onTop = onTopSelection,
            onBottom = onBottomSelection,
            onShare = onShareSelection,
            onExport = onExportSelection,
        )
    }

    state.confirmDelete?.let { target ->
        ConfirmDialog(
            title = "删除替换规则",
            message = "确认删除「${target.name}」？",
            onConfirm = { onExecuteDelete(target) },
            onDismiss = onDismissConfirm,
        )
    }

    if (state.groupManageOpen) {
        GroupManageSheet(
            groups = state.groups,
            onAdd = onGroupAdd,
            onRename = { old, new -> onGroupRename(old, new) },
            onDelete = onGroupDelete,
            onEditTarget = { groupEditTarget = it },
            onDismiss = onCloseGroupManage,
        )
    }

    batchGroupAction?.let { action ->
        GroupNameDialog(
            title = if (action == BatchGroup.ADD) "加入分组" else "移出分组",
            existing = state.groups,
            onDismiss = { batchGroupAction = null },
            onConfirm = { name ->
                if (action == BatchGroup.ADD) onAddGroupSelection(name)
                else onRemoveGroupSelection(name)
                batchGroupAction = null
            },
        )
    }

    if (state.importUrlOpen) {
        ImportUrlDialog(
            onDismiss = onCloseImportUrl,
            onConfirm = onImportOnline,
        )
    }

    state.helpText?.let { md ->
        InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助") {
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
        }
    }
}

private enum class BatchGroup { ADD, REMOVE }

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
                        "搜索名称 / 分组",
                        style = TextStyle(
                            color = ComponentTokens.textTertiary,
                            fontSize = 14.sp,
                        ),
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun FilterChips(
    groups: List<String>,
    active: ReplaceFilter,
    onFilter: (ReplaceFilter) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("全部", active == ReplaceFilter.All) { onFilter(ReplaceFilter.All) }
        Chip("启用", active == ReplaceFilter.Enabled) { onFilter(ReplaceFilter.Enabled) }
        Chip("停用", active == ReplaceFilter.Disabled) { onFilter(ReplaceFilter.Disabled) }
        Chip("未分组", active == ReplaceFilter.NoGroup) { onFilter(ReplaceFilter.NoGroup) }
        groups.forEach { group ->
            Chip(group, active == ReplaceFilter.Group(group)) { onFilter(ReplaceFilter.Group(group)) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
            .background(if (active) ComponentTokens.brandGold else ComponentTokens.bgSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            label,
            style = TextStyle(
                color = if (active) ComponentTokens.bgPage else ComponentTokens.textSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
            ),
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
        message = message.ifBlank { "替换规则读取失败" },
        offline = true,
        actionLabel = "重试",
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun EmptyBody(onAdd: () -> Unit) {
    InkEmptyState(
        message = "还没有替换规则，新建一条开始净化正文",
        actionLabel = "新建",
        onAction = onAdd,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun RuleList(
    state: ReplaceRuleListState,
    onToggleSelect: (Long) -> Unit,
    onToggleEnabled: (ReplaceRuleUi, Boolean) -> Unit,
    onEdit: (ReplaceRuleUi) -> Unit,
    onRequestDelete: (ReplaceRuleUi) -> Unit,
    onTop: (ReplaceRuleUi) -> Unit,
    onBottom: (ReplaceRuleUi) -> Unit,
    onMove: (ReplaceRuleUi, Boolean) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = toolListArrangement,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(state.rules, key = { it.id }) { rule ->
            val selected = rule.id in state.selection
            ToolRow(
                onClick = { if (state.selectionMode) onToggleSelect(rule.id) else onEdit(rule) },
                selected = selected,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            androidx.compose.foundation.text.BasicText(
                                rule.name.ifBlank { rule.pattern },
                                style = TextStyle(
                                    color = ComponentTokens.textPrimary,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Serif,
                                ),
                            )
                            androidx.compose.foundation.text.BasicText(
                                listOfNotNull(rule.group, if (rule.isRegex) "正则" else "字面")
                                    .joinToString(" · "),
                                style = TextStyle(
                                    color = ComponentTokens.textTertiary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                ),
                            )
                        }
                        InkSwitch(checked = rule.isEnabled, onToggle = { onToggleEnabled(rule, it) })
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MiniAction("置顶", { onTop(rule) })
                        MiniAction("置底", { onBottom(rule) })
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
    onAddGroup: () -> Unit,
    onRemoveGroup: () -> Unit,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
) {
    Dialog {
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
                InkButton("入组", onAddGroup, variant = InkButtonVariant.SECONDARY)
                InkButton("移组", onRemoveGroup, variant = InkButtonVariant.SECONDARY)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("置顶", onTop, variant = InkButtonVariant.SECONDARY)
                InkButton("置底", onBottom, variant = InkButtonVariant.SECONDARY)
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
private fun GroupManageSheet(
    groups: List<String>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onEditTarget: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    InkHalfSheet(visible = true, onDismiss = onDismiss, title = "分组管理") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTextField("新分组名", newName, { newName = it }, placeholder = "分组名称")
            Row(horizontalArrangement = Arrangement.End) {
                InkButton("新建分组", {
                    if (newName.isNotBlank()) {
                        onAdd(newName.trim())
                        newName = ""
                    }
                }, variant = InkButtonVariant.PRIMARY)
            }
            groups.forEach { group ->
                GroupRow(group, onRename = { onRename(group, it) }, onDelete = { onDelete(group) })
            }
            if (groups.isEmpty()) {
                InkEmptyState("暂无分组", compact = true)
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: String,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(group) }
    ToolRow(onClick = null) {
        Column(Modifier.fillMaxWidth()) {
            if (editing) {
                ToolTextField("分组名", draft, { draft = it })
                Row(horizontalArrangement = Arrangement.End) {
                    InkButton("保存", {
                        onRename(draft.trim())
                        editing = false
                    }, variant = InkButtonVariant.SECONDARY)
                    Spacer(Modifier.width(8.dp))
                    InkButton("取消", { editing = false }, variant = InkButtonVariant.GHOST)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicText(
                        group,
                        style = TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp),
                        modifier = Modifier.weight(1f),
                    )
                    MiniAction("重命名", { editing = true })
                    MiniAction("删除", onDelete, danger = true)
                }
            }
        }
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    existing: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolSectionLabel(title)
            ToolTextField("分组名", name, { name = it }, placeholder = existing.joinToString(" / "))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton("取消", onDismiss, variant = InkButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
                InkButton(
                    "确定",
                    { if (name.isNotBlank()) onConfirm(name.trim()) },
                    variant = InkButtonVariant.PRIMARY,
                )
            }
        }
    }
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
