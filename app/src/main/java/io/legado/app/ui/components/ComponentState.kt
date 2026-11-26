package io.legado.app.ui.components

import io.legado.app.ui.components.contract.ComponentVisualState as ContractVisualState

/**
 * 组件七态唯一模型 = [ContractVisualState]（切片-04:9-10 逐字：默认 / 悬浮 / 点击 / 禁用 /
 * 加载 / 空状态 / 离线状态；契约另补 SELECTED 选中档）。
 *
 * 〔为什么本文件还保留一个同名别名〕
 * 旧落地形态曾在本文件内自声明一份七态枚举（带独立 `isInteractive` 口径），与契约包枚举形成
 * 双源。本单已删除旧枚举定义，下面的 [ComponentVisualState] 只是指向契约枚举的**类型别名**：
 * 不声明第二套档位、不复制透明度 / 事件许可规则，同一个类型、同一组规则。
 *
 * 〔别名不能立即删的唯一原因〕
 * 并行域尚有未迁移的调用点仍以 components 包内非限定名引用本别名：
 *  - `ui/components/InkCard.kt:86`（卡片域，Q-311 并行单在改）；
 * 迁移完成、全仓 grep 零残留后，本别名可与 [io.legado.app.ui.components.compat.toVisualState]
 * 一起物理删除，不留双向兼容层。
 *
 * 〔状态优先级，高 → 低〕切片-04 与契约一致：禁用 > 离线 > 空数据 > 加载 > 点击 > 悬浮 > 默认。
 * 禁用时不响应任何手势；离线兜底缓存内容并提示；空数据走 EmptyState；加载中阻止点击回调。
 */
typealias ComponentVisualState = ContractVisualState

/**
 * 交互输入快照（纯数据，Composable 从指针 / 业务状态写入，测试直接构造）。
 *
 * 本类是「手势 / 业务布尔输入」记录，不是第二套状态枚举：唯一可视态经 [resolve] 收口到
 * [ComponentVisualState] 契约枚举；透明度与事件许可一律读枚举上登记的
 * [ComponentVisualState.contentAlpha] / [ComponentVisualState.eventsAllowed]，本类不另写系数。
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
    /** 按优先级解析出唯一可视态（禁用 > 离线 > 空 > 加载 > 点击 > 悬浮 > 默认）。 */
    fun resolve(): ComponentVisualState = when {
        disabled -> ComponentVisualState.DISABLED
        offline -> ComponentVisualState.OFFLINE
        empty -> ComponentVisualState.EMPTY
        loading -> ComponentVisualState.LOADING
        pressed -> ComponentVisualState.PRESSED
        hovered -> ComponentVisualState.HOVERED
        else -> ComponentVisualState.DEFAULT
    }

    /**
     * 墨触朱砂覆盖透明度（悬浮 8%，按下 16%；非可交互态不泛色）。
     *
     * 可交互闸直读 `resolve().eventsAllowed`（契约单源），快照层不再另写
     * `!disabled && !loading && !offline` 平行布尔，避免两套口径漂移。
     */
    val accentOverlayAlpha: Float
        get() = when {
            !resolve().eventsAllowed -> 0f
            pressed -> ComponentPalette.PRESS_DOWN_ACCENT_ALPHA
            hovered -> ComponentPalette.PRESS_HOVER_ACCENT_ALPHA
            else -> 0f
        }

    companion object {
        val Default: InkInteraction = InkInteraction()
        val Disabled: InkInteraction = InkInteraction(disabled = true)
        val Loading: InkInteraction = InkInteraction(loading = true)
        val Empty: InkInteraction = InkInteraction(empty = true)
        val Offline: InkInteraction = InkInteraction(offline = true)
    }
}
