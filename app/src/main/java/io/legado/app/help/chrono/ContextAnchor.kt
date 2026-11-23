package io.legado.app.help.chrono

/**
 * ContextAnchor —— 纪年、字词、地名、人物四模块之间唯一的共享状态，
 * 也是全 app 唯一的名称消歧入口（[resolve]）。
 *
 * 为什么必须是单点：纪年算出的年份写这里，地名按这里选朝代图层，人物同名按这里消歧。
 * 四个模块互不调用，只读写这一个锚点；锚点不持 UI，不订阅生命周期，纯逻辑可单测。
 *
 * 锚定优先级（由高到低）：
 *   1 用户点年号气泡 → setYear（用户主动确认，最高）
 *   2 正文识别王公纪年「鲁隐公元年」→ setYear(-722)
 *   3 只有干支/朔望 → setYearRange（最弱，只作兜底）
 *
 * 三源交叉置信度（锁定规则）：
 *  - 3/3 三源一致 → HIGH，默认命中；
 *  - 2/3 两源一致 → MEDIUM，标「待校」；
 *  - ≤1/3 单源或来源冲突 → LOW，标「待考·来源冲突」，不进默认命中；
 *  - 今址参考层（[Candidate.refLayer]）恒 LOW，永不默认命中。
 */
object ContextAnchor {

    // ---------- 消歧候选模型（契约 §一） ----------

    enum class Type {
        PLACE, PERSON, ERA, ALLUSION;

        val wire: String get() = when (this) {
            PLACE -> "place"; PERSON -> "person"; ERA -> "era"; ALLUSION -> "allusion"
        }
    }

    enum class Confidence(
        val wire: String,
        /** 展示标签：high 不标；medium 标「待校」；low 标「待考·来源冲突」。 */
        val label: String,
        /** 是否允许进默认命中：只有 high / medium 可以；low 一律不进。 */
        val canDefault: Boolean,
    ) {
        HIGH("high", "", true),
        MEDIUM("medium", "待校", true),
        LOW("low", "待考·来源冲突", false);

        companion object {
            fun of(raw: String?): Confidence = when (raw?.trim()?.lowercase()) {
                "high" -> HIGH; "medium" -> MEDIUM; else -> LOW
            }

            /**
             * 三源交叉打分。
             * @param channels 该实体汇集到的去重学术渠道（实体行 + 名称快照行）
             * @param refLayer 是否今址参考层
             * @param tier 坐标层级 1 实测 / 2 考证 / 3 推定（tier1 遗址点按三源一致处理）
             */
            fun grade(channels: Set<SourceChannel>, refLayer: Boolean, tier: Int): Confidence {
                if (refLayer || SourceChannel.REFERENCE in channels) return LOW
                val scholarly = channels - SourceChannel.REFERENCE
                return when {
                    // tier1 遗址实测点按三源一致处理；其余按学术渠道交叉数：3/3 HIGH，2/3 MEDIUM，≤1/3 LOW
                    tier == 1 -> HIGH
                    scholarly.size >= 3 -> HIGH
                    scholarly.size == 2 -> MEDIUM
                    else -> LOW
                }
            }
        }
    }

    data class Candidate(
        val id: String,
        val name: String,
        val type: Type,
        val tier: Int,
        val start: Int?,
        val end: Int?,
        val aliases: List<String>,
        val confidence: Confidence,
        val source: String,
        val isDefault: Boolean = false,
        /** 面/点：place 用，点优先排序；其余类型默认 true 不参与该档。 */
        val isPoint: Boolean = true,
        val note: String = "",
        /** 今址参考层：永不进默认命中。 */
        val refLayer: Boolean = false,
    ) {
        val confidenceLabel: String get() = confidence.label
    }

    /** 当前书。 */
    var bookId: String = ""

    /** 当前章。 */
    var chapterId: String = ""

    /** 章节标题，如「隐公元年」。 */
    var title: String = ""

    /** 候选年代区间（弱锚定，可多条）。 */
    var candidateYears: List<IntRange> = emptyList()

    @Volatile
    private var year: Int? = null

    @Volatile
    private var range: IntRange? = null

    /** 当前文本所属国（《左传》按鲁，但一章可能写楚事）。 */
    @Volatile
    var state: String? = null
        private set

    @Volatile
    private var listener: (() -> Unit)? = null

    fun observe(fn: () -> Unit) {
        listener = fn
    }

    private fun notifyChanged() {
        listener?.invoke()
    }

    // ---------- 锚点写入 ----------

    /** 纪年换算后写入（用户确认或正文确指）。 */
    fun setYear(year: Int) {
        setYear(year, state)
    }

    fun setYear(year: Int, st: String?) {
        this.year = year
        this.range = year..year
        this.state = st
        candidateYears = listOf(year..year)
        notifyChanged()
    }

    /** 模糊年代写入。 */
    fun setYearRange(range: IntRange) {
        setYearRange(range, state)
    }

    fun setYearRange(range: IntRange, st: String?) {
        this.year = if (range.first == range.last) range.first else null
        this.range = range
        this.state = st
        candidateYears = listOf(range)
        notifyChanged()
    }

    /** 取最佳锚定年份：能定到一年才返回。 */
    fun getYear(): Int? = year

    /** 取候选年代区间（弱锚定兜底用）。 */
    fun getRange(): IntRange? = range

    /** 年份 → 朝代名。 */
    fun getDynasty(): String? = eraOf(year ?: range?.first)

    /**
     * 当前文本所处年代的展示标签（人物详情 / 关系图路由透传用）：
     * 精确年给「朝代 · 公元年（干支）· 距今」；只能定区间时给朝代 + 区间两端；
     * 无锚点返回 null，不猜年份。Action 枚举签名冻结，年代经本共享总线在触发点携带。
     */
    fun eraLabel(): String? {
        val y = year
        if (y != null) {
            return listOfNotNull(eraOf(y), Chronology.yearLabel(y)).joinToString(" · ")
        }
        val r = range
        if (r != null) {
            val span = if (r.first == r.last) fmtYearText(r.first)
            else "${fmtYearText(r.first)} — ${fmtYearText(r.last)}"
            return listOfNotNull(eraOf(r.first), span).joinToString(" · ")
        }
        return null
    }

    private fun fmtYearText(y: Int): String =
        if (y <= 0) "公元前${-y}" else "公元$y"

    /** 是否已锚定年代。 */
    fun hasAnchor(): Boolean = year != null || range != null

    fun reset() {
        bookId = ""
        chapterId = ""
        title = ""
        candidateYears = emptyList()
        year = null
        range = null
        state = null
        notifyChanged()
    }

    // ---------- 兼容旧调用（内部一律转新接口） ----------

    data class State(
        val bookName: String? = null,
        val chapterTitle: String? = null,
        val year: Int? = null,
        val fromYear: Int? = null,
        val toYear: Int? = null,
        val state: String? = null,
    ) {
        val range: IntRange? get() = when {
            year != null -> year..year
            fromYear != null && toYear != null -> fromYear..toYear
            else -> null
        }
    }

    fun get(): State = State(
        bookName = bookId.ifBlank { null },
        chapterTitle = title.ifBlank { null },
        year = year,
        fromYear = range?.first,
        toYear = range?.last,
        state = state
    )

    fun clear() = reset()

    fun setChapter(bookName: String?, chapterTitle: String?) {
        bookId = bookName.orEmpty()
        title = chapterTitle.orEmpty()
        notifyChanged()
    }

    fun setRange(from: Int?, to: Int?) {
        if (from == null || to == null) {
            year = null
            range = null
        } else {
            setYearRange(from..to, state)
        }
        notifyChanged()
    }

    fun contains(y: Int?): Boolean? {
        if (y == null) return null
        val r = range ?: return null
        return y in r
    }

    /** 按公元年判时代，给地图选图层用。 */
    fun eraOf(y: Int?): String? {
        val v = y ?: year ?: range?.first ?: return null
        return Chronology.dynastyOf(v)
    }

    /** 从一段正文猜年代：先王公/年号，再人物/地名旁证，再干支；都没把握就留空。 */
    fun guessFromText(text: String, knownState: String? = null) {
        if (text.isBlank()) return
        val hit = Chronology.anchorFromText(text, knownState) ?: return
        when {
            hit.year != null -> setYear(hit.year, hit.state ?: knownState)
            hit.from != null && hit.to != null -> setYearRange(hit.from..hit.to, hit.state ?: knownState)
        }
    }

    // ---------- §一 全 app 唯一消歧入口 ----------

    /**
     * 人物 / 典故来源由各自模块在预载时注册（避免 anchor 反向依赖人物档案层）；
     * 未注册时这两路候选为空，地名 / 年号两路仍正常工作。
     */
    @Volatile
    var personProvider: ((String) -> List<Candidate>)? = null

    @Volatile
    var allusionProvider: ((String) -> List<Candidate>)? = null

    /**
     * 名字 → 候选 → 时空过滤(锚点年) → tier 高优先 → 区间窄优先 → 点优先 → 兜底。
     *
     * - 锚点年为空：不猜，返回全部候选按 tier 排序，全部 `isDefault=false`。
     * - low 置信度候选照收，但 `isDefault=false`，不进默认命中。
     * - 今址参考层候选照收，但 `isDefault=false`，排在最后。
     * - 锚点年下无任何候选落在其在位/存在区间时，保留全部候选作兜底（出区间者排序靠后）。
     */
    fun resolve(name: String, year: Int? = getYear()): List<Candidate> {
        val q = name.trim()
        if (q.isEmpty()) return emptyList()

        val byKey = LinkedHashMap<String, Candidate>()
        fun put(c: Candidate) {
            val key = c.type.wire + "#" + c.id
            byKey.putIfAbsent(key, c)
        }

        ChronoPlaces.candidates(q).forEach { s ->
            put(
                Candidate(
                    id = s.id,
                    name = s.name,
                    type = Type.PLACE,
                    tier = s.tier,
                    start = s.start,
                    end = s.end,
                    aliases = s.aliases,
                    confidence = s.confidenceTag,
                    source = s.source,
                    isPoint = s.isPoint,
                    note = s.note,
                    refLayer = s.refLayer,
                )
            )
        }
        Chronology.eraCandidates(q).forEach { e ->
            put(
                Candidate(
                    id = "era:" + e.name + "@" + e.dynasty,
                    name = e.name,
                    type = Type.ERA,
                    tier = 0,
                    start = e.startYear,
                    end = e.endYear,
                    aliases = emptyList(),
                    confidence = Confidence.MEDIUM,
                    source = e.dynasty,
                )
            )
        }
        personProvider?.invoke(q)?.forEach(::put)
        allusionProvider?.invoke(q)?.forEach(::put)

        // 固定年优先；只有区间锚定时用区间相交参与排序与默认判定。
        val effYear = year ?: this.year
        val effRange = range
        val sorted = byKey.values.sortedWith(anchorComparator(effYear, effRange))

        // 默认命中：锚定年份 / 区间内、非参考层、可默认的第一条；
        // 无锚点、区间内无命中、区间内全 low / 参考层时不设默认（不猜）
        val defaultIdx = if (effYear == null && effRange == null) -1 else
            sorted.indexOfFirst { inAnchor(it, effYear, effRange) && !it.refLayer && it.confidence.canDefault }
        return sorted.mapIndexed { i, c -> c.copy(isDefault = i == defaultIdx) }
    }

    private fun inSpan(c: Candidate, year: Int): Boolean =
        c.start != null && c.end != null && year in c.start..c.end

    private fun inRange(c: Candidate, range: IntRange): Boolean =
        c.start != null && c.end != null && c.start <= range.last && c.end >= range.first

    private fun inAnchor(c: Candidate, year: Int?, range: IntRange?): Boolean =
        (year != null && inSpan(c, year)) || (range != null && inRange(c, range))

    /** 消歧排序：时空命中（年精确 > 区间相交）→ 非参考层 → tier 高 → 区间窄 → 点优先 → 置信度高。 */
    private fun anchorComparator(year: Int?, range: IntRange?): Comparator<Candidate> =
        compareBy<Candidate> { c ->
            when {
                year != null && c.start != null && c.end != null && year in c.start..c.end -> 0
                range != null && inRange(c, range) -> 1
                else -> 2
            }
        }.thenBy { if (it.refLayer) 1 else 0 }
            .thenBy { it.tier }
            .thenBy { c ->
                val s = c.start; val e = c.end
                if (s != null && e != null) e - s else Int.MAX_VALUE
            }.thenBy { if (it.isPoint) 0 else 1 }
            .thenBy { confidenceRank(it.confidence) }

    private fun confidenceRank(c: Confidence): Int = when (c) {
        Confidence.HIGH -> 0; Confidence.MEDIUM -> 1; Confidence.LOW -> 2
    }
}
