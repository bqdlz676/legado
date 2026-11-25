package io.legado.app.ui.components.compat

import io.legado.app.ui.components.ComponentVisualState as LegacyVisualState
import io.legado.app.ui.components.contract.ComponentVisualState

/**
 * S-04 状态契约接线 · 旧七态 → 新契约单向适配（只新增适配，不删旧七态模型）。
 *
 * 〔为什么只做旧→新单向〕
 * 旧七态 [LegacyVisualState]（默认 / 悬浮 / 按下 / 禁用 / 加载 / 空数据 / 离线，登记在
 * `ComponentState.kt`）是切片-04 早期落地形态；新契约 [ComponentVisualState]
 * （默认 / 悬停 / 按下 / 选中 / 禁用 / 加载中 / 空数据 / 离线）是 S-04 收口契约。
 * 本适配只把旧态搬运到新契约，**不提供反向映射**：新契约 [ComponentVisualState.SELECTED]
 * 选中档在旧模型无对应档，反向映射必然造数据；旧源退役后直接删除本适配，不留双向兼容层。
 *
 * 〔逐项口径〕
 * - DEFAULT / HOVERED / PRESSED / DISABLED / LOADING 同名平移；
 * - EMPTY / OFFLINE 各自平移到契约独立档位 [ComponentVisualState.EMPTY] /
 *   [ComponentVisualState.OFFLINE]，**不并入 DISABLED**：压淡系数与事件许可一律取契约枚举
 *   上登记的 [ComponentVisualState.contentAlpha] / [ComponentVisualState.eventsAllowed]
 *   单源字段，适配层不另写透明度系数，也不复制 0.30f 禁用系数。
 *
 * 纯枚举映射：不读全局状态、不调 Android / Compose API，普通 JVM 单测可直接断言。
 */
fun LegacyVisualState.toVisualState(): ComponentVisualState = when (this) {
    LegacyVisualState.DEFAULT -> ComponentVisualState.DEFAULT
    LegacyVisualState.HOVERED -> ComponentVisualState.HOVERED
    LegacyVisualState.PRESSED -> ComponentVisualState.PRESSED
    LegacyVisualState.DISABLED -> ComponentVisualState.DISABLED
    LegacyVisualState.LOADING -> ComponentVisualState.LOADING
    // 空数据独立档位：不压淡、允许事件（旧模型 empty 仍可点）
    LegacyVisualState.EMPTY -> ComponentVisualState.EMPTY
    // 离线独立档位：不压淡、阻止事件（旧模型 offline 禁点）
    LegacyVisualState.OFFLINE -> ComponentVisualState.OFFLINE
}
