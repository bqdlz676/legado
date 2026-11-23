package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.model.CheckSource
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkHalfSheet

/**
 * 落文 · 书源管理全部半屏 Sheet（替代旧 PopupMenu / AlertDialog / GroupManageDialog）。
 *
 * 每个 Sheet 对应旧页一处菜单或弹窗；开关状态回读 [SourceManageUiState]，动作交 VM / Actions。
 */
@Composable
fun ManageSheets(
    state: SourceManageUiState,
    vm: SourceManageViewModel,
    actions: SourceManageActions,
) {
    when (val sheet = state.sheet) {
        ManageSheet.None -> Unit
        ManageSheet.MainOptions -> MainOptionsSheet(state, vm, actions)
        ManageSheet.SortOptions -> SortSheet(state, vm)
        ManageSheet.GroupFilters -> GroupFiltersSheet(state, vm)
        ManageSheet.GroupManage -> GroupManageSheet(state, vm)
        ManageSheet.ImportMenu -> Unit // 导入入口已并入主选项 Sheet
        ManageSheet.OnlineImport -> OnlineImportSheet(vm, actions)
        ManageSheet.CheckSource -> CheckSourceSheet(vm, actions)
        is ManageSheet.ItemActions -> ItemActionsSheet(sheet.sourceUrl, state, vm, actions)
        is ManageSheet.SelectionActions -> SelectionActionsSheet(sheet.selection, vm, actions)
        is ManageSheet.AddToGroup -> GroupInputSheet(
            title = stringResource(R.string.source_add_group_title),
            groups = state.groups,
            confirm = { name -> vm.selectionAddToGroups(sheet.selection, name) },
            onDismiss = vm::closeSheet,
        )
        is ManageSheet.RemoveFromGroup -> GroupInputSheet(
            title = stringResource(R.string.source_remove_group_title),
            groups = state.groups,
            confirm = { name -> vm.selectionRemoveFromGroups(sheet.selection, name) },
            onDismiss = vm::closeSheet,
        )
        is ManageSheet.ExportResult -> ExportResultSheet(sheet, actions, vm::closeSheet)
    }
}

// ───────────────────────────── 主选项 ─────────────────────────────

@Composable
private fun MainOptionsSheet(
    state: SourceManageUiState,
    vm: SourceManageViewModel,
    actions: SourceManageActions,
) {
    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.book_source_manage_title)) {
        Column {
            SheetRow(label = stringResource(R.string.add_book_source), onClick = actions::onAddSource)
            SheetRow(label = stringResource(R.string.js_source_new), onClick = actions::onAddJsSource)
            SheetRow(label = stringResource(R.string.import_local), onClick = actions::onImportLocal)
            SheetRow(label = stringResource(R.string.import_on_line), onClick = {
                vm.closeSheet()
                vm.openSheet(ManageSheet.OnlineImport)
            })
            SheetRow(label = stringResource(R.string.import_by_qr_code), onClick = actions::onImportQr)
            SheetRow(label = stringResource(R.string.group_manage), onClick = {
                vm.closeSheet()
                vm.openSheet(ManageSheet.GroupManage)
            })
            SheetRow(label = stringResource(R.string.sort), onClick = {
                vm.closeSheet()
                vm.openSheet(ManageSheet.SortOptions)
            })
            SheetRow(label = stringResource(R.string.menu_action_group), onClick = {
                vm.closeSheet()
                vm.openSheet(ManageSheet.GroupFilters)
            })
            SheetToggle(
                label = stringResource(R.string.group_sources_by_domain),
                checked = state.groupByDomain,
                onToggle = vm::toggleGroupByDomain,
            )
            SheetToggle(
                label = stringResource(R.string.show_source_check_status),
                checked = state.showCheckStatus,
                onToggle = vm::toggleShowCheckStatus,
            )
            SheetToggle(
                label = stringResource(R.string.show_source_bookshelf_count),
                checked = state.showBookshelfCount,
                onToggle = vm::toggleShowBookshelfCount,
            )
            SheetToggle(
                label = stringResource(R.string.block_source_navigation),
                checked = io.legado.app.help.config.AppConfig.blockSourceNavigation,
                onToggle = { vm.toggleBlockSourceNavigation() },
            )
            SheetRow(label = stringResource(R.string.help), onClick = actions::onHelp)
        }
    }
}

// ───────────────────────────── 排序 ─────────────────────────────

@Composable
private fun SortSheet(state: SourceManageUiState, vm: SourceManageViewModel) {
    val options = listOf(
        BookSourceSort.Default to stringResource(R.string.sort_manual),
        BookSourceSort.Weight to stringResource(R.string.sort_auto),
        BookSourceSort.Name to stringResource(R.string.sort_by_name),
        BookSourceSort.Url to stringResource(R.string.sort_by_url),
        BookSourceSort.Update to stringResource(R.string.sort_by_lastUpdateTime),
        BookSourceSort.Respond to stringResource(R.string.sort_by_respondTime),
        BookSourceSort.Enable to stringResource(R.string.is_enabled),
    )
    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.sort)) {
        Column {
            options.forEach { (sort, label) ->
                SheetRow(
                    label = label,
                    checked = state.sort == sort,
                    onClick = { vm.setSort(sort); vm.closeSheet() },
                )
            }
            SheetToggle(
                label = stringResource(R.string.sort_desc),
                checked = !state.ascending,
                onToggle = { vm.toggleAscending() },
            )
        }
    }
}

// ───────────────────────────── 分组筛选 ─────────────────────────────

@Composable
private fun GroupFiltersSheet(state: SourceManageUiState, vm: SourceManageViewModel) {
    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.menu_action_group)) {
        Column {
            val presets = listOf(
                SourcePreset.ENABLED to stringResource(R.string.enabled),
                SourcePreset.DISABLED to stringResource(R.string.disabled),
                SourcePreset.LOGIN to stringResource(R.string.need_login),
                SourcePreset.NO_GROUP to stringResource(R.string.no_group),
                SourcePreset.EXPLORE_ENABLED to stringResource(R.string.enabled_explore),
                SourcePreset.EXPLORE_DISABLED to stringResource(R.string.disabled_explore),
            )
            presets.forEach { (preset, label) ->
                SheetRow(
                    label = label,
                    checked = state.preset == preset,
                    onClick = { vm.selectPreset(preset); vm.closeSheet() },
                )
            }
            state.groups.forEach { group ->
                SheetRow(
                    label = group,
                    checked = state.preset == SourcePreset.GROUP && state.groupName == group,
                    onClick = { vm.selectPreset(SourcePreset.GROUP, group); vm.closeSheet() },
                )
            }
        }
    }
}

// ───────────────────────────── 分组管理 ─────────────────────────────

@Composable
private fun GroupManageSheet(state: SourceManageUiState, vm: SourceManageViewModel) {
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var newGroup by remember { mutableStateOf("") }
    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.group_manage)) {
        Column {
            if (state.groups.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.text.BasicText(
                        text = stringResource(R.string.source_empty_filter),
                        style = TextStyle(
                            color = ComponentTokens.textTertiary,
                            fontFamily = FontFamily.Serif,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
            state.groups.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        SheetRow(label = group, onClick = {})
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable {
                                renaming = group
                                renameText = group
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = stringResource(R.string.group_edit),
                            style = TextStyle(color = ComponentTokens.textSecondary, fontFamily = FontFamily.Serif, fontSize = 13.sp),
                        )
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable { vm.deleteGroup(group) },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = stringResource(R.string.delete),
                            style = TextStyle(color = ComponentTokens.textAccent, fontFamily = FontFamily.Serif, fontSize = 13.sp),
                        )
                    }
                }
            }
            if (renaming != null) {
                Spacer(Modifier.height(10.dp))
                SheetTextField(value = renameText, onValueChange = { renameText = it }, hint = renaming!!)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton(
                        text = stringResource(R.string.sure_rename),
                        modifier = Modifier.weight(1f),
                        enabled = renameText.isNotBlank(),
                        onClick = {
                            vm.renameGroup(renaming!!, renameText.trim())
                            renaming = null
                            renameText = ""
                        },
                    )
                    InkButton(
                        text = stringResource(R.string.cancel),
                        variant = InkButtonVariant.SECONDARY,
                        onClick = { renaming = null; renameText = "" },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SheetTextField(
                value = newGroup,
                onValueChange = { newGroup = it },
                hint = stringResource(R.string.source_group_name_hint),
            )
            Spacer(Modifier.height(10.dp))
            InkButton(
                text = stringResource(R.string.add_group),
                onClick = {
                    if (newGroup.isNotBlank()) {
                        vm.addGroup(newGroup.trim())
                        newGroup = ""
                    }
                },
            )
        }
    }
}

// ───────────────────────────── 在线导入 ─────────────────────────────

@Composable
private fun OnlineImportSheet(vm: SourceManageViewModel, actions: SourceManageActions) {
    var url by remember { mutableStateOf("") }
    val history = vm.cachedImportUrls()
    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.import_on_line)) {
        Column {
            SheetTextField(value = url, onValueChange = { url = it }, hint = stringResource(R.string.source_online_import_hint))
            Spacer(Modifier.height(10.dp))
            history.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SheetRow(label = item, onClick = { url = item })
                    }
                    Box(
                        Modifier.size(48.dp).clickable { vm.removeCachedImportUrl(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "×",
                            style = TextStyle(color = ComponentTokens.textAccent, fontFamily = FontFamily.Serif),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            InkButton(
                text = stringResource(R.string.import_on_line),
                onClick = {
                    val target = url.trim()
                    if (target.isNotBlank()) {
                        vm.rememberImportUrl(target)
                        actions.onImportOnline(target)
                        vm.closeSheet()
                    }
                },
            )
        }
    }
}

// ───────────────────────────── 校验 ─────────────────────────────

@Composable
private fun CheckSourceSheet(vm: SourceManageViewModel, actions: SourceManageActions) {
    var keyword by remember { mutableStateOf(CheckSource.keyword) }
    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.check_select_source)) {
        Column {
            SheetTextField(
                value = keyword,
                onValueChange = { keyword = it },
                hint = stringResource(R.string.source_check_keyword_hint),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkButton(
                    text = stringResource(R.string.check_select_source),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        actions.onCheckSource(keyword.ifBlank { CheckSource.keyword })
                        vm.closeSheet()
                    },
                )
                InkButton(
                    text = stringResource(R.string.check_source_config),
                    variant = InkButtonVariant.SECONDARY,
                    onClick = actions::onShowCheckConfig,
                )
            }
        }
    }
}

// ───────────────────────────── 单条操作 ─────────────────────────────

@Composable
private fun ItemActionsSheet(
    sourceUrl: String,
    state: SourceManageUiState,
    vm: SourceManageViewModel,
    actions: SourceManageActions,
) {
    val source = state.items.firstOrNull { it.bookSourceUrl == sourceUrl } ?: return
    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = source.bookSourceName) {
        Column {
            SheetRow(
                label = stringResource(R.string.to_top),
                enabled = state.canDrag,
                onClick = { vm.topSource(listOf(source)); vm.closeSheet() },
            )
            SheetRow(
                label = stringResource(R.string.to_bottom),
                enabled = state.canDrag,
                onClick = { vm.bottomSource(listOf(source)); vm.closeSheet() },
            )
            SheetRow(
                label = stringResource(R.string.login),
                enabled = source.hasLoginUrl,
                onClick = { actions.onLogin(source); vm.closeSheet() },
            )
            SheetRow(label = stringResource(R.string.search), onClick = { actions.onSearch(source); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.debug), onClick = { actions.onDebug(source); vm.closeSheet() })
            SheetRow(
                label = if (source.enabledExplore) {
                    stringResource(R.string.disable_explore)
                } else {
                    stringResource(R.string.enable_explore)
                },
                enabled = source.hasExploreUrl,
                onClick = {
                    vm.enableExplore(!source.enabledExplore, source)
                    vm.closeSheet()
                },
            )
            SheetRow(
                label = stringResource(R.string.delete),
                danger = true,
                onClick = { vm.delete(listOf(source)); vm.closeSheet() },
            )
        }
    }
}

// ───────────────────────────── 批量操作 ─────────────────────────────

@Composable
private fun SelectionActionsSheet(
    selection: List<BookSourcePart>,
    vm: SourceManageViewModel,
    actions: SourceManageActions,
) {
    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.source_selection_count, selection.size)) {
        Column {
            SheetRow(label = stringResource(R.string.enable_selection), onClick = { vm.enableSelection(); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.disable_selection), onClick = { vm.disableSelection(); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.revert_selection), onClick = { vm.revertSelection(); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.enable_explore), onClick = { vm.enableSelectExplore(); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.disable_explore), onClick = { vm.disableSelectExplore(); vm.closeSheet() })
            SheetRow(
                label = stringResource(R.string.add_group),
                onClick = { vm.openSheet(ManageSheet.AddToGroup(selection)) },
            )
            SheetRow(
                label = stringResource(R.string.remove_group),
                onClick = { vm.openSheet(ManageSheet.RemoveFromGroup(selection)) },
            )
            SheetRow(label = stringResource(R.string.selection_to_top), onClick = { vm.topSource(selection); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.selection_to_bottom), onClick = { vm.bottomSource(selection); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.export_selection), onClick = { actions.onExport(selection); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.share_selected_source), onClick = { actions.onShare(selection); vm.closeSheet() })
            SheetRow(label = stringResource(R.string.check_select_source), onClick = {
                vm.openSheet(ManageSheet.CheckSource)
            })
            SheetRow(label = stringResource(R.string.check_selected_interval), onClick = { vm.selectCheckedInterval(); vm.closeSheet() })
        }
    }
}

// ───────────────────────────── 分组输入 ─────────────────────────────

@Composable
private fun GroupInputSheet(
    title: String,
    groups: List<String>,
    confirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    InkHalfSheet(visible = true, onDismiss = onDismiss, title = title) {
        Column {
            SheetTextField(value = name, onValueChange = { name = it }, hint = stringResource(R.string.source_group_name_hint))
            Spacer(Modifier.height(8.dp))
            groups.forEach { group ->
                SheetRow(label = group, onClick = { name = group })
            }
            Spacer(Modifier.height(10.dp))
            InkButton(
                text = title,
                enabled = name.isNotBlank(),
                onClick = {
                    if (name.isNotBlank()) {
                        confirm(name.trim())
                        onDismiss()
                    }
                },
            )
        }
    }
}

// ───────────────────────────── 导出结果 ─────────────────────────────

@Composable
private fun ExportResultSheet(
    sheet: ManageSheet.ExportResult,
    actions: SourceManageActions,
    onDismiss: () -> Unit,
) {
    InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_export_title)) {
        Column {
            androidx.compose.foundation.text.BasicText(
                text = sheet.fileName,
                style = TextStyle(color = ComponentTokens.textPrimary, fontFamily = FontFamily.Serif, fontSize = 15.sp),
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.text.BasicText(
                text = sheet.uriText,
                style = TextStyle(color = ComponentTokens.textSecondary, fontFamily = FontFamily.Serif, fontSize = 12.sp),
            )
            if (sheet.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.text.BasicText(
                    text = sheet.summary,
                    style = TextStyle(color = ComponentTokens.textTertiary, fontFamily = FontFamily.Serif, fontSize = 12.sp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkButton(
                    text = stringResource(R.string.source_copy_path),
                    variant = InkButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                    onClick = { actions.onCopyText(sheet.uriText) },
                )
                if (sheet.isRemote) {
                    InkButton(
                        text = stringResource(R.string.source_share_passphrase),
                        variant = InkButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                        onClick = { actions.onPassphraseShare(sheet.uriText) },
                    )
                }
            }
        }
    }
}

// ───────────────────────────── 通用行 / 输入 ─────────────────────────────

@Composable
private fun SheetRow(
    label: String,
    onClick: () -> Unit,
    checked: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightInMin48()
            .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                text = label,
                style = TextStyle(
                    color = when {
                        !enabled -> ComponentTokens.textTertiary
                        danger -> ComponentTokens.textAccent
                        else -> ComponentTokens.textPrimary
                    },
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                ),
                modifier = Modifier.weight(1f),
            )
            if (checked) {
                androidx.compose.foundation.text.BasicText(
                    text = "✓",
                    style = TextStyle(color = ComponentTokens.brandGold, fontFamily = FontFamily.Serif),
                )
            }
        }
    }
}

@Composable
private fun SheetToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    SheetRow(label = label, checked = checked, onClick = onToggle)
}

@Composable
private fun SheetTextField(value: String, onValueChange: (String) -> Unit, hint: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComponentTokens.bgSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(ComponentTokens.textAccent),
            textStyle = TextStyle(color = ComponentTokens.textPrimary, fontFamily = FontFamily.Serif, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        text = hint,
                        style = TextStyle(color = ComponentTokens.textTertiary, fontFamily = FontFamily.Serif, fontSize = 15.sp),
                    )
                }
                inner()
            },
        )
    }
}

private fun Modifier.heightInMin48(): Modifier = this.then(Modifier.heightIn(min = 48.dp))
