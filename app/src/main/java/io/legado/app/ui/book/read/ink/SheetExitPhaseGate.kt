package io.legado.app.ui.book.read.ink

/**
 * B21 半屏离场两段式状态机阶段（纯 JVM，平台无关层）。B30 起作为所有离场守卫 / 控制器的
 * 共享枚举，不存在第二份阶段定义。
 *
 * - [OPEN]：半屏在场，进入 / 停留态；
 * - [EXITING]：关闭请求已到，离场动画播放中；子树保留组合、`visible=false`，此段不接受新请求；
 * - [REMOVED]：离场动画收束，保留请求清空，子树可摘除。
 */
enum class ReaderSheetExitPhase {
    OPEN,
    EXITING,
    REMOVED,
}

/**
 * B30 两段式离场阶段机纯 JVM 基类（平台无关层，不引用 Android / Compose 类型，普通单测可断言）。
 *
 * 〔为什么抽基类〕
 * B21 [ReaderSheetExitGate]（持会话请求负载）与 B26 `NoteDialogExitGate`（无负载）各自复制了
 * 同一套 OPEN → EXITING → REMOVED 迁移规则与三份派生属性；B30 收口：阶段迁移与
 * `visible` / `isComposed` / `canOpen` 派生口径只在本基类写一份，子类只承担会话负载的
 * 登记与清存，禁止再写第二套判断。
 *
 * 〔阶段语义〕
 * - [ReaderSheetExitPhase.OPEN]：在场，子树组合、可见；
 * - [ReaderSheetExitPhase.EXITING]：离场动画播放中，子树保留组合、`visible=false`，此段拒收新打开；
 * - [ReaderSheetExitPhase.REMOVED]：动画收束，子树可摘除，负载清空。
 *
 * 〔派生口径单一事实源〕
 * 三份派生属性全部由 [phase] 派生，不读子类负载槽位：
 * - [visible]：仅 OPEN 段 true；
 * - [isComposed]：OPEN / EXITING 段保留子树，REMOVED 段 false；
 * - [canOpen]：仅 EXITING 段拒收；OPEN 段重复请求的判重仍归宿主既有槽位 / B20 守卫，本闸不替代。
 */
abstract class SheetExitPhaseGate {

    /** 当前离场阶段；初始为已摘除态（尚未开过）。 */
    var phase: ReaderSheetExitPhase = ReaderSheetExitPhase.REMOVED
        private set

    /** 离场收束时清存会话负载；无负载子类留空实现。 */
    protected open fun onFinishedExit() {}

    /** 新开会话：回到在场段；子类在调用前完成自身负载登记。 */
    protected fun enterOpen() {
        phase = ReaderSheetExitPhase.OPEN
    }

    /**
     * 宿主入口判闸：仅 [ReaderSheetExitPhase.REMOVED] 段放行并登记新会话；OPEN（节点在场）
     * 与 EXITING（离场动画中）一律拒收，返回 false 且不改阶段、不登记负载。供宿主打开点一次性
     * 完成「先问闸、再 open」，避免打开点漏写判重。在场段重复请求的业务判重仍归宿主槽位 / B20 闸。
     */
    fun tryOpen(): Boolean {
        if (phase != ReaderSheetExitPhase.REMOVED) return false
        enterOpen()
        return true
    }

    /**
     * 收到关闭请求：由在场段进入离场段；子树在离场动画期间仍按原内容组合，`visible` 派生为 false。
     * 非在场段调用为空操作，避免离场段 / 摘除段的冗余关闭改写阶段。
     */
    open fun beginExit() {
        if (phase != ReaderSheetExitPhase.OPEN) return
        phase = ReaderSheetExitPhase.EXITING
    }

    /**
     * 离场动画收束：离开离场段、清存负载，子树可摘除。非离场段调用为空操作，
     * 避免冗余收尾清掉下一轮会话。
     */
    open fun finishExit() {
        if (phase != ReaderSheetExitPhase.EXITING) return
        phase = ReaderSheetExitPhase.REMOVED
        onFinishedExit()
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
