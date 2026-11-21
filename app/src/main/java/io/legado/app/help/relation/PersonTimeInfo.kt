package io.legado.app.help.relation

import android.content.Context
import io.legado.app.help.chrono.Chronology
import io.legado.app.utils.printOnDebug

/**
 * 人物时空信息：L1 档案 + 别名归并 + 当年年龄。
 *
 * person_l1.txt  ：本名 字 号 封号 生 卒 在位起 在位止 国 身份 最著名一事
 * person_alias.txt：别名 归属 类型(谥称/爵称/字/号/本名/省称) 国 起 止
 *
 * 置信分层不是硬编码，是按数据本身的形态判：
 *   L1 = 本表（人工核对，有身份有事迹）
 *   L2 = CBDB 有生卒
 *   L3 = CBDB 生卒缺，孤证
 */
object PersonTimeInfo {

    data class L1(
        val name: String,
        val zi: String,
        val hao: String,
        val fief: String,
        val birth: Int?,
        val death: Int?,
        val reignStart: Int?,
        val reignEnd: Int?,
        val state: String,
        val identity: String,
        val deed: String,
    )

    data class Alias(
        val alias: String,
        val owner: String,
        val type: String,
        val state: String,
    )

    enum class Confidence { EXACT, YEAR_ONLY, UNKNOWN, NOT_BORN, DECEASED }

    data class Age(
        val age: Int?,
        val label: String,
        val confidence: Confidence,
    )

    @Volatile
    private var l1: Map<String, L1>? = null

    @Volatile
    private var aliasTable: Map<String, List<Alias>>? = null

    fun ensureLoaded(context: Context) {
        loadL1(context)
        loadAlias(context)
    }

    fun of(context: Context, name: String): L1? = loadL1(context)[name]

    fun all(context: Context): List<L1> = loadL1(context).values.toList()

    /** 别名候选：一个「桓公」会拉出鲁桓公、齐桓公、宋桓公、卫桓公… */
    fun aliasCandidates(context: Context, alias: String): List<Alias> =
        loadAlias(context)[alias] ?: emptyList()

    /** 全部已知短称谓，供抽取时按首字立桶 */
    fun allAliases(context: Context): List<String> = loadAlias(context).keys.toList()

    /**
     * 按锚点选本名。
     * 谥称按「当前文本所属国」优先；爵称（楚子/齐侯）指向该国当年在位的君主，
     * 这正是《左传》里「楚子」随年份换人的地方。
     */
    fun resolveAlias(context: Context, alias: String, year: Int?, state: String?): Pair<String, Alias>? {
        val cands = aliasCandidates(context, alias)
        if (cands.isEmpty()) return null
        if (state != null) {
            val lordAlias = cands.firstOrNull { it.state == state && it.owner.startsWith("@") }
            if (lordAlias != null) {
                val owner = currentRuler(context, state, year)
                if (owner != null) return owner to lordAlias
            }
            cands.firstOrNull { it.state == state && !it.owner.startsWith("@") }?.let {
                return it.owner to it
            }
            cands.firstOrNull { it.state == state }?.let { return it.owner to it }
        }
        cands.firstOrNull { it.owner.startsWith("@") }?.let { a ->
            val st = a.state
            val owner = currentRuler(context, st, year)
            if (owner != null) return owner to a
        }
        val first = cands.first()
        return first.owner to first
    }

    /** 某国某年在位君主名，如 楚@前632 → 楚成王 */
    private fun currentRuler(context: Context, state: String, year: Int?): String? {
        val y = year ?: return null
        Chronology.ensureLoaded(context)
        val rows = Chronology.timelineFor(y)
        val hit = rows.firstOrNull { it.first.startsWith(state) } ?: return null
        return hit.first
    }

    /**
     * 此年几岁。生卒只有年没有月，跨年边界有模糊期，所以 EXACT 只在生年明确时给；
     * 只有在位年、没有生年的，给 YEAR_ONLY 并明说「生年无载」。
     */
    fun ageAt(context: Context, name: String, year: Int?): Age {
        val y = year ?: return Age(null, "年代未锚定", Confidence.UNKNOWN)
        val p = of(context, name)
        if (p != null) {
            val b = p.birth
            val d = p.death
            if (b != null && y < b) return Age(null, "尚未出生（生于${fmt(b)}）", Confidence.NOT_BORN)
            if (d != null && y > d) return Age(null, "已卒（卒于${fmt(d)}）", Confidence.DECEASED)
            if (b != null) {
                val a = y - b
                val tail = if (d != null) "" else "（卒年无载）"
                return Age(a, "约 $a 岁$tail", Confidence.EXACT)
            }
            if (d != null) return Age(null, "卒于${fmt(d)}", Confidence.YEAR_ONLY)
            if (p.reignStart != null && y in p.reignStart..(p.reignEnd ?: p.reignStart)) {
                return Age(null, "在位第 ${y - p.reignStart + 1} 年（生年无载）", Confidence.YEAR_ONLY)
            }
            return Age(null, "生卒无载", Confidence.UNKNOWN)
        }
        val cbdb = PersonProfile.profileOf(name)
        if (cbdb != null) {
            val b = cbdb.birth
            val d = cbdb.death
            if (b != null && y < b) return Age(null, "尚未出生（生于$b）", Confidence.NOT_BORN)
            if (d != null && y > d) return Age(null, "已卒（卒于$d）", Confidence.DECEASED)
            if (b != null && y in b..(d ?: 9999)) return Age(y - b, "约 ${y - b} 岁", Confidence.EXACT)
            return Age(null, "生卒无载（CBDB 孤证）", Confidence.UNKNOWN)
        }
        return Age(null, "此人未入人物表", Confidence.UNKNOWN)
    }

    fun confidenceOf(context: Context, name: String): String {
        if (of(context, name) != null) return "L1 人工档案"
        val dup = PersonDup.candidates(context, name)
        if (dup.size >= 2) {
            val l3 = dup.count { it.confidence == "L3" }
            return if (l3 > 0) "同名 ${dup.size} 人，${l3} 条孤证（不默认命中）"
            else "同名 ${dup.size} 人，均有生卒可辨"
        }
        val p = PersonProfile.profileOf(name) ?: return "未收录"
        return if (p.birth != null || p.death != null) "L2 CBDB 有生卒"
        else "L3 CBDB 孤证，存疑"
    }

    private fun fmt(y: Int) = if (y <= 0) "前${1 - y}" else "$y"

    private fun loadL1(context: Context): Map<String, L1> {
        l1?.let { return it }
        synchronized(this) {
            l1?.let { return it }
            val m = HashMap<String, L1>(100)
            runCatching {
                context.applicationContext.assets.open("person_l1.txt")
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 11) return@forEach
                            val e = L1(
                                name = p[0], zi = p[1], hao = p[2], fief = p[3],
                                birth = p[4].toIntOrNull(), death = p[5].toIntOrNull(),
                                reignStart = p[6].toIntOrNull(), reignEnd = p[7].toIntOrNull(),
                                state = p[8], identity = p[9], deed = p[10],
                            )
                            m[e.name] = e
                        }
                    }
            }.onFailure { it.printOnDebug() }
            l1 = m
            return m
        }
    }

    private fun loadAlias(context: Context): Map<String, List<Alias>> {
        aliasTable?.let { return it }
        synchronized(this) {
            aliasTable?.let { return it }
            val m = HashMap<String, MutableList<Alias>>(120)
            runCatching {
                context.applicationContext.assets.open("person_alias.txt")
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 4) return@forEach
                            val a = Alias(p[0], p[1], p[2], p[3])
                            m.getOrPut(a.alias) { ArrayList(4) }.add(a)
                        }
                    }
            }.onFailure { it.printOnDebug() }
            val out = HashMap<String, List<Alias>>(m.size)
            m.forEach { (k, v) -> out[k] = v }
            aliasTable = out
            return out
        }
    }
}
