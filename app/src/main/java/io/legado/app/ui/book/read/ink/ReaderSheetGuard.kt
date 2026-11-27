package io.legado.app.ui.book.read.ink

import io.legado.app.ui.components.ReaderSnapshot
import io.legado.app.ui.components.isRestoredExactly

/**
 * 开合快照比对守卫（纯 JVM，平台无关层）。
 *
 * 〔不变式〕
 * 1. 本类只读快照、不读写宿主阅读引擎；两次快照取自同一口径（同一滚动单位、同一选中键规则）。
 * 2. 可见性事实源在状态层（`request != null` 派生的 `isOpen`），本守卫不另存开关 boolean，
 *    避免出现第二份开合记录。
 * 3. [snapshotBefore] 为首份弹起前基线；已开态重复打开不得冲掉基线；[snapshotAfter] 为关闭后
 *    快照，新开会话时清空，避免两轮开合串味。
 * 4. 保存 / 恢复只搬运纯数据 [ReaderSnapshot]，不持 Android Bundle / Context；Bundle 桥接由
 *    宿主 Compose 层在后续接线批次完成，本层只给可单测的纯数据接缝。
 *
 * 〔为什么从 Compose 状态持有者里拆出来〕
 * 切片-11 §5.4「关闭后阅读位置、滚动位置、下划线状态一字不差」必须可在普通 JVM
 * 单测中断言；Compose `mutableStateOf` 属于组合层依赖，不宜进入纯逻辑测试。故开合
 * 前后快照的采集记录与逐字段比对收口在本守卫，不引用任何 Android / Compose 类型；
 * [ReaderSheetState] 只在其上包一层组合状态。
 *
 * 〔B20 连点闸〕开合方法由状态层透传当前可见性 [alreadyOpen]，守卫据此在已开态拒收重复打开、
 * 在已关态拒收冗余关闭，保证首份 `snapshotBefore` 不被连点竞态冲掉基线。
 */
class ReaderSheetGuard {

    /** 弹起前快照；未打开过为 null。读出门面对状态层 / 单测可见，写入仍私有。 */
    var snapshotBefore: ReaderSnapshot? = null
        private set

    /** 关闭后快照；未关闭为 null。 */
    var snapshotAfter: ReaderSnapshot? = null
        private set

    /**
     * 打开覆盖层。
     *
     * 〔连点竞态口径〕[alreadyOpen] 为 true 表示半屏会话已开，本次是打开中的重复请求：
     * 不重采弹起前快照、不清 `snapshotAfter`，首份基线保持首次值，返回 false 由状态层
     * 保留首次请求。已关态（false）才落新基线，并清掉上一轮关闭后的残留快照。
     *
     * @return true = 新开会话；false = 已开态重复请求被拒收。
     */
    fun open(before: ReaderSnapshot, alreadyOpen: Boolean): Boolean {
        if (alreadyOpen) return false
        snapshotBefore = before
        snapshotAfter = null
        return true
    }

    /**
     * 关闭覆盖层。[alreadyOpen] 为 false 时属已关态的冗余关闭，不回写 after 快照；
     * 正常关闭记录关闭后快照；不改写任何阅读位置。
     */
    fun dismiss(after: ReaderSnapshot, alreadyOpen: Boolean) {
        if (!alreadyOpen) return
        snapshotAfter = after
    }

    /**
     * 进程重建前保存首份基线（纯数据，可由宿主桥接进 saveable 槽位）。
     * 未打开过返回 null；不复制引用语义，调用方不得回写返回对象。
     */
    fun saveBaseline(): ReaderSnapshot? = snapshotBefore

    /**
     * 进程重建后在全新守卫上恢复基线；恢复前快照、清空关闭后快照，使重建后的回位比对
     * 仍以重建前首份基线为准。传入 null 表示无基线可恢复，守卫回到未打开态。
     */
    fun restoreBaseline(baseline: ReaderSnapshot?) {
        snapshotBefore = baseline
        snapshotAfter = null
    }

    /**
     * 开合前后宿主状态是否一字不差；尚未打开或尚未关闭时返回 null（不可判定）。
     * 比对口径见 [isRestoredExactly]：章序号、段内偏移、滚动位置、下划线可见性、
     * 已选段集合五项全等才为 true。
     */
    val restoredExactly: Boolean?
        get() {
            val before = snapshotBefore ?: return null
            val after = snapshotAfter ?: return null
            return isRestoredExactly(before, after)
        }
}
