package io.legado.app.design

import androidx.compose.animation.core.CubicBezierEasing

/**
 * S-03 设计 token · 缓动曲线与时长单源（冻结 v1）。
 *
 * 为什么必须单源：动效一旦在各页面内联写死曲线 / 时长，同类交互（如卡片浮入）在
 * 不同页面会出现快慢不一、回弹不一致；集中收口后调一处即全站生效。页面里禁止
 * 内联写时长 / 曲线，只许引用本对象常量。
 *
 * 真值源：docs/落文-规格切片/切片-03-设计token与四层框架.md §动效层增补
 * （缓动曲线表 + duration 常数段），数值逐条照抄，禁止自行编造。
 * 本文件只收四条缓动曲线与五档时长；spring 刚度 / 阻尼等物理参数归各动画层，不在此列。
 */
object DesignMotionTokens {

    // ─────────────────────────── 四条缓动曲线 ───────────────────────────

    /** 卡片浮入（stagger reveal）cubic-bezier(0.22, 0.61, 0.36, 1)：减速入，稳落。 */
    val CARD_ENTER_EASING: CubicBezierEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    /** 气泡弹入（spring）cubic-bezier(0.34, 1.56, 0.64, 1)：overshoot 回弹。 */
    val BUBBLE_POP_EASING: CubicBezierEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** 翻页掀角 cubic-bezier(0.4, 0, 0.2, 1)：匀速掀、急落。 */
    val PAGE_PEEL_EASING: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 遮罩淡入淡出 cubic-bezier(0.4, 0, 0.6, 1)：对称，不抢。 */
    val MASK_FADE_EASING: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

    // ─────────────────────────── 五档时长（毫秒，全局统一） ───────────────────────────

    /** 微交互（点触反馈）120ms。 */
    const val DURATION_MICRO_MS: Int = 120

    /** 卡片 / 气泡入场 250ms。 */
    const val DURATION_CARD_BUBBLE_MS: Int = 250

    /** 页面转场 350ms。 */
    const val DURATION_PAGE_TRANSITION_MS: Int = 350

    /** 遮罩淡入淡出 200ms。 */
    const val DURATION_MASK_MS: Int = 200

    /** 启动页粒子整段编排 2800ms（整段编排，不拆分）。 */
    const val DURATION_BOOT_PARTICLES_MS: Int = 2800
}
