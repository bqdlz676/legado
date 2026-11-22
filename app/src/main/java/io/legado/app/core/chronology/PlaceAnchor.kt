package io.legado.app.core.chronology

/**
 * 落文 · 地名时空锚定 / 同名消歧内核（纯 Kotlin，不依赖 Android Context / Assets）。
 *
 * 链路（规格切片 05）：按名字拉出所有候选（春秋晋国、三家分晋后的晋、汉以后叫晋的地方、
 * 现代同名晋城…）→ 用时空上下文过滤：先靠纪年换算 + 人物朝代锚定「当前这章是哪一年」，
 * 再按年份区间筛。时间命中优先，其余同名收进「还有 N 个同名」供用户一键切换。
 *
 * 分层铁律：
 *  - 今址参考层（现代同名异地，如山西晋城）只作「古地今址」参照，[PlaceRecord.refLayer]=true，
 *    绝不参与古地名默认命中；
 *  - 春秋晋（-770~-453）、战国三晋（赵/魏/韩，-403 起）、西晋/东晋（265 起）是三个不同实体，
 *    名字都可称「晋」，靠年份区间互斥消歧，绝不混层；
 *  - low 置信度（≤1/3 来源冲突）不进默认命中；medium 标「待校」可进默认；high 默认命中。
 */
object PlaceAnchor {

    data class Resolve(
        val hit: PlaceRecord?,
        val others: List<PlaceRecord>,
        val anchored: Boolean,
        val candidates: List<PlaceRecord>,
    ) {
        val hasOthers: Boolean get() = others.isNotEmpty()
        val othersCount: Int get() = others.size
    }

    @Volatile
    private var records: List<PlaceRecord> = builtIn()

    /** 资产表灌入：同 id 覆盖种子，种子独有条目保留兜底 */
    fun importRecords(all: List<PlaceRecord>) {
        if (all.isEmpty()) return
        val override = all.associateBy { it.id }
        val merged = records.map { override[it.id] ?: it } +
            all.filter { candidate -> records.none { it.id == candidate.id } }
        records = merged
    }

    /** 名称快照回填：合并别名、渠道集合，名称级 source / confidence / superior 覆盖实体级 */
    fun applyNameLinks(links: List<NameLink>) {
        if (links.isEmpty()) return
        val byId = links.groupBy { it.entityId }
        records = records.map { rec ->
            val snapshots = byId[rec.id] ?: return@map rec
            val aliases = (rec.aliases + snapshots.map { it.name }).distinct()
            val channels = rec.channels + snapshots.mapNotNull { it.channel }
            val last = snapshots.last()
            rec.copy(
                aliases = aliases,
                channels = channels,
                superior = last.superior ?: rec.superior,
                source = last.source ?: rec.source,
                declaredConfidence = last.confidence ?: rec.declaredConfidence,
            )
        }
    }

    fun all(): List<PlaceRecord> = records

    /** 仅供单测：还原内置种子 */
    fun resetForTest() {
        records = builtIn()
    }

    /** 按正名或别名取候选；查询名空白 / 大小写不敏感 */
    fun candidates(name: String): List<PlaceRecord> {
        val q = name.trim()
        if (q.isEmpty()) return emptyList()
        return records.filter { r ->
            r.name == q || q in r.aliases
        }
    }

    /**
     * 同名消歧：名字 → 全部候选 → 锚点年份 / 区间过滤 → 排序 → 默认命中。
     *
     * @param year  锚定公元年（可空；空则只给区间锚）
     * @param range 候选年代区间（可空；与 [year] 任一非空即视为已锚定）
     *
     * 锚点为空：不猜，hit = null，全部进候选，均不标默认。
     * 锚点年下无候选落在其存在区间时，保留全部候选兜底（出区间者排序靠后）。
     * 今址参考层候选即使出现在列表里，也永不进默认命中。
     */
    fun resolve(name: String, year: Int? = null, range: IntRange? = null): Resolve {
        val cands = candidates(name)
        if (cands.isEmpty()) return Resolve(null, emptyList(), false, emptyList())

        val anchored = year != null || range != null
        if (!anchored) return Resolve(null, cands, false, cands)

        fun matchLevel(r: PlaceRecord): Int = when {
            year != null && r.inSpan(year) -> 2
            range != null && r.overlaps(range) -> 1
            else -> 0
        }

        val active = cands.filter { matchLevel(it) > 0 }
        val pool = if (active.isNotEmpty()) active else cands

        val sorted = pool.sortedWith(anchorComparator(year, range))

        val defaultIdx = sorted.indexOfFirst {
            matchLevel(it) > 0 && !it.refLayer && it.confidence.canDefault
        }
        val hit = if (defaultIdx >= 0) sorted[defaultIdx] else null
        val others = cands.filter { it.id != hit?.id }
        return Resolve(hit, others, true, sorted)
    }

    private fun anchorComparator(year: Int?, range: IntRange?): Comparator<PlaceRecord> =
        compareBy<PlaceRecord> { r ->
            when {
                year != null && r.inSpan(year) -> 0
                range != null && r.overlaps(range) -> 1
                else -> 2
            }
        }.thenBy { if (it.refLayer) 1 else 0 }
            .thenBy { it.tier }
            .thenBy { it.span }
            .thenBy { if (it.isPoint) 0 else 1 }
            .thenBy { confidenceRank(it.confidence) }

    private fun confidenceRank(c: Confidence): Int = when (c) {
        Confidence.HIGH -> 0; Confidence.MEDIUM -> 1; Confidence.LOW -> 2
    }

    // ---------- 内置种子（无资产 / 单测兜底；资产表载入后按 id 覆盖） ----------

    private fun builtIn(): List<PlaceRecord> = listOf(
        // 春秋晋都 · 绛（今山西翼城）：「晋 / 晋国」春秋语境的默认所指
        PlaceRecord(
            id = "P003", kind = 'P', name = "绛", lng = 111.40, lat = 35.70,
            start = -1035, end = -376, era = "春秋", tier = 2,
            note = "晋国都城，今山西翼城",
            aliases = listOf("晋", "晋国", "翼", "故绛"),
            source = "CHGIS/谭其骧《中国历史地图集》",
            channels = setOf(SourceChannel.ATLAS), declaredConfidence = "medium",
        ),
        // 晋国晚期都城 · 新田（今侯马）：与绛同名「晋」，靠年份区间消歧
        PlaceRecord(
            id = "P045", kind = 'P', name = "新田", lng = 111.50, lat = 35.40,
            start = -585, end = -376, era = "春秋", tier = 2,
            note = "晋国晚期都城，今侯马",
            aliases = listOf("新田", "晋", "晋国"),
            source = "CHGIS/谭其骧《中国历史地图集》",
            channels = setOf(SourceChannel.ATLAS), declaredConfidence = "medium",
        ),
        // 晋地泛称（面实体，tier3 文献推定，示意色块，非精确边界）
        PlaceRecord(
            id = "A012", kind = 'A', name = "晋", lng = 112.50, lat = 37.80,
            start = -221, end = 1911, era = "西汉", tier = 3,
            note = "晋地/并州，山西地区泛称（示意色块，非精确边界）",
            aliases = listOf("晋", "并州", "山西", "三晋"),
            source = "ctext 地理注释",
            channels = setOf(SourceChannel.TEXT), declaredConfidence = "low",
        ),
        // 现代山西晋城：今址参考层；晋都遗址在翼城/侯马，非此城，永不默认命中
        PlaceRecord(
            id = "P057", kind = 'P', name = "晋城", lng = 112.85, lat = 35.49,
            start = 1912, end = 2024, era = "近现代", tier = 3,
            note = "现代山西晋城；晋都遗址在翼城/侯马，非此城",
            aliases = listOf("晋", "晋城"),
            source = "今址参考层",
            channels = setOf(SourceChannel.REFERENCE), declaredConfidence = "low",
            refLayer = true,
        ),
        // 春秋十二诸侯都邑（种子，资产表载入后按 id 覆盖）
        PlaceRecord(
            id = "P001", kind = 'P', name = "曲阜", lng = 116.98, lat = 35.60,
            start = -1043, end = -256, era = "春秋", tier = 2,
            note = "鲁国都城，今山东曲阜", aliases = listOf("鲁", "鲁国", "奄"),
            source = "CHGIS/谭其骧《中国历史地图集》",
            channels = setOf(SourceChannel.ATLAS), declaredConfidence = "medium",
        ),
        PlaceRecord(
            id = "P002", kind = 'P', name = "临淄", lng = 118.31, lat = 36.86,
            start = -1046, end = -221, era = "春秋", tier = 2,
            note = "齐国都城，今山东淄博临淄区", aliases = listOf("齐", "齐国", "营丘"),
            source = "CHGIS/谭其骧《中国历史地图集》",
            channels = setOf(SourceChannel.ATLAS), declaredConfidence = "medium",
        ),
        PlaceRecord(
            id = "P008", kind = 'P', name = "新郑", lng = 113.74, lat = 34.40,
            start = -806, end = -375, era = "春秋", tier = 2,
            note = "郑国都城，今河南新郑", aliases = listOf("郑", "郑国", "郑韩故城"),
            source = "CHGIS/谭其骧《中国历史地图集》",
            channels = setOf(SourceChannel.ATLAS), declaredConfidence = "medium",
        ),
        PlaceRecord(
            id = "P042", kind = 'P', name = "洛邑", lng = 112.45, lat = 34.62,
            start = -1036, end = 1911, era = "东周", tier = 1,
            note = "成周洛邑，今洛阳，东周王城",
            aliases = listOf("周", "东周", "洛阳", "成周", "雒阳", "河南"),
            source = "考古遗址实测",
            channels = setOf(SourceChannel.ARCHAEOLOGY), declaredConfidence = "high",
        ),
    )
}
