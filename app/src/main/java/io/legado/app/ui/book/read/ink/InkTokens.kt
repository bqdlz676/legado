package io.legado.app.ui.book.read.ink

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.book.read.vertical.VerticalTokens

/**
 * 落文 · 阅读页设计系统 token（冻结 v1，色值 / 曲线 / 时长不可变）。
 *
 * 为什么颜色必须集中收口：规格切片-03 只允许全站用 12 个语义色，一旦色值散落到
 * 各 Composable / 自绘 View 里，调色时必然出现“同一朱砂两套红”。本对象是阅读页
 * 唯一取色出处，其余类只读不改；不包裹 MaterialTheme，所有颜色显式传入。
 *
 * 冻结契约（U5-B 会按 UPPER_SNAKE 名 import，名字不许改）：
 *  BG_PAGE / BG_SURFACE / BG_OVERLAY / TEXT_PRIMARY / TEXT_SECONDARY / TEXT_TERTIARY /
 *  TEXT_ACCENT / BRAND_GOLD / BRAND_COPPER / COLOR_LINK / COLOR_DIVIDER / COLOR_GLOW。
 *  下面同时保留 camelCase 别名，供同仓旧引用平滑过渡；二者指向同一 [Color]。
 */
object InkTokens {

    // ─────────────────────────── 12 色语义 Token（冻结名） ───────────────────────────

    /** --bg-page 宣纸底 #F7F1E6 */
    val BG_PAGE: Color = Color(0xFFF7F1E6)

    /** --bg-surface 绢帛面（气泡 / 底栏 / Sheet）#EFE8D8 */
    val BG_SURFACE: Color = Color(0xFFEFE8D8)

    /** --bg-overlay 磨砂遮罩 rgba(26,20,16,.45) */
    val BG_OVERLAY: Color = Color(0x731A1410)

    /** --text-primary 墨色 #1A1410 */
    val TEXT_PRIMARY: Color = Color(0xFF1A1410)

    /** --text-secondary 茶褐 #5C4F3A */
    val TEXT_SECONDARY: Color = Color(0xFF5C4F3A)

    /** --text-tertiary 灰褐（进度线未读段「云灰」）#9B9687 */
    val TEXT_TERTIARY: Color = Color(0xFF9B9687)

    /** --text-accent 朱砂（进度线已读段）#C23B2E */
    val TEXT_ACCENT: Color = Color(0xFFC23B2E)

    /** --brand-gold 暖金 #D4A54A */
    val BRAND_GOLD: Color = Color(0xFFD4A54A)

    /** --brand-copper 古铜 #8B6914 */
    val BRAND_COPPER: Color = Color(0xFF8B6914)

    /** --color-link 链接 / 朱砂 #C23B2E */
    val COLOR_LINK: Color = Color(0xFFC23B2E)

    /** --color-divider 细分隔线 rgba(26,20,16,.08) */
    val COLOR_DIVIDER: Color = Color(0x141A1410)

    /** --color-glow 金辉 rgba(212,165,74,.3) */
    val COLOR_GLOW: Color = Color(0x4DD4A54A)

    // ─────────────────────── camelCase 过渡别名（与冻结名同色，勿新增独立色值） ───────────────────────

    val bgPage: Color get() = BG_PAGE
    val bgSurface: Color get() = BG_SURFACE
    val bgOverlay: Color get() = BG_OVERLAY
    val textPrimary: Color get() = TEXT_PRIMARY
    val textSecondary: Color get() = TEXT_SECONDARY
    val textTertiary: Color get() = TEXT_TERTIARY
    val textAccent: Color get() = TEXT_ACCENT
    val brandGold: Color get() = BRAND_GOLD
    val brandCopper: Color get() = BRAND_COPPER
    val colorLink: Color get() = COLOR_LINK
    val colorDivider: Color get() = COLOR_DIVIDER
    val colorGlow: Color get() = COLOR_GLOW

    // ─────────────────────────── 缓动曲线（切片-15，值不可变） ───────────────────────────

    /** 卡片浮入 cubic-bezier(0.22,0.61,0.36,1)：减速入，稳落。 */
    val cardEnterEasing: CubicBezierEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    /** 气泡弹入 cubic-bezier(0.34,1.56,0.64,1)：overshoot 回弹。 */
    val bubbleEasing: CubicBezierEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** 翻页掀角 cubic-bezier(0.4,0,0.2,1)：匀速掀、急落。物理掀角仍以 [PeelSpring] 为主。 */
    val peelEasing: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 遮罩淡入淡出 cubic-bezier(0.4,0,0.6,1)：对称，不抢。 */
    val maskEasing: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

    // ─────────────────────────── 时长（毫秒，切片-15 全局统一） ───────────────────────────

    /** 微交互（点触反馈）120ms。 */
    const val DURATION_MICRO: Int = 120

    /** 卡片 / 气泡入场 250ms。 */
    const val DURATION_CARD: Int = 250
    const val DURATION_BUBBLE: Int = 250

    /** 页面转场 350ms。 */
    const val DURATION_PAGE: Int = 350

    /** 遮罩淡入淡出 200ms。 */
    const val DURATION_MASK: Int = 200

    /** Sheet 弹起 250ms。 */
    const val DURATION_SHEET: Int = 250

    /** 翻页掀角 400ms（切片-09 §3.3「整页像真实纸张翻过去（400ms）」）。 */
    const val DURATION_PEEL: Int = VerticalTokens.PAGE_PEEK_DURATION_MS

    /** 启动页粒子整段编排 2800ms（不拆分）。 */
    const val DURATION_BOOT: Int = 2800

    // ─────────────────────────── 手势分区尺寸（切片-09 手势铁律） ───────────────────────────

    /** 左右边缘各占屏宽 1/5。 */
    const val EDGE_FRACTION: Float = 1f / 5f

    /** 中间正文区占屏宽 3/5。 */
    const val CENTER_FRACTION: Float = 3f / 5f

    /** 底栏高度 60dp，Compose 全吃。 */
    val BOTTOM_BAR_HEIGHT: Dp = 60.dp

    /** 气泡与原字间距 8dp。 */
    val BUBBLE_GAP: Dp = 8.dp

    /** 气泡最大宽 = 屏宽 70%。 */
    const val BUBBLE_MAX_WIDTH_RATIO: Float = 0.7f

    /** 墨触按压朱砂覆盖透明度（0→8%）；不使用 Material 默认 ripple。 */
    const val PRESS_ACCENT_ALPHA: Float = 0.08f

    /** 气泡尖角宽 / 高。 */
    val BUBBLE_ARROW_WIDTH: Dp = 12.dp
    val BUBBLE_ARROW_HEIGHT: Dp = 8.dp

    // ─────────────────────────── 宣纸 / 光影 / 粒子 / 掀角（切片-09 §3.1~3.3） ───────────────────────────

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

    /** 氛围粒子墨色透明度（不高于 #000 8%，切片-03 约束）。 */
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

    // ─────────────────────────── 磨砂毛玻璃（切片-03 四层框架 · 绢帛面） ───────────────────────────

    /** API 31+ RenderEffect 模糊半径（dp）；26-30 不走模糊，改半透明 + 噪点纹理模拟。 */
    val FROST_BLUR_RADIUS_DP: Dp = 18.dp

    /** API 31+ 磨砂面底色透明度（模糊层自身仍保留绢帛底色，不是纯透明）。 */
    const val FROST_SURFACE_ALPHA_MODERN: Float = 0.82f

    /** API 26-30 兜底磨砂：半透明绢帛 + 噪点颗粒叠加，禁止纯透明冒充。 */
    const val FROST_SURFACE_ALPHA_LEGACY: Float = 0.88f

    /** 低端机兜底：退近纯色绢帛，不做模糊 / 噪点叠层。 */
    const val FROST_SURFACE_ALPHA_LOW_SPEC: Float = 0.94f

    /** 磨砂噪点网格粒度（dp），与宣纸颗粒同口径。 */
    const val FROST_NOISE_GRID_DP: Float = 2f

    /** 磨砂噪点墨色透明度。 */
    const val FROST_NOISE_ALPHA: Float = 0.04f

    // ─────────────────────────── 墨爆 / 氛围粒子运动 ───────────────────────────

    /** 点字墨爆字面冲击时长（ms）：独立于气泡 250ms 入场，100ms 内完成扩散。 */
    const val BURST_DURATION_MS: Int = 100

    /** 墨爆冲击环最大半径（dp）。 */
    val BURST_RING_RADIUS_DP: Dp = 28.dp

    /** 墨爆飞散墨点颗数。 */
    const val BURST_FLECK_COUNT: Int = 6

    /** 墨爆飞散墨点半径区间（dp）。 */
    val BURST_FLECK_MIN_DP: Dp = 1.dp
    val BURST_FLECK_MAX_DP: Dp = 2.dp

    /** 氛围粒子低端机颗数（常规 [PARTICLE_COUNT] 的一半）。 */
    const val PARTICLE_COUNT_LOW_SPEC: Int = 14

    /** 氛围粒子漂移速度：归一化屏宽 / 秒（极缓，避免抢正文视线）。 */
    const val PARTICLE_DRIFT_RATIO_PER_SEC: Float = 0.008f

    /** 氛围粒子明灭周期（ms），相位由种子固定。 */
    const val PARTICLE_TWINKLE_PERIOD_MS: Int = 3200

    /** 静止超时：用户滚动 / 阅读 3 秒无操作后氛围粒子自动暂停，不白烧 GPU（切片-03）。 */
    const val PARTICLE_IDLE_PAUSE_MS: Long = 3000L

    // ─────────────────────────── 掀角 spring 物理 ───────────────────────────

    /** 掀角弹簧刚度（N/m 量级）；与阻尼比配合使整定时间落在 400ms 量级。 */
    const val PEEL_SPRING_STIFFNESS: Float = 220f

    /** 掀角弹簧阻尼比：略欠阻尼，纸角到位时留一丝轻微回弹。 */
    const val PEEL_SPRING_DAMPING_RATIO: Float = 0.75f

    // ─────────────────────────── 帧预算 / 性能红线（切片-08） ───────────────────────────

    /** 单帧绘制预算（ms，60fps 一帧 16.6ms）；翻页全程 ≤16ms/帧。 */
    const val FRAME_BUDGET_MS: Double = 16.0

    /** 气泡热路径预算（ms，规格：冷启 ≤300ms、之后 ≤80ms）。 */
    const val BUBBLE_HOT_BUDGET_MS: Double = 80.0

    /** 连续超预算帧数阈值：达到即触发低端降级（粒子减半、跳过噪点叠层）。 */
    const val OVER_BUDGET_STREAK: Int = 3

    /** 冷启动 → 书架可交互红线 ≤1.8s（切片-08 启动速度；bench 写死断言）。 */
    const val COLD_START_TO_BOOKSHELF_MS: Double = 1800.0

    /** 阅读页峰值内存红线 ≤180MB（切片-08 内存；bench 写死断言）。 */
    const val PEAK_MEMORY_MB: Double = 180.0

    /** 字体子集首屏体积红线 ≤200KB（切片-14 / 切片-08 字体加载；bench 写死断言）。 */
    const val FONT_SUBSET_MAX_KB: Double = 200.0

    /** 章节排版缓存只保留前后各 3 章（切片-08 阅读缓存；其余释放）。 */
    const val CHAPTER_CACHE_RADIUS: Int = 3
}
