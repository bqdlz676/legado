package io.legado.app.ui.welcome

import androidx.compose.ui.graphics.Color

/**
 * 落文 · 启动动画视觉 token（ui/welcome 独占）。
 *
 * 颜色 / 时长 / 粒子预算只取两份冻结契约，本文件不自行发明色值：
 *  - 12 色语义 token（纸白 / 墨色 / 朱砂 / 暖金 / 古铜 / 石青 / 茶玻）；
 *  - 《落文 · 启动动画》时间线（0–2800ms 逐段，整段编排固定 2.8s 不拆分）。
 *
 * 启动页黑底 `#0A0C14` 为启动动画原文 0ms 场景逐字指定的场景底色，非新增语义色。
 *
 * TODO(D23 收口)：12 色真值源为 io.legado.app.ui.components.ComponentPalette；本文件是启动页副本，
 * 本轮只留值不改引用，后续改为转发到单源后删除本副本。
 */
object LuowenSplash {

    // —— 12 色语义 Token（值不可变，与设计系统契约逐字对齐）——
    val bgPage: Color = Color(0xFFF7F1E6)          // --bg-page 绢帛底
    val bgSurface: Color = Color(0xFFEFE8D8)       // --bg-surface
    val bgOverlay: Color = Color(0x731A1410)       // --bg-overlay rgba(26,20,16,.45)
    val textPrimary: Color = Color(0xFF1A1410)     // --text-primary 墨色
    val textSecondary: Color = Color(0xFF5C4F3A)   // --text-secondary
    // 标语落款云灰已单源到 splash.contract.SplashParams.textTertiary（TEXT_TERTIARY），本副本不再留第二份。
    val textAccent: Color = Color(0xFFC23B2E)      // --text-accent 朱砂
    val brandGold: Color = Color(0xFFD4A54A)       // --brand-gold 暖金
    val brandCopper: Color = Color(0xFF8B6914)     // --brand-copper 古铜
    val colorLink: Color = Color(0xFFC23B2E)
    val colorDivider: Color = Color(0x141A1410)    // rgba(26,20,16,.08)
    val colorGlow: Color = Color(0x4DD4A54A)       // rgba(212,165,74,.3) 暖金辉光

    /** 启动页全黑底（启动动画原文 0ms，逐字指定） */
    val splashBg: Color = Color(0xFF0A0C14)

    /** 黑底上凝墨成字的字面纸色：由 --bg-page 派生的暖绢色 */
    val glyphPaper: Color = Color(0xFFF2EADB)

    // —— 时间线（启动动画原文，ms；整段编排固定 2800ms，不拆分）——
    // 总时长单源到 splash.contract.SplashParams.TOTAL_MS（转发 DURATION_BOOT），本副本不再留第二份。

    /** 0–400 墨滴坠落（重力加速，边缘撕裂） */
    const val DROP_END_MS: Long = 400L
    /** 400–700 触底炸裂 */
    const val BURST_END_MS: Long = 700L
    /** 700–1000 悬停 0.3s 后加速回聚 */
    const val HOLD_END_MS: Long = 1000L
    /** 回聚吸入从悬停尾部叠加启动（700–1000 区段内完成汇聚） */
    const val SUCK_START_MS: Long = 850L
    const val GATHER_END_MS: Long = 1000L
    /** 1000–1200 金粉爆 */
    const val GOLD_BURST_END_MS: Long = 1200L
    /** 金粉空中飘 0.5s，亮度衰减到 0 */
    const val GOLD_LIFE_MS: Long = 1200L + 500L
    /** 1200–1600 远山浮现，山脊线淡入 400ms */
    const val HILL_END_MS: Long = 1600L
    /** 1400–1800 暖金线顿挫扫过 */
    const val SWEEP_START_MS: Long = 1400L
    const val SWEEP_END_MS: Long = 1800L
    /** 1800 标语落款，淡入 400ms */
    const val SLOGAN_IN_MS: Long = 1800L
    const val SLOGAN_END_MS: Long = 1800L + 400L
    /** 2200–2800 全部淡出 600ms；山最先灭，「文」末捺最后消失 */
    const val FADE_IN_MS: Long = 2200L
    const val FADE_END_MS: Long = 2800L
    /** 远山在淡出段优先消失（2200–2350） */
    const val HILL_FADE_END_MS: Long = FADE_IN_MS + 150L

    // —— 粒子预算（原文性能节：墨 40–50 + 金 60–80，合计 ≤140）——
    const val INK_COUNT: Int = 48
    const val INK_ROUND: Int = 22
    const val INK_FILAMENT: Int = 14
    const val INK_SPLAT: Int = 12
    const val GOLD_COUNT: Int = 76

    /** 粒子飞散半径上限（dp，原文 60dp） */
    const val BURST_RADIUS_DP: Float = 60f

    // 远山浓度 / 辉光半径 / 落款字号已单源到 splash.contract.SplashParams
    // （HILL_MAX_ALPHA / GLOW_RADIUS_DP / SLOGAN_FONT_SIZE_SP），本副本不再留第二份。

    /** 帧率底线（原文 55fps）；引擎帧循环只做确定性推进，不做分配 */
    const val FPS_FLOOR: Int = 55

    /** 一帧预算（ms）：55fps 对应 18.18ms，取 16ms 为健康目标 */
    const val FRAME_BUDGET_MS: Float = 16f
}
