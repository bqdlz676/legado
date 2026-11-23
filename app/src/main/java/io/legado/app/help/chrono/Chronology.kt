package io.legado.app.help.chrono

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 纪年 · 纪日换算（core:engine 纯逻辑内核，UI 不得直接拼装换算结果，一律走这里）。
 *
 * 纪年：年号 → 公元（assets/era.json，西汉建元至清末宣统）；
 *      公元 → 干支年（天文纪年套 (y-4) mod 60）。
 * 纪日：公历 → 干支日；给定年月与干支，反解当月所有可能的那几天；
 *      朔 / 望 / 晦 直接落到初一 / 十五 / 月末。
 *
 * 干支日基准：1900-01-01 记作甲戌前一位；实测 1949-10-01 与 2024-01-01 均落甲子，常数取 10。
 *
 * 先秦王公纪年：assets/princes.txt（国 \t 称号 \t 即位公元年 \t 在位年数 \t 末年公元年），
 * 无资产环境下由内置种子兜住《春秋》开篇关键世系，保证对照时间轴不空白。
 *
 * 契约 §三：[anchorFromText] 扫段落的年号 / 王公 / 干支，回候选年代；
 * [timelineFor] / [timelineReport] 出同年多政权横向对照 + 干支 + 距今。
 */
object Chronology {

    /** 「距今」以运行时当前公历年为基准（规格切片05：前722 → 距今2748 = 2026+722） */
    val presentBase: Int get() = LocalDate.now().year

    private const val GAN = "甲乙丙丁戊己庚辛壬癸"
    private const val ZHI = "子丑寅卯辰巳午未申酉戌亥"

    /** 五等爵 + 王，称谓末尾的爵称字，用于「楚子 / 郑伯」这类省称拆国名 */
    private val RANK_CHARS = setOf("王", "公", "侯", "伯", "子", "男")

    /**
     * 可称爵号的已知国名/族名白名单。末尾是「子/伯/侯」还不够：「孔子」是人名，
     * 必须国名也在白名单内才拆，否则返回 null。
     */
    private val KNOWN_STATES = setOf(
        "周", "鲁", "齐", "晋", "楚", "秦", "宋", "卫", "郑", "燕", "吴", "越",
        "陈", "蔡", "曹", "滕", "许", "邾", "莒", "虞", "虢", "梁", "徐", "巴",
        "蜀", "息", "随", "纪", "邢", "谭", "遂", "邓", "唐", "黄", "江", "舒",
        "庸", "萧", "钟离", "赵", "魏", "韩", "薛", "郜", "焦", "杞"
    )

    data class Era(val name: String, val dynasty: String, val startYear: Int, val endYear: Int)

    /** 一条君主在位记录，供同年横向对照 */
    data class PrinceRow(val state: String, val title: String, val start: Int, val end: Int) {
        /** 某公元年是该国在位第几年（元年=1）；不在位返回 null */
        fun nth(year: Int): Int? = if (year in start..end) year - start + 1 else null
    }

    /** 对照时间轴一行：国 / 称号 / 在位第几年 */
    data class TimelineRow(val state: String, val title: String, val nth: Int, val start: Int, val end: Int)

    /**
     * 从正文猜出的年代。
     * 能定一年给 [year]；只能定区间给 [from]/[to]；干支 / 朔望这类弱锚定只标 [weak]=true，
     * 不给具体年份（六十年一循环，单靠干支无法定年）。
     *
     * @param year    锚定到的公元年（公元前为负，天文纪年）
     * @param from    候选区间起年（可空）
     * @param to      候选区间止年（可空）
     * @param state   命中国名（《春秋》本位鲁，可空）
     * @param weak    弱锚定（干支 / 朔望），只作兜底，不覆盖强锚点
     * @param label   命中的原文片段，如「鲁隐公元年」
     * @param confidence high 王公直书 / 公元直书；medium 年号纪年；low 孤证弱匹配
     */
    data class AnchorGuess(
        val year: Int?,
        val from: Int?,
        val to: Int?,
        val state: String?,
        val weak: Boolean = false,
        val label: String? = null,
        val confidence: String = "medium",
    )

    /**
     * 同年对照时间轴富结构：公元年 + 干支 + 距今 + 各政权在位行。
     * 契约 §三「对照时间轴，对齐 > 答案」：并列呈现同年周 / 鲁 / 齐 / 晋 / 楚…各自纪年。
     */
    data class TimelineReport(
        val year: Int,
        val ganzhi: String,
        val yearsAgo: Int,
        val rows: List<TimelineRow>,
    ) {
        /** 一行摘要标签，如「前722年（己未）· 距今约2748年」 */
        val title: String get() = yearTitle(year)
    }

    @Volatile
    private var eras: Map<String, List<Era>> = emptyMap()

    /** 国名+称号 → 即位年与在位数，如「鲁隐公」 */
    @Volatile
    private var princes: Map<String, IntArray> = seedPrincesMap()

    @Volatile
    private var princeRows: List<PrinceRow> = seedPrinceRows()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val text = context.assets.open("era.json").bufferedReader().use { it.readText() }
                val arr = JSONArray(text)
                val map = HashMap<String, MutableList<Era>>(600)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val e = Era(
                        o.optString("n"),
                        o.optString("d"),
                        o.optInt("a"),
                        o.optInt("b")
                    )
                    if (e.name.isNotEmpty()) {
                        map.getOrPut(e.name) { ArrayList(2) }.add(e)
                    }
                }
                eras = map
            }
            runCatching {
                val pm = HashMap<String, IntArray>(princes.size + 260)
                pm.putAll(princes)
                val rows = ArrayList<PrinceRow>(princeRows.size + 220)
                rows.addAll(princeRows)
                context.assets.open("princes.txt").bufferedReader().use { r ->
                    while (true) {
                        val ln = r.readLine() ?: break
                        if (ln.isBlank() || ln.startsWith("#")) continue
                        val f = ln.split('\t')
                        if (f.size < 4) continue
                        val start = f[2].toIntOrNull() ?: continue
                        val span = f[3].toIntOrNull() ?: 1
                        val key = f[0] + f[1]
                        pm[key] = intArrayOf(start, span)
                        rows.removeAll { it.state == f[0] && it.title == f[1] }
                        rows.add(PrinceRow(f[0], f[1], start, start + span - 1))
                    }
                }
                princes = pm
                princeRows = rows
            }
            loaded = true
        }
    }

    // ---------- 内置种子（无资产 / 单测兜底） ----------

    private fun seedPrinceRows(): List<PrinceRow> = listOf(
        // 东周王世系（资产表缺周王时兜底；ensureLoaded 载入同名资产行后覆盖）
        PrinceRow("周", "平王", -770, -720),
        PrinceRow("周", "桓王", -719, -697),
        PrinceRow("周", "庄王", -696, -682),
        PrinceRow("周", "釐王", -681, -677),
        PrinceRow("周", "惠王", -676, -652),
        PrinceRow("周", "襄王", -651, -619),
        PrinceRow("周", "顷王", -618, -613),
        PrinceRow("周", "匡王", -612, -607),
        PrinceRow("周", "定王", -606, -586),
        PrinceRow("周", "简王", -585, -572),
        PrinceRow("周", "灵王", -571, -545),
        PrinceRow("周", "景王", -544, -520),
        PrinceRow("周", "悼王", -520, -520),
        PrinceRow("周", "敬王", -519, -476),
        PrinceRow("周", "元王", -475, -469),
        PrinceRow("周", "贞定王", -468, -442),
        PrinceRow("周", "哀王", -441, -441),
        PrinceRow("周", "思王", -441, -441),
        PrinceRow("周", "考王", -440, -426),
        PrinceRow("周", "威烈王", -425, -402),
        PrinceRow("周", "安王", -401, -376),
        PrinceRow("周", "烈王", -375, -369),
        PrinceRow("周", "显王", -368, -321),
        PrinceRow("周", "慎靓王", -320, -315),
        PrinceRow("周", "赧王", -314, -256),
        // 《春秋》开篇：隐公元年 = 前722
        PrinceRow("鲁", "隐公", -722, -712),
        // 郑庄公 前743–前701，前722 为在位第 22 年
        PrinceRow("郑", "庄公", -743, -701),
    )

    private fun seedPrincesMap(): Map<String, IntArray> =
        seedPrinceRows().associate { row ->
            (row.state + row.title) to intArrayOf(row.start, row.end - row.start + 1)
        }

    fun eraOf(name: String): Era? = eras[name]?.firstOrNull()

    /** 同名年号可能被多个政权用过：建元、太平、永平、显德都重名 */
    fun eraCandidates(name: String): List<Era> = eras[name] ?: emptyList()

    /** 「贞观三年」→ 629 */
    fun toGregorianYear(eraName: String, nthYear: Int): Int? {
        val e = eraOf(eraName) ?: return null
        if (nthYear <= 0) return null
        val y = e.startYear + nthYear - 1
        return if (y > e.endYear) null else y
    }

    /**
     * 「鲁隐公元年」→ 前722。
     * 先秦无年号，《春秋》《左传》用王公纪年；国名省略时按鲁（《春秋》本位）兜底。
     */
    fun princeYear(stateTitle: String, nthYear: Int): Int? {
        if (nthYear <= 0) return null
        val keys = if (stateTitle.length in 2..6) {
            listOf(stateTitle, "鲁$stateTitle")
        } else emptyList()
        for (k in keys) {
            val v = princes[k] ?: continue
            if (nthYear > v[1]) continue
            return v[0] + nthYear - 1
        }
        return null
    }

    /**
     * 拆解「楚子 / 郑伯 / 齐侯」这类爵称省称：返回 国名 to 爵称字；
     * 末尾不是五等爵 / 王则返回 null。供人物消歧走「@国 → 当年在位君主」链路。
     */
    fun parseStateTitle(mention: String): Pair<String, String>? {
        if (mention.length < 2) return null
        val rank = mention.last().toString()
        if (rank !in RANK_CHARS) return null
        val state = mention.substring(0, mention.length - 1)
        if (state.isEmpty() || state !in KNOWN_STATES) return null
        return state to rank
    }

    /** 某国某年在位君主行；取不到返回 null（不猜） */
    fun rulerOf(state: String, year: Int): PrinceRow? =
        princeRows.firstOrNull { it.state == state && year in it.start..it.end }

    /** 爵称省称 → 该国当年在位君主全称，如 楚子 + 前632 → 楚成王；无在位记录返回 null */
    fun rulerOfMention(mention: String, year: Int?): PrinceRow? {
        if (year == null) return null
        val (state, _) = parseStateTitle(mention) ?: return null
        return rulerOf(state, year)
    }

    /**
     * 从一段正文猜年代（取最强一条）。
     * 顺序：全称「鲁隐公十一年」→ 简称「隐公十一年」（按鲁兜底）→ 「前六三八年」→ 年号纪年 → 干支弱锚定。
     * 猜不出就返回 null，交给上层当「年代未知」处理（不猜、不空白态）。
     */
    fun anchorFromText(text: String, knownState: String? = null): AnchorGuess? =
        anchorCandidates(text, knownState).firstOrNull()

    /**
     * 从正文拉出全部候选年代（强锚定在前，弱锚定在后）。
     * 重名年号 / 多名王公同句时，候选全部保留，由 ContextAnchor 按上下文加权选默认。
     */
    fun anchorCandidates(text: String, knownState: String? = null): List<AnchorGuess> {
        if (text.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, AnchorGuess>()

        fun add(g: AnchorGuess) {
            val key = listOfNotNull(g.year, g.from, g.to, g.state, g.label, g.weak).joinToString("|")
            out.putIfAbsent(key, g)
        }

        // 1. 全称：princes 的键就是「国+称号」，如「郑庄公二十二年」
        for (k in princes.keys) {
            val i = text.indexOf(k)
            if (i < 0) continue
            val m = NTH.find(text.substring(i + k.length)) ?: continue
            val nth = chineseNumber(m.groupValues[1]) ?: continue
            val y = princeYear(k, nth) ?: continue
            add(AnchorGuess(y, y, y, k.substring(0, 1), label = k + m.value, confidence = "high"))
        }

        // 2. 简称：称号在文里但不带国名（《春秋》以鲁为本位），如「隐公元年」
        for (k in princes.keys) {
            if (k.length < 3) continue
            val short = k.substring(1)
            val i = text.indexOf(short)
            if (i < 0) continue
            val m = NTH.find(text.substring(i + short.length)) ?: continue
            val nth = chineseNumber(m.groupValues[1]) ?: continue
            val y = princeYear(k, nth) ?: continue
            add(AnchorGuess(y, y, y, k.substring(0, 1), label = short + m.value, confidence = "high"))
        }

        // 3. 公元前直书，如「前六三八年」
        BCE.find(text)?.let { m ->
            val n = chineseNumber(m.groupValues[1])
            if (n != null) {
                val y = -n + 1
                add(AnchorGuess(y, y, y, knownState, label = m.value, confidence = "high"))
            }
        }

        // 4. 年号纪年（重名年号把全部候选政权列出，标注朝代，不默认取第一条）
        for (name in eras.keys) {
            val i = text.indexOf(name)
            if (i < 0) continue
            val m = NTH.find(text.substring(i + name.length)) ?: continue
            val nth = chineseNumber(m.groupValues[1]) ?: continue
            eraCandidates(name).forEach { e ->
                if (nth <= 0 || nth > e.endYear - e.startYear + 1) return@forEach
                val y = e.startYear + nth - 1
                if (y > e.endYear) return@forEach
                add(AnchorGuess(y, y, y, null, label = name + m.value, confidence = "medium"))
            }
        }

        // 5. 干支弱锚定：六十年一循环，单靠干支定不到具体年份，只标记弱锚
        ganzhiInText(text)?.let { gz ->
            add(AnchorGuess(null, null, null, knownState, weak = true, label = gz, confidence = "low"))
        }

        // 排序：强锚定（有 year）在前；同年份按 confidence 高→低；弱锚定沉底
        return out.values.sortedWith(
            compareBy<AnchorGuess> { if (it.year != null) 0 else 1 }
                .thenBy { confidenceRank(it.confidence) }
        )
    }

    private fun confidenceRank(c: String): Int = when (c) {
        "high" -> 0; "medium" -> 1; else -> 2
    }

    private val BCE = Regex("前([一二三四五六七八九十百零]{1,6})年")
    private val NTH = Regex("^([元一二三四五六七八九十]{1,3})年")
    private val GANZHI = Regex("[甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥]")

    /** 文本里出现的第一个干支词（如「甲子」）；没有返回 null */
    private fun ganzhiInText(text: String): String? = GANZHI.find(text)?.value

    /** 元年/一/二/三…十/十一/二十三 → Int；只收 1–999 */
    fun chineseNumber(raw: String): Int? {
        if (raw.isEmpty()) return null
        if (raw == "元") return 1
        var total = 0
        var section = 0
        var num = 0
        for (c in raw) {
            when {
                c in "零一二三四五六七八九" -> num = "零一二三四五六七八九".indexOf(c)
                c == '十' -> {
                    section += if (num == 0) 10 else num * 10
                    num = 0
                }
                c == '百' -> {
                    section += if (num == 0) 100 else num * 100
                    num = 0
                }
                else -> return null
            }
        }
        total = section + num
        return if (total in 1..999) total else null
    }

    /**
     * 同一年各政权在位对照（富结构）。
     * 按《春秋》次序排：周、鲁、齐、晋、楚、秦、宋、卫、郑、燕、吴、越，其余附后。
     */
    fun timelineRows(year: Int): List<TimelineRow> {
        val order = listOf("周", "鲁", "齐", "晋", "楚", "秦", "宋", "卫", "郑", "燕", "吴", "越")
        return princeRows
            .mapNotNull { row ->
                val nth = row.nth(year) ?: return@mapNotNull null
                TimelineRow(row.state, row.title, nth, row.start, row.end)
            }
            .sortedBy { r ->
                val i = order.indexOf(r.state)
                if (i >= 0) i else order.size + 1
            }
    }

    /**
     * 同一年各政权在位对照：鲁隐公元年 = 前722 = 周平王四十九年 = 郑庄公二十二年。
     * 兼容旧调用：返回「国+称号」 to 在位第几年。
     */
    fun timelineFor(year: Int): List<Pair<String, Int>> =
        timelineRows(year).map { it.state + it.title to it.nth }

    /** 富结构时间轴：公元年 + 干支 + 距今 + 同年各政权对照行（契约 §三） */
    fun timelineReport(year: Int): TimelineReport = TimelineReport(
        year = year,
        ganzhi = yearGanzhi(year),
        yearsAgo = yearsAgo(year),
        rows = timelineRows(year),
    )

    /** 数字 → 汉字年序，如 22 → 二十二 */
    fun chineseNumberText(n: Int): String {
        if (n <= 0) return n.toString()
        if (n == 1) return "元"
        val d = "零一二三四五六七八九"
        return when {
            n < 10 -> d[n].toString()
            n < 20 -> "十" + if (n % 10 == 0) "" else d[n % 10].toString()
            n < 100 -> d[n / 10].toString() + "十" +
                if (n % 10 == 0) "" else d[n % 10].toString()
            else -> n.toString()
        }
    }

    /** 一年的完整标签：干支 + 公元前/后 + 距今 */
    fun yearTitle(year: Int): String {
        val gz = yearGanzhi(year)
        val d = if (year <= 0) "前${1 - year}" else "公元$year"
        return "${d}年（$gz）· 距今约${yearsAgo(year)}年"
    }

    /**
     * 公元年 → 干支年。
     * 公元前 n 年（内部记 -n）先转天文纪年 -(n-1) = year + 1，再套 (y-4) mod 60；
     * 实测：前770 = 辛未，前722 = 己未，公元2024 = 甲辰。
     *
     * 注：冻结契约示例印作「前722 辛酉」，经 Wikipedia / 全知识年表 / 百科三源交叉，
     * 前722 实为己未，辛酉为前720（隐公三年）；本实现取史料值，不按契约错值硬编码。
     */
    fun yearGanzhi(year: Int): String {
        val astro = if (year <= 0) year + 1 else year
        val i = floorMod(astro - 4, 60)
        return "${GAN[i % 10]}${ZHI[i % 12]}"
    }

    /** 公历 → 干支日 */
    fun dayGanzhi(date: LocalDate): String {
        val base = LocalDate.of(1900, 1, 1).toEpochDay()
        val i = floorMod(date.toEpochDay() - base + 10, 60)
        return "${GAN[i % 10]}${ZHI[i % 12]}"
    }

    /** 「甲子」→ 0，取不到返回 null */
    fun ganzhiIndex(gz: String): Int? {
        val t = gz.trim()
        if (t.length != 2) return null
        val g = GAN.indexOf(t[0])
        val z = ZHI.indexOf(t[1])
        if (g < 0 || z < 0) return null
        for (i in 0 until 60) {
            if (i % 10 == g && i % 12 == z) return i
        }
        return null
    }

    /** 某年某月里，干支为该日的所有公历日期（一个月最多两次） */
    fun resolveDays(year: Int, month: Int, gz: String): List<LocalDate> {
        val target = ganzhiIndex(gz) ?: return emptyList()
        val first = LocalDate.of(year, month, 1)
        val len = first.lengthOfMonth()
        val out = ArrayList<LocalDate>(2)
        for (d in 1..len) {
            val date = first.withDayOfMonth(d)
            if (floorMod(date.toEpochDay() - LocalDate.of(1900, 1, 1).toEpochDay() + 10, 60) == target) {
                out.add(date)
            }
        }
        return out
    }

    /** 朔 → 初一，望 → 十五，晦 → 月末 */
    fun phaseDay(year: Int, month: Int, phase: String): LocalDate? {
        val first = LocalDate.of(year, month, 1)
        return when (phase) {
            "朔" -> first
            "望" -> first.withDayOfMonth(15)
            "晦" -> first.withDayOfMonth(first.lengthOfMonth())
            else -> null
        }
    }

    /** 距今多少年，基准为运行时当前公历年（LocalDate.now().year） */
    fun yearsAgo(year: Int): Int = presentBase - year

    /** 显示用：公元前/公元 + 干支 + 距今 */
    fun yearLabel(year: Int): String {
        val bc = if (year <= 0) "公元前${1 - year}" else "公元${year}"
        return "$bc 年（${yearGanzhi(year)}）· 距今${yearsAgo(year)}年"
    }

    /**
     * 按公元年判朝代图层（对齐规格切片 05 的 18 套图层区间）。
     * 区间互斥；空档（楚汉之际等）就近归入前一政权，不返回 null 空白态。
     */
    fun dynastyOf(year: Int): String? = when {
        year in -1046..-771 -> "西周"
        year in -770..-476 -> "春秋"
        year in -475..-222 -> "战国"
        year in -221..-202 -> "秦"
        year in -201..8 -> "西汉"
        year in 9..24 -> "新"
        year in 25..220 -> "东汉"
        year in 221..264 -> "三国"
        year in 265..316 -> "西晋"
        year in 317..419 -> "东晋/十六国"
        year in 420..580 -> "南北朝"
        year in 581..618 -> "隋"
        year in 618..906 -> "唐"
        year in 907..959 -> "五代十国"
        year in 960..1126 -> "北宋/辽"
        year in 1127..1270 -> "南宋/金/西夏"
        year in 1271..1368 -> "元"
        year in 1368..1644 -> "明"
        year in 1644..1911 -> "清"
        year >= 1912 -> "近现代"
        else -> null
    }

    private fun floorMod(a: Long, b: Int): Int {
        val m = a % b
        return if (m < 0) (m + b).toInt() else m.toInt()
    }

    private fun floorMod(a: Int, b: Int): Int {
        val m = a % b
        return if (m < 0) m + b else m
    }
}
