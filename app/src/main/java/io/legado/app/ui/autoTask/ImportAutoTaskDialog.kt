package io.legado.app.ui.autoTask

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.legado.app.data.entities.AutoTaskRule
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.InkHalfSheet
import io.legado.app.ui.components.ToolRow
import io.legado.app.ui.config.settings.rememberSheetExitController
import io.legado.app.ui.sheet.HalfSheetExitHost
import io.legado.app.utils.GSON

/**
 * 落文 · 自动任务导入弹窗（Compose 自绘壳）。
 *
 * 保留旧构造口径 [ImportAutoTaskDialog] (source, finishOnDismiss)，外部调用方不变。
 * 解析 / 比对 / 勾选落库归 [ImportAutoTaskViewModel]。
 * 兜底：解析失败 → 错误文案；空结果 → 空态；未勾选 → 导入按钮禁用。
 */
class ImportAutoTaskDialog() : DialogFragment() {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportAutoTaskViewModel>()
    private var finishOnDismiss: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        finishOnDismiss = arguments?.getBoolean("finishOnDismiss") ?: false
        val source = arguments?.getString("source").orEmpty()
        if (source.isBlank()) {
            dismissAllowingStateLoss()
        } else {
            viewModel.importSource(source)
        }
        return ComposeView(requireContext()).apply {
            setContent {
                val state = viewModel.uiState.collectAsState().value
                var jsonTarget by remember { mutableStateOf<AutoTaskRule?>(null) }
                // B29：JSON 预览半屏两段式离场控制器；程序化入口先过 canOpen 闸再 open()。
                val jsonExit = rememberSheetExitController()
                LaunchedEffect(jsonTarget) {
                    if (jsonTarget != null && jsonExit.canOpen) jsonExit.open()
                }
                ImportContent(
                    state = state,
                    onToggle = viewModel::toggle,
                    onToggleAll = viewModel::toggleAll,
                    onViewJson = { jsonTarget = it },
                    onImport = {
                        viewModel.importSelect {
                            dismissAllowingStateLoss()
                            if (finishOnDismiss) activity?.finish()
                        }
                    },
                    onDismiss = {
                        dismissAllowingStateLoss()
                        if (finishOnDismiss) activity?.finish()
                    },
                )
                jsonTarget?.let { task ->
                    HalfSheetExitHost(jsonExit, onFinished = { jsonTarget = null }) {
                        InkHalfSheet(
                            visible = jsonExit.visible,
                            onDismiss = { jsonExit.beginExit() },
                            title = task.name,
                            content = {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    androidx.compose.foundation.text.BasicText(
                                        GSON.toJson(task),
                                        style = TextStyle(
                                            color = ComponentTokens.textSecondary,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Serif,
                                            lineHeight = 18.sp,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ImportContent(
        state: ImportAutoTaskState,
        onToggle: (Int) -> Unit,
        onToggleAll: () -> Unit,
        onViewJson: (AutoTaskRule) -> Unit,
        onImport: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    "导入自动任务",
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    modifier = Modifier.weight(1f),
                )
                InkButton("关闭", onDismiss, variant = InkButtonVariant.GHOST)
            }

            when {
                state.loading -> Box(Modifier.height(120.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.text.BasicText(
                        "解析中…",
                        style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 14.sp),
                    )
                }

                state.error != null -> InkEmptyState(
                    message = state.error ?: "解析失败",
                    offline = true,
                    actionLabel = "关闭",
                    onAction = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.tasks.isEmpty() -> InkEmptyState(
                    message = "没有可导入的任务",
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.tasks) { index, task ->
                        val local = state.local.getOrNull(index)
                        val checked = state.selected.getOrNull(index) ?: false
                        val status = when {
                            local == null -> "新增"
                            !sameAutoTaskForImport(task, local) -> "更新"
                            else -> "已存在"
                        }
                        ToolRow(onClick = { onToggle(index) }, selected = checked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    androidx.compose.foundation.text.BasicText(
                                        task.name.ifBlank { task.id },
                                        style = TextStyle(
                                            color = ComponentTokens.textPrimary,
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Serif,
                                        ),
                                    )
                                    androidx.compose.foundation.text.BasicText(
                                        status,
                                        style = TextStyle(
                                            color = ComponentTokens.stoneBlue,
                                            fontSize = 12.sp,
                                        ),
                                    )
                                }
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { onViewJson(task) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        "查看",
                                        style = TextStyle(
                                            color = ComponentTokens.textSecondary,
                                            fontSize = 13.sp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                InkButton(
                    if (state.isSelectAll) "取消全选" else "全选",
                    onToggleAll,
                    variant = InkButtonVariant.GHOST,
                )
                Spacer(Modifier.width(8.dp))
                InkButton("取消", onDismiss, variant = InkButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
                InkButton(
                    "导入（${state.selectCount}）",
                    onImport,
                    variant = InkButtonVariant.PRIMARY,
                    enabled = state.selectCount > 0,
                )
            }
        }
    }
}
