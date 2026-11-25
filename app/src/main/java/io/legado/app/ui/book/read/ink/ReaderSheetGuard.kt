package io.legado.app.ui.book.read.ink

import io.legado.app.ui.components.ReaderSnapshot
import io.legado.app.ui.components.isRestoredExactly

/**
 * 开合快照比对守卫（纯 JVM，平台无关层）。
 *
 * 〔为什么从 Compose 状态持有者里拆出来〕
 * 切片-11 §5.4「关闭后阅读位置、滚动位置、下划线状态一字不差」必须可在普通 JVM
 * 单测中断言；Compose `mutableStateOf` 属于组合层依赖，不宜进入纯逻辑测试。故开合
 * 前后快照的采集记录与逐字段比对收口在本守卫，不引用任何 Android / Compose 类型；
 * [ReaderSheetState] 只在其上包一层组合状态。
 *
 * 守卫只读快照、不读写宿主阅读引擎；两次快照取自同一口径（同一滚动单位、同一选中键规则）。
 */
class ReaderSheetGuard {

    /** 弹起前快照；未打开过为 null。 */
    private var snapshotBefore: ReaderSnapshot? = null

    /** 关闭后快照；未关闭为 null。 */
    var snapshotAfter: ReaderSnapshot? = null
        private set

    /** 打开覆盖层：记录弹起前快照，清掉上一轮关闭后的残留快照。 */
    fun open(before: ReaderSnapshot) {
        snapshotBefore = before
        snapshotAfter = null
    }

    /** 关闭覆盖层：记录关闭后快照；不改写任何阅读位置。 */
    fun dismiss(after: ReaderSnapshot) {
        snapshotAfter = after
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
