package io.legado.app.ui.components

import io.legado.app.help.bubble.BubbleCandidate
import io.legado.app.help.bubble.BubbleContent
import io.legado.app.help.bubble.BubbleKind

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

    /** 茶玻遮罩毛玻璃模糊基准（dp，切片-11 §5.1：模糊 12px 基准）。 */
    const val MASK_BLUR_DP: Int = 12

    /** 弹起时长（ms，切片-11 §5.1：250ms ease-out）。 */
    const val RISE_DURATION_MS: Int = 250

    /** 遮罩淡入淡出时长（ms，切片-11 §5.1：200ms cubic-bezier(0.4,0,0.6,1)）。 */
    const val MASK_DURATION_MS: Int = 200

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
 * @param links 与 [lines] 逐行对齐的跳转 query；空串表示该行不可点。
 *              同场人物 / 同名候选点击后回传该 query 交宿主重查切换。
 */
data class HalfSheetSection(
    val kind: HalfSheetSectionKind,
    val title: String,
    val lines: List<String>,
    val boldFirst: Boolean = false,
    val links: List<String> = emptyList(),
)

/**
 * 分区兜底文案（切片-11 §5.4 / 任务兜底口径）：
 * - 无载字段一律显示「无载」，不留空白；
 * - 古籍用例为空出引导文案；以该字开头的常见词为空出引导文案。
 */
object HalfSheetFallback {
    /** 字段无载时的占位文案。 */
    const val MISSING_TEXT: String = "无载"

    /** 古籍用例为空时的引导文案。 */
    const val QUOTATION_EMPTY_GUIDE: String = "暂无古籍用例"

    /** 常见词为空时的引导文案。 */
    const val WORDS_EMPTY_GUIDE: String = "暂无常见词"
}

/**
 * 阅读页宿主状态快照（切片-11 §5.4 铁律）：
 * 弹 Sheet 前后必须逐字相等——阅读位置、滚动位置、下划线（命中高亮）集合一字不差。
 *
 * 本快照是纯数据结构：Sheet 组件只叠一层覆盖，**不读写**宿主页码 / 偏移 / 高亮集合；
 * 开合前后各取一次快照，[matches] 全等即视为未扰动宿主。可在普通 JVM 单测中断言。
 *
 * @param pageIndex 当前阅读页序号（阅读位置）
 * @param scrollOffset 页内 / 竖排滚动偏移（px）
 * @param highlightKeys 当前命中下划线 / 高亮的原文键集合
 */
data class HalfSheetHostSnapshot(
    val pageIndex: Int,
    val scrollOffset: Int,
    val highlightKeys: Set<String>,
) {
    /** 开合前后快照是否全等：页码、偏移、高亮集合三项逐一比对。 */
    fun matches(other: HalfSheetHostSnapshot): Boolean =
        pageIndex == other.pageIndex &&
            scrollOffset == other.scrollOffset &&
            highlightKeys == other.highlightKeys

    companion object {
        /** 空快照（无宿主状态可采时的兜底，不代表真实阅读位置）。 */
        val Empty: HalfSheetHostSnapshot = HalfSheetHostSnapshot(0, 0, emptySet())
    }
}

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
            if (section.links.isNotEmpty() && section.links.size != section.lines.size) {
                add("分区 ${section.kind} 跳转链接数 ${section.links.size} 与行数 ${section.lines.size} 不齐")
            }
        }
    }

    companion object {
        /** 空内容兜底（组件内走 EmptyState，不允许空白态） */
        val Empty: HalfSheetContent = HalfSheetContent(HalfSheetKind.WORD, emptyList())
    }
}

/**
 * 气泡协议 → 半屏 Sheet 分区映射器（切片-11 §5.2 人物 / §5.3 字词）。
 *
 * 铁律：本映射**只消费** [BubbleContent]（help.bubble 冻结协议），不另建第二套气泡数据模型；
 * 人物 / 字词两套固定顺序由 [HalfSheetSectionKind.personOrder] / [wordOrder] 收口，
 * 渲染层 [InkHalfSheet] 只认 [HalfSheetContent]。
 *
 * 协议缺字段的兜底口径（规格覆盖不到处，按「不空白、不编造」迂回）：
 * - 同场人物：冻结协议无同场人物字段，该分区显示「无载」，不猜人名；
 * - 常见词：协议未携带以该字起首的词表，该分区出引导文案；待 Provider 补字段后此处直接消费；
 * - 任何无载字段显示 [HalfSheetFallback.MISSING_TEXT]，不留空白态。
 */
object HalfSheetMapper {

    /**
     * 按 [BubbleContent.kind] 分派两套顺序；地名 / 纪年 / 干支 / 朔望 / 未识不在
     * §5.2 / §5.3 两套固定形态内，返回 null，由 [InkHalfSheet] 走自定义内容槽原样展示。
     */
    fun build(content: BubbleContent): HalfSheetContent? = when (content.kind) {
        BubbleKind.PERSON -> buildPerson(content)
        BubbleKind.CHAR, BubbleKind.WORD, BubbleKind.ALLUSION -> buildWord(content)
        else -> null
    }

    // ---------- §5.2 人物 Sheet ----------

    private fun buildPerson(c: BubbleContent): HalfSheetContent {
        val sections = mutableListOf<HalfSheetSection>()

        // ① 本段行为（一句加粗）：取气泡 body 各行；无载出占位。
        val actionLines = splitLoose(c.body).ifEmpty { listOf(HalfSheetFallback.MISSING_TEXT) }
        sections += HalfSheetSection(
            kind = HalfSheetSectionKind.PERSON_ACTION,
            title = "本段行为",
            lines = actionLines,
            boldFirst = true,
        )

        // ② 本段同场人物：纯文本每行一人可点；协议无同场人物字段，显示无载。
        sections += HalfSheetSection(
            kind = HalfSheetSectionKind.PERSON_CAST,
            title = "同场人物",
            lines = listOf(HalfSheetFallback.MISSING_TEXT),
        )

        // ③ 本书时间轴：生卒（首次出场 → 末次）+ 关键事迹。
        val plain = plainDetailLines(c.detail)
        val lifeLine = plain.firstOrNull { labelOf(it) == "生卒" }
        val eventLine = plain.firstOrNull { labelOf(it) == "事迹" }
        val timeline = listOfNotNull(lifeLine, eventLine)
        sections += HalfSheetSection(
            kind = HalfSheetSectionKind.PERSON_TIMELINE,
            title = "本书时间轴",
            lines = timeline.ifEmpty { listOf(HalfSheetFallback.MISSING_TEXT) },
        )

        // ④ 档案：本名 / 字 / 号 / 别称 / 生卒 / 身份 / 谱系；小传、事迹归入时间轴，不重复陈列。
        val archiveLabels = setOf("本名", "字 / 号 / 别称", "生卒", "身份", "谱系")
        val archive = plain.filter { labelOf(it) in archiveLabels }
        sections += HalfSheetSection(
            kind = HalfSheetSectionKind.PERSON_ARCHIVE,
            title = "档案",
            lines = archive.ifEmpty { listOf(HalfSheetFallback.MISSING_TEXT) },
        )

        // ⑤ 同名的其他 N 人：candidates 为空则整节隐藏；行点击回传裸 query 交宿主重查。
        if (c.candidates.isNotEmpty()) {
            sections += HalfSheetSection(
                kind = HalfSheetSectionKind.PERSON_DOUBLETS,
                title = "同名的其他 ${c.candidates.size} 人 ›",
                lines = c.candidates.map(BubbleCandidate::display),
                links = c.candidates.map(BubbleCandidate::query),
            )
        }

        return HalfSheetContent(HalfSheetKind.PERSON, sections)
    }

    // ---------- §5.3 字词 Sheet ----------

    private fun buildWord(c: BubbleContent): HalfSheetContent {
        val blocks = taggedBlocks(c.detail)
        val sections = mutableListOf<HalfSheetSection>()

        // ① 拼音 + 部首 + 笔画 + 全义项：拼音栏 + body 散行 +【义项】块；
        // 无标签的纯文本 detail（普通多字词释义）一并归入本节，不丢行。
        val pronunciation = buildList {
            if (!c.phonetic.isNullOrBlank()) add(c.phonetic.trim())
            addAll(splitLoose(c.body))
            addAll(blocks["义项"].orEmpty())
            addAll(blocks[""].orEmpty())
        }
        sections.addWordSection(
            kind = HalfSheetSectionKind.WORD_PRONUNCIATION,
            title = "拼音 · 部首 · 笔画 · 义项",
            lines = pronunciation,
        )

        // ② 说文释文 + 反切：【说文】块；无载出占位。
        sections.addWordSection(
            kind = HalfSheetSectionKind.WORD_SHUOWEN,
            title = "说文释文 · 反切",
            lines = blocks["说文"].orEmpty(),
            missing = HalfSheetFallback.MISSING_TEXT,
        )

        // ③ 古籍用例：≤ 3 条；为空出引导文案，不塞假用例。
        val quotations = blocks["用例"].orEmpty().take(
            HalfSheetSectionKind.lineLimit(HalfSheetSectionKind.WORD_QUOTATIONS) ?: Int.MAX_VALUE,
        )
        sections.addWordSection(
            kind = HalfSheetSectionKind.WORD_QUOTATIONS,
            title = "古籍用例",
            lines = quotations,
            missing = HalfSheetFallback.QUOTATION_EMPTY_GUIDE,
        )

        // ④ 以该字开头的常见词（≤ 14）：协议未携带词表，出引导文案。
        sections.addWordSection(
            kind = HalfSheetSectionKind.WORD_WORDS,
            title = "常见词",
            lines = emptyList(),
            missing = HalfSheetFallback.WORDS_EMPTY_GUIDE,
        )

        return HalfSheetContent(HalfSheetKind.WORD, sections)
    }

    /** 向可变分区列表追加一节，空行按 [missing] 兜底；行数上限由模型自检收口。 */
    private fun MutableList<HalfSheetSection>.addWordSection(
        kind: HalfSheetSectionKind,
        title: String,
        lines: List<String>,
        missing: String = HalfSheetFallback.MISSING_TEXT,
    ) {
        this += HalfSheetSection(
            kind = kind,
            title = title,
            lines = lines.ifEmpty { listOf(missing) },
        )
    }

    // ---------- 文本拆解 ----------

    /** 按换行 / 全角空格 / 半角空格切散行，去空白与空串。 */
    private fun splitLoose(text: String): List<String> =
        text.split('\n', '　', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** 人物档案散行：去掉空行；保留「标签　值」原样，标签由 [labelOf] 提取。 */
    private fun plainDetailLines(detail: String): List<String> =
        detail.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    /**
     * 提取「标签　值」行的标签前缀；优先按全角空格切分（标签自身可能含半角空格，
     * 如「字 / 号 / 别称」），无全角空格时再回退半角空格；无分隔符返回空串。
     */
    private fun labelOf(line: String): String {
        val full = line.indexOf('　')
        val idx = if (full >= 0) full else line.indexOf(' ')
        return if (idx in 1 until line.length) line.substring(0, idx) else ""
    }

    /**
     * 解析【标签】块：块之间以空行分隔；块首标签剥除后其余行归入该标签。
     * 无标签块归入空串键。返回顺序与原文出现顺序一致。
     */
    private fun taggedBlocks(detail: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        detail.split("\n\n").forEach { raw ->
            val block = raw.trim()
            if (block.isEmpty()) return@forEach
            val match = Regex("^【(.+?)】").find(block)
            val tag = match?.groupValues?.get(1).orEmpty()
            val body = if (match != null) block.removeRange(match.range) else block
            val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            out.getOrPut(tag) { mutableListOf() }.addAll(lines)
        }
        return out
    }
}
