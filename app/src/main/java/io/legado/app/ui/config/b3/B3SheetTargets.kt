package io.legado.app.ui.config.b3

/**
 * B3 设置页 · 弹层「待打开目标」共享状态包装。
 *
 * 多个取值行（文本 / 数字盘 / 单选 / 危险确认）点击后，先把「弹哪种盘、当前值、确认回调」
 * 暂存成本状态，再交给既有弹层渲染；与旧 `settings/SheetTargets.kt` 同构收拢，
 * 避免每个分区各写一份同构 data class（R63 认知成本）。
 *
 * 注意：这只是 UI 层待决状态，不碰偏好存取；落盘由 [onConfirm] 回调经既有 putPref* 完成。
 */
internal sealed class B3SheetTarget {

    /** 文本编辑目标：[password] 时不显示明文；[onConfirm] 回传去首尾空白后的文本。 */
    data class Text(
        val key: String,
        val title: String,
        val initial: String?,
        val hint: String,
        val password: Boolean,
        val onConfirm: (String) -> Unit,
    ) : B3SheetTarget()

    /** 数字步进盘目标：[min]–[max] 钳制；[onConfirm] 回传步进盘最终值（R21）。 */
    data class Number(
        val title: String,
        val min: Int,
        val max: Int,
        val current: Int,
        val unit: String?,
        val onConfirm: (Int) -> Unit,
    ) : B3SheetTarget()

    /** 列表单选目标：[entries] 展示文案，[values] 落盘值；[current] 为当前值。 */
    data class ListPick(
        val title: String,
        val entries: List<String>,
        val values: List<String>,
        val current: String,
        val onPick: (String) -> Unit,
    ) : B3SheetTarget()

    /** 危险动作确认目标：[danger] 朱砂标题 / 双钮；确认后才执行 [onConfirm]（R18）。 */
    data class Confirm(
        val title: String,
        val message: String,
        val confirmLabel: String,
        val onConfirm: () -> Unit,
    ) : B3SheetTarget()
}
