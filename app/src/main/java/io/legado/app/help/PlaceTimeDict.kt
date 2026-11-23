package io.legado.app.help

import android.content.Context
import io.legado.app.help.chrono.ChronoPlaces
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.chrono.SourceChannel
import io.legado.app.utils.printOnDebug

/**
 * 地名时空本体（对齐 CHGIS / 中研院「中华文明之时空基础架构」字段）
 *
 * 三层建模：
 *   实体 place   —— 点（城/关隘/渡口）| 面（政区/诸侯国/郡县），稳定不变
 *   名称 place_name —— 同一实体随时间改名（长安/西京/京兆尹）
 *   区间 year_start..year_end —— 同名靠「当前章是哪一年」消歧
 *
 * 铁律：tier 3（文献推定）只画色块，不画点，避免把「大概在某县」伪装成「精确到百米」。
 *
 * 数据（assets）：
 *   place_entity.txt : id \t 类型(P/A) \t 正名 \t 经度 \t 纬度 \t 起年 \t 止年 \t 朝代
 *                      \t tier \t 说明 \t 别名(|) \t geometry \t superior \t source \t seat \t confidence
 *   place_name.txt   : 名称 \t 实体id \t 起年 \t 止年 \t 朝代 \t 类型 \t superior \t source \t confidence
 */
object PlaceTimeDict {

    data class Ent(
        val id: String,
        val kind: Char,                 // 'P' 点 / 'A' 面
        val name: String,
        val lng: Double,
        val lat: Double,
        val start: Int,
        val end: Int,
        val era: String,
        val tier: Int,                  // 1 遗址实测 / 2 考证 / 3 文献推定
        val note: String,
        val aliases: List<String> = emptyList(),
        val geometry: String? = null,   // GeoJSON，面实体用
        val superior: String? = null,   // 上级政区
        val source: String? = null,     // tier_source 出处
        val seat: String? = null,       // 治所实体 id（面实体）
        val confidence: String? = null, // 显式置信度 high|medium|low，缺省按 tier 推导
    ) {
        /** schema 契约置信度：显式列优先；缺省 tier 1 实测→high，2 考证→medium，3 推定→low */
        val confidenceTag: String get() = when {
            !confidence.isNullOrBlank() -> confidence.trim().lowercase()
            tier == 1 -> "high"; tier == 2 -> "medium"; else -> "low"
        }

        val isPoint: Boolean get() = kind == 'P'

        /** tier 3 只画色块：isPoint 且 tier 3 → 不落点 */
        val asPoint: Boolean get() = isPoint && tier <= 2

        val tierLabel: String get() = when (tier) {
            1 -> "遗址实测"
            2 -> "考证坐标"
            else -> "文献推定"
        }

        fun spanLabel(): String =
            if (start == end) yearText(start) else "${yearText(start)}–${yearText(end)}"

        fun whereLabel(): String = when {
            tier == 1 -> "遗址实测坐标"
            tier == 2 -> "考证坐标" + (source?.let { "（$it）" } ?: "")
            else -> "文献推定，精确到县"
        }

        companion object {
            fun yearText(y: Int) = if (y <= 0) "前${1 - y}" else "$y"
        }
    }

    class Resolve(
        val hit: Ent?,
        val others: List<Ent>,
        val anchored: Boolean,
    ) {
        val hasOthers: Boolean get() = others.isNotEmpty()
    }

    @Volatile private var byName: Map<String, List<Ent>>? = null
    @Volatile private var byId: Map<String, Ent>? = null

    fun ensureLoaded(context: Context) {
        if (byName == null) load(context)
    }

    fun candidates(context: Context, name: String): List<Ent> =
        load(context)[name] ?: emptyList()

    fun entity(context: Context, id: String): Ent? = ensureById(context)[id]

    /** 全部实体（只读遍历用：周边参照点等跨实体场景） */
    fun all(context: Context): List<Ent> {
        load(context)
        return byId?.values?.toList().orEmpty()
    }

    /**
     * 消歧：名字 → 全部候选 → 锚点年份过滤 → tier 高优先 → 区间更窄优先 → 点优先。
     * 锚点为空：不猜（hit = null，全部进候选）。
     */
    fun resolve(context: Context, name: String, year: Int? = ContextAnchor.getYear()): Resolve {
        val cands = candidates(context, name)
        if (cands.isEmpty()) return Resolve(null, emptyList(), year != null)
        if (year == null) return Resolve(null, cands, false)

        val inSpan = cands.filter { year in it.start..it.end }
        val pool = if (inSpan.isNotEmpty()) inSpan else cands
        val sorted = pool.sortedWith(
            compareBy<Ent> { if (year in it.start..it.end) 0 else 1 }
                .thenBy { it.tier }
                .thenBy { it.end - it.start }
                .thenBy { if (it.isPoint) 0 else 1 }
        )
        val hit = sorted.firstOrNull()
        val others = cands.filter { it.id != hit?.id }
        return Resolve(hit, others, true)
    }

    /** 面实体的色块半径（km），点返回 0 */
    fun radiusOf(e: Ent): Double = when {
        e.isPoint -> 0.0
        e.geometry != null -> 0.0          // 有真多边形就不画圆
        e.name.length >= 2 -> 2.4
        else -> 2.4
    }

    // ---------- 加载 ----------

    private fun load(context: Context): Map<String, List<Ent>> {
        byName?.let { return it }
        synchronized(this) {
            byName?.let { return it }
            val ents = HashMap<String, Ent>(120)
            val names = HashMap<String, MutableList<Ent>>(320)
            runCatching {
                context.applicationContext.assets.open("place_entity.txt")
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 9) return@forEach
                            val lng = p[3].toDoubleOrNull() ?: return@forEach
                            val lat = p[4].toDoubleOrNull() ?: return@forEach
                            val e = Ent(
                                id = p[0],
                                kind = if (p[1].startsWith("A")) 'A' else 'P',
                                name = p[2],
                                lng = lng, lat = lat,
                                start = p[5].toIntOrNull() ?: -1046,
                                end = p[6].toIntOrNull() ?: 1911,
                                era = p.getOrNull(7).orEmpty(),
                                tier = p.getOrNull(8)?.toIntOrNull() ?: 3,
                                note = p.getOrNull(9).orEmpty(),
                                aliases = p.getOrNull(10)
                                    ?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }
                                    ?: emptyList(),
                                geometry = p.getOrNull(11)?.takeIf { it.isNotBlank() },
                                superior = p.getOrNull(12)?.takeIf { it.isNotBlank() },
                                source = p.getOrNull(13)?.takeIf { it.isNotBlank() },
                                seat = p.getOrNull(14)?.takeIf { it.isNotBlank() },
                                confidence = p.getOrNull(15)?.takeIf { it.isNotBlank() },
                            )
                            ents[e.id] = e
                            names.getOrPut(e.name) { ArrayList(2) }.add(e)
                            e.aliases.forEach { a -> names.getOrPut(a) { ArrayList(2) }.add(e) }
                        }
                    }

                context.applicationContext.assets.open("place_name.txt")
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isEmpty() || line.startsWith("#")) return@forEach
                            val p = line.split('\t')
                            if (p.size < 2) return@forEach
                            val e = ents[p[1]] ?: return@forEach
                            // place_name 的 superior/source/confidence 覆盖实体级（名称快照优先）
                            val sup = p.getOrNull(6)?.takeIf { it.isNotBlank() } ?: e.superior
                            val src = p.getOrNull(7)?.takeIf { it.isNotBlank() } ?: e.source
                            val conf = p.getOrNull(8)?.takeIf { it.isNotBlank() } ?: e.confidence
                            val merged = e.copy(superior = sup, source = src, confidence = conf)
                            ents[e.id] = merged
                            names.getOrPut(p[0]) { ArrayList(2) }.add(merged)
                        }
                    }
            }.onFailure { it.printOnDebug() }

            byId = ents
            val out = HashMap<String, List<Ent>>(names.size)
            names.forEach { (k, v) -> out[k] = v.distinctBy { it.id } }
            byName = out

            // 灌入纯 Kotlin 消歧内核（ContextAnchor.resolve 读这一份）
            val idNames = HashMap<String, MutableSet<String>>()
            names.forEach { (n, el) -> el.forEach { idNames.getOrPut(it.id) { mutableSetOf() }.add(n) } }
            val seeds = ents.values.map { e ->
                val channel = SourceChannel.classify(e.source)
                ChronoPlaces.PlaceSeed(
                    id = e.id,
                    name = e.name,
                    kind = e.kind,
                    start = e.start,
                    end = e.end,
                    tier = e.tier,
                    aliases = (idNames[e.id] ?: emptySet()).toList(),
                    confidence = e.confidenceTag,
                    source = e.source ?: "",
                    note = e.note,
                    lng = e.lng,
                    lat = e.lat,
                    era = e.era,
                    refLayer = channel == SourceChannel.REFERENCE,
                    channels = setOfNotNull(channel),
                    declaredConfidence = e.confidence,
                )
            }
            ChronoPlaces.importRecords(seeds)
            return out
        }
    }

    private fun ensureById(context: Context): Map<String, Ent> {
        load(context)
        return byId ?: emptyMap()
    }
}
