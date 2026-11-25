package io.legado.app.ui.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.legado.app.ui.components.HalfSheetAnchors
import io.legado.app.ui.config.settings.SheetExitController
import kotlinx.coroutines.delay

/**
 * B29 其余三域（autoTask / dict / replace）`InkHalfSheet` 两段式离场收束宿主。
 *
 * 〔复用口径〕
 * 阶段机与控制器复用 B29 config 域 [SheetExitController]：其包装 B26 `NoteDialogExitGate`，
 * 该守卫复用 B21 `ReaderSheetExitPhase`（OPEN / EXITING / REMOVED）；本宿主不另造枚举、
 * 不另写在场 → 离场中 → 已摘除的迁移规则。
 *
 * 〔与 config 域 `SheetExitHost` 的差异〕
 * config 域浮层包 `InkBottomSheet`，离场为单段 tween（`ComponentPalette.DURATION_MASK`
 * 200ms）；本批浮层包 `InkHalfSheet`，离场为遮罩回亮 200ms + Sheet 下降 250ms 两段，
 * 常量同源 [HalfSheetAnchors]（与 `InkHalfSheet.kt:124-128`、B26
 * `HighlightNoteDialog.kt:108` 一致），收束延时取两段之和。
 *
 * 〔为何是等效延时而非完成回调〕
 * `InkHalfSheet` 契约未暴露动画结束回调，沿用 B21 / B26 / B29 同一口径：延时只引组件
 * 自有时长常数，本层不另造魔法数；日后组件补完成回调，可把 [delay] 替换为回调触发，
 * 守卫阶段口径不变。
 */
@Composable
fun HalfSheetExitHost(
    controller: SheetExitController,
    onFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(controller.isExiting) {
        if (controller.isExiting) {
            delay(HalfSheetExitDurations.exitMs)
            controller.finishExit()
            onFinished()
        }
    }
    content()
}

/**
 * 离场两段时长之和（ms）；纯 JVM 可断言。数值全部取自组件 token，不另造魔法数。
 * 为什么单列对象：时长派生规则要能在无 Compose / Android 运行时的普通单测中断言，
 * 不把 `delay` 调用本身放进断言路径。
 */
object HalfSheetExitDurations {

    /** 遮罩回亮 [HalfSheetAnchors.MASK_DURATION_MS] + Sheet 下降 [HalfSheetAnchors.RISE_DURATION_MS]。 */
    val exitMs: Long = (HalfSheetAnchors.MASK_DURATION_MS + HalfSheetAnchors.RISE_DURATION_MS).toLong()
}
