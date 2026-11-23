package io.legado.app.ui.book.source.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.ui.components.InkLoadingDots
import io.legado.app.ui.components.rememberInkGesture

/** 查看分项原始响应的类别（10 搜索 / 20 详情 / 30 目录 / 40 正文）。 */
enum class DebugSrcKind { SEARCH, BOOK, TOC, CONTENT }

/**
 * 落文 · 书源调试页宿主动作（Activity 实现；纯 UI 不碰契约 / Fragment / 扫码器）。
 */
interface SourceDebugActions {
    fun onBack()
    fun onSubmit(key: String)
    fun onScan()
    fun onViewSrc(kind: DebugSrcKind)
    fun onRefreshExplore()
    fun onHelp()
    fun onPickExplore()
}

/**
 * 落文 · 书源调试页（Compose 自绘，替代旧 BookSourceDebugActivity + BookSourceDebugAdapter +
 * menu/book_source_debug）。
 *
 * 覆盖旧页全部分项：搜索 / 发现 / 详情 / 目录（++ 前缀）/ 正文（-- 前缀）调试、分项原始响应
 * 查看、发现项长按切换、刷新发现缓存、扫码取源、帮助文档。超时 / 被墙 / 编码错 / 通道占用均以
 * 日志条目或错误横幅透出，并保留重新提交路径。
 */
@Composable
fun SourceDebugScreen(
    state: SourceDebugUiState,
    vm: SourceDebugViewModel,
    actions: SourceDebugActions,
    modifier: Modifier = Modifier,
) {
    var overflowOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComponentTokens.bgPage),
    ) {
        Column(Modifier.fillMaxSize()) {
            DebugHeader(
                keyword = state.keyword,
                onKeywordChange = vm::setKeyword,
                onSubmit = actions::onSubmit,
                onBack = actions::onBack,
                onOverflow = { overflowOpen = true },
            )

            Box(Modifier.weight(1f)) {
                when {
                    state.helpOpen -> HelpPanel(state = state, actions = actions)
                    state.logs.isEmpty() && !state.loading -> InkEmptyState(
                        message = stringResource(R.string.debug_empty_log),
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> LogBody(state = state)
                }
            }

            state.error?.let { message ->
                ErrorBanner(message = message, onRetry = { actions.onSubmit(state.keyword) })
            }
        }

        if (overflowOpen) {
            OverflowSheet(
                state = state,
                onDismiss = { overflowOpen = false },
                actions = actions,
            )
        }
    }
}

// ───────────────────────────── 顶栏 ─────────────────────────────

@Composable
private fun DebugHeader(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    onOverflow: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(label = "←", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText(
                text = stringResource(R.string.source_debug_title),
                style = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 19.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            HeaderButton(label = "⋯", onClick = onOverflow)
        }
        Spacer(Modifier.height(10.dp))
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
                value = keyword,
                onValueChange = onKeywordChange,
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
                    if (keyword.isEmpty()) {
                        androidx.compose.foundation.text.BasicText(
                            text = stringResource(R.string.search_book_key),
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

// ───────────────────────────── 帮助 / 示例面板 ─────────────────────────────

@Composable
private fun HelpPanel(
    state: SourceDebugUiState,
    actions: SourceDebugActions,
) {
    val checkKeyWord = state.source?.ruleSearch?.checkKeyWord?.takeIf { it.isNotBlank() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            val mine = stringResource(R.string.debug_help_mine)
            val system = stringResource(R.string.debug_help_system)
            HelpSection(title = stringResource(R.string.debug_help_search)) {
                ExampleChip(
                    label = checkKeyWord ?: mine,
                    onClick = { actions.onSubmit(checkKeyWord ?: mine) },
                )
                ExampleChip(
                    label = system,
                    onClick = { actions.onSubmit(system) },
                )
            }
        }

        item {
            val explore = state.activeExplore
            HelpSection(title = stringResource(R.string.debug_help_explore)) {
                ExampleChip(
                    label = explore?.let { "${it.title}::${it.url}" } ?: stringResource(R.string.debug_help_system),
                    enabled = explore != null && !explore.title.startsWith("ERROR:"),
                    onClick = {
                        explore?.let { actions.onSubmit("${it.title}::${it.url}") }
                    },
                    onLongClick = actions::onPickExplore,
                )
            }
        }

        item {
            HelpSection(title = stringResource(R.string.debug_help_info)) {
                ExampleChip(
                    label = stringResource(R.string.debug_placeholder_info),
                    enabled = state.keyword.isNotBlank(),
                    onClick = { if (state.keyword.isNotBlank()) actions.onSubmit(state.keyword) },
                )
            }
        }

        item {
            HelpSection(title = stringResource(R.string.debug_help_toc)) {
                ExampleChip(
                    label = stringResource(R.string.debug_placeholder_toc),
                    onClick = { actions.onSubmit(prefixQuery("++", state.keyword)) },
                )
            }
        }

        item {
            HelpSection(title = stringResource(R.string.debug_help_content)) {
                ExampleChip(
                    label = stringResource(R.string.debug_placeholder_content),
                    onClick = { actions.onSubmit(prefixQuery("--", state.keyword)) },
                )
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.text.BasicText(
            text = title,
            style = TextStyle(
                color = ComponentTokens.textSecondary,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun ExampleChip(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        rememberInkGesture(enabled = enabled, onClick = onClick).gestureModifier
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ComponentTokens.bgSurface)
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) ComponentTokens.textSecondary else ComponentTokens.textTertiary,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
        )
    }
}

// ───────────────────────────── 日志流 ─────────────────────────────

@Composable
private fun LogBody(state: SourceDebugUiState) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.logs) { index, log ->
                val isFailure = log.state == -1 || log.state == 1000
                androidx.compose.foundation.text.BasicText(
                    text = log.message,
                    style = TextStyle(
                        color = if (isFailure) ComponentTokens.textAccent else ComponentTokens.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                )
            }
        }
        if (state.loading) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) { InkLoadingDots() }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(ComponentTokens.cardRadius))
            .background(ComponentTokens.bgSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = message,
            style = TextStyle(
                color = ComponentTokens.textAccent,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicText(
            text = stringResource(R.string.retry),
            style = TextStyle(
                color = ComponentTokens.brandGold,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
            ),
            modifier = Modifier.clickable(onClick = onRetry),
        )
    }
}

// ───────────────────────────── 溢出菜单 ─────────────────────────────

@Composable
private fun OverflowSheet(
    state: SourceDebugUiState,
    onDismiss: () -> Unit,
    actions: SourceDebugActions,
) {
    InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_debug_title), content = {
        val options = buildList {
            add(SheetAction(R.string.scan_qr_code, actions::onScan))
            if (!state.searchSrc.isNullOrBlank()) {
                add(SheetAction(R.string.search_src) { actions.onViewSrc(DebugSrcKind.SEARCH) })
            }
            if (!state.bookSrc.isNullOrBlank()) {
                add(SheetAction(R.string.boo_src) { actions.onViewSrc(DebugSrcKind.BOOK) })
            }
            if (!state.tocSrc.isNullOrBlank()) {
                add(SheetAction(R.string.toc_src) { actions.onViewSrc(DebugSrcKind.TOC) })
            }
            if (!state.contentSrc.isNullOrBlank()) {
                add(SheetAction(R.string.content_src) { actions.onViewSrc(DebugSrcKind.CONTENT) })
            }
            add(SheetAction(R.string.refresh_explore, actions::onRefreshExplore))
            add(SheetAction(R.string.help, actions::onHelp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                SheetRow(label = stringResource(option.labelRes), onClick = option.onClick)
            }
        }
    })
}

private data class SheetAction(val labelRes: Int, val onClick: () -> Unit)

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
            ),
        )
    }
}

/** 目录 / 正文前缀补全：空查询只填前缀；已有查询未带前缀则补在最前。 */
private fun prefixQuery(prefix: String, current: String): String {
    val query = current.trim()
    return when {
        query.isBlank() || query.length <= 2 -> prefix
        query.startsWith(prefix) -> query
        else -> "$prefix$query"
    }
}
