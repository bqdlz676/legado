package io.legado.app.ui.config.b3

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.legado.app.ui.card.SettingRowCard
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.contract.ComponentVisualState
import io.legado.app.ui.config.settings.ConfirmSheet
import io.legado.app.ui.config.settings.GroupHeader
import io.legado.app.ui.config.settings.InkSearchField
import io.legado.app.ui.config.settings.ListPickSheet
import io.legado.app.ui.config.settings.NumberPickerSheet
import io.legado.app.ui.config.settings.TextEditSheet
import io.legado.app.utils.putPrefBoolean

/**
 * B3 设置页宿主：挂 B2 主壳第三槽（[io.legado.app.ui.main.shell.ShellTab.SETTINGS]）。
 *
 * 〔口径〕
 *  - 分组 / 行模型见 [B3SettingGroups]；行视觉一律走 [SettingRowCard] 实例卡，无卡片框、
 *    只靠云灰细线分隔；颜色 / 字号 / 圆角取既有 token，不在本页内联 hex（R1/R4/R5）。
 *  - 设置内搜索复用顶层匹配纯函数 [configPreferenceMatches]；空 query 零过滤，整组无命中
 *    隐藏，无命中落 [InkEmptyState] 兜底，不留白页（R19）。
 *  - 危险动作先经 [ConfirmSheet] 二次确认，确认后才执行侧效动作（R18）。
 *  - [reduceMotion] 透传系统减少动画档；本页主体为静态列表，动效集中在实例卡与既有弹层，
 *    各自动效档回 MotionSpec 单源，页面不自造时长 / 刚度（B8）。
 */
@Composable
fun B3SettingScreen(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    actions: B3SettingActions = rememberB3SettingActions(),
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var sheetTarget by remember { mutableStateOf<B3SheetTarget?>(null) }
    var webDavTestSummary by remember { mutableStateOf<String?>(null) }

    val groups = b3SettingGroups(
        actions = actions,
        webDavTestSummary = webDavTestSummary,
        onTestWebDav = {
            actions.testWebDav { ok ->
                webDavTestSummary = if (ok) "已连接" else "无法连接，检查地址和密码"
            }
        },
        openTarget = { sheetTarget = it },
    )
    val visibleGroups = filterB3Groups(groups, query)
    val searching = query.trim().isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComponentTokens.bgPage),
    ) {
        InkSearchField(
            query = query,
            onQueryChange = { query = it },
        )

        if (visibleGroups.isEmpty()) {
            InkEmptyState(
                message = if (searching) "未找到匹配的设置项" else "暂无设置项",
                state = ComponentVisualState.EMPTY,
                compact = true,
            )
            return
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            visibleGroups.forEach { group ->
                if (!group.title.isNullOrBlank()) {
                    item(key = "header-${group.title}") {
                        GroupHeader(text = group.title)
                    }
                }
                itemsIndexed(group.rows, key = { index, row -> rowKey(row, index) }) { index, row ->
                    val showDivider = index < group.rows.lastIndex
                    B3RowCard(
                        row = row,
                        context = context,
                        showDivider = showDivider,
                    )
                }
            }
        }
    }

    B3SheetHost(target = sheetTarget, onDismiss = { sheetTarget = null })
}

/** 行实例卡渲染：开关行读偏好并落盘；取值 / 导航行挂点击；说明行只读。 */
@Composable
private fun B3RowCard(
    row: B3Row,
    context: Context,
    showDivider: Boolean,
) {
    when (row) {
        is B3SwitchRow -> {
            val checked = rememberB3Boolean(row.key, row.def)
            SettingRowCard(
                title = row.title,
                value = row.summary,
                checked = checked,
                onCheckedChange = { next ->
                    context.putPrefBoolean(row.key, next)
                    row.onChange?.invoke(next)
                },
                showDivider = showDivider,
            )
        }

        is B3ValueRow -> SettingRowCard(
            title = row.title,
            value = row.summary,
            showArrow = true,
            onClick = row.onClick,
            showDivider = showDivider,
        )

        is B3InfoRow -> SettingRowCard(
            title = row.title,
            value = row.summary,
            showDivider = showDivider,
        )
    }
}

/** 弹层宿主：按目标类型分派既有弹层；离场段由弹层自身两段式闸口保留（B29 口径）。 */
@Composable
private fun B3SheetHost(target: B3SheetTarget?, onDismiss: () -> Unit) {
    when (target) {
        is B3SheetTarget.Text -> TextEditSheet(
            visible = true,
            title = target.title,
            initial = target.initial,
            hint = target.hint,
            password = target.password,
            onConfirm = { value ->
                target.onConfirm(value)
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        is B3SheetTarget.Number -> NumberPickerSheet(
            visible = true,
            title = target.title,
            min = target.min,
            max = target.max,
            current = clampB3Int(target.current, target.min, target.max),
            unit = target.unit,
            onConfirm = { value ->
                target.onConfirm(value)
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        is B3SheetTarget.ListPick -> ListPickSheet(
            visible = true,
            title = target.title,
            entries = target.entries,
            values = target.values,
            current = target.current,
            onPick = { value ->
                target.onPick(value)
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        is B3SheetTarget.Confirm -> ConfirmSheet(
            visible = true,
            title = target.title,
            message = target.message,
            confirmLabel = target.confirmLabel,
            danger = true,
            onConfirm = {
                target.onConfirm()
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}

/** 列表项稳定键：分组内按类型 + 标题 / 键名取唯一标识，重组不丢滚动位置。 */
private fun rowKey(row: B3Row, fallback: Int): String = when (row) {
    is B3SwitchRow -> "sw-${row.key}"
    is B3ValueRow -> "val-${row.title}"
    is B3InfoRow -> "info-${row.title}"
}.takeIf { it.isNotBlank() } ?: "row-$fallback"
