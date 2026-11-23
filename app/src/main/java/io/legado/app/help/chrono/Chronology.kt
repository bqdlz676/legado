package io.legado.app.help.chrono

import io.legado.app.help.person.PersonProfile
import io.legado.app.help.place.PlaceDict
import java.time.LocalDate

/**
 * 纪年 · 干支 · 朝代图层 · 同年多政权对照（C1 时空锚定引擎，纯逻辑内核）。
 *
 * 职责边界：本类只做「文本 → 候选年代 / 公元年 → 干支·距今·朝代·同年对照」的纯计算，
 * 不碰 UI、不碰 Compose、不读 Android 资源；人物朝代与地名年代作为旁证区间并入候选，
 * 强锚定（王公直书 / 公元直书）优先，弱锚定（干支）只兜底不定年。
 *
 * 契约 §2 冻结 API（签名不许改）：
 * - [anchorFromText] 扫段落的王公纪年 / 年号 / 公元直书 / 人物 / 地名 / 干支，回候选年代；
 * - [timelineFor] 出同年多政权对照；[timelineReport] 富结构对照 + 干支 + 距今。
 *
 * 口径锁定：
 * - 距今基准 1950（考古学通用口径，不随运行时当前年漂移）；
 * - 干支按天文纪年公式：公元前 n 年先转天文纪年 -(n-1)，再套 (y-4) mod 60；
 *   前722 = 己未（规格示例印作「辛酉」系前720，笔误不改原文，实现按史料值输出）；
 * - 18 朝代区间与 D3 [io.legado.app.help.place.PlaceLayers] 同名同界，本类做互斥单标签划分。
 */
object Chronology {

    /** 「距今」冻结基准年（考古学口径）：前722 → 1950 - (-722) = 2672。 */
    const val PRESENT_BASE = 1950

    private const val GAN = "甲乙丙丁戊己庚辛壬癸"
    private const val ZHI = "子丑寅卯辰巳午未申酉戌亥"

    /** 五等爵 + 王，称谓末尾的爵称字，用于「楚子 / 郑伯」省称拆国名。 */
    private val RANK_CHARS = setOf("王", "公", "侯", "伯", "子", "男")

    /**
     * 可称爵号的已知国名白名单。末尾是「子/伯/侯」还不够：「孔子」是人名，
     * 必须国名也在白名单内才拆，否则返回 null。
     */
    private val KNOWN_STATES = setOf(
        "周", "鲁", "齐", "晋", "楚", "秦", "宋", "卫", "郑", "燕", "吴", "越",
        "陈", "蔡", "曹", "滕", "许", "邾", "莒", "虞", "虢", "梁", "徐", "巴",
        "蜀", "息", "随", "纪", "邢", "谭", "遂", "邓", "唐", "黄", "江", "舒",
        "庸", "萧", "钟离", "赵", "魏", "韩", "薛", "郜", "焦", "杞"
    )

    data class Era(val name: String, val dynasty: String, val startYear: Int, val endYear: Int)

    /** 一条君主在位记录，供同年横向对照。 */
    data class PrinceRow(val state: String, val title: String, val start: Int, val end: Int) {
        /** 某公元年是该国在位第几年（元年=1）；不在位返回 null。 */
        fun nth(year: Int): Int? = if (year in start..end) year - start + 1 else null
    }

    /** 对照时间轴一行：国 / 称号 / 在位第几年 / 在位起止。 */
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
     * @param confidence high 王公直书 / 公元直书；medium 年号 / 人物生卒 / 地名年代；low 孤证弱匹配
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

    /** 同年对照时间轴富结构：公元年 + 干支 + 距今 + 各政权在位行。 */
    data class TimelineReport(
        val year: Int,
        val ganzhi: String,
        val yearsAgo: Int,
        val rows: List<TimelineRow>,
    ) {
        /** 一行摘要标签，如「前722年（己未）· 距今约2672年」。 */
        val title: String get() = yearTitle(year)
    }

    /**
     * 人物称呼目录注入点：C2 气泡层 / 预载阶段把 D2 全部可称呼名注册进来，
     * 正文扫描人物生卒区间时用。为什么不直接遍历 D2 内部表：C1 只允许走 D2 冻结门面，
     * 目录枚举属于预载期职责，由上游注入，避免反向依赖内部实现。
     */
    @Volatile
    var personNameProvider: (() -> List<String>)? = null

    // ---------- 内置种子（纯 Kotlin，无资产依赖；无资产 / 单测环境兜底不空白） ----------

    private val princeRows: List<PrinceRow> = seedPrinceRows()

    private val princes: Map<String, IntArray> = princeRows.associate { row ->
        (row.state + row.title) to intArrayOf(row.start, row.end - row.start + 1)
    }

    private val eras: Map<String, List<Era>> = seedEras().groupBy { it.name }

    private fun seedPrinceRows(): List<PrinceRow> = listOf(
        // 东周王世系：同年对照时间轴的周王行；资产表将来可按 state+title 覆盖
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

    /** 内置年号种子：覆盖秦汉至清常见年号；重名年号多条并列，不默认取第一条。 */
    private fun seedEras(): List<Era> = listOf(
        Era("建元", "西汉", -140, -135),
        Era("元光", "西汉", -134, -129),
        Era("元朔", "西汉", -128, -123),
        Era("元狩", "西汉", -122, -117),
        Era("元鼎", "西汉", -116, -111),
        Era("元封", "西汉", -110, -105),
        Era("太初", "西汉", -104, -101),
        Era("天汉", "西汉", -100, -97),
        Era("建安", "东汉", 196, 220),
        Era("黄初", "三国·魏", 220, 226),
        Era("太和", "三国·魏", 227, 233),
        Era("贞观", "唐", 627, 649),
        Era("开元", "唐", 713, 741),
        Era("天宝", "唐", 742, 756),
        Era("至德", "唐", 756, 758),
        Era("洪武", "明", 1368, 1398),
        Era("永乐", "明", 1403, 1424),
        Era("康熙", "清", 1662, 1722),
        Era("雍正", "清", 1723, 1735),
        Era("乾隆", "清", 1736, 1795),
        Era("嘉庆", "清", 1796, 1820),
        Era("道光", "清", 1821, 1850),
        Era("咸丰", "清", 1851, 1861),
        Era("同治", "清", 1862, 1874),
        Era("光绪", "清", 1875, 1908),
        Era("宣统", "清", 1909, 1911),
    )

    /**
     * 兼容旧调用入口：资产预载钩子。新实现为纯 Kotlin 内置种子，无需读 assets，
     * 保留签名是为了不破坏 App 启动期的预载调用；方法体为空操作。
     */
    fun ensureLoaded(context: Any?) = Unit

    fun eraOf(name: String): Era? = eras[name]?.firstOrNull()

    /** 同名年号可能被多个政权用过：建元、太平、永平、显德都重名。 */
    fun eraCandidates(name: String): List<Era> = eras[name] ?: emptyList()

    /** 「贞观三年」→ 629。 */
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
        val keys = if (stateTitle.length in 2..6) listOf(stateTitle, "鲁$stateTitle") else emptyList()
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

    /** 某国某年在位君主行；取不到返回 null（不猜）。 */
    fun rulerOf(state: String, year: Int): PrinceRow? =
        princeRows.firstOrNull { it.state == state && year in it.start..it.end }

    /** 爵称省称 → 该国当年在位君主全称，如 楚子 + 前632 → 楚成王；无在位记录返回 null。 */
    fun rulerOfMention(mention: String, year: Int?): PrinceRow? {
        if (year == null) return null
        val (state, _) = parseStateTitle(mention) ?: return null
        return rulerOf(state, year)
    }

    // ---------- 候选年代 ----------

    /** 从一段正文猜年代（取最强一条）；猜不出返回 null，交上层当「年代未知」处理，不猜不空白。 */
    fun anchorFromText(text: String, knownState: String? = null): AnchorGuess? =
        anchorCandidates(text, knownState).firstOrNull()

    /**
     * 从正文拉出全部候选年代（强锚定在前，弱锚定在后）。
     * 重名年号 / 多名王公同句时候选全部保留，由 ContextAnchor 按上下文加权选默认。
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

        // 5. 地名年代旁证：正文出现 D3 在册地名 / 别名时，取其存在区间作候选区间。
        placeAnchorCandidates(text).forEach(::add)

        // 6. 人物生卒旁证：注入目录后，正文出现人物称呼时按生卒年拉区间。
        personAnchorCandidates(text).forEach(::add)

        // 7. 干支弱锚定：六十年一循环，单靠干支定不到具体年份，只标记弱锚。
        ganzhiInText(text)?.let { gz ->
            add(AnchorGuess(null, null, null, knownState, weak = true, label = gz, confidence = "low"))
        }

        return out.values.sortedWith(
            compareBy<AnchorGuess> { if (it.year != null) 0 else 1 }
                .thenBy { confidenceRank(it.confidence) }
                .thenBy { if (it.weak) 1 else 0 }
        )
    }

    private fun confidenceRank(c: String): Int = when (c) {
        "high" -> 0; "medium" -> 1; else -> 2
    }

    /** 人物称呼 → 生卒区间候选；生卒俱缺返回 null。 */
    fun personAnchor(name: String): AnchorGuess? {
        val span = PersonProfile.lifeSpan(name) ?: return null
        val (b, d) = span
        if (b == null && d == null) return null
        return AnchorGuess(
            year = null, from = b, to = d, state = null,
            label = name, confidence = "medium",
        )
    }

    /** 地名 → 存在区间候选（取非参考层候选的并集区间）；查无返回 null。 */
    fun placeAnchor(name: String): AnchorGuess? {
        val entries = PlaceDict.find(name).filter { !it.refLayer }
        if (entries.isEmpty()) return null
        val from = entries.minOf { it.fromYear }
        val to = entries.maxOf { it.toYear }
        return AnchorGuess(year = null, from = from, to = to, state = null, label = name, confidence = "medium")
    }

    private fun placeAnchorCandidates(text: String): List<AnchorGuess> {
        val tokens = PlaceDict.all()
            .flatMap { listOf(it.name) + it.aliases }
            .filter { it.length >= 2 && text.contains(it) }
            .distinct()
            .sortedByDescending { it.length }
        return tokens.mapNotNull { placeAnchor(it) }
    }

    private fun personAnchorCandidates(text: String): List<AnchorGuess> {
        val names = personNameProvider?.invoke() ?: return emptyList()
        return names.filter { it.length >= 2 && text.contains(it) }
            .distinct()
            .sortedByDescending { it.length }
            .mapNotNull { personAnchor(it) }
    }

    private val BCE = Regex("前([一二三四五六七八九十百零]{1,6})年")
    private val NTH = Regex("^([元一二三四五六七八九十]{1,3})年")
    private val GANZHI = Regex("[甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥]")

    /** 文本里出现的第一个干支词（如「甲子」）；没有返回 null。 */
    private fun ganzhiInText(text: String): String? = GANZHI.find(text)?.value

    /** 元年/一/二/三…十/十一/二十三 → Int；只收 1–999。 */
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

    // ---------- 同年多政权对照 ----------

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

    /** 富结构时间轴：公元年 + 干支 + 距今 + 同年各政权对照行。 */
    fun timelineReport(year: Int): TimelineReport = TimelineReport(
        year = year,
        ganzhi = yearGanzhi(year),
        yearsAgo = yearsAgo(year),
        rows = timelineRows(year),
    )

    /** 数字 → 汉字年序，如 22 → 二十二。 */
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

    /** 一年的完整标签：干支 + 公元前/后 + 距今。 */
    fun yearTitle(year: Int): String {
        val gz = yearGanzhi(year)
        // 内部纪年公元前记为 -n（如前722 存 -722），展示年数取 -year，勿再减 1。
        val d = if (year <= 0) "前${-year}" else "公元$year"
        return "${d}年（$gz）· 距今约${yearsAgo(year)}年"
    }

    /**
     * 公元年 → 干支年（天文公式）。
     * 公元前 n 年（内部记 -n）先转天文纪年 -(n-1) = year + 1，再套 (y-4) mod 60；
     * 实测：前770 = 辛未，前722 = 己未，公元2024 = 甲辰，公元1年 = 辛酉。
     *
     * 注：冻结契约示例印作「前722 辛酉」，经年表三源交叉，前722 实为己未，
     * 辛酉为前720（隐公三年）；本实现取史料值，不按契约错值硬编码。
     */
    fun yearGanzhi(year: Int): String {
        val astro = if (year <= 0) year + 1 else year
        val i = floorMod(astro - 4, 60)
        return "${GAN[i % 10]}${ZHI[i % 12]}"
    }

    /** 公历 → 干支日。 */
    fun dayGanzhi(date: LocalDate): String {
        val base = LocalDate.of(1900, 1, 1).toEpochDay()
        val i = floorMod(date.toEpochDay() - base + 10, 60)
        return "${GAN[i % 10]}${ZHI[i % 12]}"
    }

    /** 「甲子」→ 0，取不到返回 null。 */
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

    /** 某年某月里，干支为该日的所有公历日期（一个月最多两次）。 */
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

    /** 朔 → 初一，望 → 十五，晦 → 月末。 */
    fun phaseDay(year: Int, month: Int, phase: String): LocalDate? {
        val first = LocalDate.of(year, month, 1)
        return when (phase) {
            "朔" -> first
            "望" -> first.withDayOfMonth(15)
            "晦" -> first.withDayOfMonth(first.lengthOfMonth())
            else -> null
        }
    }

    /** 距今多少年，基准 1950（考古学通用口径）。 */
    fun yearsAgo(year: Int): Int = PRESENT_BASE - year

    /** 显示用：公元前/公元 + 干支 + 距今。 */
    fun yearLabel(year: Int): String {
        val bc = if (year <= 0) "公元前${1 - year}" else "公元${year}"
        return "$bc 年（${yearGanzhi(year)}）· 距今${yearsAgo(year)}年"
    }

    // ---------- 18 朝代互斥边界 ----------

    /**
     * 一个互斥朝代区间。区间表与切片-05「扩到 18 套」表格同名；原表交叠 / 空档
     * （西晋叠三国、楚汉之际、新莽之际）在此做单标签归并：交叠年归更晚建立的政权，
     * 空档就近归入前一政权，保证 [-1046, 1911] 逐年可分、无重叠无遗漏。
     */
    data class Band(val era: String, val from: Int, val to: Int)

    val bands: List<Band> = listOf(
        Band("西周", -1046, -771),
        Band("春秋", -770, -476),
        Band("战国", -475, -222),
        Band("秦", -221, -202),
        Band("西汉", -201, 24),
        Band("东汉", 25, 220),
        Band("三国", 221, 264),
        Band("西晋", 265, 316),
        Band("东晋/十六国", 317, 419),
        Band("南北朝", 420, 580),
        Band("隋", 581, 617),
        Band("唐", 618, 906),
        Band("五代十国", 907, 959),
        Band("北宋/辽", 960, 1126),
        Band("南宋/金/西夏", 1127, 1270),
        Band("元", 1271, 1367),
        Band("明", 1368, 1643),
        Band("清", 1644, 1911),
    )

    /**
     * 按公元年判朝代图层。[-1046, 1911] 内逐年命中唯一朝代；
     * 1912 以后归「近现代」兜底；早于西周立国返回 null，由上层标「史前 / 年代无考」。
     */
    fun dynastyOf(year: Int): String? {
        bands.firstOrNull { year in it.from..it.to }?.let { return it.era }
        return if (year >= 1912) "近现代" else null
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
