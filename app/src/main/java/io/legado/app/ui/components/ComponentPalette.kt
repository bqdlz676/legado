package io.legado.app.ui.components

/**
 * 落文组件库 · 纯数值调色板（平台无关层）
 *
 * 这里只持有 12 色语义 token 的原始 ARGB 值、覆盖透明度、时长、缓动参数、
 * 弹簧规格、Sheet 分段比例与尺寸常数。本文件**不引用任何 Compose / Android 类型**，
 * 因此可以在普通 JVM 单测里直接断言；Compose 侧的 [ComponentTokens] 只做类型转换，
 * 全站组件不允许在别处另造色值、曲线或时长。
 *
 * 规格来源：切片-03 颜色 Token 系统（12 变量）、动效层缓动曲线表与 duration 常数；
 * 切片-04 四个专属组件与七态覆盖（默认/悬浮/点击/禁用/加载/空数据/离线）。
 */
object ComponentPalette {

    // ─────────────────────────── 12 色语义 Token（ARGB, 0xAARRGGBB） ───────────────────────────

    /** --bg-page 阅读页纸白 #F7F1E6 */
    const val BG_PAGE: Long = 0xFFF7F1E6

    /** --bg-surface 卡片 / Sheet 绢帛微黄 #EFE8D8 */
    const val BG_SURFACE: Long = 0xFFEFE8D8

    /** --bg-overlay 茶玻遮罩 rgba(26,20,16,.45) */
    const val BG_OVERLAY: Long = 0x731A1410

    /** --text-primary 正文墨色 #1A1410 */
    const val TEXT_PRIMARY: Long = 0xFF1A1410

    /** --text-secondary 副文茶褐 #5C4F3A */
    const val TEXT_SECONDARY: Long = 0xFF5C4F3A

    /** --text-tertiary 辅助 / 占位灰褐 #9B9687 */
    const val TEXT_TERTIARY: Long = 0xFF9B9687

    /** --text-accent 朱砂强调 #C23B2E */
    const val TEXT_ACCENT: Long = 0xFFC23B2E

    /** --brand-gold 暖金 #D4A54A */
    const val BRAND_GOLD: Long = 0xFFD4A54A

    /** --brand-copper 古铜 #8B6914 */
    const val BRAND_COPPER: Long = 0xFF8B6914

    /** --color-link 链接（同朱砂） #C23B2E */
    const val COLOR_LINK: Long = 0xFFC23B2E

    /** --color-divider 细分隔线 rgba(26,20,16,.08) */
    const val COLOR_DIVIDER: Long = 0x141A1410

    /** --color-glow 暖金辉光 rgba(212,165,74,.3) */
    const val COLOR_GLOW: Long = 0x4DD4A54A

    /**
     * 石青（辅助功能色，Toast 提示态专用）。
     * 12 色主 token 之外的唯一增补：规格要求 Toast 三色为 成功暖金 / 失败朱砂 / 提示石青，
     * 石青取传统矿物青的低饱和墨调，不与朱砂、暖金抢视觉。
     */
    const val STONE_BLUE: Long = 0xFF3A6B66

    /** 12 色主 token 清单（供自检遍历，顺序即规格顺序） */
    val mainTokens: List<Long> = listOf(
        BG_PAGE, BG_SURFACE, BG_OVERLAY,
        TEXT_PRIMARY, TEXT_SECONDARY, TEXT_TERTIARY, TEXT_ACCENT,
        BRAND_GOLD, BRAND_COPPER, COLOR_LINK, COLOR_DIVIDER, COLOR_GLOW,
    )

    // ─────────────────────────── 墨触覆盖透明度 ───────────────────────────

    /** 悬浮：朱砂背景 8%（touchstart 泛朱砂 0→8%） */
    const val PRESS_HOVER_ACCENT_ALPHA: Float = 0.08f

    /** 点击：朱砂背景 16% */
    const val PRESS_DOWN_ACCENT_ALPHA: Float = 0.16f

    /** 禁用：云灰 30% opacity */
    const val DISABLED_OPACITY: Float = 0.30f

    // ─────────────────────────── 时长常数（毫秒，全局统一） ───────────────────────────

    /** 微交互 120ms（墨触回落、列表项朱砂反馈） */
    const val DURATION_MICRO: Int = 120

    /** 卡片 / 气泡 250ms */
    const val DURATION_CARD: Int = 250

    /** 气泡弹入 250ms（spring overshoot） */
    const val DURATION_BUBBLE: Int = 250

    /** 页面转场 350ms */
    const val DURATION_PAGE: Int = 350

    /** 遮罩淡入淡出 200ms */
    const val DURATION_MASK: Int = 200

    /** 启动页粒子编排 2800ms（整段不拆分） */
    const val DURATION_BOOT: Int = 2800

    // ─────────────────────────── 缓动曲线参数（cubic-bezier） ───────────────────────────

    /** 卡片浮入 cubic-bezier(0.22, 0.61, 0.36, 1) 减速入，稳落 */
    val CARD_ENTER_EASING: FloatArray = floatArrayOf(0.22f, 0.61f, 0.36f, 1f)

    /** 气泡弹入 cubic-bezier(0.34, 1.56, 0.64, 1) overshoot 回弹 */
    val BUBBLE_EASING: FloatArray = floatArrayOf(0.34f, 1.56f, 0.64f, 1f)

    /** 翻页掀角 cubic-bezier(0.4, 0, 0.2, 1) 匀速掀、急落 */
    val PAGE_TURN_EASING: FloatArray = floatArrayOf(0.4f, 0f, 0.2f, 1f)

    /** 遮罩淡入淡出 cubic-bezier(0.4, 0, 0.6, 1) 对称，不抢 */
    val MASK_EASING: FloatArray = floatArrayOf(0.4f, 0f, 0.6f, 1f)

    // ─────────────────────────── 弹簧规格（spring） ───────────────────────────

    /** 气泡注入回弹弹簧（scale 0.3 → 1.05 → 1.0） */
    const val BUBBLE_SPRING_DAMPING: Float = 0.55f
    const val BUBBLE_SPRING_STIFFNESS: Float = 300f

    /** BottomSheet 升起 / 回弹弹簧 */
    const val SHEET_SPRING_DAMPING: Float = 0.72f
    const val SHEET_SPRING_STIFFNESS: Float = 320f

    /** 卡片入场 / 错峰浮入弹簧 */
    const val REVEAL_SPRING_DAMPING: Float = 0.80f
    const val REVEAL_SPRING_STIFFNESS: Float = 360f

    // ─────────────────────────── BottomSheet 三段高度（占屏高比例） ───────────────────────────

    /** 低段 30% */
    const val SHEET_DETENT_LOW: Float = 0.30f

    /** 中段 60%（人物详情 / 气泡详情默认） */
    const val SHEET_DETENT_MID: Float = 0.60f

    /** 高段 90% */
    const val SHEET_DETENT_HIGH: Float = 0.90f

    /** 拖拽超过该比例且速度向下时关闭，否则回弹到最近分段 */
    const val SHEET_DISMISS_THRESHOLD: Float = 0.12f

    val sheetDetents: List<Float> = listOf(SHEET_DETENT_LOW, SHEET_DETENT_MID, SHEET_DETENT_HIGH)

    // ─────────────────────────── 尺寸常数（dp，Compose 侧转 Dp） ───────────────────────────

    /** 卡片圆角 16dp */
    const val CARD_RADIUS_DP: Int = 16

    /** 按钮圆角 20dp */
    const val BUTTON_RADIUS_DP: Int = 20

    /** 气泡圆角 12dp */
    const val BUBBLE_RADIUS_DP: Int = 12

    /** Sheet 顶部圆角 20dp */
    const val SHEET_RADIUS_DP: Int = 20

    /** 金杠（拖拽指示器）宽 / 高 */
    const val SHEET_GRABBER_WIDTH_DP: Int = 40
    const val SHEET_GRABBER_HEIGHT_DP: Int = 4

    /** 气泡与原字间距 8dp（交互铁律：原文上方 8dp） */
    const val BUBBLE_GAP_DP: Int = 8

    /** 气泡最大宽 = 屏宽 70% */
    const val BUBBLE_MAX_WIDTH_RATIO: Float = 0.70f

    /** 气泡尖角宽 / 高 */
    const val BUBBLE_ARROW_WIDTH_DP: Int = 12
    const val BUBBLE_ARROW_HEIGHT_DP: Int = 8

    /** 性能红线：单帧预算 16ms（60fps 不掉帧） */
    const val FRAME_BUDGET_MS: Long = 16L

    /** 组件模型构建 / 状态解析 bench 预算（单帧内留出绘制余量） */
    const val MODEL_BUDGET_MS: Long = 4L
}
