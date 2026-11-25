package io.legado.app.ui.book.read.ink

import io.legado.app.ui.book.read.page.ReaderDetailRequest

/**
 * B21 半屏离场两段式状态机阶段（纯 JVM，平台无关层）。
 *
 * - [OPEN]：半屏在场，进入 / 停留态；[ReaderSheetState.request] 为当前会话请求。
 * - [EXITING]：关闭请求已到，离场动画播放中；子树保留组合、`visible=false` 走
 *   [io.legado.app.ui.components.InkHalfSheet] 内部下降与遮罩回亮动画，请求暂存于
 *   [retainedRequest]，此段不接受新半屏请求。
 * - [REMOVED]：离场动画收束，保留请求清空，子树可摘除。
 */
enum class ReaderSheetExitPhase {
    OPEN,
    EXITING,
    REMOVED,
}

/**
 * 离场两段式收口守卫（纯 JVM，不引用 Android / Compose 类型，普通单测可断言）。
 *
 * 〔为什么从 Compose 覆盖层里拆出来〕
 * B21 判据 5 要求可断言「关闭时子树仍在、visible 已 false」。阶段迁移规则（在场 →
 * 离场中 → 已摘除）不依赖组合树与动画时钟，下沉到本守卫后，覆盖层只做组合接线与
 * 延时回调，状态迁移可在普通 JVM 单测逐条断言。
 *
 * 〔与 B20 连点闸的边界〕
 * 本守卫只管「离场段」开合：[canOpen] 在 [EXITING] 段拒收新请求；已开态重复请求的
 * 判重仍由 [ReaderSheetGuard] + [ReaderSheetState.isOpen] 那条 B20 闸负责，本守卫
 * 不替代、不改写其快照比对与判重语义。
 *
 * 〔可见性单一事实源〕
 * 在场会话的事实源仍是 [ReaderSheetState.request]；本守卫持有的 [retainedRequest]
 * 只是离场段为保留子树而缓存的原请求副本，不是第二份开合记录；可见性由 [phase] 派生。
 */
class ReaderSheetExitGate {

    /** 当前离场阶段；初始为已摘除态（尚未开过）。 */
    var phase: ReaderSheetExitPhase = ReaderSheetExitPhase.REMOVED
        private set

    /** 离场段保留的原请求；[REMOVED] 段清空。供覆盖层在动画期间继续组合原 Sheet。 */
    var retainedRequest: ReaderDetailRequest? = null
        private set

    /** 新开会话：回到在场段并登记请求。 */
    fun open(request: ReaderDetailRequest) {
        phase = ReaderSheetExitPhase.OPEN
        retainedRequest = request
    }

    /**
     * 收到关闭请求：由在场段进入离场段；保留请求不清空——子树在离场动画期间仍需按
     * 原请求组合，`visible` 由此派生为 false，播组件内离场动画。非在场段调用为空操作。
     */
    fun beginExit(request: ReaderDetailRequest?) {
        if (phase != ReaderSheetExitPhase.OPEN) return
        phase = ReaderSheetExitPhase.EXITING
        // retainedRequest 保持原值：离场段不摘子树；入参为空属防御兜底，不动既有缓存。
        if (request != null) retainedRequest = request
    }

    /**
     * 离场动画收束（完成回调或等效延时触发）：离开离场段，清空保留请求，子树可摘除。
     * 非离场段调用为空操作，避免冗余收尾清掉下一轮会话。
     */
    fun finishExit() {
        if (phase != ReaderSheetExitPhase.EXITING) return
        phase = ReaderSheetExitPhase.REMOVED
        retainedRequest = null
    }

    /** 传给 InkHalfSheet 的可见性：仅在场段为 true；离场段 false 播离场动画。 */
    val visible: Boolean
        get() = phase == ReaderSheetExitPhase.OPEN

    /** 覆盖层是否仍需组合子树：离场段与在场段均保留，摘除段返回 false。 */
    val isComposed: Boolean
        get() = retainedRequest != null

    /**
     * 是否接受新半屏请求：离场段拒收（动画播放中不叠新 Sheet）；在场段的重复请求判重
     * 不归本守卫，仍走 B20 闸。
     */
    val canOpen: Boolean
        get() = phase != ReaderSheetExitPhase.EXITING
}
