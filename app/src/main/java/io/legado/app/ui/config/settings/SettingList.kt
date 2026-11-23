package io.legado.app.ui.config.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    if (groups.isEmpty()) {
        Column(modifier = modifier) {
            SettingRowShell(onClick = null, enabled = false) {
                RowTitleBlock("暂无设置项", null)
            }
        }
        return
    }
    LazyColumn(modifier = modifier) {
        groups.forEach { group ->
            if (!group.title.isNullOrBlank()) {
                item(key = "header-${group.title}") { GroupHeader(group.title) }
            }
            items(group.rows, key = { row ->
                when (row) {
                    is SwitchRow -> "sw-${row.key}"
                    is ValueRow -> "val-${row.title}"
                    is ListRow -> "list-${row.key}"
                    is GroupHeader -> "h-${row.title}"
                }
            }) { row ->
                when (row) {
                    is GroupHeader -> GroupHeader(row.title)
                    is SwitchRow -> SwitchRowItem(row)
                    is ValueRow -> ValueRowItem(row)
                    is ListRow -> ListRowItem(row)
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
        RowTitleBlock(row.title, row.summary)
        InkSwitch(checked = checked, enabled = row.enabled) { next ->
            context.putPrefBoolean(row.key, next)
            row.onChange?.invoke(next)
        }
    }
}

@Composable
private fun ValueRowItem(row: ValueRow) {
    SettingRowShell(onClick = row.onClick, enabled = row.enabled) {
        RowTitleBlock(row.title, row.summary, danger = row.danger)
        RowValueText(row.summary, arrow = true)
    }
}

@Composable
private fun ListRowItem(row: ListRow) {
    val context = LocalContext.current
    val current = rememberPrefString(row.key, row.def) ?: row.def
    var sheetVisible by remember { mutableStateOf(false) }
    val currentLabel = row.entries.getOrNull(row.values.indexOf(current)) ?: current
    SettingRowShell(onClick = { sheetVisible = true }, enabled = true) {
        RowTitleBlock(row.title, row.summary ?: currentLabel)
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
