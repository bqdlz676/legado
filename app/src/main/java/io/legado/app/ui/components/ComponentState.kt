package io.legado.app.ui.components

/**
 * 组件七态枚举（切片-04：默认 / 悬浮 / 点击 / 禁用 / 加载 / 空状态 / 离线状态）。
 *
 * 状态优先级（高 → 低）：禁用 > 离线 > 空数据 > 加载 > 点击 > 悬浮 > 默认。
 * 禁用时不响应任何手势；离线兜底缓存内容并提示；空数据走 EmptyState；
 * 加载中阻止点击回调。
 */
enum class ComponentVisualState {
    DEFAULT,
    HOVERED,
    PRESSED,
    DISABLED,
    LOADING,
    EMPTY,
    OFFLINE;

    val isInteractive: Boolean
        get() = this == DEFAULT || this == HOVERED || this == PRESSED
}

/**
 * 交互输入快照（纯数据，Composable 从指针 / 业务状态写入，测试直接构造）。
 *
 * @param hovered 指针悬停（桌面 / 触控悬停）
 * @param pressed 按压中（touchstart / mousedown）
 * @param disabled 禁用
 * @param loading 加载中（按钮三点 / 卡片骨架 / 气泡释义拉取中）
 * @param empty 空数据
 * @param offline 离线（降级缓存 + 提示）
 */
data class InkInteraction(
    val hovered: Boolean = false,
    val pressed: Boolean = false,
    val disabled: Boolean = false,
    val loading: Boolean = false,
    val empty: Boolean = false,
    val offline: Boolean = false,
) {
    /** 按优先级解析出唯一可视态 */
    fun resolve(): ComponentVisualState = when {
        disabled -> ComponentVisualState.DISABLED
        offline -> ComponentVisualState.OFFLINE
        empty -> ComponentVisualState.EMPTY
        loading -> ComponentVisualState.LOADING
        pressed -> ComponentVisualState.PRESSED
        hovered -> ComponentVisualState.HOVERED
        else -> ComponentVisualState.DEFAULT
    }

    /** 是否允许触发点击回调：禁用 / 加载 / 离线都不响应 */
    val clickEnabled: Boolean
        get() = !disabled && !loading && !offline

    /** 墨触朱砂覆盖透明度（悬浮 8%，按下 16%；非可交互态不泛色） */
    val accentOverlayAlpha: Float
        get() = when {
            !clickEnabled -> 0f
            pressed -> ComponentPalette.PRESS_DOWN_ACCENT_ALPHA
            hovered -> ComponentPalette.PRESS_HOVER_ACCENT_ALPHA
            else -> 0f
        }

    /** 禁用态整体透明度（云灰 30%），其余态不压透明度 */
    val contentAlpha: Float
        get() = if (disabled) ComponentPalette.DISABLED_OPACITY else 1f

    companion object {
        val Default: InkInteraction = InkInteraction()
        val Disabled: InkInteraction = InkInteraction(disabled = true)
        val Loading: InkInteraction = InkInteraction(loading = true)
        val Empty: InkInteraction = InkInteraction(empty = true)
        val Offline: InkInteraction = InkInteraction(offline = true)
    }
}
