package io.legado.app.ui.book.read.ink

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.book.read.vertical.VerticalTokens

/**
 * 落文 · 设计系统 token（冻结版 v1，值不可变）
 *
 * 仅此处集中持有 12 色语义色、缓动曲线、时长与手势分区尺寸；
 * 阅读页 Compose 层任何像素都必须从这里取，禁止自行发明色值/曲线。
 * 注意：不包裹 MaterialTheme，所有颜色显式传入。
 */
object InkTokens {

    // ─────────────────────────── 12 色语义 Token ───────────────────────────

    /** --bg-page 宣纸底 */
    val bgPage: Color = Color(0xFFF7F1E6)

    /** --bg-surface 绢帛面（气泡 / 底栏 / Sheet） */
    val bgSurface: Color = Color(0xFFEFE8D8)

    /** --bg-overlay 磨砂遮罩 rgba(26,20,16,.45) */
    val bgOverlay: Color = Color(0x731A1410)

    /** --text-primary 墨色 */
    val textPrimary: Color = Color(0xFF1A1410)

    /** --text-secondary 茶褐 */
    val textSecondary: Color = Color(0xFF5C4F3A)

    /** --text-tertiary 灰褐（进度线未读段「云灰」） */
    val textTertiary: Color = Color(0xFF9B9687)

    /** --text-accent 朱砂（进度线已读段） */
    val textAccent: Color = Color(0xFFC23B2E)

    /** --brand-gold 暖金 */
    val brandGold: Color = Color(0xFFD4A54A)

    /** --brand-copper 铜 */
    val brandCopper: Color = Color(0xFF8B6914)

    /** --color-link 链接 / 朱砂 */
    val colorLink: Color = Color(0xFFC23B2E)

    /** --color-divider 细分隔线 rgba(26,20,16,.08) */
    val colorDivider: Color = Color(0x141A1410)

    /** --color-glow 金辉 rgba(212,165,74,.3) */
    val colorGlow: Color = Color(0x4DD4A54A)

    // ─────────────────────────── 缓动曲线（值不可变） ───────────────────────────

    /** 卡片浮入 cubic-bezier(.22,.61,.36,1) */
    val cardEnterEasing: CubicBezierEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    /** 气泡 cubic-bezier(.34,1.56,.64,1)（回弹过冲） */
    val bubbleEasing: CubicBezierEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** 翻页掀角 cubic-bezier(.4,0,.2,1) */
    val pageTurnEasing: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 遮罩 cubic-bezier(.4,0,.6,1) */
    val maskEasing: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

    // ─────────────────────────── 时长（毫秒，值不可变） ───────────────────────────

    const val DURATION_CARD: Int = 250
    const val DURATION_BUBBLE: Int = 250
    const val DURATION_MASK: Int = 200
    const val DURATION_MICRO: Int = 120

    /**
     * 翻页掀角时长 400ms（规格切片-09 §3.3「整页像真实纸张翻过去（400ms）」）。
     * 与竖排引擎 [VerticalTokens.PAGE_PEEK_DURATION_MS] 同源口径，不在本处另立数值。
     */
    const val DURATION_PAGE: Int = VerticalTokens.PAGE_PEEK_DURATION_MS

    const val DURATION_BOOT: Int = 2800

    // ─────────────────────────── 手势分区尺寸（铁律） ───────────────────────────

    /** 左右边缘各占屏宽 1/5 */
    const val EDGE_FRACTION: Float = 1f / 5f

    /** 中间正文区占屏宽 3/5 */
    const val CENTER_FRACTION: Float = 3f / 5f

    /** 底栏高度 60dp，Compose 全吃 */
    val BOTTOM_BAR_HEIGHT: Dp = 60.dp

    /** 气泡与原字间距 8dp */
    val BUBBLE_GAP: Dp = 8.dp

    /** 气泡最大宽 = 屏宽 70% */
    const val BUBBLE_MAX_WIDTH_RATIO: Float = 0.7f

    /** 墨触按压朱砂覆盖透明度（0→8%） */
    const val PRESS_ACCENT_ALPHA: Float = 0.08f

    /** 气泡尖角宽 / 高 */
    val BUBBLE_ARROW_WIDTH: Dp = 12.dp
    val BUBBLE_ARROW_HEIGHT: Dp = 8.dp

    // ─────────────────────────── 宣纸 / 光影 / 粒子 / 掀角（规格切片-09 §3.1~3.3） ───────────────────────────

    /** 宣纸颗粒：噪点瓦片网格粒度（dp），颗粒半径 2px 级。 */
    const val NOISE_GRID_DP: Float = 2f

    /** 噪点墨色透明度（叠在宣纸底上，极淡）。 */
    const val NOISE_ALPHA: Float = 0.05f

    /** 左上 2800K 烛光暖光：金辉覆盖到屏宽约 60% 处淡出。 */
    const val CANDLELIGHT_RADIUS_RATIO: Float = 0.6f

    /** 烛光暖光峰值透明度。 */
    const val CANDLELIGHT_ALPHA: Float = 0.10f

    /** 右下角压暗（墨色薄纱）透明度。 */
    const val CORNER_SHADE_ALPHA: Float = 0.08f

    /** 章节氛围粒子：一屏常驻颗数（水墨战场轮廓式散点，不是贴图）。 */
    const val PARTICLE_COUNT: Int = 28

    /** 氛围粒子墨色透明度。 */
    const val PARTICLE_ALPHA: Float = 0.07f

    /** 氛围粒子半径区间（dp），随机落点由种子固定，避免每帧抖动。 */
    val PARTICLE_RADIUS_MIN_DP: Dp = 1.dp
    val PARTICLE_RADIUS_MAX_DP: Dp = 3.dp

    /** 右缘进度线宽 1dp（规格 §3.1：页面最右边缘一条竖线）。 */
    val PROGRESS_LINE_WIDTH: Dp = 1.dp

    /** 翻页背面墨色透印 alpha 15%（规格 §3.3；与引擎 BACK_INK_ALPHA 同口径）。 */
    const val BACK_TRANSMISSION_ALPHA: Float = VerticalTokens.BACK_INK_ALPHA

    /** 掀角折起幅度：角点沿对角线缩回屏宽的比例。 */
    const val PEEL_CORNER_FRACTION: Float = 0.22f

    /** 翻角物理阴影最大透明度。 */
    const val PEEL_SHADOW_ALPHA: Float = 0.18f
}
