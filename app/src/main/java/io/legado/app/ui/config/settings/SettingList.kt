package io.legado.app.ui.config.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.ui.config.configPreferenceMatches
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString

/**
 * 设置列表渲染器：把 [SettingGroup] 列表铺成顺滑 LazyColumn。
 *
 * - SwitchRow / ListRow 自己读偏好状态并落盘，副作用经 [SwitchRow.onChange] 回吐；
 * - ValueRow 的点击动作由各分区页面注入（图片选择 / 备份流程 / 跳转等）；
 * - 空分组 / 空列表落到兜底文案，不留空白态。
 */
@Composable
fun SettingList(groups: List<SettingGroup>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val query = LocalSettingSearchQuery.current
    val searching = query.isNotBlank()
    // 空 query 时零过滤：原分组、原顺序、原可见性全部保留；非空才按行裁剪，整组无命中则隐藏。
    val visibleGroups = if (!searching) groups else groups.mapNotNull { group ->
        val rows = group.rows.filter { it.matchesSettingQuery(query) }
        if (rows.isEmpty()) null else group.copy(rows = rows)
    }
    if (visibleGroups.isEmpty()) {
        Column(modifier = modifier) {
            SettingRowShell(onClick = null, enabled = false) {
                RowTitleBlock(if (searching) "未找到匹配的设置项" else "暂无设置项", null)
            }
        }
        return
    }
    LazyColumn(modifier = modifier) {
        visibleGroups.forEach { group ->
            if (!group.title.isNullOrBlank()) {
                item(key = "header-${group.title}") { GroupHeader(group.title) }
            }
            items(group.rows, key = { row ->
                when (row) {
                    is SwitchRow -> "sw-${row.key}"
                    is ValueRow -> "val-${row.title}"
                    is ListRow -> "list-${row.key}"
                    is ColorSwatchRow -> "color-${row.key}"
                    is GroupHeader -> "h-${row.title}"
                }
            }) { row ->
                when (row) {
                    is GroupHeader -> GroupHeader(row.title)
                    is SwitchRow -> SwitchRowItem(row)
                    is ValueRow -> ValueRowItem(row)
                    is ListRow -> ListRowItem(row)
                    is ColorSwatchRow -> ColorSwatchRowItem(row)
                }
            }
            item(key = "divider-${group.title}") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SwitchRowItem(row: SwitchRow) {
    val context = LocalContext.current
    val checked = rememberPrefBoolean(row.key, row.def)
    SettingRowShell(onClick = null, enabled = row.enabled) {
        RowTitleBlock(row.title, row.summary, modifier = Modifier.weight(1f))
        InkSwitch(
            checked = checked,
            enabled = row.enabled,
            onCheckedChange = { next ->
                context.putPrefBoolean(row.key, next)
                row.onChange?.invoke(next)
            },
        )
    }
}

@Composable
private fun ValueRowItem(row: ValueRow) {
    SettingRowShell(onClick = row.onClick, enabled = row.enabled) {
        RowTitleBlock(row.title, row.summary, danger = row.danger, modifier = Modifier.weight(1f))
        RowValueText(row.summary, arrow = true)
    }
}

@Composable
private fun ColorSwatchRowItem(row: ColorSwatchRow) {
    SettingRowShell(onClick = { row.onOpen(row.title) }, enabled = true) {
        RowTitleBlock(row.title, row.summary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        ColorSwatchBlock(row.color)
    }
}

@Composable
private fun ListRowItem(row: ListRow) {
    val context = LocalContext.current
    val current = rememberPrefString(row.key, row.def) ?: row.def
    var sheetVisible by remember { mutableStateOf(false) }
    val currentLabel = row.entries.getOrNull(row.values.indexOf(current)) ?: current
    SettingRowShell(onClick = { sheetVisible = true }, enabled = true) {
        RowTitleBlock(row.title, row.summary ?: currentLabel, modifier = Modifier.weight(1f))
        RowValueText(currentLabel, arrow = true)
    }
    ListPickSheet(
        visible = sheetVisible,
        title = row.title,
        entries = row.entries,
        values = row.values,
        current = current,
        onPick = { value ->
            context.putPrefString(row.key, value)
            row.onChange(value)
        },
        onDismiss = { sheetVisible = false },
    )
}

/**
 * 单行搜索命中：标题 / 说明 / 关键词任一子串包含，大小写不敏感。
 * 匹配口径完全委托顶层 [configPreferenceMatches]，渲染层不另写一套大小写 / 空白规则。
 */
private fun SettingRow.matchesSettingQuery(query: String): Boolean = when (this) {
    is GroupHeader -> configPreferenceMatches(query, title, null, emptyList())
    is SwitchRow -> configPreferenceMatches(query, title, summary, keywords)
    is ValueRow -> configPreferenceMatches(query, title, summary, keywords)
    is ListRow -> configPreferenceMatches(query, title, summary, keywords)
    is ColorSwatchRow -> configPreferenceMatches(query, title, summary, keywords)
}
