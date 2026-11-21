package io.legado.app.ui.widget

import android.view.animation.PathInterpolator

/**
 * 落文 · 缓动曲线表
 *
 * 全站只许用这四条，不许再出现 ease-in-out / LinearInterpolator 这类糊弄人的默认值。
 * 曲线值直接取自设计规格，四条各管一类动作，不许混用。
 */
object InkEase {

    /** 卡片浮入：减速入，稳落。看书架卡片 stagger reveal 用这条。 */
    val cardReveal = PathInterpolator(0.22f, 0.61f, 0.36f, 1f)

    /** 气泡弹入：overshoot 回弹，那一下过冲就是它。 */
    val spring = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)

    /** 翻页掀角：匀速掀、急落。 */
    val pageTurn = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    /** 遮罩淡入淡出：对称，不抢戏。 */
    val scrim = PathInterpolator(0.4f, 0f, 0.6f, 1f)
}

/**
 * 落文 · 时长常数（全局统一，不许各处自造毫秒数）
 */
object InkDur {
    /** 微交互：泛色、按下回弹 */
    const val MICRO = 120L

    /** 卡片 / 气泡 */
    const val CARD = 250L

    /** 页面转场 */
    const val PAGE = 350L

    /** 遮罩 */
    const val SCRIM = 200L

    /** 启动页整段编排，不拆分 */
    const val SPLASH = 2800L
}
