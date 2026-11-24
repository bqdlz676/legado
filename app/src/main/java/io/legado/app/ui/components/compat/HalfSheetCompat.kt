package io.legado.app.ui.components.compat

import io.legado.app.ui.components.HalfSheetHostSnapshot
import io.legado.app.ui.components.ReaderSnapshot

/**
 * S-11/S-12 双源收口 · 旧快照 → 新契约单向适配（只新增，不改旧实现）。
 *
 * 〔为什么只做旧→新单向〕
 * 旧模型 [HalfSheetHostSnapshot]（三字段：页码 / 偏移 / 高亮键集合）是切片-11 早期
 * 落地形态；新契约 [ReaderSnapshot]（五字段：章序号 / 段内偏移 / 滚动位置 /
 * 下划线可见性 / 已选段集合）是收口后的状态判据。本适配只把旧快照搬运到新契约，
 * **不提供反向映射**：旧三字段无法还原新契约的段内偏移与可见性布尔，反向映射必然
 * 造数据，故旧源退役后直接删除本适配，不留双向兼容层。
 *
 * 〔字段逐项口径；新契约为准的条目已标注〕
 * - [HalfSheetHostSnapshot.pageIndex] → [ReaderSnapshot.chapterIndex]：
 *   旧页码口径映射新章序号；字段语义以新契约 chapterIndex 为准。
 * - [HalfSheetHostSnapshot.scrollOffset] → [ReaderSnapshot.scrollPosition]：
 *   滚动偏移同口径搬运（单位换算由渲染层接入时保证一致）；数值口径以新契约
 *   scrollPosition 为准。
 * - [HalfSheetHostSnapshot.highlightKeys] → [ReaderSnapshot.selectedSegments]：
 *   旧下划线 / 高亮键集合映射新已选段集合；集合规则以新契约 selectedSegments 为准。
 * - [ReaderSnapshot.segmentOffset]：旧快照无段内偏移概念，旧源不可得，兜底 0；
 *   该项以新契约为准，段内偏移须由渲染层在新快照采集时补齐，不在适配里推断。
 * - [ReaderSnapshot.underlineVisible]：旧快照无可见性布尔，兜底 false；
 *   该项以新契约为准，不从高亮集合非空与否反推可见性。
 *
 * 纯数据搬运：不读全局状态、不调 Android / Compose API，普通 JVM 单测可直接断言。
 */
fun HalfSheetHostSnapshot.toReaderSnapshot(): ReaderSnapshot =
    ReaderSnapshot(
        chapterIndex = pageIndex,
        segmentOffset = 0,
        scrollPosition = scrollOffset,
        underlineVisible = false,
        selectedSegments = highlightKeys,
    )
