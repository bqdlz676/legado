package io.legado.app.ui.components

/**
 * U6 半屏 Sheet · 纯模型层（切片-11 逐字落地）。
 *
 * 本文件**不引用任何 Compose / Android 类型**，可在普通 JVM 单测中断言；
 * Compose 侧的 [InkHalfSheet] 只把这里的比例 / 透明度 / 下沉量转成布局参数。
 *
 * 切片-11 契约（逐字要点）：
 * - Sheet 最大高度 = 屏幕 × 60%；
 * - 弹起时阅读页整体下沉 8dp + 暗化 40%（茶色玻璃罩）；
 * - 跟手下拉关闭：Sheet 下降，阅读页同时上升恢复亮度；
 * - 关闭后阅读位置、滚动位置、下划线状态一字不差（本组件不持有宿主状态，
 *   宿主内容槽原样透传，不做任何滚动 / 选中态改写）。
 */
object HalfSheetAnchors {

    /** Sheet 最大高度占屏高比例：屏幕 × 60% */
    const val MAX_HEIGHT_FRACTION: Float = 0.60f

    /** 背后阅读页暗化程度：40% */
    const val DIM_ALPHA: Float = 0.40f

    /** 弹起时阅读页整体下沉量（dp） */
    const val HOST_SINK_DP: Int = 8

    /** 拖拽低于该占屏比例视为关闭（未过阈值回弹） */
    const val DISMISS_THRESHOLD: Float = 0.15f

    /** 快速下甩关闭速度阈值（px/s，正值向下） */
    const val DISMISS_VELOCITY_PX_PER_SEC: Float = 600f

    /** 金杠横杠高度（dp）：4dp 金色横杠，不是圆角条 */
    const val GRABBER_BAR_HEIGHT_DP: Int = 4

    /** 金杠横杠宽度（dp） */
    const val GRABBER_BAR_WIDTH_DP: Int = 40

    /** 横杠两端金点半径（dp） */
    const val GRABBER_DOT_RADIUS_DP: Int = 3

    /** 把 Sheet 高度比例夹紧到 [0, 60%] */
    fun clampHeight(fraction: Float): Float =
        fraction.coerceIn(0f, MAX_HEIGHT_FRACTION)

    /**
     * 茶玻遮罩透明度：升起进度 0（落下）→ 1（全升），暗化上限 40%。
     * 下拉过程中进度回落，遮罩同步减淡，阅读页随之恢复亮度。
     */
    fun maskAlpha(progress: Float): Float =
        DIM_ALPHA * progress.coerceIn(0f, 1f)

    /** 阅读页下沉量（dp）：升起进度越大下沉越多，全升时 8dp */
    fun hostSinkDp(progress: Float): Float =
        HOST_SINK_DP * progress.coerceIn(0f, 1f)

    /**
     * 是否跟手关闭：拖出高度低于低段阈值，或下拉速度足够快。
     * @param fraction 当前 Sheet 高度占屏比例
     * @param velocityPxPerSec 垂直下拉速度（正值向下）
     */
    fun shouldDismiss(fraction: Float, velocityPxPerSec: Float): Boolean {
        val clamped = fraction.coerceIn(0f, MAX_HEIGHT_FRACTION)
        return clamped < DISMISS_THRESHOLD ||
            (clamped < MAX_HEIGHT_FRACTION && velocityPxPerSec > DISMISS_VELOCITY_PX_PER_SEC)
    }

    /** 松手后吸附到的停留比例：半屏 Sheet 唯一锚点为 60% 最大高 */
    fun restingFraction(): Float = MAX_HEIGHT_FRACTION
}

/**
 * 半屏 Sheet 两类业务形态：人物 Sheet / 字词 Sheet（切片-11 §5.2、§5.3）。
 */
enum class HalfSheetKind { PERSON, WORD }

/**
 * 分区类型。人物 Sheet 顺序固定：行为 → 同场人物 → 时间轴 → 档案 → 同名候选；
 * 字词 Sheet 顺序固定：音义 → 说文 → 用例 → 常用词。
 */
enum class HalfSheetSectionKind {
    /** ① 本段行为（一句加粗） */
    PERSON_ACTION,

    /** ② 本段同场人物（纯文本列表，每人一行，点一下切过去） */
    PERSON_CAST,

    /** ③ 本书时间轴（首次出场 → 关键事迹 → 末次） */
    PERSON_TIMELINE,

    /** ④ 档案（本名 / 字 / 号 / 封号 / 生卒 / 谱系，表格对齐） */
    PERSON_ARCHIVE,

    /** ⑤ 同名的其他 N 人 */
    PERSON_DOUBLETS,

    /** ① 拼音 + 部首 + 笔画 + 全义项 */
    WORD_PRONUNCIATION,

    /** ② 说文释文 + 反切 */
    WORD_SHUOWEN,

    /** ③ 古籍用例（≤ 3 条） */
    WORD_QUOTATIONS,

    /** ④ 以该字开头的常见词（最多 14 个） */
    WORD_WORDS;

    /** 所属业务形态 */
    val kind: HalfSheetKind
        get() = when (this) {
            PERSON_ACTION, PERSON_CAST, PERSON_TIMELINE, PERSON_ARCHIVE, PERSON_DOUBLETS ->
                HalfSheetKind.PERSON

            WORD_PRONUNCIATION, WORD_SHUOWEN, WORD_QUOTATIONS, WORD_WORDS ->
                HalfSheetKind.WORD
        }

    companion object {
        /** 人物 Sheet 固定分区顺序 */
        val personOrder: List<HalfSheetSectionKind> = listOf(
            PERSON_ACTION, PERSON_CAST, PERSON_TIMELINE, PERSON_ARCHIVE, PERSON_DOUBLETS,
        )

        /** 字词 Sheet 固定分区顺序 */
        val wordOrder: List<HalfSheetSectionKind> = listOf(
            WORD_PRONUNCIATION, WORD_SHUOWEN, WORD_QUOTATIONS, WORD_WORDS,
        )

        /** 各分区行数上限（null 表示不限） */
        fun lineLimit(kind: HalfSheetSectionKind): Int? = when (kind) {
            WORD_QUOTATIONS -> 3
            WORD_WORDS -> 14
            else -> null
        }
    }
}

/**
 * Sheet 内一个分区：标题 + 纯文本行列表。
 *
 * @param boldFirst 首行是否加粗（人物 Sheet ① 本段行为为一句加粗）
 */
data class HalfSheetSection(
    val kind: HalfSheetSectionKind,
    val title: String,
    val lines: List<String>,
    val boldFirst: Boolean = false,
)

/**
 * 半屏 Sheet 内容模型。分区顺序与行数上限由 [validate] 收口；
 * 空数据 / 离线 / 加载态由组件槽位兜底，模型本身不持有视图状态。
 */
data class HalfSheetContent(
    val kind: HalfSheetKind,
    val sections: List<HalfSheetSection>,
) {

    /** 模型自检：返回不合规说明，空列表表示通过 */
    fun validate(): List<String> = buildList {
        val order = when (kind) {
            HalfSheetKind.PERSON -> HalfSheetSectionKind.personOrder
            HalfSheetKind.WORD -> HalfSheetSectionKind.wordOrder
        }

        val seen = sections.map { it.kind }
        if (sections.isEmpty()) add("sections 不可为空")

        // 顺序固定：必须与规定顺序一致，且不重复
        if (seen != order.filter { required -> seen.contains(required) }) {
            add("分区顺序必须按 $order 排列，实际为 $seen")
        }
        if (seen.size != seen.distinct().size) add("分区不可重复")

        sections.forEach { section ->
            if (section.kind.kind != kind) {
                add("分区 ${section.kind} 不属于 $kind 形态")
            }
            val limit = HalfSheetSectionKind.lineLimit(section.kind)
            if (limit != null && section.lines.size > limit) {
                add("分区 ${section.kind} 行数 ${section.lines.size} 超过上限 $limit")
            }
            if (section.title.isBlank() && section.lines.isEmpty()) {
                add("分区 ${section.kind} 标题与内容不可同时为空")
            }
        }
    }

    companion object {
        /** 空内容兜底（组件内走 EmptyState，不允许空白态） */
        val Empty: HalfSheetContent = HalfSheetContent(HalfSheetKind.WORD, emptyList())
    }
}
