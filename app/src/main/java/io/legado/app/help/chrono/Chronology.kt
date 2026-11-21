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

    @Volatile
    private var eras: Map<String, List<Era>> = emptyMap()

    /** 国名+称号 → 即位年与在位数，如「鲁隐公」 */
    @Volatile
    private var princes: Map<String, IntArray> = emptyMap()

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
                context.assets.open("princes.txt").bufferedReader().use { r ->
                    while (true) {
                        val ln = r.readLine() ?: break
                        if (ln.isBlank() || ln.startsWith("#")) continue
                        val f = ln.split('\t')
                        if (f.size < 4) continue
                        val start = f[2].toIntOrNull() ?: continue
                        val span = f[3].toIntOrNull() ?: 1
                        pm[f[0] + f[1]] = intArrayOf(start, span)
                    }
                }
                princes = pm
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
