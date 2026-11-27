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
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.legado.app.ui.config.settings.SheetExitHost
import io.legado.app.ui.config.settings.TextEditSheet
import io.legado.app.ui.config.settings.rememberSheetExitController
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
    // O-14：搜索词 / 测试结果为 String，走 rememberSaveable 跨配置变更存活；弹层目标持有 lambda 不可序列化，
    // 仍 remember 持有，待壳态批次提升到宿主状态机（见返工报告 §O-14）。
    var query by rememberSaveable { mutableStateOf("") }
    var webDavTestSummary by rememberSaveable { mutableStateOf<String?>(null) }

    // B29 收口（Q466 / F-1）：文本 / 数字 / 单选 / 确认四类弹层各持独立槽位与一份两段式离场控制器。
    // 阶段机链路委托 config 域 SheetExitController → NoteDialogExitGate → B30 SheetExitPhaseGate，
    // b3 包内不造第二份枚举 / 守卫 / 派生表达式；四槽位互不串阶段（与 OtherSection 同口径）。
    var textTarget by remember { mutableStateOf<B3SheetTarget.Text?>(null) }
    var numberTarget by remember { mutableStateOf<B3SheetTarget.Number?>(null) }
    var listTarget by remember { mutableStateOf<B3SheetTarget.ListPick?>(null) }
    var confirmTarget by remember { mutableStateOf<B3SheetTarget.Confirm?>(null) }
    val textExit = rememberSheetExitController()
    val numberExit = rememberSheetExitController()
    val listExit = rememberSheetExitController()
    val confirmExit = rememberSheetExitController()

    val groups = b3SettingGroups(
        actions = actions,
        webDavTestSummary = webDavTestSummary,
        onTestWebDav = {
            actions.testWebDav { ok ->
                webDavTestSummary = b3WebDavTestSummary(ok)
            }
        },
        openTarget = { target ->
            // 先过 canOpen 闸：离场动画播放中不叠同类新弹层；放行后再登记槽位、open() 回在场段。
            when (target) {
                is B3SheetTarget.Text -> if (textExit.canOpen) {
                    textTarget = target
                    textExit.open()
                }
                is B3SheetTarget.Number -> if (numberExit.canOpen) {
                    numberTarget = target
                    numberExit.open()
                }
                is B3SheetTarget.ListPick -> if (listExit.canOpen) {
                    listTarget = target
                    listExit.open()
                }
                is B3SheetTarget.Confirm -> if (confirmExit.canOpen) {
                    confirmTarget = target
                    confirmExit.open()
                }
            }
        },
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

    // 两段式离场收束宿主：dismiss 先走 beginExit() 进离场段（派生 visible=false、子树保留播离场动画），
    // SheetExitHost 按 ComponentPalette.DURATION_MASK 等效延时后 finishExit()，回调清槽位摘子树；
    // 不再 dismiss 即摘（旧 B3SheetHost 直清槽位路径已删，不留兼容层）。
    textTarget?.let { target ->
        SheetExitHost(textExit, onFinished = { textTarget = null }) {
            TextEditSheet(
                visible = textExit.visible,
                title = target.title,
                initial = target.initial,
                hint = target.hint,
                password = target.password,
                onConfirm = { value -> target.onConfirm(value) },
                onDismiss = { textExit.beginExit() },
            )
        }
    }
    numberTarget?.let { target ->
        SheetExitHost(numberExit, onFinished = { numberTarget = null }) {
            NumberPickerSheet(
                visible = numberExit.visible,
                title = target.title,
                min = target.min,
                max = target.max,
                current = clampB3Int(target.current, target.min, target.max),
                unit = target.unit,
                defaultLabel = target.defaultLabel,
                onDefault = target.onDefault,
                onConfirm = { value -> target.onConfirm(value) },
                onDismiss = { numberExit.beginExit() },
            )
        }
    }
    listTarget?.let { target ->
        SheetExitHost(listExit, onFinished = { listTarget = null }) {
            ListPickSheet(
                visible = listExit.visible,
                title = target.title,
                entries = target.entries,
                values = target.values,
                current = target.current,
                onPick = { value -> target.onPick(value) },
                onDismiss = { listExit.beginExit() },
            )
        }
    }
    confirmTarget?.let { target ->
        SheetExitHost(confirmExit, onFinished = { confirmTarget = null }) {
            ConfirmSheet(
                visible = confirmExit.visible,
                title = target.title,
                message = target.message,
                confirmLabel = target.confirmLabel,
                danger = true,
                onConfirm = { target.onConfirm() },
                onDismiss = { confirmExit.beginExit() },
            )
        }
    }
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

/** 列表项稳定键：分组内按类型 + 标题 / 键名取唯一标识，重组不丢滚动位置。 */
private fun rowKey(row: B3Row, fallback: Int): String = when (row) {
    is B3SwitchRow -> "sw-${row.key}"
    is B3ValueRow -> "val-${row.title}"
    is B3InfoRow -> "info-${row.title}"
}.takeIf { it.isNotBlank() } ?: "row-$fallback"
