package io.legado.app.ui.components.contract

import io.legado.app.ui.components.ComponentPalette

/**
 * S-04 组件状态契约枚举（默认 / 悬停 / 按下 / 选中 / 禁用 / 加载中 / 空数据 / 离线）。
 *
 * 〔状态口径〕切片-04:9-10 原文列「默认 / 悬浮 / 点击 / 禁用 / 加载 / 空状态 / 离线状态」；
 * 悬浮 → [HOVERED]、点击 → [PRESSED]，另补 [SELECTED] 选中档；空数据 [EMPTY] / 离线
 * [OFFLINE] 是与禁用并列的**独立档位**，不并入 DISABLED——旧模型 `ComponentState.kt`
 * 口径：仅 disabled 压淡到 [ComponentPalette.DISABLED_OPACITY]，empty / offline 透明度
 * 仍为 1f；可点性上 empty 仍可点，offline 禁点。
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
    LOADING(contentAlpha = 1f, eventsAllowed = false),

    /** 空数据：兜底占位；不压淡透明度（旧模型 empty 仍 1f），允许点击回调（旧模型 empty 可点）。 */
    EMPTY(contentAlpha = 1f, eventsAllowed = true),

    /** 离线：降级缓存 + 提示；不压淡透明度，但阻止点击回调（旧模型 offline 禁点）。 */
    OFFLINE(contentAlpha = 1f, eventsAllowed = false);
}

/**
 * 〔B18 处置：原 `componentStateOf(enabled, pressed, selected)` 已删除，不留零消费假契约〕
 *
 * 为什么删：三布尔快照不足以表达六态——加载中 / 空数据 / 离线三档无法由
 * enabled / pressed / selected 推出；原函数只覆盖 默认 / 按下 / 选中 / 禁用 子集，
 * 在 main 渲染源码无任何调用点（仅旧单测引用），属于与手势层重复的第二套解析。
 *
 * 唯一解析口径：交互快照 `io.legado.app.ui.components.InkInteraction` 经
 * `resolve()` 得到旧七态，再由 `compat.toVisualState()` 单向映射到本契约枚举；
 * 事件许可与透明度一律读 [ComponentVisualState.contentAlpha] / [ComponentVisualState.eventsAllowed]，
 * 不再新增三布尔解析函数。
 */
