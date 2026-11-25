package io.legado.app.ui.book.read.ink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.book.read.page.ReaderDetailKind
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.HalfSheetFallback
import io.legado.app.ui.components.InkHalfSheet

/**
 * 阅读页点字详情半屏覆盖层（B1 接线）。
 *
 * 宿主 View（ContentTextView）把命中结果打包成 [ReaderDetailRequest] 经状态持有者灌入；
 * 本覆盖层按入口类型分派人物 / 纪年 / 字词 / 地名四个独立调用点，各自渲染统一自绘
 * [InkHalfSheet]（`dialog = true` 跨窗口覆盖层模式，适配阅读页 AndroidView 宿主）。
 *
 * 铁律（切片-11 §5.4）：覆盖层只读宿主内容，不滚动正文、不改写选中 / 下划线；
 * 开合前后快照由 [ReaderSheetState] 采集比对。本层不套 MaterialTheme、不取
 * Material colorScheme、不用 ripple / elevation；颜色只引 [ComponentTokens]。
 *
 * @param state 状态持有者；`state.request` 为 null 时覆盖层整体不组合。
 * @param onDismiss 关闭回调；宿主据此采关闭后快照并收口覆盖层。
 * @param onLinkSelected 分区行点击回传跳转 query（同名候选 / 同场人物切换）；遗留通道暂无候选映射时为空操作。
 */
@Composable
fun ReaderSheetOverlay(
    state: ReaderSheetState,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit = {},
) {
    val request = state.request ?: return
    when (request.kind) {
        ReaderDetailKind.PERSON -> PersonSheetBody(request, onDismiss, onLinkSelected)
        ReaderDetailKind.YEAR -> YearSheetBody(request, onDismiss, onLinkSelected)
        ReaderDetailKind.CHAR -> CharSheetBody(request, onDismiss, onLinkSelected)
        ReaderDetailKind.PLACE -> PlaceSheetBody(request, onDismiss, onLinkSelected)
    }
}

/** 人物 Sheet 调用点（切片-11 §5.2 固定顺序由组件模型层收口；遗留通道先以摘录 + 小传正文落槽）。 */
@Composable
private fun PersonSheetBody(
    request: ReaderDetailRequest,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
) {
    InkHalfSheet(
        visible = true,
        onDismiss = onDismiss,
        dialog = true,
        title = request.token,
        source = request.source,
        onLinkSelected = onLinkSelected,
        content = { DetailBodyLines(request) },
    )
}

/** 纪年 / 干支 Sheet 调用点：公元对照、干支、距今与多政权对照正文落自定义槽。 */
@Composable
private fun YearSheetBody(
    request: ReaderDetailRequest,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
) {
    InkHalfSheet(
        visible = true,
        onDismiss = onDismiss,
        dialog = true,
        title = request.token,
        source = request.source,
        onLinkSelected = onLinkSelected,
        content = { DetailBodyLines(request) },
    )
}

/** 字词 / 典故 Sheet 调用点（切片-11 §5.3）：拼音义项、说文、用例、词组正文落自定义槽。 */
@Composable
private fun CharSheetBody(
    request: ReaderDetailRequest,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
) {
    InkHalfSheet(
        visible = true,
        onDismiss = onDismiss,
        dialog = true,
        title = request.token,
        source = request.source,
        onLinkSelected = onLinkSelected,
        content = { DetailBodyLines(request) },
    )
}

/** 地名 Sheet 调用点：沿革正文 + 同名候选列表；候选行点击回传裸 query 交宿主重查。 */
@Composable
private fun PlaceSheetBody(
    request: ReaderDetailRequest,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
) {
    InkHalfSheet(
        visible = true,
        onDismiss = onDismiss,
        dialog = true,
        title = request.token,
        source = request.source,
        onLinkSelected = onLinkSelected,
        content = { DetailBodyLines(request) },
    )
}

/**
 * 自定义正文槽：摘录（人物入口）+ 释义 / 沿革 / 对照正文逐行渲染；无载字段出
 * [HalfSheetFallback.MISSING_TEXT] 占位，不留空白态。地名同名候选附在正文之后。
 */
@Composable
private fun DetailBodyLines(request: ReaderDetailRequest) {
    val lines = buildList {
        if (request.excerpt.isNotBlank()) add(request.excerpt.trim())
        addAll(
            request.detail.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() },
        )
    }
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (lines.isEmpty()) {
            BasicText(
                text = HalfSheetFallback.MISSING_TEXT,
                style = TextStyle(
                    color = ComponentTokens.textTertiary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        } else {
            lines.forEach { line ->
                BasicText(
                    text = line,
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 26.sp,
                    ),
                )
            }
        }
        if (request.placeOthers.isNotEmpty()) {
            request.placeOthers.forEach { other ->
                BasicText(
                    text = "同名 · $other",
                    style = TextStyle(
                        color = ComponentTokens.textSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 22.sp,
                    ),
                )
            }
        }
    }
}
