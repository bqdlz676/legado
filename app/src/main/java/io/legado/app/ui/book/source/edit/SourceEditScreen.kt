package io.legado.app.ui.book.source.edit

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.ui.components.InkLoadingDots
import io.legado.app.ui.components.InkToastHost

/**
 * 落文 · 书源编辑宿主动作（Activity 实现；纯 UI 不直接碰契约 / 编辑器 / Fragment）。
 */
interface SourceEditActions {
    fun onBack()
    fun onSave()
    fun onDebug()
    fun onLogin()
    fun onSearch()
    fun onClearCookie()
    fun onCopy()
    fun onPaste()
    fun onQr()
    fun onShareText()
    fun onShareQr()
    fun onShowLog()
    fun onHelp()
    fun onSetVariable()
    fun onFullscreenEdit(tab: EditTab, field: EditField)
    fun onInsertUrlOption()
    fun onInsertGroup()
    fun onSelectFile()
    fun onShowDoc(name: String)
}

/**
 * 落文 · 书源编辑页（Compose 自绘，替代旧 BookSourceEditActivity + BookSourceEditAdapter + TabLayout）。
 *
 * 覆盖旧页全部字段：基本 13、搜索 11、发现 10、详情 11、目录 10、正文 13、段评 20，
 * 以及选项面板 6 开关 + 类型选择；错误就地提示，长规则字段可进全屏编辑器。
 */
@Composable
fun SourceEditScreen(
    state: SourceEditUiState,
    vm: SourceEditViewModel,
    actions: SourceEditActions,
    typeLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    var activeTab by remember { mutableStateOf(EditTab.BASE) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComponentTokens.bgPage),
    ) {
        Column(Modifier.fillMaxSize()) {
            EditHeader(
                onBack = actions::onBack,
                onSave = actions::onSave,
                onOverflow = { overflowOpen = true },
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InkLoadingDots() }
            } else {
                OptionPanel(
                    state = state,
                    expanded = optionsExpanded,
                    onToggleExpand = { optionsExpanded = !optionsExpanded },
                    typeLabels = typeLabels,
                    onOptions = vm::updateOptions,
                )

                TabRow(activeTab = activeTab, onSelect = { activeTab = it })

                val fields = state.fields[activeTab].orEmpty()
                if (fields.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        InkEmptyState(message = stringResource(R.string.source_empty_filter))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp,
                        ),
                    ) {
                        itemsIndexed(fields, key = { _, f -> f.key }) { _, field ->
                            FieldRow(
                                field = field,
                                error = state.errors[field.key],
                                onValueChange = { vm.updateField(activeTab, field.key, it) },
                                onFocusChange = { vm.updateActiveField(activeTab, field.key) },
                                onFullEdit = { actions.onFullscreenEdit(activeTab, field) },
                            )
                        }
                    }
                }
            }
        }

        InkToastHost(
            toasts = state.toasts,
            modifier = Modifier.align(Alignment.BottomCenter),
            onDismiss = { vm.dismissToast(it.id) },
        )

        if (overflowOpen) {
            OverflowSheet(
                state = state,
                vm = vm,
                actions = actions,
                onDismiss = { overflowOpen = false },
            )
        }
    }
}

// ───────────────────────────── 顶栏 ─────────────────────────────

@Composable
private fun EditHeader(onBack: () -> Unit, onSave: () -> Unit, onOverflow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderButton(label = "←", onClick = onBack)
        androidx.compose.foundation.text.BasicText(
            text = stringResource(R.string.source_edit_title),
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 19.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.weight(1f),
        )
        InkButton(text = stringResource(R.string.action_save), onClick = onSave)
        HeaderButton(label = "⋯", onClick = onOverflow)
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(color = ComponentTokens.textPrimary, fontFamily = FontFamily.Serif, fontSize = 20.sp),
        )
    }
}

// ───────────────────────────── 选项面板 ─────────────────────────────

@Composable
private fun OptionPanel(
    state: SourceEditUiState,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    typeLabels: List<String>,
    onOptions: (EditOptions.() -> EditOptions) -> Unit,
) {
    val options = state.options
    val summary = buildList {
        add(typeLabels.getOrElse(options.typeIndex) { typeLabels.firstOrNull().orEmpty() })
        if (options.enabled) add(stringResource(R.string.is_enable))
        if (options.enabledExplore) add(stringResource(R.string.discovery))
        if (options.enabledCookieJar) add(stringResource(R.string.auto_save_cookie))
        if (options.enabledReview) add(stringResource(R.string.review))
        if (options.eventListener) add(stringResource(R.string.is_event_listener))
        if (options.customButton) add(stringResource(R.string.custom_button))
    }.joinToString(" | ")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(ComponentTokens.cardRadius))
            .background(ComponentTokens.bgSurface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicText(
                    text = stringResource(R.string.setting),
                    style = TextStyle(color = ComponentTokens.textPrimary, fontFamily = FontFamily.Serif, fontSize = 15.sp),
                )
                androidx.compose.foundation.text.BasicText(
                    text = summary,
                    style = TextStyle(color = ComponentTokens.textTertiary, fontFamily = FontFamily.Serif, fontSize = 12.sp),
                )
            }
            androidx.compose.foundation.text.BasicText(
                text = if (expanded) "−" else "+",
                style = TextStyle(color = ComponentTokens.brandGold, fontFamily = FontFamily.Serif, fontSize = 18.sp),
            )
        }

        if (expanded) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                typeLabels.forEachIndexed { index, label ->
                    TypeChip(
                        label = label,
                        selected = options.typeIndex == index,
                        onClick = { onOptions { copy(typeIndex = index) } },
                    )
                }
            }
            OptionToggle(stringResource(R.string.is_enable), options.enabled) {
                onOptions { copy(enabled = it) }
            }
            OptionToggle(stringResource(R.string.discovery), options.enabledExplore) {
                onOptions { copy(enabledExplore = it) }
            }
            OptionToggle(stringResource(R.string.auto_save_cookie), options.enabledCookieJar) {
                onOptions { copy(enabledCookieJar = it) }
            }
            OptionToggle(stringResource(R.string.review), options.enabledReview) {
                onOptions { copy(enabledReview = it) }
            }
            OptionToggle(stringResource(R.string.is_event_listener), options.eventListener) {
                onOptions { copy(eventListener = it) }
            }
            OptionToggle(stringResource(R.string.custom_button), options.customButton) {
                onOptions { copy(customButton = it) }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ComponentTokens.brandGold else ComponentTokens.bgPage)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = if (selected) ComponentTokens.bgPage else ComponentTokens.textSecondary,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
        )
    }
}

@Composable
private fun OptionToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(color = ComponentTokens.textPrimary, fontFamily = FontFamily.Serif, fontSize = 14.sp),
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(44.dp, 24.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) ComponentTokens.brandGold else ComponentTokens.colorDivider)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(ComponentTokens.bgPage))
        }
    }
}

// ───────────────────────────── 标签页 ─────────────────────────────

@Composable
private fun TabRow(activeTab: EditTab, onSelect: (EditTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditTab.entries.forEach { tab ->
            TypeChip(
                label = stringResource(tab.titleRes),
                selected = activeTab == tab,
                onClick = { onSelect(tab) },
            )
        }
    }
}

// ───────────────────────────── 字段行 ─────────────────────────────

@Composable
private fun FieldRow(
    field: EditField,
    error: String?,
    onValueChange: (String?) -> Unit,
    onFocusChange: () -> Unit,
    onFullEdit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ComponentTokens.cardRadius))
            .background(ComponentTokens.bgSurface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                text = stringResource(field.labelRes),
                style = TextStyle(
                    color = if (error != null) ComponentTokens.textAccent else ComponentTokens.stoneBlue,
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(48.dp).clickable(onClick = onFullEdit),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = "⛶",
                    style = TextStyle(color = ComponentTokens.textSecondary, fontFamily = FontFamily.Serif, fontSize = 15.sp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = field.value.orEmpty(),
            onValueChange = onValueChange,
            minLines = 2,
            maxLines = 6,
            cursorBrush = SolidColor(ComponentTokens.textAccent),
            textStyle = TextStyle(
                color = ComponentTokens.textPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocusChange() },
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.text.BasicText(
                text = error,
                style = TextStyle(color = ComponentTokens.textAccent, fontFamily = FontFamily.Serif, fontSize = 12.sp),
            )
        }
    }
}

// ───────────────────────────── 溢出菜单 ─────────────────────────────

@Composable
private fun OverflowSheet(
    state: SourceEditUiState,
    vm: SourceEditViewModel,
    actions: SourceEditActions,
    onDismiss: () -> Unit,
) {
    InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_edit_title), content = {
        Column {
            SheetAction(stringResource(R.string.edit_content), onClick = { onDismiss(); actions.onFullscreenEdit(EditTab.BASE, currentFocusedField(state)) })
            SheetAction(stringResource(R.string.debug_source), onClick = { onDismiss(); actions.onDebug() })
            SheetAction(stringResource(R.string.login), onClick = { onDismiss(); actions.onLogin() })
            SheetAction(stringResource(R.string.search), onClick = { onDismiss(); actions.onSearch() })
            SheetAction(stringResource(R.string.cookie), onClick = { onDismiss(); actions.onClearCookie() })
            SheetAction(
                label = stringResource(R.string.auto_complete),
                checked = state.autoComplete,
                onClick = { vm.toggleAutoComplete() },
            )
            SheetAction(stringResource(R.string.copy_source), onClick = { onDismiss(); actions.onCopy() })
            SheetAction(stringResource(R.string.paste_source), onClick = { onDismiss(); actions.onPaste() })
            SheetAction(stringResource(R.string.set_source_variable), onClick = { onDismiss(); actions.onSetVariable() })
            SheetAction(stringResource(R.string.import_by_qr_code), onClick = { onDismiss(); actions.onQr() })
            SheetAction(stringResource(R.string.str_share), onClick = { onDismiss(); actions.onShareText() })
            SheetAction(stringResource(R.string.qr_share), onClick = { onDismiss(); actions.onShareQr() })
            SheetAction(stringResource(R.string.log), onClick = { onDismiss(); actions.onShowLog() })
            SheetAction(stringResource(R.string.help), onClick = { onDismiss(); actions.onHelp() })
            SheetAction(stringResource(R.string.url_option), onClick = { onDismiss(); actions.onInsertUrlOption() })
            SheetAction(stringResource(R.string.add_group), onClick = { onDismiss(); actions.onInsertGroup() })
            SheetAction(stringResource(R.string.select_file), onClick = { onDismiss(); actions.onSelectFile() })
            SheetAction(stringResource(R.string.source_js_tutorial), onClick = { onDismiss(); actions.onShowDoc("jsHelp") })
            SheetAction(stringResource(R.string.source_regex_tutorial), onClick = { onDismiss(); actions.onShowDoc("regexHelp") })
        }
    })
}

/** 全屏编辑未聚焦字段时的回退：取基本页首字段（旧页无焦点时 toast 提示）。 */
private fun currentFocusedField(state: SourceEditUiState): EditField {
    return state.fields[EditTab.BASE]?.firstOrNull() ?: EditField("bookSourceUrl", R.string.source_url, null)
}

@Composable
private fun SheetAction(
    label: String,
    checked: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                text = label,
                style = TextStyle(color = ComponentTokens.textPrimary, fontFamily = FontFamily.Serif, fontSize = 15.sp),
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
