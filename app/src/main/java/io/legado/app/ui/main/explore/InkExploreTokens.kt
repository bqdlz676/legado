package io.legado.app.ui.main.explore

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 落文 · 发现页 token 表（冻结契约 v1，值不可变）
 *
 * 12 色语义色 + 四条缓动 + 四档时长，全部与《落文-设计系统-契约.md》一一对应。
 * 本子域不引 MaterialTheme，不写 Material ripple；颜色只许从这里取。
 */
object InkExploreTokens {

    // —— 12 色语义 Token（值不可变） ——

    /** --bg-page 纸白 */
    val BgPage = Color(0xFFF7F1E6)

    /** --bg-surface 卡片 / Sheet 微黄 */
    val BgSurface = Color(0xFFEFE8D8)

    /** --bg-overlay 茶玻遮罩 rgba(26,20,16,.45)，0x73 ≈ .451 */
    val BgOverlay = Color(0x731A1410)

    /** --text-primary 正文墨色 */
    val TextPrimary = Color(0xFF1A1410)

    /** --text-secondary 副文 */
    val TextSecondary = Color(0xFF5C4F3A)

    /** --text-tertiary 辅助 / 占位 */
    val TextTertiary = Color(0xFF9B9687)

    /** --text-accent 朱砂 */
    val TextAccent = Color(0xFFC23B2E)

    /** --brand-gold 暖金 */
    val BrandGold = Color(0xFFD4A54A)

    /** --brand-copper 古铜 */
    val BrandCopper = Color(0xFF8B6914)

    /** --color-link 链接（同朱砂） */
    val ColorLink = Color(0xFFC23B2E)

    /** --color-divider 墨色细线 rgba(26,20,16,.08)，0x14 ≈ .078 */
    val ColorDivider = Color(0x141A1410)

    /** --color-glow 暖金微光 rgba(212,165,74,.3)，0x4D ≈ .302 */
    val ColorGlow = Color(0x4DD4A54A)

    // —— 缓动曲线（值不可变，只许这四条） ——

    /** 卡片浮入 cubic-bezier(.22,.61,.36,1) */
    val CardReveal = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    /** 气泡弹入 cubic-bezier(.34,1.56,.64,1)，允许过冲 */
    val BubbleSpring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** 翻页掀角 cubic-bezier(.4,0,.2,1) */
    val PageTurn = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 遮罩 cubic-bezier(.4,0,.6,1) */
    val Scrim = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

    // —— 时长（毫秒，值不可变） ——

    /** 微交互：泛色、按下回弹 */
    const val MicroMs = 120L

    /** 卡片 / 气泡 */
    const val CardMs = 250L

    /** 页面转场 */
    const val PageMs = 350L

    /** 遮罩 */
    const val ScrimMs = 200L

    // —— 布局常数 ——

    /** 页面左右边距 */
    val PagePadding = 16.dp

    /** 卡片之间的槽距 */
    val CardGap = 10.dp

    /** 通栏卡片内边距 */
    val BannerPadding = 14.dp
}
