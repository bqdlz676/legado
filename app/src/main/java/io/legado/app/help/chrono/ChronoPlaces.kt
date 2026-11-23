package io.legado.app.help.chrono

import io.legado.app.help.chrono.ContextAnchor.Confidence

/**
 * 地名时空本体的纯 Kotlin 内核（不依赖 Android Context / Assets）。
 *
 * 设计要点：
 *  - 运行时由 [io.legado.app.help.PlaceTimeDict] 读完 place_entity.txt / place_name.txt
 *    后调用 [importRecords] 把资产表合并进来，按 id 覆盖种子；
 *  - 单测 / 无资产环境下用内置种子兜底，保证消歧链路不出现空白态；
 *  - 置信度沿用 schema 契约：high | medium | low；tier 1 实测→high，2 考证→medium，3 推定→low；
 *    三源交叉：3/3=high 默认命中，2/3=medium 标「待校」，≤1/3=low 标「待考·来源冲突」不进默认命中；
 *  - 今址参考层（[PlaceSeed.refLayer]）恒 low，永不进默认命中。
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

    @Volatile
    private var records: List<PlaceSeed> = builtIn()

    /** 资产表灌入：同 id 覆盖种子，种子独有的条目保留兜底 */
    fun importRecords(all: List<PlaceSeed>) {
        if (all.isEmpty()) return
        val override = all.associateBy { it.id }
        val merged = records.map { override[it.id] ?: it } +
            all.filter { candidate -> records.none { it.id == candidate.id } }
        records = merged
    }

    fun all(): List<PlaceSeed> = records

    /** 按正名或别名取候选；查询名大小写/空白不敏感 */
    fun candidates(name: String): List<PlaceSeed> {
        val q = name.trim()
        if (q.isEmpty()) return emptyList()
        return records.filter { r -> r.name == q || q in r.aliases }
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

    /** 仅供单测：还原内置种子 */
    fun resetForTest() {
        records = builtIn()
    }

    private fun builtIn(): List<PlaceSeed> = listOf(
        // 春秋晋都 · 绛（今山西翼城）：「晋 / 晋国」春秋语境的默认所指
        PlaceSeed(
            id = "P003", name = "绛", kind = 'P', start = -1035, end = -376, tier = 2,
            aliases = listOf("晋", "晋国", "翼", "故绛"), confidence = "medium",
            source = "CHGIS/谭其骧《中国历史地图集》", note = "晋国都城，今山西翼城",
            lng = 111.40, lat = 35.70, era = "春秋",
            channels = setOf(SourceChannel.ATLAS),
        ),
        // 晋国晚期都城 · 新田（今侯马）：与绛同名「晋」，靠年份区间消歧
        PlaceSeed(
            id = "P045", name = "新田", kind = 'P', start = -585, end = -376, tier = 2,
            aliases = listOf("新田", "晋", "晋国"), confidence = "medium",
            source = "CHGIS/谭其骧《中国历史地图集》", note = "晋国晚期都城，今侯马",
            lng = 111.50, lat = 35.40, era = "春秋",
            channels = setOf(SourceChannel.ATLAS),
        ),
        // 晋地泛称（面实体，tier3 文献推定，示意色块，非精确边界）
        PlaceSeed(
            id = "A012", name = "晋", kind = 'A', start = -221, end = 1911, tier = 3,
            aliases = listOf("晋", "并州", "山西", "三晋"), confidence = "low",
            source = "ctext 地理注释", note = "晋地/并州，山西地区泛称（示意色块，非精确边界）",
            lng = 112.50, lat = 37.80, era = "西汉",
            channels = setOf(SourceChannel.TEXT),
        ),
        // 现代山西晋城：今址参考层；晋都遗址在翼城/侯马，非此城，永不默认命中
        PlaceSeed(
            id = "P057", name = "晋城", kind = 'P', start = 1912, end = 2024, tier = 3,
            aliases = listOf("晋", "晋城"), confidence = "low",
            source = "今址参考层", note = "现代山西晋城；晋都遗址在翼城/侯马，非此城",
            lng = 112.85, lat = 35.49, era = "近现代", refLayer = true,
            channels = setOf(SourceChannel.REFERENCE),
        ),
        // 春秋十二诸侯都邑（种子，资产表载入后按 id 覆盖）
        PlaceSeed(
            id = "P001", name = "曲阜", kind = 'P', start = -1043, end = -256, tier = 2,
            aliases = listOf("鲁", "鲁国", "奄"), confidence = "medium",
            source = "CHGIS/谭其骧《中国历史地图集》", note = "鲁国都城，今山东曲阜",
            lng = 116.98, lat = 35.60, era = "春秋",
            channels = setOf(SourceChannel.ATLAS),
        ),
        PlaceSeed(
            id = "P002", name = "临淄", kind = 'P', start = -1046, end = -221, tier = 2,
            aliases = listOf("齐", "齐国", "营丘"), confidence = "medium",
            source = "CHGIS/谭其骧《中国历史地图集》", note = "齐国都城，今山东淄博临淄区",
            lng = 118.31, lat = 36.86, era = "春秋",
            channels = setOf(SourceChannel.ATLAS),
        ),
        PlaceSeed(
            id = "P008", name = "新郑", kind = 'P', start = -806, end = -375, tier = 2,
            aliases = listOf("郑", "郑国", "郑韩故城"), confidence = "medium",
            source = "CHGIS/谭其骧《中国历史地图集》", note = "郑国都城，今河南新郑",
            lng = 113.74, lat = 34.40, era = "春秋",
            channels = setOf(SourceChannel.ATLAS),
        ),
        PlaceSeed(
            id = "P042", name = "洛邑", kind = 'P', start = -1036, end = 1911, tier = 1,
            aliases = listOf("周", "东周", "洛阳", "成周", "雒阳", "河南"), confidence = "high",
            source = "考古遗址实测", note = "成周洛邑，今洛阳，东周王城",
            lng = 112.45, lat = 34.62, era = "东周",
            channels = setOf(SourceChannel.ARCHAEOLOGY),
        ),
    )
}
