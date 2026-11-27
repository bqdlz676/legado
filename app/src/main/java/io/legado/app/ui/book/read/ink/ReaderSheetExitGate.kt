package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.page.ReaderDetailRequest

/**
 * B21 半屏离场两段式状态机（纯 JVM，平台无关层）。
 *
 * 〔不变式〕
 * 1. 本类只承担会话请求负载的登记与清存：阶段迁移、`visible` / `isComposed` / `canOpen`
 *    派生全部继承 [SheetExitPhaseGate] 基类，本类不另写第二套阶段判断（定界 §六二阶风险 5）。
 * 2. [retainedRequest] 只是离场段为保留子树而缓存的原请求副本，不是第二份开合记录；
 *    在场会话的事实源仍是 [io.legado.app.ui.book.read.ink.ReaderSheetState.request]。
 * 3. 收束（[onFinishedExit]）必清存负载；宿主销毁强制收口经 [releaseOnHostDestroy] 走基类
 *    安全态回落，同样清存负载，不回调宿主、不抛异常。
 *
 * 〔阶段语义〕
 * - [ReaderSheetExitPhase.OPEN]：半屏在场；[retainedRequest] 为当前会话请求。
 * - [ReaderSheetExitPhase.EXITING]：关闭请求已到，离场动画播放中；子树保留组合、`visible=false`
 *   走 `io.legado.app.ui.components.InkHalfSheet` 内部下降与遮罩回亮动画，请求暂存于
 *   [retainedRequest]，此段不接受新半屏请求。
 * - [ReaderSheetExitPhase.REMOVED]：离场动画收束，保留请求清空，子树可摘除。
 *
 * 〔与 B20 连点闸的边界〕
 * 本守卫只管「离场段」开合：[canOpen] 在 [ReaderSheetExitPhase.EXITING] 段拒收新请求；已开态
 * 重复请求的判重仍由 [ReaderSheetGuard] + [ReaderSheetState.isOpen] 那条 B20 闸负责，本守卫
 * 不替代、不改写其快照比对与判重语义。
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
     * 收到关闭请求：阶段迁移口径以基类 [beginExit] 为准，本方法只在迁移成功后登记请求负载。
     * 保留请求不清空——子树在离场动画期间仍需按原请求组合，`visible` 由此派生为 false，
     * 播组件内离场动画。非在场段调用随基类空操作；入参为空属防御兜底，不动既有缓存。
     */
    fun beginExit(request: ReaderDetailRequest?) {
        if (super.beginExit() && request != null) retainedRequest = request
    }

    /**
     * 宿主销毁时弹层仍开的强制收口：回落 [ReaderSheetExitPhase.REMOVED] 安全态并清存负载；
     * 不触碰 Activity / Context，不回调已销毁宿主，不抛异常。
     */
    fun releaseOnHostDestroy() = resetToRemoved()
}
