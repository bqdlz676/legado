package io.legado.app.design

import androidx.compose.ui.graphics.Color

/**
 * S-03 设计 token · 12 色单源（冻结 v1）。
 *
 * 为什么必须单源：规格只允许全站使用这 12 个语义色；色值一旦散落到各 Composable /
 * 自绘 View，调色时必然出现「同一朱砂两套红」，暗色 / 强调场景也会各自失效。
 * 本对象是全站唯一取色出处，其余类只读不改；不包裹 MaterialTheme，颜色显式传入。
 *
 * 真值源：docs/锚点/S-03-设计token.md §二参数表（12 项，名字 / 色值逐字照抄，
 * 不许改名、不许合并 [TEXT_ACCENT] 与 [COLOR_LINK]——二者语义不同，误合并会在
 * 暗色 / 强调场景各自失效）。冲突裁决 docs/立项-冲突裁决.md §三：七色表述作废，取 12 色。
 *
 * 透明度折算：锚点表中的 rgba(r,g,b,a) 折算为 8 位 ARGB 字面量，alpha = round(a*255)，
 * 折算口径与既有冻结 token 保持一致；KDoc 内保留原始 rgba() 文本以便回源核对。
 */
object DesignColorTokens {

    // ─────────────────────────── 背景三阶 ───────────────────────────

    /** --bg-page 阅读页纸白 #F7F1E6 */
    val BG_PAGE: Color = Color(0xFFF7F1E6)

    /** --bg-surface 卡片 / Sheet 绢帛面 #EFE8D8 */
    val BG_SURFACE: Color = Color(0xFFEFE8D8)

    /** --bg-overlay 遮罩茶玻 rgba(26,20,16,.45) */
    val BG_OVERLAY: Color = Color(0x731A1410)

    // ─────────────────────────── 文字四阶 ───────────────────────────

    /** --text-primary 正文墨色 #1A1410 */
    val TEXT_PRIMARY: Color = Color(0xFF1A1410)

    /** --text-secondary 副文茶褐 #5C4F3A */
    val TEXT_SECONDARY: Color = Color(0xFF5C4F3A)

    /** --text-tertiary 辅助 / 占位灰褐 #9B9687 */
    val TEXT_TERTIARY: Color = Color(0xFF9B9687)

    /** --text-accent 朱砂强调 #C23B2E（与 [COLOR_LINK] 同色不同义，禁止合并） */
    val TEXT_ACCENT: Color = Color(0xFFC23B2E)

    // ─────────────────────────── 品牌 ───────────────────────────

    /** --brand-gold 暖金 #D4A54A */
    val BRAND_GOLD: Color = Color(0xFFD4A54A)

    /** --brand-copper 古铜 #8B6914 */
    val BRAND_COPPER: Color = Color(0xFF8B6914)

    // ─────────────────────────── 功能色 ───────────────────────────

    /** --color-link 链接朱砂 #C23B2E（与 [TEXT_ACCENT] 同色不同义，禁止合并） */
    val COLOR_LINK: Color = Color(0xFFC23B2E)

    /** --color-divider 细分隔线 rgba(26,20,16,.08) */
    val COLOR_DIVIDER: Color = Color(0x141A1410)

    /** --color-glow 暖金辉光 rgba(212,165,74,.3) */
    val COLOR_GLOW: Color = Color(0x4DD4A54A)
}
