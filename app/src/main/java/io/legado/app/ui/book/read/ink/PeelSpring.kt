package io.legado.app.ui.book.read.ink

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 掀角弹簧物理积分器（真 spring，不是 tween + 缓动曲线）。
 *
 * 二阶质量-弹簧-阻尼方程：
 * ```
 *  a = -stiffness · (x - target) - 2 · dampingRatio · √stiffness · v
 *  v += a · dt ;  x += v · dt
 * ```
 * 目标值 1（角完全掀起），初值 0、初速 0；刚度 / 阻尼比由 [InkTokens] 给定，
 * 整定时间落在 400ms 量级。纯 Kotlin 积分，不依赖 Android 动画类，可离线单测。
 *
 * 为什么不用 CubicBezierEasing 直接推：规格 §3.3 要求「真实纸张翻过去」，纸角起手
 * 慢、发力掀起到位后带一丝回弹，这是二阶欠阻尼系统的形态；缓动曲线只能给单段
 * 标量进度，给不出速度与过冲，故这里自己做显式积分。
 *
 * @param stiffness 弹簧刚度（[InkTokens.PEEL_SPRING_STIFFNESS]）
 * @param dampingRatio 阻尼比（[InkTokens.PEEL_SPRING_DAMPING_RATIO]）；<1 略欠阻尼留轻微回弹，=1 临界阻尼，>1 过阻尼。
 */
class PeelSpring(
    val stiffness: Float,
    val dampingRatio: Float,
) {
    /** 当前掀角进度 0f..1f（绘制层再做夹取）。 */
    var value: Float = 0f
        private set

    /** 当前速度（进度 / 秒）。 */
    var velocity: Float = 0f
        private set

    /** 复位到未掀起状态（翻页结束 / 重排时调用）。 */
    fun reset() {
        value = 0f
        velocity = 0f
    }

    /**
     * 推进一步积分。
     * @param dtSeconds 距上一帧的秒数；非正数 / 非法值直接返回当前值，不产生 NaN。
     * @return 新的 [value]
     */
    fun update(dtSeconds: Float): Float {
        if (dtSeconds <= 0f || dtSeconds.isInfinite() || dtSeconds.isNaN()) return value
        val omega = sqrt(stiffness.coerceAtLeast(0f))
        val force = -stiffness * (value - TARGET) - 2f * dampingRatio * omega * velocity
        velocity += force * dtSeconds
        value += velocity * dtSeconds
        // 上界软夹取：过冲最多留出轻微回弹余量，不允许飞出 1.15 以上。
        if (value > OVERSHOOT_CAP) {
            value = OVERSHOOT_CAP
            velocity = 0f
        }
        return value
    }

    /** 是否已整定到位（进度贴近目标且速度趋零）。 */
    val settled: Boolean
        get() = abs(value - TARGET) < SETTLE_VALUE_EPSILON && abs(velocity) < SETTLE_VELOCITY_EPSILON

    private companion object {
        const val TARGET = 1f
        const val OVERSHOOT_CAP = 1.15f
        const val SETTLE_VALUE_EPSILON = 0.01f
        const val SETTLE_VELOCITY_EPSILON = 0.02f
    }
}
