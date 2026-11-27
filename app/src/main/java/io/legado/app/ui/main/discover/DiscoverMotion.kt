package io.legado.app.ui.main.discover

import androidx.compose.animation.core.CubicBezierEasing
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.motion.contract.MotionSpec
import io.legado.app.ui.motion.contract.motionDurationMs
import io.legado.app.ui.motion.contract.springSpecFor

/**
 * 落文 · 发现页动效编排（B6-04，承接旧 `ui/main/explore/InkExploreMotion.kt`）。
 *
 * 职责边界：本文件只做浮入 stagger 的纯逻辑编排（顺位延迟 / 时长 / 弹簧档位 / 缓动引用），
 * 不持有 Compose 状态、不画层、不挂交互；阻尼 / 刚度 / 时长 / 缓动全部回单源——
 * 弹簧与时长走 [MotionSpec] 契约，缓动走 [ComponentTokens]；页面层不另造 spring 数值，
 * 不复制旧侧 `InkExploreTokens` 的色值、时长与曲线副本。
 *
 * 判据锚（逐字可回源）：
 * - docs/拆分-B6发现页小单.md:97：B6-04 白名单目录；:98：判据锚 R24 / §3.1 两行 / R13 / R14；
 *   :99：产物；:100：依赖 B6-01；:101：放行条件四条。
 * - docs/任务书-B6-发现页.md:49：「stagger 编排思路保留；弹簧阻尼刚度 / 时长改取
 *   `CardTokens` / `ComponentTokens`，私有曲线废弃」；:50：「整文件废弃；发现页全部引
 *   `CardTokens` / `ComponentTokens`」。
 * - docs/落文-规格切片/切片-03-设计token与四层框架.md:116：「`stagger` 典籍竖卡逐行浮入」；
 *   :117：「竖排典籍卡片 `camera parallax` 微前倾（与书架同调性）」。
 * - docs/落文-规格切片/切片-15-全局动效铁律.md:7：「卡片浮入 stagger 350ms each」、
 *   「cubic-bezier(0.22,0.61,0.36,1)」。
 *
 * 动效信息口径（不依赖动效传递）：本层函数只接收顺位 / 条目数 / reduce-motion 开关，
 * 不把阻尼比、刚度、时长、曲线作为跨层形参传入；需要档位时由本层自行回 [MotionSpec] 现取。
 */

/** 发现页浮入档位名；对应 [MotionSpec.PANEL]（卡片错峰浮入，REVEAL 弹簧 0.80 / 360，350ms 档）。 */
const val DISCOVER_REVEAL_MOTION: String = "PANEL"

/**
 * 单条浮入编排结果（纯数值平台无关层，普通 JVM 单测可直接断言）。
 *
 * @param delayMs 相对首条的错峰延迟（ms）；reduce-motion 下恒 0
 * @param durationMs 单条浮入时长（ms）；reduce-motion 下恒 0
 * @param spring 弹簧规格（取自 [MotionSpec] 契约层 SpringSpec，本层不持有私有副本）
 */
data class DiscoverRevealSchedule(
    val delayMs: Long,
    val durationMs: Int,
    val spring: io.legado.app.ui.motion.contract.SpringSpec,
)

/** 浮入缓动：转发 [ComponentTokens.cardEnterEasing]（CARD_ENTER_EASING），页面不另写 cubic-bezier 四参数。 */
val discoverRevealEasing: CubicBezierEasing
    get() = ComponentTokens.cardEnterEasing

/**
 * 档位归一：空白 / null 回落本层默认 [DISCOVER_REVEAL_MOTION]；非空原样交 [MotionSpec]，
 * 由其 BY_NAME 表查不到时静默回退 PRESS 档，本层不抛未知档位异常。
 */
internal fun discoverResolveTier(raw: String?): String =
    raw?.trim()?.uppercase()?.ifBlank { DISCOVER_REVEAL_MOTION } ?: DISCOVER_REVEAL_MOTION

/** 浮入时长：回 [motionDurationMs] 单源；reduce-motion 下契约口径 0ms。 */
fun revealDurationMs(tier: String? = null, reduceMotion: Boolean): Int =
    motionDurationMs(discoverResolveTier(tier), reduceMotion)

/** 浮入弹簧：回 [springSpecFor] 单源；reduce-motion 下契约口径瞬时档（刚度拉满、临界阻尼）。 */
fun revealSpring(tier: String? = null, reduceMotion: Boolean): io.legado.app.ui.motion.contract.SpringSpec =
    springSpecFor(discoverResolveTier(tier), reduceMotion)

/**
 * stagger 顺位延迟（ms）。
 *
 * 编排口径：正常档把一档慢时长窗口按条目数均分顺延，顺位越大延迟越长，末条不超出整段窗口；
 * 不另造旧侧 45ms 步长 / 最多错 8 条的私有数值（旧 `InkExploreMotion.kt:38`、
 * `SearchTokens.kt:125` / `:128` 为旧域副本，本层不复用其数值）。reduce-motion 下全部
 * 条目同帧入画，延迟恒 0。
 *
 * 边界：count ≤ 1 延迟 0；index 越界钳到 [0, count-1]；空列表 / 单条 / 超多条目均不越界、不抛异常。
 */
fun staggerDelayMs(index: Int, count: Int, reduceMotion: Boolean, tier: String? = null): Long {
    if (reduceMotion) return 0L
    if (count <= 1) return 0L
    val safeIndex = index.coerceIn(0, count - 1)
    val window = revealDurationMs(tier, reduceMotion = false).toLong()
    return safeIndex * window / count
}

/**
 * 汇总编排：按顺位取延迟、时长与弹簧；供分区壳 / 槽位挂载层（B6-05 / B6-12）现取，
 * 不在页面层缓存第二份动效数值。
 */
fun discoverRevealSchedule(
    index: Int,
    count: Int,
    reduceMotion: Boolean,
    tier: String? = null,
): DiscoverRevealSchedule = DiscoverRevealSchedule(
    delayMs = staggerDelayMs(index, count, reduceMotion, tier),
    durationMs = revealDurationMs(tier, reduceMotion),
    spring = revealSpring(tier, reduceMotion),
)
