package io.legado.app.ui.components.contract

import io.legado.app.ui.components.ComponentPalette

/**
 * S-04 组件六态契约枚举（默认 / 悬停 / 按下 / 选中 / 禁用 / 加载中）。
 *
 * 〔状态口径〕切片-04:9-10 原文列「默认 / 悬浮 / 点击 / 禁用 / 加载 / 空状态 / 离线状态」；
 * 本契约按 S-04 写码单收口为六态：悬浮 → [HOVERED]、点击 → [PRESSED]，另补 [SELECTED] 选中档；
 * 空状态 / 离线属数据兜底态，仍归既有七态模型
 * （`io.legado.app.ui.components.ComponentState`），本枚举不重复登记。
 *
 * 〔每个态同时给出两个可视字段〕
 *  - [contentAlpha]：整体不透明度系数（1f 不压淡；禁用取 [ComponentPalette.DISABLED_OPACITY] 0.30）；
 *  - [eventsAllowed]：是否允许响应点击 / 手势事件。
 *
 * 本枚举不引用任何 Compose / Android 类型，可在普通 JVM 单测中断言；渲染接线时由 Compose 侧
 * 读取这两个字段落 alpha 与 clickable 开关。
 */
enum class ComponentVisualState(
    /** 整体不透明度系数（1f = 不压淡） */
    val contentAlpha: Float,
    /** 是否允许事件（点击 / 手势回调） */
    val eventsAllowed: Boolean,
) {
    /** 默认态：完整不透明，允许事件。 */
    DEFAULT(contentAlpha = 1f, eventsAllowed = true),

    /** 悬停态：指针悬停（桌面 / 触控悬停）；完整不透明，允许事件。 */
    HOVERED(contentAlpha = 1f, eventsAllowed = true),

    /** 按下态：按压中（touchstart / mousedown）；完整不透明，允许事件。 */
    PRESSED(contentAlpha = 1f, eventsAllowed = true),

    /** 选中态：当前被选中（标签 / Tab / 条目高亮）；完整不透明，允许事件。 */
    SELECTED(contentAlpha = 1f, eventsAllowed = true),

    /** 禁用态：整体压淡到 [ComponentPalette.DISABLED_OPACITY]（云灰 30%），禁止一切事件。 */
    DISABLED(contentAlpha = ComponentPalette.DISABLED_OPACITY, eventsAllowed = false),

    /** 加载中：三点 / 骨架屏占位；不压淡透明度，但阻止点击回调（既有模型 Loading 同口径）。 */
    LOADING(contentAlpha = 1f, eventsAllowed = false);
}

/**
 * 由业务三布尔快照解析唯一可视态（纯函数，无副作用，非法组合归禁用、不抛异常）。
 *
 * 优先级：禁用 > 按下 > 选中 > 默认。
 *  - [enabled] = false 时一律归 [ComponentVisualState.DISABLED]，即便 [pressed] / [selected]
 *    同时为 true（禁用与按压 / 选中互斥，非法组合不抛异常，压到禁用态）；
 *  - 可用且按压中 → [ComponentVisualState.PRESSED]；
 *  - 可用且选中 → [ComponentVisualState.SELECTED]；
 *  - 其余 → [ComponentVisualState.DEFAULT]。
 *
 * 〔边界〕悬停由指针层写入、加载由数据层写入，不在本三布尔快照内；这两态的外观面
 * （透明度 / 事件许可）已在 [ComponentVisualState] 枚举上单源登记，接线层按指针 / 数据状态直取。
 */
fun componentStateOf(
    enabled: Boolean,
    pressed: Boolean,
    selected: Boolean,
): ComponentVisualState {
    if (!enabled) return ComponentVisualState.DISABLED
    if (pressed) return ComponentVisualState.PRESSED
    if (selected) return ComponentVisualState.SELECTED
    return ComponentVisualState.DEFAULT
}
