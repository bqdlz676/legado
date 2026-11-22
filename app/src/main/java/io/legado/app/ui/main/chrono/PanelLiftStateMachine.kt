package io.legado.app.ui.main.chrono

/**
 * 落文 · 纪年毛玻璃面板升降状态机（纯 JVM，无 Compose / Android 依赖，可直接跑单测）。
 *
 * 对应微交互契约第 3 项：
 *  - 点日期图标 → 面板从底部升起 400dp，底栏同时上移让位；
 *  - 下滑收起：拖动超过 1/3 面板高度、或快速下甩即关闭，否则弹回原位；
 *  - 关闭落回原位带回弹过冲 10px。
 *
 * 状态机只负责语义状态与阈值判定；物理弹簧（overshoot 回弹）由 Compose 动画层承接，
 * 本类暴露 [progress] / [overshootPx] 供动画层映射位移。
 */
enum class PanelPhase { CLOSED, RISING, OPEN, FALLING }

class PanelLiftStateMachine(
    val panelHeightPx: Float,
    val overshootAmountPx: Float = ChronoTokens.PANEL_OVERSHOOT_PX,
    val closeDragFraction: Float = ChronoTokens.CLOSE_DRAG_FRACTION,
    /** 快速下甩阈值：px/ms，超过即判定关闭 */
    val flickVelocityPxPerMs: Float = 0.6f,
) {

    /** 面板开合进度：0 = 收起，1 = 升起 */
    var progress: Float = 0f
        private set

    var phase: PanelPhase = PanelPhase.CLOSED
        private set

    /** 拖拽中已向下拖过的像素（>0 向下） */
    private var dragDownPx: Float = 0f

    /** 落回过冲量（px）；关闭 settle 瞬间置为额定过冲，由动画层收回 0 */
    var overshootPx: Float = 0f
        private set

    val isOpen: Boolean get() = phase == PanelPhase.OPEN || phase == PanelPhase.RISING

    /** 点击日期图标：升起 */
    fun open() {
        if (phase == PanelPhase.CLOSED || phase == PanelPhase.FALLING) {
            phase = PanelPhase.RISING
            dragDownPx = 0f
            overshootPx = 0f
        }
    }

    /** 再次点击：收起 */
    fun close() {
        if (phase == PanelPhase.OPEN || phase == PanelPhase.RISING) {
            phase = PanelPhase.FALLING
        }
    }

    fun toggle() {
        if (isOpen) close() else open()
    }

    /** 拖拽开始（仅面板升起态可拖） */
    fun beginDrag() {
        if (phase == PanelPhase.OPEN || phase == PanelPhase.RISING) {
            phase = PanelPhase.OPEN
            dragDownPx = (1f - progress) * panelHeightPx
        }
    }

    /**
     * 拖拽增量（正数向下）。
     * @return 当前开合进度
     */
    fun onDrag(deltaDownPx: Float): Float {
        if (phase != PanelPhase.OPEN) return progress
        dragDownPx = (dragDownPx + deltaDownPx).coerceIn(0f, panelHeightPx)
        progress = 1f - dragDownPx / panelHeightPx
        return progress
    }

    /**
     * 抬手判定：拖过 1/3 高度或快速下甩 → 关闭；否则弹回升起。
     * @param velocityDownPxPerMs 抬手时向下速度（px/ms，向上为负）
     */
    fun release(velocityDownPxPerMs: Float) {
        if (phase != PanelPhase.OPEN) return
        val draggedFraction = dragDownPx / panelHeightPx
        val closeByDrag = draggedFraction >= closeDragFraction
        val closeByFlick = velocityDownPxPerMs > flickVelocityPxPerMs
        phase = if (closeByDrag || closeByFlick) PanelPhase.FALLING else PanelPhase.RISING
    }

    /** 动画层 settle 回调：弹簧停稳后推进状态；关闭落回时给出额定过冲量 */
    fun onSettled() {
        when (phase) {
            PanelPhase.RISING -> {
                phase = PanelPhase.OPEN
                progress = 1f
                dragDownPx = 0f
            }
            PanelPhase.FALLING -> {
                phase = PanelPhase.CLOSED
                progress = 0f
                dragDownPx = 0f
                overshootPx = overshootAmountPx
            }
            PanelPhase.OPEN, PanelPhase.CLOSED -> Unit
        }
    }

    /** 动画层收回过冲后调用，清来过冲量 */
    fun consumeOvershoot() {
        overshootPx = 0f
    }

    /**
     * 底栏让位位移（px）：面板升起多少，底栏上移多少；关闭落回时叠加过冲。
     * @param barLiftGapPx 底栏需要上移的总间距（面板高 + 底边距）
     */
    fun barLiftPx(barLiftGapPx: Float): Float =
        progress * barLiftGapPx + overshootPx
}
