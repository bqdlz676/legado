package io.legado.app.help.chrono

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate

/**
 * 纪年 · 纪日换算
 *
 * 纪年：年号 → 公元（用 assets/era.json，519 条，西汉建元至清末宣统）
 *      公元 → 干支年（(y-4)%60）
 * 纪日：公历 → 干支日；给定年月与干支，反解当月所有可能的那几天
 *      朔/望/晦 直接落到初一/十五/月末
 *
 * 干支日基准：1900-01-01 记作 甲戌 前一位，实测 1949-10-01 与 2024-01-01 均落甲子，常数取 10。
 */
object Chronology {

    /** 「距今」以 1950 年为基准，考古学通用口径 */
    const val PRESENT_BASE = 1950

    private const val GAN = "甲乙丙丁戊己庚辛壬癸"
    private const val ZHI = "子丑寅卯辰巳午未申酉戌亥"

    data class Era(val name: String, val dynasty: String, val startYear: Int, val endYear: Int)

    /** 一条君主在位记录，供同年横向对照 */
    data class PrinceRow(val state: String, val title: String, val start: Int, val end: Int)

    /** 从正文猜出的年代：能定一年给 year，只能定区间给 from/to */
    data class AnchorGuess(val year: Int?, val from: Int?, val to: Int?, val state: String?)

    @Volatile
    private var eras: Map<String, List<Era>> = emptyMap()

    /** 国名+称号 → 即位年与在位数，如「鲁隐公」 */
    @Volatile
    private var princes: Map<String, IntArray> = emptyMap()

    @Volatile
    private var princeRows: List<PrinceRow> = emptyList()

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
                val pm = HashMap<String, IntArray>(260)
                val rows = ArrayList<PrinceRow>(220)
                context.assets.open("princes.txt").bufferedReader().use { r ->
                    while (true) {
                        val ln = r.readLine() ?: break
                        if (ln.isBlank() || ln.startsWith("#")) continue
                        val f = ln.split('\t')
                        if (f.size < 4) continue
                        val start = f[2].toIntOrNull() ?: continue
                        val span = f[3].toIntOrNull() ?: 1
                        pm[f[0] + f[1]] = intArrayOf(start, span)
                        rows.add(PrinceRow(f[0], f[1], start, start + span - 1))
                    }
                }
                princes = pm
                princeRows = rows
            }
            loaded = true
        }
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
            val y = v[0] + nthYear - 1
            if (nthYear <= v[1] + 1) return y
        }
        return null
    }


    /**
     * 从一段正文猜年代。
     * 顺序：全称「鲁隐公十一年」→ 简称「隐公十一年」（按鲁兜底）→ 「前六三八年」→ 年号纪年。
     * 只认君主年表里真有的称号；猜不出就返回 null，交给上层当「年代未知」处理。
     */
    fun anchorFromText(text: String, knownState: String? = null): AnchorGuess? {
        if (text.isEmpty()) return null
        // 1. 全称：princes 的键就是「国+称号」
        val best = findPrinceMention(text)
        if (best != null) return best
        // 2. 公元前直书
        val m = BCE.find(text)
        if (m != null) {
            val n = chineseNumber(m.groupValues[1]) ?: return null
            return AnchorGuess(-n + 1, -n + 1, -n + 1, knownState)
        }
        // 3. 年号纪年
        val e = eraMention(text)
        if (e != null) return e
        return null
    }

    private val BCE = Regex("前([一二三四五六七八九十百零]{1,6})年")
    private val NTH = Regex("^([元一二三四五六七八九十]{1,3})年")

    private fun findPrinceMention(text: String): AnchorGuess? {
        var bestPos = Int.MAX_VALUE
        var bestKey: String? = null
        var bestNth = 0
        for (k in princes.keys) {
            val i = text.indexOf(k)
            if (i < 0 || i >= bestPos) continue
            val m = NTH.find(text.substring(i + k.length))
            val nth = m?.let { chineseNumber(it.groupValues[1]) } ?: continue
            bestPos = i
            bestKey = k
            bestNth = nth
        }
        // 简称：称号在文里，但不带国名（《春秋》以鲁为本位）
        if (bestKey == null) {
            var sPos = Int.MAX_VALUE
            var sKey: String? = null
            var sNth = 0
            for (k in princes.keys) {
                if (k.length < 3) continue
                val short = k.substring(1)
                val i = text.indexOf(short)
                if (i < 0 || i >= sPos) continue
                val m = NTH.find(text.substring(i + short.length)) ?: continue
                val nth = chineseNumber(m.groupValues[1]) ?: continue
                sPos = i
                sKey = k
                sNth = nth
            }
            val k = sKey ?: return null
            val y = princeYear(k, sNth) ?: return null
            return AnchorGuess(y, y, y, k.substring(0, 1))
        }
        val k = bestKey ?: return null
        val y = princeYear(k, bestNth) ?: return null
        return AnchorGuess(y, y, y, k.substring(0, 1))
    }

    private fun eraMention(text: String): AnchorGuess? {
        for (name in eras.keys) {
            val i = text.indexOf(name)
            if (i < 0) continue
            val m = NTH.find(text.substring(i + name.length)) ?: continue
            val nth = chineseNumber(m.groupValues[1]) ?: continue
            val y = toGregorianYear(name, nth) ?: continue
            return AnchorGuess(y, y, y, null)
        }
        return null
    }

    /** 元年/一/二/三…十/十一/二十三 → Int；只收 1–99 */
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
     * 同一年各政权在位对照：鲁隐公元年 = 前722 = 周平王四十九年 = 郑庄公二十二年。
     * 按《春秋》次序排：周、鲁、齐、晋、楚、秦、宋、卫、郑、燕、吴、越，其余附后。
     */
    fun timelineFor(year: Int): List<Pair<String, Int>> {
        val order = listOf("周", "鲁", "齐", "晋", "楚", "秦", "宋", "卫", "郑", "燕", "吴", "越")
        return princeRows
            .filter { year in it.start..it.end }
            .sortedBy { r ->
                val i = order.indexOf(r.state)
                if (i >= 0) i else order.size + 1
            }
            .map { it.state + it.title to (year - it.start + 1) }
    }

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

    /** 公元年 → 干支年 */
    fun yearGanzhi(year: Int): String {
        val i = floorMod(year - 4, 60)
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

    /** 距今多少年，基准 1950 */
    fun yearsAgo(year: Int): Int = PRESENT_BASE - year

    /** 显示用：公元前/公元 + 干支 + 距今 */
    fun yearLabel(year: Int): String {
        val bc = if (year <= 0) "公元前${1 - year}" else "公元${year}"
        return "$bc 年（${yearGanzhi(year)}）· 距今${yearsAgo(year)}年"
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
