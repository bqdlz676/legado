package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.page.ReaderDetailRequest

/**
 * B21 半屏离场两段式状态机（纯 JVM，平台无关层）。
 *
 * - [ReaderSheetExitPhase.OPEN]：半屏在场，进入 / 停留态；[retainedRequest] 为当前会话请求。
 * - [ReaderSheetExitPhase.EXITING]：关闭请求已到，离场动画播放中；子树保留组合、`visible=false` 走
 *   [io.legado.app.ui.components.InkHalfSheet] 内部下降与遮罩回亮动画，请求暂存于
 *   [retainedRequest]，此段不接受新半屏请求。
 * - [ReaderSheetExitPhase.REMOVED]：离场动画收束，保留请求清空，子树可摘除。
 *
 * 〔B30 口径收口〕
 * 阶段迁移与 `visible` / `isComposed` / `canOpen` 派生全部继承 [SheetExitPhaseGate] 基类，
 * 本守卫只承担会话请求负载的登记与清存；`isComposed` 不再由 `retainedRequest != null` 单独派生，
 * 与 B26 无负载守卫同口径（`phase != REMOVED`），消除 B21 / B26 分叉。
 *
 * 〔与 B20 连点闸的边界〕
 * 本守卫只管「离场段」开合：[canOpen] 在 [ReaderSheetExitPhase.EXITING] 段拒收新请求；已开态重复请求的
 * 判重仍由 [ReaderSheetGuard] + [ReaderSheetState.isOpen] 那条 B20 闸负责，本守卫
 * 不替代、不改写其快照比对与判重语义。
 *
 * 〔可见性单一事实源〕
 * 在场会话的事实源仍是 [ReaderSheetState.request]；本守卫持有的 [retainedRequest]
 * 只是离场段为保留子树而缓存的原请求副本，不是第二份开合记录；可见性由阶段派生。
 */
class ReaderSheetExitGate : SheetExitPhaseGate() {

    /** 离场段保留的原请求；[ReaderSheetExitPhase.REMOVED] 段清空。供覆盖层在动画期间继续组合原 Sheet。 */
    var retainedRequest: ReaderDetailRequest? = null
        private set

    override fun onFinishedExit() {
        retainedRequest = null
    }

    /** 新开会话：登记请求并回到在场段。 */
    fun open(request: ReaderDetailRequest) {
        retainedRequest = request
        enterOpen()
    }

    /**
     * 收到关闭请求：由在场段进入离场段；保留请求不清空——子树在离场动画期间仍需按
     * 原请求组合，`visible` 由此派生为 false，播组件内离场动画。非在场段调用为空操作；
     * 入参为空属防御兜底，不动既有缓存。
     */
    fun beginExit(request: ReaderDetailRequest?) {
        if (phase != ReaderSheetExitPhase.OPEN) return
        super.beginExit()
        if (request != null) retainedRequest = request
    }
}
