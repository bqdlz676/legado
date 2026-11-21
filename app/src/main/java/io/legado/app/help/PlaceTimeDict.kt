package io.legado.app.help

import android.content.Context
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.utils.printOnDebug

/**
 * 地名时空本体（对齐 CHGIS 的三层建模）
 *
 *   实体 place_entity：id / 点面 / 经纬 / 起止年 / 朝代 / tier / 说明
 *   名字 place_name  ：名 / 实体id / 起止年 / 类型（正名、旧名、别称）
 *
 * 「长安 = 点，京兆 = 面，咸阳 = 另一个点」是三种不同的东西，不能混：
 *   类型 P 才画点，A 只画辖区色块；
 *   tier 1 遗址实测、2 考证（谭其骧/CHGIS）、3 文献推定（只画色块，不画点）。
 *
 * 同名靠时间消歧：点「晋」时按 ContextAnchor 的年代筛，
 * 读《左传》锚在前 722–前 453，就只留春秋晋国（P003），汉以后的「晋」进候选。
 */
object PlaceTimeDict {

    data class Ent(
        val id: String,
        val kind: Char,
        val name: String,
        val lng: Double,
        val lat: Double,
        val start: Int,
        val end: Int,
        val era: String,
        val tier: Int,
        val note: String,
    ) {
        val isPoint: Boolean get() = kind == 'P'
        val tierLabel: String get() = when (tier) {
            1 -> "遗址实测"
            2 -> "考证坐标"
            else -> "文献推定"
        }
        fun spanLabel(): String = if (start == end) yearText(start)
        else "${yearText(start)}–${yearText(end)}"

        private fun yearText(y: Int) = if (y <= 0) "前${1 - y}" else "$y"
    }

    class Resolve(val hit: Ent?, val others: List<Ent>, val anchored: Boolean)

    @Volatile
    private var byName: Map<String, List<Ent>>? = null

    @Volatile
    private var byId: Map<String, Ent>? = null

    fun ensureLoaded(context: Context) {
        if (byName == null) load(context)
    }

    fun candidates(context: Context, name: String): List<Ent> = load(context)[name] ?: emptyList()

    fun entity(context: Context, id: String): Ent? = ensureById(context)[id]

    /**
     * 按锚点年份挑一个；锚点为空就返回 null 并把全部候选作为 others——
     * 这是「不猜」：宁可让用户自己选，也不给一个假的唯一答案。
     */
    fun resolve(context: Context, name: String, year: Int? = ContextAnchor.get().year): Resolve {
        val all = candidates(context, name)
        if (all.isEmpty()) return Resolve(null, emptyList(), year != null)
        if (year == null) return Resolve(null, all, false)
        val inTime = all.filter { year in it.start..it.end }
        if (inTime.isEmpty()) return Resolve(null, all, true)
        // 时间命中里：tier 高的优先，其次时间区间更窄的（更具体）
        val best = inTime.sortedWith(
            compareBy<Ent> { it.tier }
                .thenBy { it.end - it.start }
                .thenByDescending { if (it.isPoint) 1 else 0 }
        ).first()
        val rest = (all - best).sortedBy { it.start }
        return Resolve(best, rest, true)
    }

    /** 面实体的辖区半径（度），按 tier 决定画不画 */
    fun radiusOf(e: Ent): Double = when {
        e.isPoint -> 0.0
        e.tier <= 2 -> 1.6
        else -> 2.4
    }

    private fun load(context: Context): Map<String, List<Ent>> {
        byName?.let { return it }
        synchronized(this) {
            byName?.let { return it }
            val ents = HashMap<String, Ent>(90)
            val names = HashMap<String, MutableList<Ent>>(260)
            runCatching {
                context.applicationContext.assets.open("place_entity.txt")
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 10) return@forEach
                            val lng = p[3].toDoubleOrNull() ?: return@forEach
                            val lat = p[4].toDoubleOrNull() ?: return@forEach
                            val e = Ent(
                                id = p[0],
                                kind = if (p[1].startsWith("A")) 'A' else 'P',
                                name = p[2],
                                lng = lng, lat = lat,
                                start = p[5].toIntOrNull() ?: -1046,
                                end = p[6].toIntOrNull() ?: 1911,
                                era = p[7],
                                tier = p[8].toIntOrNull() ?: 3,
                                note = p[9],
                            )
                            ents[e.id] = e
                        }
                    }
                context.applicationContext.assets.open("place_name.txt")
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 2) return@forEach
                            val e = ents[p[1]] ?: return@forEach
                            names.getOrPut(p[0]) { ArrayList(2) }.add(e)
                        }
                    }
            }.onFailure { it.printOnDebug() }
            byId = ents
            val out = HashMap<String, List<Ent>>(names.size)
            names.forEach { (k, v) -> out[k] = v.distinctBy { it.id } }
            byName = out
            return out
        }
    }

    private fun ensureById(context: Context): Map<String, Ent> {
        load(context)
        return byId ?: emptyMap()
    }
}
