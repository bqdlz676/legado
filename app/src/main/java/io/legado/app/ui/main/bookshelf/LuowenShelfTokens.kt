package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * 落文 · 书架视觉 token（模块 9 独占）
 *
 * 值全部取自冻结契约《落文-设计系统-契约 v1》第一、二、三节，本子任务不得自行发明色值 / 曲线 / 时长。
 * 共享 token 待主控 merge 时上提到公共层，此处先在书架域内冻结一份，避免跨模块漂移。
 */
object LuowenShelf {

    // —— 12 色语义 Token（值不可变）——
    val bgPage: Color = Color(0xFFF7F1E6)          // --bg-page 绢帛底
    val bgSurface: Color = Color(0xFFEFE8D8)       // --bg-surface 卡片面
    val bgOverlay: Color = Color(0x731A1410)       // --bg-overlay rgba(26,20,16,.45) 磨砂遮罩
    val textPrimary: Color = Color(0xFF1A1410)     // --text-primary 墨色
    val textSecondary: Color = Color(0xFF5C4F3A)   // --text-secondary
    val textTertiary: Color = Color(0xFF9B9687)    // --text-tertiary
    val textAccent: Color = Color(0xFFC23B2E)      // --text-accent 朱砂
    val brandGold: Color = Color(0xFFD4A54A)       // --brand-gold 暖金
    val brandCopper: Color = Color(0xFF8B6914)     // --brand-copper 古铜
    val colorLink: Color = Color(0xFFC23B2E)       // --color-link
    val colorDivider: Color = Color(0x141A1410)    // --color-divider rgba(26,20,16,.08)
    val colorGlow: Color = Color(0x4DD4A54A)       // --color-glow rgba(212,165,74,.3) 暖金微光

    /** 朱砂按下泛色透明度（InkButton 基类：0 → 8%，120ms 回落） */
    const val PRESS_ALPHA: Float = 0.08f

    // —— 缓动曲线（值不可变）——
    /** 卡片浮入 cubic-bezier(.22,.61,.36,1) */
    val cardReveal: CubicBezierEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    /** 遮罩 cubic-bezier(.4,0,.6,1) */
    val scrim: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

    // —— 时长（ms，值不可变；浮入时长按模块验收取 350ms）——
    const val CARD_REVEAL_MS: Long = 350L
    const val MICRO_MS: Long = 120L
    const val SCRIM_MS: Long = 200L
    const val PAGE_MS: Long = 350L
    /** 卡片逐个浮入的顺位错开间隔 */
    const val STAGGER_MS: Long = 45L
    /** 碎成墨滴的消散时长 */
    const val SHATTER_MS: Long = 480L

    // —— 字体栈（契约第三节；字体资源上提由主控处理，此处先用平台衬线兜底）——
    val serifBody: FontFamily = FontFamily.Serif
    val serifTitle: FontFamily = FontFamily.Serif
    val titleWeight: FontWeight = FontWeight.SemiBold
}
