package io.legado.app.ui.config.settings

/**
 * 分区弹层「待打开目标」的共享状态包装。
 *
 * 背景：多个分区（主题 / 备份 / 其它）在点击取值行后，需要把「弹哪种盘、当前值、确认回调」
 * 暂存成 Compose 状态再交给 [NumberPickerSheet] 渲染。三个分区的数字盘目标字段完全一致，
 * 故收拢到此一处，避免每份分区各写一份同构 data class（R63 认知成本）。
 *
 * 注意：这只是 UI 层的待决状态，不碰偏好存取；落盘仍由 [apply] 回调经既有 putPref* 扩展完成。
 */
internal data class NumberTarget(
    /** 弹层标题字符串资源 id。 */
    val titleRes: Int,
    /** 步进下限（含）。 */
    val min: Int,
    /** 步进上限（含）。 */
    val max: Int,
    /** 进入弹层时的当前值。 */
    val current: Int,
    /** 确认回调；入参为步进盘最终值。 */
    val apply: (Int) -> Unit,
)
