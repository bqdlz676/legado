package io.legado.app.ui.perf.contract

/**
 * S-08 性能红线 · 运行档位阶梯参数单源（平台无关层，纯数值）。
 *
 * 〔档位阶梯口径〕
 * 依据 `docs/立项-补条款-第二批.md` 第一节 S-08 三条：
 *  1.1 L1 低端机静态档（切片-08:34「粒子减半、阴影关、fog 距离缩 | 50fps 保底」，有原文支撑）；
 *  1.2 L2 运行时帧率掉线档（工程推定：P90 21ms / 33ms 两级）；
 *  1.3 L3 后台被系统冻结档（工程推定：动画帧循环、粒子循环在后台暂停）。
 * 本文件只放档位枚举、参数常量与两条纯判定函数，不调用任何 Android / Compose API，
 * 普通 JVM 单测即可直接断言；渲染层接入时只允许引用本表，不得在页面里另写系数。
 */
enum class PerfTier {
    /** L0 常规档：帧率稳定、内存充裕，装饰粒子全量。 */
    HIGH,

    /** L2 二级掉线档：P90 介于 21ms 与 33ms 之间，装饰粒子收敛。 */
    MID,

    /** L1 / L2 一级档：低端机或 P90 超 33ms，停悬浮微粒 / 辉光类装饰。 */
    LOW,

    /** L3 后台冻结档：后台暂停动画帧循环与粒子循环，回前台重建视图。 */
    FROZEN,
}

/**
 * S-08 性能分档参数单源。所有阈值 / 比例集中在此一处，便于 grep 回源与单测兜底。
 */
object PerfTierParams {

    /** 二级掉线阈值：帧耗时 P90 ≤ 21ms（对应 ≥47.6fps）才算常规档；补条款 1.2「21/33ms 两级」。 */
    const val P90_TARGET_MS: Float = 21f

    /** 一级掉线阈值：帧耗时 P90 > 33ms（对应 <30fps）立即进一级削减；补条款 1.2「21/33ms 两级」。 */
    const val P90_DROP_MS: Float = 33f

    /** HIGH 档装饰粒子密度比例（常规档全量）。 */
    const val PARTICLE_SCALE_HIGH: Float = 1.0f

    /** MID 档装饰粒子密度比例（二级掉线：沿用切片-08:34「粒子减半」口径收敛到 0.6）。 */
    const val PARTICLE_SCALE_MID: Float = 0.6f

    /** LOW 档装饰粒子密度比例（一级掉线 / 低端机：停悬浮微粒装饰，取 0.0）。 */
    const val PARTICLE_SCALE_LOW: Float = 0.0f

    /** L1 低端机静态档开关：低端机走静态帧口径（切片-08:34 低端机 50fps 保底；补条款 1.1）。 */
    const val STATIC_FRAME_LOW: Boolean = true

    /** L3 后台冻结开关：App 在后台时暂停动画帧循环与粒子循环（补条款 1.3 ②）。 */
    const val BACKGROUND_FREEZE: Boolean = true

    /** HIGH 档所需最小可用内存 MB（推定 6000MB；判据由渲染层接入时按 D24 内存三档校准）。 */
    const val HIGH_MIN_MEMORY_MB: Int = 6000
}

/**
 * 按 P90 帧耗时（ms）与设备内存（MB）纯计算运行档位，平台无关、不抛异常。
 *
 * 判定顺序：
 *  1. P90 ≤ [PerfTierParams.P90_TARGET_MS]（21ms）且内存 ≥ [PerfTierParams.HIGH_MIN_MEMORY_MB] → HIGH；
 *  2. 否则 P90 ≤ [PerfTierParams.P90_DROP_MS]（33ms）→ MID；
 *  3. 其余情况 → LOW。
 *
 * 边界兜底：[p90FrameMs] 为 NaN 或负数、[deviceMemoryMb] 为负数时，不是合法帧耗时 /
 * 内存读数，函数不抛异常、不产生 NaN 档位结果，直接按 LOW 兜底（异常输入不进 HIGH/MID 判定）。
 */
fun perfTierOf(p90FrameMs: Float, deviceMemoryMb: Int): PerfTier {
    if (p90FrameMs.isNaN() || p90FrameMs < 0f || deviceMemoryMb < 0) {
        return PerfTier.LOW
    }
    if (p90FrameMs <= PerfTierParams.P90_TARGET_MS &&
        deviceMemoryMb >= PerfTierParams.HIGH_MIN_MEMORY_MB
    ) {
        return PerfTier.HIGH
    }
    if (p90FrameMs <= PerfTierParams.P90_DROP_MS) {
        return PerfTier.MID
    }
    return PerfTier.LOW
}

/**
 * 按档位与前后台状态纯计算装饰粒子密度比例，平台无关。
 *
 * 后台口径：[appInBackground] 为 true 时（L3 后台冻结，补条款 1.3 ②）一律返回 0.0，
 * 动画帧循环、粒子循环在后台暂停；回前台时由渲染层按当前章重建视图。
 * 前台口径：HIGH / MID / LOW 分别取对应粒子比例；[PerfTier.FROZEN] 档同样返回 0.0。
 */
fun particleScaleFor(tier: PerfTier, appInBackground: Boolean): Float {
    if (appInBackground) {
        return 0.0f
    }
    return when (tier) {
        PerfTier.HIGH -> PerfTierParams.PARTICLE_SCALE_HIGH
        PerfTier.MID -> PerfTierParams.PARTICLE_SCALE_MID
        PerfTier.LOW -> PerfTierParams.PARTICLE_SCALE_LOW
        PerfTier.FROZEN -> 0.0f
    }
}
