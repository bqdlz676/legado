package io.legado.app.core.chronology

/**
 * 落文 · ContextAnchor —— 纪年、字词、地名、人物四模块之间唯一的共享状态，
 * 也是全 app 唯一的名称消歧入口（[resolve]）。
 *
 * 不是可选件：纪年算出的年份写这里，地名按这里选朝代图层，人物同名按这里消歧。
 * 四个模块互不调用，只读写这一个锚点。
 *
 * 锚定优先级（由高到低）：
 *  1. 用户点年号气泡 → setYear（用户主动确认，最高）
 *  2. 正文识别王公纪年「鲁隐公元年」→ setYear(-722)
 *  3. 只有干支/朔望 → setYearRange（最弱，只作兜底）
 *
 * 锁定持有：当前书 / 当前章 / 候选年代区间。
 */
object ContextAnchor {

    enum class Type {
        PLACE, PERSON, ERA, ALLUSION;

        val wire: String get() = when (this) {
            PLACE -> "place"; PERSON -> "person"; ERA -> "era"; ALLUSION -> "allusion"
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
        /** 面/点：place 用，点优先排序；其余类型默认 true 不参与该档 */
        val isPoint: Boolean = true,
        val note: String = "",
        /** 今址参考层：永不进默认命中 */
        val refLayer: Boolean = false,
    ) {
        val confidenceLabel: String get() = confidence.label
    }

    /** 当前书 */
    var bookId: String = ""

    /** 当前章 */
    var chapterId: String = ""

    /** 章节标题，如「隐公元年」 */
    var title: String = ""

    /** 候选年代区间（弱锚定，可多条） */
    var candidateYears: List<IntRange> = emptyList()

    @Volatile
    private var year: Int? = null

    @Volatile
    private var range: IntRange? = null

    /** 当前文本所属国（《左传》按鲁，但一章可能写楚事） */
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

    /** 纪年换算后写入（用户确认或正文确指） */
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

    /** 模糊年代写入 */
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

    /** 取最佳锚定年份：能定到一年才返回 */
    fun getYear(): Int? = year

    /** 取候选年代区间（弱锚定兜底用） */
    fun getRange(): IntRange? = range

    /** 年份 → 朝代图层名 */
    fun getDynasty(): String? {
        val anchorYear = year ?: range?.first ?: return null
        return Chronology.dynastyOf(anchorYear)
    }

    /** 是否已锚定年代 */
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

    /** 仅供单测：清空锚点与注册提供者 */
    fun resetForTest() {
        reset()
        personProvider = null
        allusionProvider = null
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

    /** 从一段正文猜年代：先王公/年号，再干支；都没把握就留空 */
    fun guessFromText(text: String, knownState: String? = null) {
        if (text.isBlank()) return
        val hit = Chronology.anchorFromText(text, knownState) ?: return
        when {
            hit.year != null -> setYear(hit.year, hit.state ?: knownState)
            hit.from != null && hit.to != null -> setYearRange(hit.from..hit.to, hit.state ?: knownState)
        }
    }

    // ---------- 全 app 唯一消歧入口 ----------

    /**
     * 人物 / 典故来源由各自模块在预载时注册（避免 anchor 反向依赖 relation）；
     * 未注册时这两路候选为空，地名 / 年号两路仍正常工作。
     */
    @Volatile
    var personProvider: ((String) -> List<Candidate>)? = null

    @Volatile
    var allusionProvider: ((String) -> List<Candidate>)? = null

    /**
     * 名字 → 候选 → 时空过滤(锚点年/区间) → tier 高优先 → 区间窄优先 → 点优先 → 兜底。
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

        PlaceAnchor.candidates(q).forEach { s ->
            put(
                Candidate(
                    id = s.id,
                    name = s.name,
                    type = Type.PLACE,
                    tier = s.tier,
                    start = s.start,
                    end = s.end,
                    aliases = s.aliases,
                    confidence = s.confidence,
                    source = s.source ?: "",
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

        val sorted = byKey.values.sortedWith(anchorComparator(year))

        // 默认命中：锚定年份下、落在存在区间内、非参考层、可默认的第一条；
        // 无锚点、区间内无命中、区间内全 low / 参考层时不设默认（不猜）
        val defaultIdx = if (year == null) -1 else
            sorted.indexOfFirst { inSpan(it, year) && !it.refLayer && it.confidence.canDefault }
        return sorted.mapIndexed { i, c -> c.copy(isDefault = i == defaultIdx) }
    }

    private fun inSpan(c: Candidate, year: Int): Boolean =
        c.start != null && c.end != null && year in c.start..c.end

    /** 消歧排序：时空命中 → 非参考层 → tier 高 → 区间窄 → 点优先 → 置信度高 */
    private fun anchorComparator(year: Int?): Comparator<Candidate> =
        compareBy<Candidate> { c ->
            if (year != null && c.start != null && c.end != null && year in c.start..c.end) 0 else 1
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
