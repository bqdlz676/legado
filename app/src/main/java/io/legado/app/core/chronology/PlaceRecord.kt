package io.legado.app.core.chronology

/**
 * 落文 · 地名时空本体记录（对齐 CHGIS / 中研院「中华文明之时空基础架构」字段）。
 *
 * 三层建模：
 *  - 实体 place      —— 点（城/关隘/渡口）| 面（政区/诸侯国/郡县），稳定不变；
 *  - 名称 place_name —— 同一实体随时间改名（长安/西京/京兆尹）；
 *  - 区间 start..end —— 同名靠「当前章是哪一年」消歧。
 *
 * 铁律：tier 3（文献推定）只画色块，不画点，避免把「大概在某县」伪装成「精确到百米」。
 *
 * 资产表（assets）：
 *  - place_entity.txt : id \t 类型(P/A) \t 正名 \t 经度 \t 纬度 \t 起年 \t 止年 \t 朝代
 *                       \t tier \t 说明 \t 别名(|) \t geometry \t 上级 \t source \t seat \t confidence
 *  - place_name.txt   : 名称 \t 实体id \t 起年 \t 止年 \t 朝代 \t 类型 \t 上级 \t source \t confidence
 */
data class PlaceRecord(
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
    val geometry: String? = null,
    val superior: String? = null,
    val source: String? = null,
    val seat: String? = null,
    val declaredConfidence: String? = null,
    val channels: Set<SourceChannel> = emptySet(),
    val refLayer: Boolean = false,
) {
    val isPoint: Boolean get() = kind == 'P'

    /** tier 3 只画色块：isPoint 且 tier 3 → 不落点 */
    val asPoint: Boolean get() = isPoint && tier <= 2

    val span: Int get() = end - start

    /** 显式列优先；缺省按三源交叉 + tier 推导 */
    val confidence: Confidence
        get() = declaredConfidence?.takeIf { it.isNotBlank() }
            ?.let { Confidence.of(it) }
            ?: Confidence.grade(channels, refLayer, tier)

    fun inSpan(year: Int): Boolean = year in start..end

    /** 候选年代区间是否与本实体存在年份交叠 */
    fun overlaps(range: IntRange): Boolean = start <= range.last && end >= range.first

    val tierLabel: String get() = when (tier) {
        1 -> "遗址实测"
        2 -> "考证坐标"
        else -> "文献推定"
    }

    fun spanLabel(): String =
        if (start == end) yearText(start) else "${yearText(start)}–${yearText(end)}"

    companion object {
        fun yearText(y: Int) = if (y <= 0) "前${1 - y}" else "$y"

        /** 解析 place_entity.txt 一行；注释 / 列数不足 / 坐标非法返回 null */
        fun parseEntityLine(line: String): PlaceRecord? {
            if (line.isBlank() || line.startsWith("#")) return null
            val p = line.split('\t')
            if (p.size < 9) return null
            val lng = p[3].toDoubleOrNull() ?: return null
            val lat = p[4].toDoubleOrNull() ?: return null
            val source = p.getOrNull(13)?.takeIf { it.isNotBlank() }
            val channel = SourceChannel.classify(source)
            val ref = channel == SourceChannel.REFERENCE
            return PlaceRecord(
                id = p[0].trim(),
                kind = if (p[1].trim().startsWith("A")) 'A' else 'P',
                name = p[2].trim(),
                lng = lng,
                lat = lat,
                start = p[5].trim().toIntOrNull() ?: -1046,
                end = p[6].trim().toIntOrNull() ?: 1911,
                era = p.getOrNull(7).orEmpty().trim(),
                tier = p.getOrNull(8)?.trim()?.toIntOrNull() ?: 3,
                note = p.getOrNull(9).orEmpty().trim(),
                aliases = p.getOrNull(10)
                    ?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList(),
                geometry = p.getOrNull(11)?.takeIf { it.isNotBlank() },
                superior = p.getOrNull(12)?.takeIf { it.isNotBlank() },
                source = source,
                seat = p.getOrNull(14)?.takeIf { it.isNotBlank() },
                declaredConfidence = p.getOrNull(15)?.takeIf { it.isNotBlank() },
                channels = setOfNotNull(channel),
                refLayer = ref,
            )
        }
    }
}

/** place_name.txt 一行：名称快照，回填到对应实体 */
data class NameLink(
    val name: String,
    val entityId: String,
    val start: Int,
    val end: Int,
    val era: String,
    val kind: String,               // 正名 | 别名
    val superior: String?,
    val source: String?,
    val confidence: String?,
) {
    val channel: SourceChannel? get() = SourceChannel.classify(source)

    companion object {
        fun parseNameLine(line: String): NameLink? {
            if (line.isBlank() || line.startsWith("#")) return null
            val p = line.split('\t')
            if (p.size < 2) return null
            return NameLink(
                name = p[0].trim(),
                entityId = p[1].trim(),
                start = p.getOrNull(2)?.trim()?.toIntOrNull() ?: -1046,
                end = p.getOrNull(3)?.trim()?.toIntOrNull() ?: 1911,
                era = p.getOrNull(4).orEmpty().trim(),
                kind = p.getOrNull(5).orEmpty().trim(),
                superior = p.getOrNull(6)?.takeIf { it.isNotBlank() },
                source = p.getOrNull(7)?.takeIf { it.isNotBlank() },
                confidence = p.getOrNull(8)?.takeIf { it.isNotBlank() },
            )
        }
    }
}
