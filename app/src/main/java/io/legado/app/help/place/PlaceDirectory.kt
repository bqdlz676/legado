package io.legado.app.help.place

/**
 * 地名目录：两表索引 + 同名消歧 + 种子数据 + 外部（CHGIS 资产）注册扩展点。
 *
 * 职责边界（模块 D3）：
 * - 只做「名称 → 候选实体」的归并、年代过滤与排序；不碰气泡 UI、不碰拼音、不碰地图渲染；
 * - 纯 Kotlin / 纯 JVM，不依赖 android.content.Context，宿主单测可直接跑；
 * - 数据可信 = 命：每条记录必须带 [PlaceSource]，无出处、Tier3 违规带点位坐标的记录
 *   在注册时即被拒绝，不进索引。
 *
 * 同名消歧规则（切片-05 §「按名字拉出所有候选 → 时空上下文过滤 → 时间命中优先」）：
 * 1. 主键 [PlaceEntity.placeId] 精确命中，直接返回（候选切换走这条）；
 * 2. 有年代锚点时：单年落在区间内 > 区间相交 > 无锚点；
 * 3. 区间内再按：非今址参考层 → 置信度 high/medium/low → Tier 低优先 → 区间窄优先 → 点优先；
 * 4. low 与今址参考层候选照收进列表，但 [PlaceEntry.canDefault] = false，不进默认命中。
 */
internal object PlaceDirectory {

    private val seedEntities: List<PlaceEntity> = PlaceSeedData.entities()
    private val seedNameRows: List<PlaceNameRow> = PlaceSeedData.nameRows()

    /** 外部实体（后续接入 CHGIS 资产解析后注册到此）；种子优先，外部补缺。 */
    private val externalEntities: MutableList<PlaceEntity> = mutableListOf()
    private val externalNameRows: MutableList<PlaceNameRow> = mutableListOf()

    private var entities: List<PlaceEntity> = seedEntities
    private var nameRows: List<PlaceNameRow> = seedNameRows
    private var byId: Map<String, PlaceEntity> = emptyMap()

    /** 归一化称呼 → 实体 id 列表（一个称呼可能对应同名 / 撞称多人，故为列表）。 */
    private var byName: Map<String, List<String>> = emptyMap()

    init {
        rebuild()
    }

    // ---- 对外查询（companion facade 转发到这里）----

    fun find(raw: String, year: Int? = null, range: IntRange? = null): List<PlaceEntry> {
        val key = normalize(raw)
        if (key.isEmpty()) return emptyList()
        val ids = byName[key] ?: return emptyList()
        val candidates = ids.mapNotNull { byId[it] }.distinct().map { toEntry(it, key) }
        return disambiguate(candidates, year, range)
    }

    fun geoOf(placeId: String): PlaceGeo? = byId[placeId]?.let { toGeo(it) }

    fun entity(placeId: String): PlaceEntity? = byId[placeId]

    /** 全部在册条目（测试 / 自检遍历出处、tier 约束用）。 */
    fun allEntries(): List<PlaceEntry> = entities.map { toEntry(it, it.canonicalName) }

    /**
     * 注册外部实体与名称快照（CHGIS 解析层调用）。重复 [PlaceEntity.placeId] 以后注册者覆盖；
     * 校验不通过的记录直接拒绝，抛 [IllegalArgumentException]，不进索引。
     */
    @Synchronized
    fun register(records: List<PlaceEntity>, names: List<PlaceNameRow> = emptyList()) {
        // 先在待合并集合上全量校验，任何一条不通过都不落盘，避免失败注册留下半份状态
        val mergedEntities = seedEntities + externalEntities + records
        records.forEach { requireValid(it) }
        names.forEach { row ->
            require(mergedEntities.any { it.placeId == row.placeId }) {
                "名称快照回指的实体不存在：${row.placeId} / ${row.name}"
            }
        }
        externalEntities.addAll(records)
        externalNameRows.addAll(names)
        rebuild()
    }

    // ---- 内部实现 ----

    @Synchronized
    private fun rebuild() {
        val mergedEntities = (seedEntities + externalEntities).associateBy { it.placeId }
            .let { map -> map.values.toList() }
        val mergedNames = seedNameRows + externalNameRows
        mergedEntities.forEach { requireValid(it) }
        mergedNames.forEach { row ->
            require(mergedEntities.any { it.placeId == row.placeId }) {
                "名称快照回指的实体不存在：${row.placeId} / ${row.name}"
            }
        }

        entities = mergedEntities
        nameRows = mergedNames
        byId = mergedEntities.associateBy { it.placeId }

        val index = HashMap<String, MutableList<String>>()
        fun put(name: String, id: String) {
            val k = normalize(name)
            if (k.isNotEmpty()) index.getOrPut(k) { mutableListOf() }.add(id)
        }
        for (e in mergedEntities) {
            put(e.canonicalName, e.placeId)
            e.aliases.forEach { put(it, e.placeId) }
        }
        for (row in mergedNames) put(row.name, row.placeId)

        byName = index.mapValues { (_, v) -> v.distinct() }
    }

    private fun requireValid(e: PlaceEntity) {
        require(e.placeId.isNotBlank()) { "placeId 不能为空" }
        require(e.canonicalName.isNotBlank()) { "实体正名不能为空：${e.placeId}" }
        require(e.source.label.isNotBlank()) { "出处不能为空：${e.canonicalName}" }
        require(e.fromYear <= e.toYear) {
            "起止年份倒置：${e.canonicalName} ${e.fromYear}~${e.toYear}"
        }
        // Tier3 铁律：文献推定到县，只给色块，禁止带点位坐标伪装百米精度
        require(e.tier != PlaceTier.T3_TEXT || (e.lng == null && e.lat == null)) {
            "Tier3 文献推定只允许色块，不得带点位坐标：${e.canonicalName}"
        }
    }

    private fun disambiguate(
        candidates: List<PlaceEntry>,
        year: Int?,
        range: IntRange?,
    ): List<PlaceEntry> {
        if (candidates.isEmpty()) return emptyList()
        val anchored = year != null || range != null

        fun matchLevel(c: PlaceEntry): Int = when {
            year != null && c.inSpan(year) -> 2
            range != null && c.overlaps(range) -> 1
            else -> 0
        }

        if (!anchored) return candidates.sortedWith(comparator(null))

        val active = candidates.filter { matchLevel(it) > 0 }
        val pool = if (active.isNotEmpty()) active else candidates
        return pool.sortedWith(comparator(year))
    }

    /** 排序：年代命中 > 非参考层 > 置信度 > Tier > 区间窄 > 点优先。 */
    private fun comparator(year: Int?): Comparator<PlaceEntry> = compareBy<PlaceEntry> {
        if (year != null && it.inSpan(year)) 0 else 1
    }.thenBy { if (it.refLayer) 1 else 0 }
        .thenBy { it.confidence.ordinal }
        .thenBy { it.tier.level }
        .thenBy { it.toYear - it.fromYear }
        .thenBy { if (it.kind == PlaceKind.POINT) 0 else 1 }

    private fun toEntry(e: PlaceEntity, matchedName: String): PlaceEntry {
        val snapshot = nameRows.firstOrNull { it.placeId == e.placeId && it.name == matchedName }
        val from = snapshot?.fromYear ?: e.fromYear
        val to = snapshot?.toYear ?: e.toYear
        val era = snapshot?.dynasty ?: PlaceLayers.overlaps(from..to).firstOrNull()?.era
        return PlaceEntry(
            placeId = e.placeId,
            name = matchedName,
            fromYear = from,
            toYear = to,
            tier = e.tier,
            source = snapshot?.source ?: e.source,
            kind = e.kind,
            confidence = e.confidence,
            channels = e.channels,
            refLayer = e.refLayer,
            aliases = e.aliases,
            superior = e.superior,
            era = era,
            note = e.note,
            geo = toGeo(e),
        )
    }

    private fun toGeo(e: PlaceEntity): PlaceGeo = PlaceGeo(
        placeId = e.placeId,
        kind = e.kind,
        tier = e.tier,
        source = e.source,
        confidence = e.confidence,
        channels = e.channels,
        refLayer = e.refLayer,
        lng = e.lng,
        lat = e.lat,
        polygon = e.polygon,
        regionLabel = e.regionLabel,
        radiusKm = e.radiusKm,
    )

    /** 称呼归一化：去首尾空白与包裹引号 / 括号，折叠内部空白；不做繁简转换（归一键表层另管）。 */
    fun normalize(raw: String): String {
        var s = raw.trim()
        val wrap = charArrayOf(
            '「', '」', '『', '』', '“', '”', '‘', '’', '"', '\'',
            '（', '）', '(', ')', '【', '】', '[', ']', '《', '》',
        )
        var changed = true
        while (changed && s.isNotEmpty()) {
            changed = false
            if (s.first() in wrap) { s = s.substring(1); changed = true }
            if (s.isNotEmpty() && s.last() in wrap) { s = s.substring(0, s.length - 1); changed = true }
        }
        return s.replace(Regex("\\s+"), "")
    }
}
