package io.legado.app.help.chrono

import io.legado.app.help.chrono.ContextAnchor.Confidence
import io.legado.app.help.place.PlaceChannel
import io.legado.app.help.place.PlaceDict
import io.legado.app.help.place.PlaceEntry

/**
 * 地名时空本体在 C1 侧的置信度闸（纯 Kotlin 内核，不依赖 Android Context / Assets）。
 *
 * 为什么需要这一层：D3 [PlaceDict] 只负责两表检索与同名返回，C1 消歧时还要统一套
 * 三源交叉置信度闸 + 时空区间排序 + 今址参考层隔离。闸口径（切片-05 §坐标可信度分层）：
 *  - 3/3 三源一致 → HIGH，默认命中；
 *  - 2/3 两源一致 → MEDIUM，标「待校」，可进默认；
 *  - ≤1/3 单源或来源冲突 → LOW，标「待考·来源冲突」，不进默认命中；
 *  - 今址参考层（[PlaceSeed.refLayer]）恒 LOW，永不进默认命中。
 *
 * 生产数据转发自 D3 冻结门面；[importRecords] 是测试 / 资产灌入的覆盖缝，同 id 覆盖 D3 结果。
 */
object ChronoPlaces {

    data class PlaceSeed(
        val id: String,
        val name: String,
        val kind: Char,                 // 'P' 点 / 'A' 面
        val start: Int,
        val end: Int,
        val tier: Int,
        val aliases: List<String>,
        val confidence: String,
        val source: String,
        val note: String = "",
        val lng: Double = 0.0,
        val lat: Double = 0.0,
        val era: String = "",
        val refLayer: Boolean = false,
        val channels: Set<SourceChannel> = emptySet(),
        val declaredConfidence: String? = null,
    ) {
        val isPoint: Boolean get() = kind == 'P'
        val span: Int get() = end - start
        fun inSpan(year: Int): Boolean = year in start..end
        fun overlaps(range: IntRange): Boolean = start <= range.last && end >= range.first

        /**
         * 置信度：显式 confidence 字符串（资产列 / 种子字面量）仅作覆盖；
         * 无显式值时按三源渠道交叉 + 参考层 + tier 走 [Confidence.grade] 打分。
         */
        val confidenceTag: Confidence
            get() {
                val explicit = declaredConfidence?.takeIf { it.isNotBlank() }
                    ?: confidence.takeIf { it.isNotBlank() }
                return if (explicit != null) Confidence.of(explicit)
                else Confidence.grade(channels, refLayer, tier)
            }
    }

    data class Resolve(
        val hit: PlaceSeed?,
        val others: List<PlaceSeed>,
        val anchored: Boolean,
        val candidates: List<PlaceSeed>,
    ) {
        val hasOthers: Boolean get() = others.isNotEmpty()
    }

    /** 测试 / 资产灌入的覆盖记录：同 id 盖在 D3 结果之上。 */
    @Volatile
    private var overrides: List<PlaceSeed> = emptyList()

    /** D3 渠道 → C1 渠道（两套枚举同构，显式映射避免跨包泄漏）。 */
    private fun mapChannel(c: PlaceChannel): SourceChannel = when (c) {
        PlaceChannel.ARCHAEOLOGY -> SourceChannel.ARCHAEOLOGY
        PlaceChannel.ATLAS -> SourceChannel.ATLAS
        PlaceChannel.TEXT -> SourceChannel.TEXT
        PlaceChannel.REFERENCE -> SourceChannel.REFERENCE
    }

    private fun fromEntry(e: PlaceEntry): PlaceSeed = PlaceSeed(
        id = e.placeId,
        name = e.name,
        kind = e.kind.code,
        start = e.fromYear,
        end = e.toYear,
        tier = e.tier.level,
        aliases = e.aliases,
        confidence = e.confidence.wire,
        source = e.source.label,
        note = e.note,
        lng = e.geo?.lng ?: 0.0,
        lat = e.geo?.lat ?: 0.0,
        era = e.era ?: "",
        refLayer = e.refLayer,
        channels = e.channels.map(::mapChannel).toSet(),
        declaredConfidence = null,
    )

    /** 资产 / 测试灌入：同 id 覆盖 D3 种子，其余条目保留兜底。 */
    fun importRecords(all: List<PlaceSeed>) {
        if (all.isEmpty()) return
        val merged = overrides.map { o -> all.firstOrNull { it.id == o.id } ?: o } +
            all.filter { candidate -> overrides.none { it.id == candidate.id } }
        overrides = merged
    }

    /** 全部在册条目（覆盖记录 + D3 全量），按 id 去重，覆盖记录优先。 */
    fun all(): List<PlaceSeed> {
        val base = PlaceDict.all().map(::fromEntry)
        return mergeOverrides(base)
    }

    private fun mergeOverrides(base: List<PlaceSeed>): List<PlaceSeed> {
        val byId = base.associateBy { it.id }.toMutableMap()
        overrides.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    /** 按正名或别名取候选；查询名大小写/空白不敏感。 */
    fun candidates(name: String): List<PlaceSeed> {
        val q = name.trim()
        if (q.isEmpty()) return emptyList()
        val base = PlaceDict.find(name).map(::fromEntry)
        return mergeOverrides(base).filter { r -> r.name == q || q in r.aliases }
    }

    /**
     * 同名消歧：名字 → 全部候选 → 锚点年份 / 区间过滤 → 排序 → 默认命中。
     * 锚点为空：不猜，hit = null，全部进候选，均不标默认。
     * 今址参考层候选即使出现在列表里，也永不进默认命中。
     */
    fun resolve(name: String, year: Int? = null, range: IntRange? = null): Resolve {
        val cands = candidates(name)
        if (cands.isEmpty()) return Resolve(null, emptyList(), false, emptyList())

        val anchored = year != null || range != null
        if (!anchored) return Resolve(null, cands, false, cands)

        fun matchLevel(r: PlaceSeed): Int = when {
            year != null && r.inSpan(year) -> 2
            range != null && r.overlaps(range) -> 1
            else -> 0
        }

        val active = cands.filter { matchLevel(it) > 0 }
        val pool = if (active.isNotEmpty()) active else cands

        val sortedActive = pool.sortedWith(anchorComparator(year, range))

        val defaultIdx = sortedActive.indexOfFirst {
            matchLevel(it) > 0 && !it.refLayer && it.confidenceTag.canDefault
        }
        val hit = if (defaultIdx >= 0) sortedActive[defaultIdx] else null
        val others = cands.filter { it.id != hit?.id }
        // 候选清单保留全部同名条目（含出区间 / 今址参考层），仅排序靠后，不丢层
        val allSorted = cands.sortedWith(anchorComparator(year, range))
        return Resolve(hit, others, true, allSorted)
    }

    private fun anchorComparator(year: Int?, range: IntRange?): Comparator<PlaceSeed> =
        compareBy<PlaceSeed> { r ->
            when {
                year != null && r.inSpan(year) -> 0
                range != null && r.overlaps(range) -> 1
                else -> 2
            }
        }.thenBy { if (it.refLayer) 1 else 0 }
            .thenBy { it.tier }
            .thenBy { it.span }
            .thenBy { if (it.isPoint) 0 else 1 }
            .thenBy { confidenceRank(it.confidenceTag) }

    private fun confidenceRank(c: Confidence): Int = when (c) {
        Confidence.HIGH -> 0; Confidence.MEDIUM -> 1; Confidence.LOW -> 2
    }

    /** 仅供单测：清空覆盖记录。 */
    fun resetForTest() {
        overrides = emptyList()
    }
}
