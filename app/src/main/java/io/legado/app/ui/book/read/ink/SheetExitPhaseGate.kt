package io.legado.app.ui.book.read.ink

/**
 * B21 半屏离场两段式状态机阶段（纯 JVM，平台无关层）。B30 起作为所有离场守卫 / 控制器的
 * 共享枚举，不存在第二份阶段定义。
 *
 * 状态全量（三态闭集合，无第四态）：
 * - [OPEN]：半屏在场，进入 / 停留态；子树组合、可见；
 * - [EXITING]：关闭请求已到，离场动画播放中；子树保留组合、`visible=false`，此段不接受新请求；
 * - [REMOVED]：离场动画收束，保留请求清空，子树可摘除；初始态即为已摘除态。
 */
enum class ReaderSheetExitPhase {
    OPEN,
    EXITING,
    REMOVED,
    ;

    public companion object {
        /**
         * 持久化 / 代次回读兜底（C3，边界：阶段判定收到未知 phase）。
         *
         * ordinal 为 null 或越界（旧版本残留、外部数据错配）时返回 null，调用方据此回落
         * [REMOVED] 安全态；不抛异常、不猜测未知阶段、不留错阶段。
         */
        fun safeFromOrdinal(ordinal: Int?): ReaderSheetExitPhase? {
            if (ordinal == null) return null
            return entries.getOrNull(ordinal)
        }
    }
}

/**
 * B30 两段式离场阶段机纯 JVM 基类（平台无关层，不引用 Android / Compose 类型，普通单测可断言）。
 *
 * 〔不变式〕
 * 1. 阶段判定只有本基类一份事实源：迁移规则与 `visible` / `isComposed` / `canOpen` 派生口径
 *    全部写在本类；子类只登记 / 清存会话负载，禁止另写第二套阶段判断（定界 §六二阶风险 5）。
 * 2. [phase] 只经本类内部 setter 改写，外部只读；任何迁移非法都不改阶段、不抛异常。
 * 3. 拒收（非法迁移）可观测：每次被拒收累加 [rejectedTransitionCount] 并记录 [lastRejectedPhase]，
 *    供宿主与单测观察连点 / 竞态；计数只增不清零，不参与派生判断。
 * 4. 派生属性全部由 [phase] 派生，不读子类负载槽位；未知阶段回落安全默认：不可见、子树不组合、可开。
 *
 * 〔合法迁移表〕
 * - [REMOVED] → [OPEN]：[tryOpen] 放行（子类 `open` 经 [enterOpen] 进入）；
 * - [OPEN] → [EXITING]：[beginExit]；
 * - [EXITING] → [REMOVED]：[finishExit]，收束后回调 [onFinishedExit] 清存子类负载。
 *
 * 〔非法迁移处置〕
 * - [OPEN] / [EXITING] 段再调 [tryOpen]：拒收，阶段不动，计数 +1；
 * - [REMOVED] / [EXITING] 段调 [beginExit]：空操作，阶段不动，计数 +1；
 * - [REMOVED] / [OPEN] 段调 [finishExit]：空操作，阶段不动，计数 +1；
 * - 任何拒收都回退当前安全态（停留在原阶段），不抛异常、不崩、不留半迁移态。
 *
 * 〔派生口径单一事实源〕
 * - [visible]：仅 OPEN 段 true；
 * - [isComposed]：OPEN / EXITING 段保留子树，REMOVED 段 false；
 * - [canOpen]：仅 EXITING 段拒收；OPEN 段重复请求的判重仍归宿主既有槽位 / B20 守卫，本闸不替代。
 */
abstract class SheetExitPhaseGate {

    /** 当前离场阶段；初始为已摘除态（尚未开过）。 */
    var phase: ReaderSheetExitPhase = ReaderSheetExitPhase.REMOVED
        private set

    /** 被拒收（非法迁移）累计次数；可观测计数，非法迁移不抛异常。 */
    var rejectedTransitionCount: Int = 0
        private set

    /** 最近一次被拒收时所处的阶段；尚无拒收记录为 null。 */
    var lastRejectedPhase: ReaderSheetExitPhase? = null
        private set

    /** 离场收束时清存会话负载；无负载子类留空实现。 */
    protected open fun onFinishedExit() {}

    /** 新开会话：回到在场段；子类在调用前完成自身负载登记。 */
    protected fun enterOpen() {
        phase = ReaderSheetExitPhase.OPEN
    }

    /** 记录一次拒收：计数 +1、留存拒收时阶段；不改阶段、不抛异常。 */
    private fun reject(phase: ReaderSheetExitPhase) {
        rejectedTransitionCount++
        lastRejectedPhase = phase
    }

    /**
     * 宿主入口判闸：仅 [ReaderSheetExitPhase.REMOVED] 段放行并登记新会话；OPEN（节点在场）
     * 与 EXITING（离场动画中）一律拒收，返回 false 且不改阶段、不登记负载。供宿主打开点一次性
     * 完成「先问闸、再 open」，避免打开点漏写判重。在场段重复请求的业务判重仍归宿主槽位 / B20 闸。
     */
    fun tryOpen(): Boolean {
        if (phase != ReaderSheetExitPhase.REMOVED) {
            reject(phase)
            return false
        }
        enterOpen()
        return true
    }

    /**
     * 收到关闭请求：由在场段进入离场段；子树在离场动画期间仍按原内容组合，`visible` 派生为 false。
     * 非在场段调用为空操作（拒收计数 +1），避免离场段 / 摘除段的冗余关闭改写阶段。
     *
     * @return true = 迁移到离场段；false = 拒收，阶段未动。子类据返回值决定是否登记 / 更新负载。
     */
    open fun beginExit(): Boolean {
        if (phase != ReaderSheetExitPhase.OPEN) {
            reject(phase)
            return false
        }
        phase = ReaderSheetExitPhase.EXITING
        return true
    }

    /**
     * 离场动画收束：离开离场段、清存负载，子树可摘除。非离场段调用为空操作（拒收计数 +1），
     * 避免冗余收尾清掉下一轮会话。
     *
     * @return true = 迁移到摘除段；false = 拒收，阶段未动。
     */
    open fun finishExit(): Boolean {
        if (phase != ReaderSheetExitPhase.EXITING) {
            reject(phase)
            return false
        }
        phase = ReaderSheetExitPhase.REMOVED
        onFinishedExit()
        return true
    }

    /**
     * 宿主销毁等强制收口：不经过离场动画，直接回落 [ReaderSheetExitPhase.REMOVED] 安全态并
     * 清存子类负载。这是授权的强制拆链，不计入非法迁移拒收计数；不回调宿主、不抛异常。
     */
    protected fun resetToRemoved() {
        if (phase != ReaderSheetExitPhase.REMOVED) {
            phase = ReaderSheetExitPhase.REMOVED
            onFinishedExit()
        }
    }

    /** 传给半屏 / 弹窗组件的可见性：仅在场段为 true；离场段 false 播离场动画。 */
    val visible: Boolean
        get() = phase == ReaderSheetExitPhase.OPEN

    /** 覆盖层是否仍需组合子树：在场 / 离场段保留，摘除段返回 false。口径唯一，子类不得另写。 */
    val isComposed: Boolean
        get() = phase != ReaderSheetExitPhase.REMOVED

    /** 是否接受新打开：离场段拒收（动画播放中不叠新浮层）；在场段判重不归本闸。 */
    val canOpen: Boolean
        get() = phase != ReaderSheetExitPhase.EXITING
}
