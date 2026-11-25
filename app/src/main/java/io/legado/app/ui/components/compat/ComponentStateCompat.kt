package io.legado.app.ui.components.compat

import io.legado.app.ui.components.ComponentVisualState as LegacyVisualState
import io.legado.app.ui.components.contract.ComponentVisualState

/**
 * S-04 六态契约接线 · 旧七态 → 新六态单向适配（只新增适配，不删旧七态模型）。
 *
 * 〔为什么只做旧→新单向〕
 * 旧七态 [LegacyVisualState]（默认 / 悬浮 / 按下 / 禁用 / 加载 / 空数据 / 离线，登记在
 * `ComponentState.kt`）是切片-04 早期落地形态；新六态 [ComponentVisualState]
 * （默认 / 悬停 / 按下 / 选中 / 禁用 / 加载中）是 S-04 收口契约。本适配只把旧态搬运到
 * 新契约，**不提供反向映射**：新契约 [ComponentVisualState.SELECTED] 选中档在旧模型
 * 无对应档，旧 EMPTY / OFFLINE 又被归并到 DISABLED，反向映射必然造数据；旧源退役后
 * 直接删除本适配，不留双向兼容层。
 *
 * 〔逐项口径〕
 * - DEFAULT / HOVERED / PRESSED / DISABLED / LOADING 同名平移；
 * - EMPTY / OFFLINE → DISABLED：无数据/离线时按禁用态处理，事件不放行；压淡外观随目标
 *   禁用态的 [ComponentVisualState.contentAlpha] 单源登记走，适配层不另写透明度系数。
 *
 * 纯枚举映射：不读全局状态、不调 Android / Compose API，普通 JVM 单测可直接断言。
 */
fun LegacyVisualState.toVisualState(): ComponentVisualState = when (this) {
    LegacyVisualState.DEFAULT -> ComponentVisualState.DEFAULT
    LegacyVisualState.HOVERED -> ComponentVisualState.HOVERED
    LegacyVisualState.PRESSED -> ComponentVisualState.PRESSED
    LegacyVisualState.DISABLED -> ComponentVisualState.DISABLED
    LegacyVisualState.LOADING -> ComponentVisualState.LOADING
    // 无数据/离线时按禁用态处理，事件不放行
    LegacyVisualState.EMPTY -> ComponentVisualState.DISABLED
    // 无数据/离线时按禁用态处理，事件不放行
    LegacyVisualState.OFFLINE -> ComponentVisualState.DISABLED
}
