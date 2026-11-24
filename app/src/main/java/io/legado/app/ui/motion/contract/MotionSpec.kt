package io.legado.app.ui.motion.contract

import io.legado.app.ui.book.read.ink.InkTokens
import io.legado.app.ui.components.ComponentPalette

/**
 * 落文 · S-15 全局动效铁律 · spring 规格单源（平台无关层）。
 *
 * 为什么集中收口：切片-15 只给七项时长与 cubic-bezier 曲线，spring 刚度 / 阻尼比
 * 无规格原文（补条款第二批 3.1 全部为工程推定，待拍板）；本对象是全站动效取弹簧
 * 参数的唯一出处，组件不允许各自另造 stiffness / dampingRatio。
 *
 * 既有常量复用原则：BUBBLE / SHEET / PANEL(REVEAL) / PAGE(PEEL) 直接引用
 * [ComponentPalette] 与 [InkTokens] 冻结 token，不新造数值；PRESS 与 MAP_RISE
 * 在既有代码中无对应刚度常量，按推定默认值落位并标 [待拍板]。
 */

/**
 * 单组 spring 物理参数。
 *
 * @param stiffness 刚度（N/m 量级），越大回位越快
 * @param dampingRatio 阻尼比：<1 欠阻尼允许回弹，=1 临界阻尼无过冲，>1 过阻尼黏滞
 * @param label 动效名标签，便于日志回源与调试
 */
data class SpringSpec(
    val stiffness: Float,
    val dampingRatio: Float,
    val label: String,
)

object MotionSpec {

    /** 点触反馈（InkButton）120ms；切片未给 spring 数值，推定 400 / 0.85 近临界阻尼不留回弹。 */
    // [待拍板] 规格未给数值：stiffness 400f / dampingRatio 0.85f 为工程推定，拍板前不得覆盖既有冻结 token
    val PRESS: SpringSpec = SpringSpec(400f, 0.85f, "press")

    /** Sheet 弹起 250ms ease-out；复用 SHEET 弹簧常量（0.72 / 320）。 */
    val SHEET: SpringSpec = SpringSpec(
        stiffness = ComponentPalette.SHEET_SPRING_STIFFNESS,
        dampingRatio = ComponentPalette.SHEET_SPRING_DAMPING,
        label = "sheet",
    )

    /** 气泡弹入 250ms overshoot；复用 BUBBLE 弹簧常量（0.55 / 300，欠阻尼过冲）。 */
    val BUBBLE: SpringSpec = SpringSpec(
        stiffness = ComponentPalette.BUBBLE_SPRING_STIFFNESS,
        dampingRatio = ComponentPalette.BUBBLE_SPRING_DAMPING,
        label = "bubble",
    )

    /** 翻页掀角 400ms；复用 InkTokens 掀角弹簧常量（0.75 / 220，行程大刚度低、留轻微回弹）。 */
    val PAGE: SpringSpec = SpringSpec(
        stiffness = InkTokens.PEEL_SPRING_STIFFNESS,
        dampingRatio = InkTokens.PEEL_SPRING_DAMPING_RATIO,
        label = "page",
    )

    /** 卡片浮入 stagger 350ms each；复用 REVEAL 弹簧常量（0.80 / 360，减速入稳落不晃）。 */
    val PANEL: SpringSpec = SpringSpec(
        stiffness = ComponentPalette.REVEAL_SPRING_STIFFNESS,
        dampingRatio = ComponentPalette.REVEAL_SPRING_DAMPING,
        label = "panel",
    )

    /** 遮罩 / 浮层淡入淡出；补条款 3.1.5 推定跟 Sheet 同档（0.72 / 320，对称进出）。 */
    val TOAST: SpringSpec = SpringSpec(
        stiffness = ComponentPalette.SHEET_SPRING_STIFFNESS,
        dampingRatio = ComponentPalette.SHEET_SPRING_DAMPING,
        label = "toast",
    )

    /** 3D 地形隆起；切片-15 与既有 token 均无对应刚度常量，走推定默认值。 */
    // [待拍板] 规格未给数值：MAP_RISE 无 spring 原文，默认 stiffness 400f / dampingRatio 0.85f
    val MAP_RISE: SpringSpec = SpringSpec(400f, 0.85f, "map_rise")

    /** 档位名 → 规格映射；查询时大小写不敏感，键统一存大写。 */
    internal val BY_NAME: Map<String, SpringSpec> = mapOf(
        "PRESS" to PRESS,
        "SHEET" to SHEET,
        "BUBBLE" to BUBBLE,
        "PAGE" to PAGE,
        "PANEL" to PANEL,
        "TOAST" to TOAST,
        "MAP_RISE" to MAP_RISE,
    )

    /** 正常档三档时长（ms）：120 微交互 / 250 卡片气泡 / 350 页面转场，均取自 ComponentPalette。 */
    internal fun durationFor(motion: String): Int = when (motion.uppercase()) {
        "SHEET" -> ComponentPalette.DURATION_CARD
        "BUBBLE" -> ComponentPalette.DURATION_BUBBLE
        "PAGE", "PANEL", "MAP_RISE" -> ComponentPalette.DURATION_PAGE
        "TOAST" -> ComponentPalette.DURATION_CARD
        // 未知名回 PRESS 档，与 springSpecFor 的回退口径保持一致
        else -> ComponentPalette.DURATION_MICRO
    }
}

/**
 * 取某动效的 spring 规格（顶层纯函数，平台无关，JVM 单测可直接断言）。
 *
 * @param motion 动效名（PRESS / SHEET / BUBBLE / PAGE / PANEL / TOAST / MAP_RISE），大小写不敏感
 * @param reduceMotion 系统动画关闭 / 无障碍 reduce-motion 开关
 * @return reduce-motion=true 时返回瞬时档（刚度拉满、临界阻尼，近乎无弹直接落终态）；
 * 未知名静默回退 [MotionSpec.PRESS] 档，不抛异常
 */
fun springSpecFor(motion: String, reduceMotion: Boolean): SpringSpec {
    if (reduceMotion) {
        // reduce-motion 下压成近乎无弹：stiffness 1000f、dampingRatio 1.0f，位移 / 缩放直接落到终态
        return SpringSpec(1000f, 1.0f, "instant")
    }
    return MotionSpec.BY_NAME[motion.uppercase()] ?: MotionSpec.PRESS
}

/**
 * 取某动效的正常档时长（ms）；reduce-motion 下一律 0ms（stagger 延迟同步取消，屏内项同帧入画）。
 * 顶层纯函数，未知名回退 PRESS 档时长，不抛异常。
 */
fun motionDurationMs(motion: String, reduceMotion: Boolean): Int {
    if (reduceMotion) return 0
    return MotionSpec.durationFor(motion)
}
