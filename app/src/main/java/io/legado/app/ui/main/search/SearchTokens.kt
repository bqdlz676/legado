package io.legado.app.ui.main.search

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 落文 · 搜索页 token 表（冻结契约 v1，色值 / 曲线 / 时长不可变）
 *
 * 来源：切片-03「颜色 Token 系统」只允许全站 12 个语义色；切片-13 搜索页；切片-15 动效铁律。
 * 本子域不引 MaterialTheme，不写 Material ripple；颜色只许从这里取，其余类只读不改。
 * 色值与阅读页 [io.legado.app.ui.book.read.ink.InkTokens]、发现页 InkExploreTokens 同口径，
 * 禁止在本目录另立独立色值。
 */
object SearchTokens {

    // ─────────────────────────── 12 色语义 Token（冻结名） ───────────────────────────

    /** --bg-page 宣纸底 #F7F1E6 */
    val BG_PAGE: Color = Color(0xFFF7F1E6)

    /** --bg-surface 绢帛面（输入框 / 条目底）#EFE8D8 */
    val BG_SURFACE: Color = Color(0xFFEFE8D8)

    /** --bg-overlay 磨砂遮罩 rgba(26,20,16,.45)，0x73 ≈ .451 */
    val BG_OVERLAY: Color = Color(0x731A1410)

    /** --text-primary 正文墨色 #1A1410 */
    val TEXT_PRIMARY: Color = Color(0xFF1A1410)

    /** --text-secondary 茶褐（作者 / 题注副文）#5C4F3A */
    val TEXT_SECONDARY: Color = Color(0xFF5C4F3A)

    /** --text-tertiary 云灰（占位 / 引导小字 / 未命中）#9B9687 */
    val TEXT_TERTIARY: Color = Color(0xFF9B9687)

    /** --text-accent 朱砂（命中片段高亮）#C23B2E */
    val TEXT_ACCENT: Color = Color(0xFFC23B2E)

    /** --brand-gold 暖金（加载点 / 聚焦辉光）#D4A54A */
    val BRAND_GOLD: Color = Color(0xFFD4A54A)

    /** --brand-copper 古铜（离线标记）#8B6914 */
    val BRAND_COPPER: Color = Color(0xFF8B6914)

    /** --color-link 链接（同朱砂）#C23B2E */
    val COLOR_LINK: Color = Color(0xFFC23B2E)

    /** --color-divider 墨色细线 rgba(26,20,16,.08)，0x14 ≈ .078 */
    val COLOR_DIVIDER: Color = Color(0x141A1410)

    /** --color-glow 暖金辉光 rgba(212,165,74,.3)，0x4D ≈ .302 */
    val COLOR_GLOW: Color = Color(0x4DD4A54A)

    // ─────────────────────────── 缓动曲线（切片-15，值不可变） ───────────────────────────

    /** 结果 stagger 浮入 cubic-bezier(.22,.61,.36,1)：减速入，稳落。 */
    val CARD_REVEAL: CubicBezierEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    /** 聚焦 spring 回弹 cubic-bezier(.34,1.56,.64,1)：overshoot 回弹。 */
    val FOCUS_SPRING: CubicBezierEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** 状态匀速过渡 cubic-bezier(.4,0,.2,1)。 */
    val PAGE_TURN: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 遮罩淡入淡出 cubic-bezier(.4,0,.6,1)。 */
    val SCRIM: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

    // ─────────────────────────── 时长（毫秒，切片-15 全局统一） ───────────────────────────

    /** 微交互（点触泛色 / 按下回弹）120ms。 */
    const val DURATION_MICRO: Long = 120L

    /** 卡片 / 条目浮入 350ms（切片-15：卡片浮入 stagger 350ms each）。 */
    const val DURATION_CARD: Long = 350L

    /** 页面 / 状态转场 350ms。 */
    const val DURATION_PAGE: Long = 350L

    /** 遮罩淡入淡出 200ms。 */
    const val DURATION_SCRIM: Long = 200L

    // ─────────────────────────── 布局常数（切片-13 / 切片-03 搜索页行） ───────────────────────────

    /** 页面左右边距。 */
    val PAGE_PADDING = 16.dp

    /** 输入框高度。 */
    val INPUT_HEIGHT = 48.dp

    /** 输入框聚焦浮起位移（soft shadow 聚焦时输入框浮起）。 */
    val INPUT_FOCUS_LIFT = 4.dp

    /** 输入框底线宽 1dp（subtle border 云灰 1px 底线）。 */
    val INPUT_BORDER_WIDTH = 1.dp

    /** 条目之间槽距。 */
    val ROW_GAP = 8.dp

    /** 历史 / 热门标签内边距横向。 */
    val CHIP_PADDING_H = 12.dp

    /** 标签槽距。 */
    val CHIP_GAP = 10.dp

    /** 历史记录保留条数上限。 */
    const val HISTORY_LIMIT: Int = 12

    /** 热门词条展示上限。 */
    const val HOT_LIMIT: Int = 10

    /** 结果列表单次渲染上限（兜底防爆列表，超出由分页继续取）。 */
    const val RESULT_PAGE_LIMIT: Int = 100

    /** 查询词长度上限：超长词在此截断，避免 LIKE / 高亮切分出现退化输入（兜底边界）。 */
    const val QUERY_MAX_LEN: Int = 50

    /** stagger 逐条错开毫秒数（最多错 8 条）。 */
    const val STAGGER_STEP_MS: Int = 45

    /** stagger 最大错开条数。 */
    const val STAGGER_MAX_INDEX: Int = 8

    // ─────────────────────────── 文案（切片-13 逐字） ───────────────────────────

    /** 输入框下方引导小字（云灰 12sp），空态同文兜底。 */
    const val HINT_GUIDE: String = "搜书名、作者、或正文中的任意一句"

    /** 离线兜底文案。 */
    const val OFFLINE_MESSAGE: String = "当前离线，展示本地缓存结果"

    /** 无结果兜底文案（带查询词占位）。 */
    const val EMPTY_MESSAGE: String = "没有找到相关内容，换个说法试试"

    /** 检索失败兜底文案。 */
    const val ERROR_MESSAGE: String = "检索暂时不可用"

    /** 重试按钮文案。 */
    const val RETRY_LABEL: String = "重试"

    /** 历史分区标题。 */
    const val HISTORY_TITLE: String = "搜索历史"

    /** 热门分区标题。 */
    const val HOT_TITLE: String = "热门"

    /** 清空历史文案。 */
    const val CLEAR_HISTORY_LABEL: String = "清空历史"

    // ─────────────────────────── 性能预算（切片-08 红线，bench 写死断言） ───────────────────────────

    /** 模型层单帧预算（ms，60fps 一帧 16.6ms）；匹配 / 高亮 / 归约必须落在其内。 */
    const val FRAME_BUDGET_MS: Double = 16.0

    /** 纯模型阶段预算（ms）：状态归约、分词高亮这类轻量路径 ≤ 4ms。 */
    const val MODEL_BUDGET_MS: Double = 4.0
}
