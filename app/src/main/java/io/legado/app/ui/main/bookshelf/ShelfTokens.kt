package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * 落文 · U4 书架页视觉 token（ui/main/bookshelf 独占）。
 *
 * 颜色只允许取契约 §1 的 12 色语义 token；本文件是书架域内唯一允许出现十六进制色值的地方，
 * 卡片 / 粒子 / 按钮一律从这里取色，组件内禁止另写 hex。
 * 缓动与时长收口自切片-15《全局动效铁律》与切片-03 四层框架，值不可在组件内放宽。
 *
 * TODO(D23 收口)：12 色真值源为 io.legado.app.ui.components.ComponentPalette；本文件是书架域副本，
 * 本轮只留值不改引用，后续改为转发到单源后删除本副本。
 */
object ShelfTokens {

    // —— 12 色语义 Token（契约 §1.3，值不可变）——
    /** --bg-page 绢帛底 */
    val bgPage: Color = Color(0xFFF7F1E6)
    /** --bg-surface 卡片面 */
    val bgSurface: Color = Color(0xFFEFE8D8)
    /** --bg-overlay 茶玻遮罩 rgba(26,20,16,.45)，0x73 ≈ .451 */
    val bgOverlay: Color = Color(0x731A1410)
    /** --text-primary 墨色 */
    val textPrimary: Color = Color(0xFF1A1410)
    /** --text-secondary 副文 */
    val textSecondary: Color = Color(0xFF5C4F3A)
    /** --text-tertiary 辅助 / 占位 */
    val textTertiary: Color = Color(0xFF9B9687)
    /** --text-accent 朱砂 */
    val textAccent: Color = Color(0xFFC23B2E)
    /** --brand-gold 暖金 */
    val brandGold: Color = Color(0xFFD4A54A)
    /** --brand-copper 古铜 */
    val brandCopper: Color = Color(0xFF8B6914)
    /** --color-link 链接（同朱砂） */
    val colorLink: Color = Color(0xFFC23B2E)
    /** --color-divider 墨色细线 rgba(26,20,16,.08)，0x14 ≈ .078 */
    val colorDivider: Color = Color(0x141A1410)
    /** --color-glow 暖金微光 rgba(212,165,74,.3)，0x4D ≈ .302 */
    val colorGlow: Color = Color(0x4DD4A54A)

    /** 朱砂按下泛色：0 → 8%（切片-04 InkButton 基类），120ms 回落 */
    const val PRESS_ALPHA: Float = 0.08f

    // —— 时长（切片-15，ms，值不可变）——
    /** 点触反馈回落 120ms */
    const val MICRO_MS: Long = 120L
    /** 卡片逐个浮入 350ms each */
    const val CARD_REVEAL_MS: Long = 350L
    /** 遮罩淡入淡出 200ms */
    const val SCRIM_MS: Long = 200L
    /** 卡片飞满屏 / 页面转场 350ms */
    const val PAGE_MS: Long = 350L
    /** 碎墨消散 480ms */
    const val SHATTER_MS: Long = 480L
    /** 卡片浮入顺位错开间隔 */
    const val STAGGER_MS: Long = 45L

    // —— 缓动曲线（切片-15，值不可变）——
    /** 卡片浮入 cubic-bezier(.22,.61,.36,1) 减速入、稳落 */
    val cardReveal: CubicBezierEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    /** 遮罩 cubic-bezier(.4,0,.6,1) 对称不抢 */
    val scrim: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
    /** 回弹 spring 口径 cubic-bezier(.34,1.56,.64,1) overshoot */
    val overshoot: CubicBezierEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    // —— 字体（契约第 0 层；字体资源上提由 U9 收口，此处平台衬线兜底）——
    val serifBody: FontFamily = FontFamily.Serif
    val serifTitle: FontFamily = FontFamily.Serif
    val titleWeight: FontWeight = FontWeight.SemiBold

    // —— 卡片几何（切片-10 原文尺寸，dp/sp）——
    /** 竖排书名 22sp 墨色加粗 */
    const val TITLE_SP: Float = 22f
    /** 作者 / 注疏 云灰 11sp */
    const val AUTHOR_SP: Float = 11f
    /** 章节小字 10sp */
    const val CHAPTER_SP: Float = 10f
    /** 卡片圆角 16dp（切片-25:13 通用卡片 Token「圆角 16dp」；变体不得自行改圆角）。 */
    const val CARD_RADIUS_DP: Float = 16f
    /** 暖金微光溢出半径 12px（0 0 12px rgba(212,165,74,.3)） */
    const val GLOW_RADIUS_DP: Float = 12f
    /** 卡片底部暖金微光溢出 4dp */
    const val GLOW_OVERFLOW_DP: Float = 4f
    /** 按下 3D 前倾 5° */
    const val TILT_DEG: Float = 5f
    /** 书架域口径 2dp，出处 docs/切片-25 第 19 行；阅读页 1dp 属正文细线，不同域不复用。
     *  为什么：切片-10:22 只写「书名右侧一道朱砂竖线，线长 = 阅读进度比例」，未给线宽；
     *  卡片规格切片-25:19 明写「右侧朱砂进度竖线 2dp」；切片-09:7 的 1dp 是阅读页右缘正文细线，
     *  属阅读域，书架卡片不复用。components 层 `ShelfParams.PROGRESS_LINE_WIDTH_DP` 已于
     *  2026-11-26 裁决同步为 2.0；本常量仍是书架域渲染直读副本（D23 转发单源计划见文件头 TODO）。 */
    const val VLINE_WIDTH_DP: Float = 2f
    /** 底部细线进度高 2dp */
    const val BOTTOM_LINE_HEIGHT_DP: Float = 2f
    /** 云灰 1px 分隔线 */
    const val DIVIDER_HEIGHT_DP: Float = 1f

    // —— 金色微粒（切片-10：卡片下方悬浮 15-20 粒极淡金色微粒）——
    const val PARTICLE_MIN: Int = 15
    const val PARTICLE_MAX: Int = 20
    // 为什么派生：默认粒数不再自由写死 18，改由区间上下限中点（四舍五入向上）派生：
    // (15+20+1)/2=18；区间日后调整时默认值自动跟随，不会与 15-20 冻结区间脱节。
    // 用整数除法是因为 const val 只允许编译期常量表达式，不能调 roundToInt()。
    const val PARTICLE_DEFAULT: Int = (PARTICLE_MIN + PARTICLE_MAX + 1) / 2

    /** 新增判定窗口：7 天内入库视为新书 */
    const val NEW_BOOK_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000
}
