package io.legado.app.ui.book.read.ink

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import io.legado.app.ui.components.ReaderSnapshot

/**
 * 阅读壳弹层代次闸纯规则（与 `ui/main/shell/ShellRestorePolicy` 同一口径：请求代次 > 0 才放行）。
 *
 * 〔为什么阅读壳需要自己的一份代次规则〕
 * 主壳 [io.legado.app.ui.main.shell.ShellRestorePolicy] 管底部 Tab 槽位恢复；本规则管阅读页
 * 弹层会话是否允许在首 composition 自动打开。两层各管一槽，不互相替代；放行口径保持同一判据，
 * 避免出现两套代次标准。规则纯函数，不持 Context / 宿主引用，普通 JVM 单测可断言。
 */
internal object SheetGenerationPolicy {

    /** 活进程内请求代次默认值；构造状态持有者时默认按活进程口径，既有直连调用不受影响。 */
    const val LIVE_GENERATION: Int = 1

    /**
     * 代次闸：0 = 首 composition（冷启动 / 进程重建），残留 / 恢复态请求不得自动弹层；
     * >0 = 活进程内显式下发（用户点字、newIntent 检索改道），允许进弹层会话。
     */
    fun shouldAdmitRequest(requestGeneration: Int): Boolean = requestGeneration > 0
}

/**
 * 阅读页半屏覆盖层状态持有者（B1 接线；B20 补连点去重闸；B4-03 归位代次闸与回位接缝）。
 *
 * 〔职责边界〕
 * 本类只持有「当前请求」组合状态，并把开合前后快照比对委托给纯逻辑层 [ReaderSheetGuard]；
 * 不触碰排版引擎、滚动位置、选中 / 下划线集合。Sheet 弹起前由宿主采一次 [ReaderSnapshot]，
 * 关闭后再采一次，经守卫逐字段机械比对——切片-11 §5.4「关闭后阅读位置、滚动位置、
 * 下划线状态一字不差」的验收铁律由此可在普通 JVM 单测中断言。
 *
 * 〔不变式〕
 * 1. 可见性唯一事实源是 [request]（null 即关闭），[isOpen] 由它派生，不另存开关 boolean。
 * 2. 离场段保留请求只存于 [ReaderSheetExitGate.retainedRequest]，本类不持有第二份开合记录。
 * 3. 代次闸先于开合判重：代次为 0 的恢复态 / 残留请求拒收，不改基线、不登记会话。
 * 4. 拒收可观测：被代次闸、离场闸或 B20 守卫拒收的请求累计 [rejectedRequestCount]，不抛异常。
 * 5. 本类不持 Activity / Context；Compose 可观察态只做薄包装，判定逻辑全部在纯函数 / 纯守卫层。
 *
 * 〔为什么快照比对不在本类内做〕
 * [io.legado.app.ui.components.InkHalfSheet] 是纯覆盖层，不感知宿主阅读状态；
 * 快照采集口径（章序号 / 段内偏移 / 滚动像素 / 高亮集合）属于阅读宿主。比对逻辑下沉到
 * [ReaderSheetGuard] 纯 JVM 层，本类只包一层 Compose 可观察状态。
 *
 * 〔B20 连点闸口径〕
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
 * @param hostGeneration 宿主 composition 代次初值；默认活进程代次 [SheetGenerationPolicy.LIVE_GENERATION]。
 * @param request 当前打开的详情请求；null 表示覆盖层关闭（离场段保留请求另存于 exitGate）。
 *
 * 交接注记：旧阅读页实现（`ReadBookActivity`、旧 XML / Binding 侧）本单一律不动；
 * 旧实现留待 B4-31 下线，本件只接新壳 Compose 覆盖层路径。
 */
class ReaderSheetState(
    hostGeneration: Int = SheetGenerationPolicy.LIVE_GENERATION,
) {

    private val guard = ReaderSheetGuard()

    private val exitGate = ReaderSheetExitGate()

    /** 宿主 composition 代次：0 = 首 composition（重建后），>0 = 活进程内显式下发。 */
    private var hostGeneration: Int = hostGeneration

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

    /**
     * 子树是否仍需组合：委托离场闸基类唯一口径（OPEN / EXITING 保留，REMOVED 摘除），
     * 本类不另写判断；组合判据不再读保留请求槽位（消除旧 B21 `retainedRequest != null` 第二口径）。
     */
    val isComposed: Boolean
        get() = exitGate.isComposed

    /** 供单测断言首份快照基线；生产路径只经 [restoredExactly] 读比对结果。 */
    internal val snapshotBefore: ReaderSnapshot?
        get() = guard.snapshotBefore

    /** 被代次闸 / 离场闸 / B20 守卫拒收的请求累计次数；可观测计数，拒收不抛异常。 */
    var rejectedRequestCount: Int = 0
        private set

    /** 宿主 composition 回写代次；重建后首 composition 记 0，活进程显式请求记 >0。 */
    fun bindHostGeneration(generation: Int) {
        hostGeneration = generation
    }

    /**
     * 打开覆盖层：已开态重复请求被守卫拒收，不重采快照；新开会话记录请求与弹起前快照，
     * 清掉上一轮关闭后的残留快照，避免两次开合串味。
     *
     * @param requestGeneration 本次请求代次，缺省取当前宿主代次；代次 0 的恢复态 / 残留请求拒收。
     */
    fun open(request: ReaderDetailRequest, before: ReaderSnapshot, requestGeneration: Int = hostGeneration) {
        // 代次闸：首 composition（重建后）残留请求不得自动弹层，口径同主壳 ShellRestorePolicy。
        if (!SheetGenerationPolicy.shouldAdmitRequest(requestGeneration)) {
            rejectedRequestCount++
            return
        }
        // B21 闸：离场段拒收新请求；在场段重复请求判重仍走下方 B20 守卫闸，语义不替代。
        if (!exitGate.canOpen) {
            rejectedRequestCount++
            return
        }
        // 闸：isOpen 是 open 路径真实消费方；守卫按透传可见性判重，拒收时首份基线不动。
        if (!guard.open(before, alreadyOpen = isOpen)) {
            rejectedRequestCount++
            return
        }
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
        if (!isOpen) {
            rejectedRequestCount++
            return
        }
        guard.dismiss(after, alreadyOpen = isOpen)
        // B21 两段式：离场段保留原请求、visible=false；动画收束后由 finishExitAnimation 摘子树。
        exitGate.beginExit(request)
        request = null
    }

    /** 离场动画收束后由覆盖层回调：清空离场段保留请求，子树可摘除；非离场段空操作。 */
    fun finishExitAnimation() {
        exitGate.finishExit()
    }

    /** 进程重建前保存首份基线（纯数据）；供宿主桥接进 saveable 槽位后在重建后恢复。 */
    fun saveSnapshotBaseline(): ReaderSnapshot? = guard.saveBaseline()

    /**
     * 进程重建后在全新状态持有者上恢复基线：离场闸回落安全态、清空当前请求，守卫恢复
     * 重建前首份快照并清掉关闭后快照。无基线可恢复时传 null，守卫回到未打开态。
     */
    fun restoreAfterRecreation(baseline: ReaderSnapshot?) {
        exitGate.releaseOnHostDestroy()
        guard.restoreBaseline(baseline)
        request = null
    }

    /**
     * 宿主销毁时弹层仍开的强制收口：离场闸回落 REMOVED 并清存保留请求，当前请求置空；
     * 守卫基线保留以备重建后回位比对。不持 / 不回调已销毁宿主，不抛异常、不留半开态。
     */
    fun releaseOnHostDestroy() {
        exitGate.releaseOnHostDestroy()
        request = null
    }

    /** 开合前后宿主状态是否一字不差；尚未打开或尚未关闭时返回 null（不可判定）。 */
    val restoredExactly: Boolean?
        get() = guard.restoredExactly
}
