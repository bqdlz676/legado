package io.legado.app.ui.book.read.ink

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.ui.book.read.page.ReaderDetailRequest
import io.legado.app.ui.components.ReaderSnapshot

/**
 * 阅读页半屏覆盖层状态持有者（B1 接线）。
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
 * @param request 当前打开的详情请求；null 表示覆盖层关闭。
 */
class ReaderSheetState {

    private val guard = ReaderSheetGuard()

    var request by mutableStateOf<ReaderDetailRequest?>(null)
        private set

    val isOpen: Boolean
        get() = request != null

    /** 打开覆盖层：记录请求与弹起前快照；清掉上一轮关闭后的残留快照，避免两次开合串味。 */
    fun open(request: ReaderDetailRequest, before: ReaderSnapshot) {
        guard.open(before)
        this.request = request
    }

    /** 关闭覆盖层：记录关闭后快照并清空请求；宿主状态由宿主自身回写，本方法不改写任何阅读位置。 */
    fun dismiss(after: ReaderSnapshot) {
        guard.dismiss(after)
        request = null
    }

    /** 开合前后宿主状态是否一字不差；尚未打开或尚未关闭时返回 null（不可判定）。 */
    val restoredExactly: Boolean?
        get() = guard.restoredExactly
}
