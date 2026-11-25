package io.legado.app.ui.book.read.ink

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import io.legado.app.ui.components.ReaderSnapshot

/**
 * 阅读页半屏覆盖层状态持有者（B1 接线；B20 补连点去重闸）。
 *
 * 〔职责边界〕
 * 本类只持有「当前请求」组合状态，并把开合前后快照比对委托给纯逻辑层 [ReaderSheetGuard]；
 * 不触碰排版引擎、滚动位置、选中 / 下划线集合。Sheet 弹起前由宿主采一次 [ReaderSnapshot]，
 * 关闭后再采一次，经守卫逐字段机械比对——切片-11 §5.4「关闭后阅读位置、滚动位置、
 * 下划线状态一字不差」的验收铁律由此可在普通 JVM 单测中断言。
 *
 * 〔为什么快照比对不在本类内做〕
 * [io.legado.app.ui.components.InkHalfSheet] 是纯覆盖层，不感知宿主阅读状态；
 * 快照采集口径（章序号 / 段内偏移 / 滚动像素 / 高亮集合）属于阅读宿主。比对逻辑下沉到
 * [ReaderSheetGuard] 纯 JVM 层，本类只包一层 Compose 可观察状态。
 *
 * 〔B20 连点闸口径〕
 * 可见性唯一事实源是 [request]（null 即关闭），[isOpen] 由它派生，不另存开关 boolean。
 * - open 路径：[isOpen] 真实读取后透传守卫；已开态的第二次请求一律拒收——不新开、不重建、
 *   不切换内容，`request` 与首份 `snapshotBefore` 保持首次值。
 * - 异字口径（锚点卡判据 2 二选一，本单选「拒绝」）：打开中到达的不同字请求同样拒收，
 *   不替换请求、不重采快照。竞态窗口内宿主触摸已被 Dialog 独立窗口截获，正常手势路径
 *   不会再发请求；日后若要支持开着 Sheet 切查另一字，需另走「先关后开」重采弹起前快照
 *   并重走开合动画，属另一条需求，本单不做。
 * - close 路径：冗余 dismiss（已关态）不再回写 after 快照，避免两轮开合串味。
 *
 * 〔B21 离场两段式口径〕
 * 关闭不直接摘子树：[dismiss] 先把请求交 [ReaderSheetExitGate] 进入离场段（保留原请求、
 * `visible=false` 播组件内离场动画），状态请求仍按 B20 口径置空；动画收束后覆盖层回调
 * [finishExitAnimation] 清空保留请求再摘子树。离场段新请求被 [exitGate] 拒收，与 B20
 * 判重闸串联而不互相替代；守卫快照比对口径不变。
 *
 * @param request 当前打开的详情请求；null 表示覆盖层关闭（离场段保留请求另存于 exitGate）。
 */
class ReaderSheetState {

    private val guard = ReaderSheetGuard()

    private val exitGate = ReaderSheetExitGate()

    var request by mutableStateOf<ReaderDetailRequest?>(null)
        private set

    val isOpen: Boolean
        get() = request != null

    /** 离场动画播放中（两段式关闭第二段未收束）；入口闸据此拒收新半屏，配合 B20 闸。 */
    val isExiting: Boolean
        get() = exitGate.phase == ReaderSheetExitPhase.EXITING

    /** 离场段保留的原请求；覆盖层动画期间按此组合，收束后为 null。 */
    val exitRequest: ReaderDetailRequest?
        get() = exitGate.retainedRequest

    /** 传给 InkHalfSheet 的可见性：离场段 false，播组件内下降 / 遮罩回亮动画。 */
    val exitVisible: Boolean
        get() = exitGate.visible

    /** 供单测断言首份快照基线；生产路径只经 [restoredExactly] 读比对结果。 */
    internal val snapshotBefore: ReaderSnapshot?
        get() = guard.snapshotBefore

    /**
     * 打开覆盖层：已开态重复请求被守卫拒收，不重采快照；新开会话记录请求与弹起前快照，
     * 清掉上一轮关闭后的残留快照，避免两次开合串味。
     */
    fun open(request: ReaderDetailRequest, before: ReaderSnapshot) {
        // B21 闸：离场段拒收新请求；在场段重复请求判重仍走下方 B20 守卫闸，语义不替代。
        if (!exitGate.canOpen) return
        // 闸：isOpen 是 open 路径真实消费方；守卫按透传可见性判重，拒收时首份基线不动。
        if (!guard.open(before, alreadyOpen = isOpen)) return
        exitGate.open(request)
        this.request = request
    }

    /**
     * 关闭覆盖层：已关态冗余关闭直接返回；正常关闭记录关闭后快照；先交离场守卫保留原
     * 请求播离场动画，状态请求按 B20 口径置空；宿主状态由宿主自身回写，本方法不改写
     * 任何阅读位置。
     */
    fun dismiss(after: ReaderSnapshot) {
        // 闸：isOpen 是 close 路径真实消费方；未开时不回写 after 快照。
        if (!isOpen) return
        guard.dismiss(after, alreadyOpen = isOpen)
        // B21 两段式：离场段保留原请求、visible=false；动画收束后由 finishExitAnimation 摘子树。
        exitGate.beginExit(request)
        request = null
    }

    /** 离场动画收束后由覆盖层回调：清空离场段保留请求，子树可摘除；非离场段空操作。 */
    fun finishExitAnimation() {
        exitGate.finishExit()
    }

    /** 开合前后宿主状态是否一字不差；尚未打开或尚未关闭时返回 null（不可判定）。 */
    val restoredExactly: Boolean?
        get() = guard.restoredExactly
}
