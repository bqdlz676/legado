package io.legado.app.ui.file

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import io.legado.app.ui.components.ToolPageScaffold
import io.legado.app.ui.components.ToolRow
import io.legado.app.ui.components.toolListArrangement
import io.legado.app.ui.components.contract.ComponentVisualState
import java.io.File

/**
 * 落文 · 本机文件浏览页（Compose 自绘）。
 *
 * 功能对照旧 `FileManageActivity` + 两个 Adapter：路径面包屑 / 当前层文件与目录 /
 * 名称搜索过滤 / 进入目录 / 返回上级 / 调用系统应用打开文件 / 长按删除。
 * 兜底：根目录不可用 → 错误文案；空目录 → 空态；删除失败留在当前层并提示。
 */
@Composable
fun FileManageScreen(
    state: FileManageState,
    visibleFiles: List<File>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenDir: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    onPop: () -> Unit,
    onRetry: () -> Unit,
    onJump: (Int) -> Unit,
    onRequestDelete: (File) -> Unit,
    onDismissConfirm: () -> Unit,
    onExecuteDelete: (File) -> Unit,
) {
    ToolPageScaffold(title = "文件管理", onBack = onBack) {
        Breadcrumb(
            segments = state.subDocs,
            onRoot = onPop,
            onJump = onJump,
        )
        SearchBox(query = state.query, onQueryChange = onQueryChange)
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.text.BasicText(
                    "载入中…",
                    style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 14.sp),
                )
            }

            state.error != null -> InkEmptyState(
                message = state.error ?: "目录读取失败",
                offline = true,
                state = ComponentVisualState.OFFLINE,
                actionLabel = "重试",
                onAction = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            visibleFiles.isEmpty() -> InkEmptyState(
                message = "当前目录为空",
                state = ComponentVisualState.EMPTY,
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalArrangement = toolListArrangement,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(visibleFiles, key = { it.absolutePath }) { file ->
                    val isParent = file == state.subDocs.lastOrNull() ?: state.subDocs.firstOrNull()
                    FileRow(
                        file = file,
                        isParent = isParent,
                        onClick = {
                            when {
                                isParent -> onPop()
                                file.isDirectory -> onOpenDir(file)
                                else -> onOpenFile(file)
                            }
                        },
                        onLongClick = { if (!isParent) onRequestDelete(file) },
                    )
                }
            }
        }
    }

    state.confirmDelete?.let { target ->
        Dialog(onDismissRequest = onDismissConfirm) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                androidx.compose.foundation.text.BasicText(
                    "删除「${target.name}」？",
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                androidx.compose.foundation.text.BasicText(
                    target.absolutePath,
                    style = TextStyle(color = ComponentTokens.textSecondary, fontSize = 13.sp),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    InkButton("取消", onDismissConfirm, variant = InkButtonVariant.GHOST)
                    Spacer(Modifier.width(8.dp))
                    InkButton("删除", { onExecuteDelete(target) }, variant = InkButtonVariant.PRIMARY)
                }
            }
        }
    }
}

@Composable
private fun Breadcrumb(
    segments: List<File>,
    onRoot: () -> Unit,
    onJump: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb("root", onClick = onRoot)
        segments.forEachIndexed { index, file ->
            androidx.compose.foundation.text.BasicText(
                "/",
                style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 13.sp),
            )
            Crumb(file.name, onClick = { onJump(index) })
        }
    }
}

@Composable
private fun Crumb(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(ComponentTokens.buttonRadius))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            label,
            style = TextStyle(
                color = ComponentTokens.stoneBlue,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
            ),
        )
    }
}

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
                        "搜索文件名",
                        style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 14.sp),
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun FileRow(
    file: File,
    isParent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ToolRow(onClick = onClick, onLongClick = onLongClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                when {
                    isParent -> "←"
                    file.isDirectory -> "▸"
                    else -> "▤"
                },
                style = TextStyle(
                    color = ComponentTokens.brandGold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier.width(24.dp),
            )
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicText(
                    if (isParent) ".." else file.name,
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                if (!isParent && !file.isDirectory) {
                    androidx.compose.foundation.text.BasicText(
                        "${file.length() / 1024} KB",
                        style = TextStyle(
                            color = ComponentTokens.textTertiary,
                            fontSize = 12.sp,
                        ),
                    )
                }
            }
        }
    }
}
