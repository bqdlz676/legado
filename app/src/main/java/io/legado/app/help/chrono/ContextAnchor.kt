package io.legado.app.help.chrono

/**
 * 时空锚点：全书唯一的总线。
 *
 * 纪年、地名、人物、地图四个模块互不调用，只读写这里的一个状态：
 *   - 纪年页选中某年 → setYear
 *   - 正文扫到「鲁隐公元年」「城濮之战」→ guessFromText 写入候选区间
 *   - 地名消歧按年份筛同名 → 读 year/range
 *   - 地图按 year 选朝代图层 → 读 era
 *   - 人物卡片算「此年几岁」→ 读 year
 *
 * 只要锚点为空，所有下游退化成「同名候选全列 + 不判年代」，不猜。
 */
object ContextAnchor {

    data class State(
        val bookName: String? = null,
        val chapterTitle: String? = null,
        /** 精确年份，能定到一年就写这个 */
        val year: Int? = null,
        /** 只能定到区间（如「春秋」）时用这个，闭区间 */
        val fromYear: Int? = null,
        val toYear: Int? = null,
        /** 当前文本所属国（《左传》按鲁，但一章可能写楚事） */
        val state: String? = null,
    ) {
        val anchorYear: Int? get() = year
        val range: IntRange? get() = when {
            year != null -> year..year
            fromYear != null && toYear != null -> fromYear..toYear
            else -> null
        }
    }

    @Volatile
    private var cur = State()

    fun get(): State = cur

    @Volatile
    private var listener: (() -> Unit)? = null

    fun observe(fn: () -> Unit) {
        listener = fn
    }

    private fun notifyChanged() {
        listener?.invoke()
    }

    fun clear() {
        cur = State()
        notifyChanged()
    }

    fun setChapter(bookName: String?, chapterTitle: String?) {
        cur = cur.copy(bookName = bookName, chapterTitle = chapterTitle)
        notifyChanged()
    }

    fun setYear(year: Int?, state: String? = cur.state) {
        cur = cur.copy(year = year, fromYear = null, toYear = null, state = state)
        notifyChanged()
    }

    fun setRange(from: Int?, to: Int?, state: String? = cur.state) {
        cur = if (from == null || to == null) {
            cur.copy(year = null, fromYear = null, toYear = null, state = state)
        } else {
            cur.copy(
                year = if (from == to) from else null,
                fromYear = from, toYear = to, state = state
            )
        }
        notifyChanged()
    }

    /** 某年是否落在锚点内；锚点为空时返回 null（表示「不知道」，不是「不符合」） */
    fun contains(year: Int?): Boolean? {
        if (year == null) return null
        val r = cur.range ?: return null
        return year in r
    }

    /** 按公元年判时代，给地图选图层用 */
    fun eraOf(year: Int?): String? {
        val y = year ?: cur.year ?: return null
        return when {
            y in -770..-477 -> "春秋"
            y in -476..-222 -> "战国"
            y in -221..-207 -> "秦"
            y in -206..8 -> "西汉"
            y in 9..23 -> "新"
            y in 24..220 -> "东汉"
            y in 221..280 -> "三国"
            y in 281..420 -> "两晋"
            y in 421..589 -> "南北朝"
            y in 590..617 -> "隋"
            y in 618..907 -> "唐"
            y in 908..959 -> "五代"
            y in 960..1127 -> "北宋"
            y in 1128..1279 -> "南宋"
            y in 1280..1367 -> "元"
            y in 1368..1644 -> "明"
            y in 1645..1911 -> "清"
            y >= 1912 -> "近现代"
            else -> null
        }
    }

    /** 从一段正文猜年代：先年号，再王公纪年，再干支；都没把握就留空 */
    fun guessFromText(text: String, knownState: String? = null) {
        if (text.isBlank()) return
        val hit = Chronology.anchorFromText(text, knownState) ?: return
        when {
            hit.year != null -> setYear(hit.year, hit.state)
            hit.from != null && hit.to != null -> setRange(hit.from, hit.to, hit.state)
        }
    }
}
