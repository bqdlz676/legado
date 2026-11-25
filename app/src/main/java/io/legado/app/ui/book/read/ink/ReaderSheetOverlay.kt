package io.legado.app.ui.book.read.ink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.book.read.page.ReaderDetailKind
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.HalfSheetAnchors
import io.legado.app.ui.components.HalfSheetFallback
import io.legado.app.ui.components.InkHalfSheet
import kotlinx.coroutines.delay

/**
 * 阅读页点字详情半屏覆盖层（B1 接线；B21 补两段式离场）。
 *
 * 宿主 View（ContentTextView）把命中结果打包成 [ReaderDetailRequest] 经状态持有者灌入；
 * 本覆盖层按入口类型分派人物 / 纪年 / 字词 / 地名四个独立调用点，各自渲染统一自绘
 * [InkHalfSheet]（`dialog = true` 跨窗口覆盖层模式，适配阅读页 AndroidView 宿主）。
 *
 * 铁律（切片-11 §5.4）：覆盖层只读宿主内容，不滚动正文、不改写选中 / 下划线；
 * 开合前后快照由 [ReaderSheetState] 采集比对。本层不套 MaterialTheme、不取
 * Material colorScheme、不用 ripple / elevation；颜色只引 [ComponentTokens]。
 *
 * 〔B21 两段式关闭〕关闭不立即摘子树：[ReaderSheetState.dismiss] 后离场守卫保留原
 * 请求、`visible=false` 播组件内离场动画；本覆盖层按保留请求继续组合，待离场动画
 * 收束（遮罩 [HalfSheetAnchors.MASK_DURATION_MS] + Sheet [HalfSheetAnchors.RISE_DURATION_MS]，
 * 组件内顺序播放）后回调 [ReaderSheetState.finishExitAnimation] 清空保留请求，子树摘除。
 * 摘除时机以组件自有动画时长为等效延时依据，不写「立即摘」。
 *
 * @param state 状态持有者；离场收束后无保留请求时覆盖层整体不组合。
 * @param onDismiss 关闭回调；宿主据此采关闭后快照并收口覆盖层。
 * @param onLinkSelected 分区行点击回传跳转 query（同名候选 / 同场人物切换）；遗留通道暂无候选映射时为空操作。
 */
@Composable
fun ReaderSheetOverlay(
    state: ReaderSheetState,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit = {},
) {
    val request = state.exitRequest ?: return
    val visible = state.exitVisible

    // 离场段：等效延时对齐组件内离场动画总时长（遮罩 + Sheet 顺序播放），收束后再摘子树。
    // InkHalfSheet 契约未暴露动画完成回调，按锚点卡允许的「等效延时」口径收口；时长只引
    // 组件自有常量，不在本层另造魔法数。
    LaunchedEffect(state.isExiting) {
        if (state.isExiting) {
            delay((HalfSheetAnchors.MASK_DURATION_MS + HalfSheetAnchors.RISE_DURATION_MS).toLong())
            state.finishExitAnimation()
        }
    }

    when (request.kind) {
        ReaderDetailKind.PERSON -> PersonSheetBody(request, visible, onDismiss, onLinkSelected)
        ReaderDetailKind.YEAR -> YearSheetBody(request, visible, onDismiss, onLinkSelected)
        ReaderDetailKind.CHAR -> CharSheetBody(request, visible, onDismiss, onLinkSelected)
        ReaderDetailKind.PLACE -> PlaceSheetBody(request, visible, onDismiss, onLinkSelected)
    }
}

/** 人物 Sheet 调用点（切片-11 §5.2 固定顺序由组件模型层收口；遗留通道先以摘录 + 小传正文落槽）。 */
@Composable
private fun PersonSheetBody(
    request: ReaderDetailRequest,
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
) {
    InkHalfSheet(
        visible = visible,
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
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
) {
    InkHalfSheet(
        visible = visible,
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
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
) {
    InkHalfSheet(
        visible = visible,
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
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (String) -> Unit,
) {
    InkHalfSheet(
        visible = visible,
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
