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
 * 整定时间落在 400ms 量级（R17：掀角翻页 400ms）。纯 Kotlin 积分，不依赖 Android
 * 动画类，可离线单测。
 *
 * 为什么不用 CubicBezierEasing 直接推：规格 §3.3 要求「真实纸张翻过去」，纸角起手
 * 慢、发力掀起到位后带一丝回弹，这是二阶欠阻尼系统的形态；缓动曲线只能给单段
 * 标量进度，给不出速度与过冲，故这里自己做显式积分。
 *
 * 不变式：
 *  - [value] 恒落在 [0, OVERSHOOT_CAP] 区间；[velocity] 受 [MAX_VELOCITY] 软夹取；
 *  - 任何非法入参（NaN / Inf / 非正刚度 / 负阻尼比）都不抛异常，回退到安全默认参数
 *    并累加 [parameterClampedCount]，不产生 NaN / Inf 状态；
 *  - [reset] 后 value=0、velocity=0，与新建弹簧等价。
 *
 * @param stiffness 弹簧刚度（[InkTokens.PEEL_SPRING_STIFFNESS]）
 * @param dampingRatio 阻尼比（[InkTokens.PEEL_SPRING_DAMPING_RATIO]）；<1 略欠阻尼留轻微回弹，=1 临界阻尼，>1 过阻尼。
 */
class PeelSpring(
    stiffness: Float,
    dampingRatio: Float,
) {
    /** 生效刚度；非法入参回退到 [DEFAULT_STIFFNESS]。 */
    val stiffness: Float

    /** 生效阻尼比；非法入参回退到 [DEFAULT_DAMPING_RATIO]。 */
    val dampingRatio: Float

    /** 当前掀角进度 0f..1f（绘制层再做夹取）。 */
    var value: Float = 0f
        private set

    /** 当前速度（进度 / 秒）。 */
    var velocity: Float = 0f
        private set

    /** 构造期非法参数被夹取 / 回退的次数（可观测计数，不写日志、不持上下文）。 */
    var parameterClampedCount: Int = 0
        private set

    init {
        val safeStiffness = when {
            stiffness.isNaN() || stiffness.isInfinite() || stiffness <= 0f -> {
                parameterClampedCount++
                DEFAULT_STIFFNESS
            }
            else -> stiffness
        }
        val safeDamping = when {
            dampingRatio.isNaN() || dampingRatio.isInfinite() || dampingRatio < 0f -> {
                parameterClampedCount++
                DEFAULT_DAMPING_RATIO
            }
            dampingRatio > MAX_DAMPING_RATIO -> {
                parameterClampedCount++
                MAX_DAMPING_RATIO
            }
            else -> dampingRatio
        }
        this.stiffness = safeStiffness
        this.dampingRatio = safeDamping
    }

    /** 复位到未掀起状态（翻页结束 / 重排时调用）。 */
    fun reset() {
        value = 0f
        velocity = 0f
    }

    /**
     * 推进一步积分。
     *
     * 边界兜底（不产生 NaN、不崩溃）：
     *  - [dtSeconds] 非正数 / NaN / Inf：直接返回当前值，不推进；
     *  - 积分后状态出现 NaN / Inf：回退到 [reset] 安全态，避免下一帧炸绘制；
     *  - 速度超 [MAX_VELOCITY] 软夹取，防欠阻尼极端参数下速度发散。
     *
     * @param dtSeconds 距上一帧的秒数。
     * @return 新的 [value]
     */
    fun update(dtSeconds: Float): Float {
        if (dtSeconds <= 0f || dtSeconds.isInfinite() || dtSeconds.isNaN()) return value
        val omega = sqrt(stiffness)
        val force = -stiffness * (value - TARGET) - 2f * dampingRatio * omega * velocity
        velocity += force * dtSeconds
        if (velocity.isNaN() || velocity.isInfinite() || abs(velocity) > MAX_VELOCITY) {
            velocity = velocity.coerceIn(-MAX_VELOCITY, MAX_VELOCITY)
        }
        value += velocity * dtSeconds

        if (value.isNaN() || value.isInfinite()) {
            reset()
            return value
        }
        // 上界软夹取：过冲最多留出轻微回弹余量，不允许飞出 1.15 以上；下界兜住不钻到负。
        when {
            value > OVERSHOOT_CAP -> {
                value = OVERSHOOT_CAP
                velocity = 0f
            }
            value < 0f -> {
                value = 0f
                velocity = 0f
            }
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

        // 与 InkTokens.PEEL_SPRING_STIFFNESS / PEEL_SPRING_DAMPING_RATIO 同值；
        // 本件保持纯 JVM 不直引 Compose token 对象，默认值仅在入参非法时兜底使用。
        const val DEFAULT_STIFFNESS = 220f
        const val DEFAULT_DAMPING_RATIO = 0.75f
        const val MAX_DAMPING_RATIO = 2f
        const val MAX_VELOCITY = 8f
    }
}
