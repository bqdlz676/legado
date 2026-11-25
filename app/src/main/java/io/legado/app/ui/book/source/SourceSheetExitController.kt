package io.legado.app.ui.book.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.legado.app.ui.book.read.NoteDialogExitGate
import io.legado.app.ui.components.HalfSheetAnchors
import kotlinx.coroutines.delay

/**
 * B29 书源域（source）弹层两段式离场控制器（Compose 层接线；阶段机复用 B26 [NoteDialogExitGate]）。
 *
 * 〔复用口径〕
 * 阶段枚举 `ReaderSheetExitPhase`（OPEN / EXITING / REMOVED）与迁移规则直接复用 B26
 * 批注弹窗守卫 [NoteDialogExitGate]（该守卫本身复用 B21 `ReaderSheetExitPhase`）；本控制器
 * 不另造第二份枚举，也不另写一套在场 → 离场中 → 已摘除的迁移规则。书源域弹层无会话请求
 * 需要缓存，与 B26 无负载守卫同形态，故直接复用其公开守卫，不再复制第三份守卫实现。
 *
 * 〔两段式关闭〕
 * 第一段：[beginExit] 交守卫进入 EXITING 段，派生 [visible] 翻 false，弹层子树保留组合，
 * 播 [io.legado.app.ui.components.InkHalfSheet] 内部遮罩回亮 / 下降离场动画；
 * 第二段：[SourceSheetExitHost] 按 HalfSheet 两段时长等效延时收束，[finishExit] 后回调宿主
 * 清空槽位状态、摘除子树。摘除时机有明确依据，不写「立即摘」。
 *
 * 〔离场拒收〕
 * [canOpen] 在 EXITING 段为 false；宿主打开槽位前先过此闸，离场动画播放中不叠新弹层。
 * 在场段重复触发的判重仍归宿主既有槽位状态（VM `sheet` 槽位 / local `overflowOpen`），
 * 本控制器不替代。
 */
class SourceSheetExitController internal constructor(
    private val gate: NoteDialogExitGate,
    private val exitingState: MutableState<Boolean>,
) {

    /** 传给 InkHalfSheet 的可见性：仅在场段为 true；离场段 false 播离场动画。 */
    val visible: Boolean
        get() = gate.visible

    /** 覆盖层是否仍需组合子树：在场 / 离场段保留，摘除段返回 false。 */
    val isComposed: Boolean
        get() = gate.isComposed

    /** 是否接受新打开：离场段拒收（动画播放中不叠新弹层）。 */
    val canOpen: Boolean
        get() = gate.canOpen

    /** 是否处于离场段（供收束 [LaunchedEffect] 键与断言使用）。 */
    val isExiting: Boolean
        get() = exitingState.value

    /** 新开会话：回到在场段。 */
    fun open() = gate.open()

    /** 第一段：进离场段；子树保留、visible=false 播组件内离场动画。 */
    fun beginExit() {
        gate.beginExit()
        exitingState.value = true
    }

    /** 第二段：收束回调，离开离场段；随后宿主清空槽位摘子树。 */
    fun finishExit() {
        gate.finishExit()
        exitingState.value = false
    }
}

/**
 * 记住一个书源域弹层离场控制器；每个槽位（待决目标）各持一份，互不串阶段。
 */
@Composable
fun rememberSourceSheetExitController(): SourceSheetExitController {
    val gate = remember { NoteDialogExitGate() }
    val exitingState = remember { mutableStateOf(false) }
    return remember(gate, exitingState) { SourceSheetExitController(gate, exitingState) }
}

/**
 * 离场收束宿主：离场段按 [HalfSheetAnchors] 遮罩 + 弹起两段时长（200ms + 250ms）等效延时后
 * 收束守卫，并回调 [onFinished] 清空宿主槽位、摘除子树；非离场段不动作。
 *
 * 〔为何是等效延时而非完成回调〕
 * InkHalfSheet 契约未暴露动画结束回调，沿用 B21 / B26 同一口径：延时只引组件自有时长
 * 常数，本层不另造魔法数；日后组件补完成回调，可把 [delay] 替换为回调触发，守卫阶段口径不变。
 */
@Composable
fun SourceSheetExitHost(
    controller: SourceSheetExitController,
    onFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(controller.isExiting) {
        if (controller.isExiting) {
            delay((HalfSheetAnchors.MASK_DURATION_MS + HalfSheetAnchors.RISE_DURATION_MS).toLong())
            controller.finishExit()
            onFinished()
        }
    }
    content()
}
