package io.legado.app.ui.components

/**
 * S-11 阅读页状态快照契约（纯数据，平台无关层）。
 *
 * 〔为什么把「一字不差」做成可断言函数〕
 * 切片-11 §5.4 原文要求「关闭后阅读位置、滚动位置、下划线状态一字不差。这是验收铁律」。
 * 人眼在 Sheet 开合前后对照屏幕，看不出 1px 滚动偏移，看不出章内段偏移差一字，也看不出
 * 已选段集合悄悄漏了一项——视觉比对只能给出「大概没变」的主观结论，无法作为验收判据。
 * 因此把关闭前的阅读页状态冻结成纯数据对象 [ReaderSnapshot]：Sheet 弹起前采一次、关闭
 * 后采一次，再由顶层纯函数 [isRestoredExactly] 逐字段机械比对。任一字段不等（含已选段
 * 集合少一项、下划线可见性翻转、章序号或段内偏移差一）即判 false。这样「一字不差」从
 * 观感描述变成可跑断言、可 grep 回源的契约，验收不再依赖肉眼。
 *
 * 〔为什么快照是纯数据类〕
 * 本类不持有任何 Android / Compose 类型，不读写宿主阅读引擎，只做状态搬运与相等比对；
 * Sheet 组件叠一层覆盖即可，宿主内容槽原样透传。开合两次取自同一口径（同一滚动单位、
 * 同一选中键规则），字段值才能逐字段相等；单位换算与选中键规则由渲染层接入时保证一致。
 *
 * @property chapterIndex 章序号（阅读位置之一，0 起；与段内偏移共同定位阅读位置）
 * @property segmentOffset 段内偏移（阅读位置之二：当前段内的字符 / 段落偏移）
 * @property scrollPosition 滚动位置：像素偏移或行号，单位由渲染层定；快照只冻结数值本身，
 *   不做单位换算，前后两次取自同一口径即可逐字段相等
 * @property underlineVisible 下划线（选中高亮）当前是否可见
 * @property selectedSegments 已选段集合：顺序无关；集合内任一缺项或多项即视为未还原
 */
data class ReaderSnapshot(
    val chapterIndex: Int,
    val segmentOffset: Int,
    val scrollPosition: Int,
    val underlineVisible: Boolean,
    val selectedSegments: Set<String>,
) {
    companion object {
        /**
         * 空状态快照：无阅读位置、无选中、下划线不可见。
         * 供无宿主状态可采时兜底；空状态前后各取一次必须能 round-trip 为 true。
         */
        val Empty: ReaderSnapshot = ReaderSnapshot(
            chapterIndex = 0,
            segmentOffset = 0,
            scrollPosition = 0,
            underlineVisible = false,
            selectedSegments = emptySet(),
        )
    }
}

/**
 * 开合前后快照是否逐字段完全相等（切片-11 §5.4「一字不差」的机械判据）。
 *
 * 判据：章序号、段内偏移、滚动位置、下划线可见性、已选段集合五项全部相等才返回 true；
 * 任一项不等（含集合少一项 / 多一项 / 可见性翻转 / 位置差一）返回 false。集合按内容
 * 比对，与迭代顺序无关。
 *
 * 纯函数：不读全局状态、不调 Android / Compose API，只接收两个 [ReaderSnapshot] 并
 * 返回 [Boolean]，可在普通 JVM 单测中直接断言。
 */
fun isRestoredExactly(before: ReaderSnapshot, after: ReaderSnapshot): Boolean =
    before.chapterIndex == after.chapterIndex &&
        before.segmentOffset == after.segmentOffset &&
        before.scrollPosition == after.scrollPosition &&
        before.underlineVisible == after.underlineVisible &&
        before.selectedSegments == after.selectedSegments
