package io.legado.app.help.chrono

/**
 * ContextAnchor —— 纪年、字词、地名、人物四模块之间唯一的共享状态。
 *
 * 不是可选件：纪年算出的年份写这里，地名按这里选朝代图层，人物同名按这里消歧。
 * 四个模块互不调用，只读写这一个锚点。
 *
 * 锚定优先级（由高到低）：
 *   1 用户点年号气泡 → setYear（用户主动确认，最高）
 *   2 正文识别王公纪年「鲁隐公元年」→ setYear(-722)
 *   3 只有干支/朔望 → setYearRange（最弱，只作兜底）
 */
object ContextAnchor {

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

    // ---------- 1.1 接口 ----------

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

    /** 年份 → 朝代名 */
    fun getDynasty(): String? = eraOf(year ?: range?.first)

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

    /** 按公元年判时代，给地图选图层用 */
    fun eraOf(y: Int?): String? {
        val v = y ?: year ?: range?.first ?: return null
        return when {
            v in -770..-477 -> "春秋"
            v in -476..-222 -> "战国"
            v in -221..-207 -> "秦"
            v in -206..8 -> "西汉"
            v in 9..23 -> "新"
            v in 24..220 -> "东汉"
            v in 221..280 -> "三国"
            v in 281..420 -> "两晋"
            v in 421..589 -> "南北朝"
            v in 590..617 -> "隋"
            v in 618..907 -> "唐"
            v in 908..959 -> "五代"
            v in 960..1127 -> "北宋"
            v in 1128..1279 -> "南宋"
            v in 1280..1367 -> "元"
            v in 1368..1644 -> "明"
            v in 1645..1911 -> "清"
            v >= 1912 -> "近现代"
            else -> null
        }
    }

    /** 从一段正文猜年代：先年号，再王公纪年，再干支；都没把握就留空 */
    fun guessFromText(text: String, knownState: String? = null) {
        if (text.isBlank()) return
        val hit = Chronology.anchorFromText(text, knownState) ?: return
        when {
            hit.year != null -> setYear(hit.year, hit.state ?: knownState)
            hit.from != null && hit.to != null -> setYearRange(hit.from..hit.to, hit.state ?: knownState)
        }
    }
}
