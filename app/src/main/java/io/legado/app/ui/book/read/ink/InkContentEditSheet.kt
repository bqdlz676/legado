package io.legado.app.ui.book.read.ink

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.HalfSheetAnchors
import io.legado.app.ui.components.HalfSheetParams
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.InkLoadingDots
import io.legado.app.ui.components.rememberInkGesture
import kotlin.math.roundToInt

/**
 * Ink 内容编辑半屏面板（B31 承接件第 1 件）。
 *
 * 〔形态基线〕照既有 [io.legado.app.ui.components.InkHalfSheet] 的视觉契约：绢帛纸白 Sheet 面 +
 * 暖金 1px 边框 + 顶部圆角 + 4dp 金杠指示器 + 茶玻遮罩暗化 40%；弹起动效走
 * [ComponentTokens.sheetSpring] 弹簧；不套 MaterialTheme、不取 Material colorScheme、不用
 * ripple / elevation，颜色只引 [ComponentTokens] / [HalfSheetParams]。
 *
 * 〔能力对照旧页〕正文编辑、标题行内改名、正文搜索（正则 / 大小写 / 上下条 / 计数 / 非法正则
 * 提示）、保存、重置、复制全部、纯文本模式、位置进度条、脏数据离场确认、加载 / 空态 / 超长 /
 * 失败兜底；逐项覆盖清单见实现报告。
 *
 * 〔边界〕空正文、超长正文、加载失败、保存失败、进程重建均由状态层结果态驱动可读兜底；
 * 编辑器本体不崩、不留白。Compose 观感不上机，不在此假称真机验证。
 *
 * 〔拆分落点〕本件只留面板骨架（Dialog / 遮罩 / Sheet 布局 / 金杠指示器 / 消息条 / 离场确认浮层）；
 * 标题与搜索输入见 [InkContentEditInputs]，正文编辑区与进度条见 [InkContentEditEditor]，
 * 底部动作区见 [InkContentEditFooter]；状态判定见 [InkContentEditState] 与 [InkContentEditRules]。
 *
 * @param state 面板状态持有者（草稿 / 基线 / 搜索 / 离场确认）。
 * @param onSave 保存落盘回调；宿主走 [InkContentEditStore.save]，成功后由状态层收口。
 * @param onReset 重置回调：删本地缓存并按书源重新拉取（本地书仅清缓存）。
 * @param onRename 标题改名回调；参数为 trim 后的新标题，已过规则层校验。
 * @param onCopyAll 复制全部回调：标题 + 正文进剪贴板，宿主接剪贴板工具。
 * @param onTogglePlainText 纯文本开关回调；宿主持久化偏好。
 * @param onReload 加载失败后的重试回调。
 * @param onDismiss 遮罩 / 返回 / 取消关闭请求；离场裁决在状态层，脏数据先弹确认。
 */
@Composable
fun InkContentEditSheet(
    state: InkContentEditState,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onRename: (String) -> Unit,
    onCopyAll: () -> Unit,
    onTogglePlainText: (Boolean) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return

    // 编辑器滚动态提升到面板层：位置进度条与正文滚动共用同一状态，避免两条滚动轴脱节。
    val editorScroll = rememberScrollState()
    // 弹入弹簧：从 0 升到 1，刚度 / 阻尼取组件库 sheetSpring 单源，不在本层另造物理数。
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, ComponentTokens.sheetSpring())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxSheetHeight = maxHeight * HalfSheetAnchors.MAX_HEIGHT_FRACTION

            // ── 茶玻遮罩：暗化上限 40%，模糊基准 12dp；点击走墨触手势，不引 ripple ──
            Box(
                Modifier
                    .fillMaxSize()
                    .blur(HalfSheetAnchors.MASK_BLUR_DP.dp)
                    .background(
                        ComponentTokens.bgOverlay.copy(
                            alpha = HalfSheetParams.DIM_ALPHA * progress.value.coerceIn(0f, 1f),
                        ),
                    )
                    .then(rememberInkGesture(enabled = true, onClick = onDismiss).gestureModifier),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .offset {
                        IntOffset(0, ((1f - progress.value.coerceIn(0f, 1f)) * 120f).roundToInt())
                    }
                    .clip(
                        RoundedCornerShape(
                            topStart = ComponentTokens.sheetRadius,
                            topEnd = ComponentTokens.sheetRadius,
                        ),
                    )
                    .background(Color(HalfSheetParams.sheetBackground))
                    .border(
                        1.dp,
                        ComponentTokens.brandGold,
                        RoundedCornerShape(
                            topStart = ComponentTokens.sheetRadius,
                            topEnd = ComponentTokens.sheetRadius,
                        ),
                    )
                    .padding(bottom = 12.dp),
            ) {
                ContentEditGrabber()

                // 消息条：成功暖金 / 失败朱砂 / 提示石青；空消息不渲染。
                state.message?.let { msg -> EditMessageBar(msg) }

                TitleRow(state, onRename)

                if (state.searchVisible) {
                    SearchBar(state)
                }

                // ── 编辑区：竖滑滚动；空态 / 加载态 / 失败态各有可读兜底 ──
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    when {
                        state.loading -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { InkLoadingDots() }

                        state.loadError != null -> {
                            val error = state.loadError!!
                            InkEmptyState(
                                message = error,
                                compact = true,
                                actionLabel = LocalContext.current.getString(R.string.retry),
                                onAction = onReload,
                            )
                        }

                        state.content.isBlank() -> InkEmptyState(
                            message = LocalContext.current.getString(R.string.content_empty),
                            compact = true,
                        )

                        else -> EditorBody(state, editorScroll)
                    }
                }

                PositionBar(editorScroll)

                FooterActions(
                    state = state,
                    onSave = onSave,
                    onReset = onReset,
                    onCopyAll = onCopyAll,
                    onTogglePlainText = onTogglePlainText,
                    onDismiss = onDismiss,
                )
            }

            // ── 脏数据离场确认层：盖住 Sheet 本体，继续编辑 / 放弃改动二选一 ──
            if (state.confirmDiscard) {
                DiscardConfirm(
                    onKeep = state::cancelDiscard,
                    onDiscard = state::confirmDiscardClose,
                )
            }
        }
    }
}

/** 金杠拖拽指示器：4dp 直边金色横杠 + 两端金点，Canvas 自绘，不使用圆角 Shape。 */
@Composable
private fun ContentEditGrabber() {
    val barWidth = with(LocalDensity.current) {
        HalfSheetAnchors.GRABBER_BAR_WIDTH_DP.dp.toPx()
    }
    val barHeight = with(LocalDensity.current) {
        HalfSheetParams.GRABBER_BAR_HEIGHT_DP.dp.toPx()
    }
    val dotRadius = with(LocalDensity.current) {
        HalfSheetAnchors.GRABBER_DOT_RADIUS_DP.dp.toPx()
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val barLeft = cx - barWidth / 2f
        drawRect(
            color = Color(HalfSheetParams.grabberColor),
            topLeft = Offset(barLeft, cy - barHeight / 2f),
            size = Size(barWidth, barHeight),
        )
        drawCircle(Color(HalfSheetParams.grabberColor), radius = dotRadius, center = Offset(barLeft, cy))
        drawCircle(
            Color(HalfSheetParams.grabberColor),
            radius = dotRadius,
            center = Offset(barLeft + barWidth, cy),
        )
    }
}

/** 消息条：按类别取色；文案由宿主结果态灌入。 */
@Composable
private fun EditMessageBar(message: EditMessage) {
    val color = when (message.kind) {
        EditMessageKind.SUCCESS -> ComponentTokens.brandGold
        EditMessageKind.FAILURE -> ComponentTokens.textAccent
        EditMessageKind.INFO -> ComponentTokens.stoneBlue
    }
    BasicText(
        text = message.text,
        style = TextStyle(color = color, fontSize = 13.sp, fontFamily = FontFamily.Serif),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** 脏数据确认层：半透明茶玻压底 + 绢帛确认卡；继续编辑留在面板，放弃收口会话。 */
@Composable
private fun DiscardConfirm(onKeep: () -> Unit, onDiscard: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(ComponentTokens.bgOverlay)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(ComponentTokens.cardRadius))
                .background(Color(HalfSheetParams.sheetBackground))
                .border(1.dp, ComponentTokens.brandGold, RoundedCornerShape(ComponentTokens.cardRadius))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(
                text = context.getString(R.string.exit_no_save),
                style = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 24.sp,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton(
                    text = context.getString(R.string.cancel),
                    variant = InkButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                    onClick = onKeep,
                )
                InkButton(
                    text = context.getString(R.string.exit),
                    variant = InkButtonVariant.GHOST,
                    modifier = Modifier.weight(1f),
                    onClick = onDiscard,
                )
            }
        }
    }
}
