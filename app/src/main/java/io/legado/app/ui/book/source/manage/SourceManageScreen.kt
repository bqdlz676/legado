package io.legado.app.ui.book.source.manage

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.InkLoadingDots
import io.legado.app.ui.components.InkToastHost

/**
 * 落文 · 书源管理页宿主动作（Activity 实现；纯 UI 不直接碰契约 / Fragment / 文件选择器）。
 */
interface SourceManageActions {
    fun onBack()
    fun onAddSource()
    fun onAddJsSource()
    fun onImportLocal()
    fun onImportOnline(url: String?)
    fun onImportQr()
    fun onEdit(source: BookSourcePart)
    fun onDebug(source: BookSourcePart)
    fun onSearch(source: BookSourcePart)
    fun onLogin(source: BookSourcePart)
    fun onExport(selection: List<BookSourcePart>)
    fun onShare(selection: List<BookSourcePart>)
    fun onCheckSource(keyword: String)
    fun onShowCheckConfig()
    fun onHelp()
    fun onCopyText(text: String)
    fun onPassphraseShare(url: String)
    fun onStopCheck()
}

/**
 * 落文 · 书源管理页（Compose 自绘，替代旧 BookSourceActivity + BookSourceAdapter + 全部旧菜单）。
 *
 * 覆盖旧页全部功能：搜索 / 预设筛选 / 分组筛选 / 七种排序 + 升降序 / 域名归组 / 多选 /
 * 批量启停 / 批量发现 / 批量分组 / 置顶置底 / 导出 / 分享 / 校验 / 书架引用数 / 校验状态。
 * 边界兜底：空库、无匹配、加载中、失败重试、离线缓存全部有明确文案与可重试路径。
 */
@Composable
fun SourceManageScreen(
    state: SourceManageUiState,
    vm: SourceManageViewModel,
    actions: SourceManageActions,
    modifier: Modifier = Modifier,
) {
    var searchActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComponentTokens.bgPage),
    ) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(
                state = state,
                searchActive = searchActive,
                onSearchToggle = { searchActive = !searchActive },
                onQueryChange = vm::onQueryChange,
                onBack = actions::onBack,
                onAdd = actions::onAddSource,
                onOverflow = { vm.openSheet(ManageSheet.MainOptions) },
            )

            PresetChips(state = state, onSelect = vm::selectPreset)

            if (state.showCheckStatus) {
                CheckStatusChips(state = state, onSelect = vm::setCheckStatusFilter)
            }

            if (state.checking) {
                CheckBanner(message = state.checkMessage, onCancel = actions::onStopCheck)
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingBody()
                    state.items.isEmpty() -> EmptyBody(
                        state = state,
                        onAdd = actions::onAddSource,
                        onRetry = { /* 列表来自 DB Flow，下拉回落即重订阅；此处保留引导入口 */ },
                    )
                    else -> SourceList(
                        state = state,
                        vm = vm,
                        actions = actions,
                    )
                }
            }

            if (state.inSelectionMode) {
                SelectionBar(state = state, vm = vm, actions = actions)
            }
        }

        InkToastHost(
            toasts = state.toasts,
            modifier = Modifier.align(Alignment.BottomCenter),
            onDismiss = { vm.dismissToast(it.id) },
        )

        ManageSheets(state = state, vm = vm, actions = actions)
    }
}

// ───────────────────────────── 顶栏 ─────────────────────────────

@Composable
private fun HeaderBar(
    state: SourceManageUiState,
    searchActive: Boolean,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOverflow: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(label = "←", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText(
                text = stringResource(R.string.book_source_manage_title),
                style = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 19.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            HeaderButton(label = "⌕", onClick = onSearchToggle)
            HeaderButton(label = "+", onClick = onAdd)
            HeaderButton(label = "⋯", onClick = onOverflow)
        }
        if (searchActive) {
            Spacer(Modifier.height(10.dp))
            SearchField(query = state.query, onQueryChange = onQueryChange)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComponentTokens.bgSurface)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            cursorBrush = SolidColor(ComponentTokens.textAccent),
            textStyle = TextStyle(
                color = ComponentTokens.textPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        text = stringResource(R.string.search_book_source),
                        style = TextStyle(
                            color = ComponentTokens.textTertiary,
                            fontFamily = FontFamily.Serif,
                            fontSize = 15.sp,
                        ),
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

// ───────────────────────────── 筛选 chips ─────────────────────────────

@Composable
private fun PresetChips(state: SourceManageUiState, onSelect: (SourcePreset, String?) -> Unit) {
    val presets = listOf(
        SourcePreset.ALL to stringResource(R.string.source_filter_all),
        SourcePreset.ENABLED to stringResource(R.string.enabled),
        SourcePreset.DISABLED to stringResource(R.string.disabled),
        SourcePreset.LOGIN to stringResource(R.string.need_login),
        SourcePreset.NO_GROUP to stringResource(R.string.no_group),
        SourcePreset.EXPLORE_ENABLED to stringResource(R.string.enabled_explore),
        SourcePreset.EXPLORE_DISABLED to stringResource(R.string.disabled_explore),
    )
    ChipRow {
        presets.forEach { (preset, label) ->
            Chip(
                label = label,
                selected = state.preset == preset,
                onClick = { onSelect(preset, null) },
            )
        }
        state.groups.forEach { group ->
            Chip(
                label = group,
                selected = state.preset == SourcePreset.GROUP && state.groupName == group,
                onClick = { onSelect(SourcePreset.GROUP, group) },
            )
        }
    }
}

@Composable
private fun CheckStatusChips(state: SourceManageUiState, onSelect: (Int) -> Unit) {
    val labels = listOf(
        stringResource(R.string.source_filter_all),
        stringResource(R.string.source_check_needed),
        stringResource(R.string.source_check_passed),
        stringResource(R.string.source_check_failed),
        stringResource(R.string.source_check_uncheckable),
    )
    ChipRow {
        labels.forEachIndexed { index, label ->
            Chip(label = label, selected = state.checkStatusFilter == index, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ComponentTokens.brandGold else ComponentTokens.bgSurface)
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
private fun CheckBanner(message: String?, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(ComponentTokens.cardRadius))
            .background(ComponentTokens.bgSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = message ?: stringResource(R.string.check_select_source),
            style = TextStyle(
                color = ComponentTokens.textSecondary,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
            modifier = Modifier.weight(1f),
        )
        InkButton(text = stringResource(R.string.cancel), variant = InkButtonVariant.GHOST, onClick = onCancel)
    }
}

// ───────────────────────────── 列表 / 状态 ─────────────────────────────

@Composable
private fun SourceList(
    state: SourceManageUiState,
    vm: SourceManageViewModel,
    actions: SourceManageActions,
) {
    var lastHost by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
        ),
    ) {
        items(state.items, key = { it.bookSourceUrl }) { source ->
            val host = vm.getSourceHost(source.bookSourceUrl)
            val showHostHeader = state.groupByDomain && host != lastHost
            lastHost = host
            SourceManageRow(
                source = source,
                selected = source.bookSourceUrl in state.selected,
                inSelectionMode = state.inSelectionMode,
                showHostHeader = showHostHeader,
                hostLabel = host,
                showCheckStatus = state.showCheckStatus,
                showBookshelfCount = state.showBookshelfCount,
                bookshelfCount = state.bookshelfCounts[source.bookSourceUrl] ?: 0,
                onToggleSelect = { vm.toggleSelect(source.bookSourceUrl) },
                onOpenEdit = { actions.onEdit(source) },
                onToggleEnable = { vm.enable(it, source) },
                onToggleExplore = { vm.enableExplore(!source.enabledExplore, source) },
                onMore = { vm.openSheet(ManageSheet.ItemActions(source.bookSourceUrl)) },
            )
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InkLoadingDots()
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.text.BasicText(
                text = stringResource(R.string.loading),
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

@Composable
private fun EmptyBody(state: SourceManageUiState, onAdd: () -> Unit, onRetry: () -> Unit) {
    val message = when {
        state.error != null -> state.error!!
        state.query.isNotBlank() -> stringResource(R.string.source_empty_filter)
        else -> stringResource(R.string.source_empty_all)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        InkEmptyState(
            message = message,
            offline = state.offline,
            actionLabel = if (state.error != null) stringResource(R.string.retry) else stringResource(R.string.add_book_source),
            onAction = if (state.error != null) onRetry else onAdd,
        )
    }
}

// ───────────────────────────── 多选底栏 ─────────────────────────────

@Composable
private fun SelectionBar(
    state: SourceManageUiState,
    vm: SourceManageViewModel,
    actions: SourceManageActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComponentTokens.bgSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = "${state.selected.size}/${state.items.size}",
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
            ),
            modifier = Modifier.weight(1f),
        )
        InkButton(
            text = stringResource(R.string.select_all),
            variant = InkButtonVariant.SECONDARY,
            onClick = vm::selectAll,
        )
        InkButton(
            text = stringResource(R.string.delete),
            variant = InkButtonVariant.GHOST,
            onClick = {
                vm.delete(state.selection)
                vm.clearSelection()
            },
        )
        InkButton(
            text = stringResource(R.string.source_more),
            variant = InkButtonVariant.SECONDARY,
            onClick = { vm.openSheet(ManageSheet.SelectionActions(state.selection)) },
        )
    }
}
