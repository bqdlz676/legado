package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.ink.ReaderSheetExitPhase

/**
 * B26 批注弹窗离场两段式守卫（纯 JVM，不引用 Android / Compose 类型，普通单测可断言）。
 *
 * 〔口径复用 B21〕
 * 阶段枚举 [ReaderSheetExitPhase]（OPEN / EXITING / REMOVED）与迁移规则直接复用 B21
 * `ReaderSheetExitGate`：OPEN → beginExit → EXITING（子树保留、`visible=false` 播组件内
 * 离场动画）→ finishExit → REMOVED（子树摘除）。B21 守卫持型缓存 `ReaderDetailRequest`，
 * 属阅读点字半屏域；本弹窗的 highlight 参数在构造时固定、无会话请求需要缓存，故不继承其
 * 持型实现；泛化 B21 守卫属 reader 域冻结范围（B20/B21 已落），本单不动。
 *
 * 〔与 B21 守卫的差异〕
 * 无 retainedRequest：弹窗子树保留依据就是阶段本身（OPEN / EXITING 均在场），REMOVED
 * 后 Fragment 由 Compose 层收束回调摘除。
 */
class NoteDialogExitGate {

    /** 当前离场阶段；初始为已摘除态（尚未开过）。 */
    var phase: ReaderSheetExitPhase = ReaderSheetExitPhase.REMOVED
        private set

    /** 新开会话：回到在场段。 */
    fun open() {
        phase = ReaderSheetExitPhase.OPEN
    }

    /**
     * 收到关闭请求：由在场段进入离场段；子树在离场动画期间仍按原内容组合，`visible`
     * 由此派生为 false，播组件内离场动画。非在场段调用为空操作。
     */
    fun beginExit() {
        if (phase != ReaderSheetExitPhase.OPEN) return
        phase = ReaderSheetExitPhase.EXITING
    }

    /**
     * 离场动画收束（完成回调或等效延时触发）：离开离场段，子树可摘除。非离场段调用为
     * 空操作，避免冗余收尾清掉下一轮会话。
     */
    fun finishExit() {
        if (phase != ReaderSheetExitPhase.EXITING) return
        phase = ReaderSheetExitPhase.REMOVED
    }

    /** 传给 InkHalfSheet 的可见性：仅在场段为 true；离场段 false 播离场动画。 */
    val visible: Boolean
        get() = phase == ReaderSheetExitPhase.OPEN

    /** 覆盖层是否仍需组合子树：在场 / 离场段保留，摘除段返回 false。 */
    val isComposed: Boolean
        get() = phase != ReaderSheetExitPhase.REMOVED

    /**
     * 是否接受新打开：离场段拒收（动画播放中不叠新弹窗）；在场段重复触发的判重仍由
     * [HighlightNoteDialog] 的单点出口闸负责，本守卫不替代。
     */
    val canOpen: Boolean
        get() = phase != ReaderSheetExitPhase.EXITING
}
