package io.legado.app.help.place

/**
 * 模块 D3 · 地名两表时空本体（规格切片-05 §「两表时空本体」「坐标可信度分层」「数据来源 CHGIS」）。
 *
 * 本包为纯数据层：只依赖 Kotlin stdlib，不持有 Android Context、不依赖 Compose / Material。
 * 气泡组装、地图渲染全部由下游 C1 / C2 层完成，本包只给「实体 + 名称快照 + 坐标元数据」。
 *
 * 两表对齐 CHGIS（Harvard China Historical GIS）字段约定：
 * - `place` 表（[PlaceEntity]）：稳定实体，点（城/关隘/渡口）或面（诸侯国/郡/府/县）；
 * - `place_name` 表（[PlaceNameRow]）：同一实体随时间改名的名称快照（长安 / 西京 / 京兆尹），
 *   每行自带自己的起止年份与出处，同名异地靠年代区间消歧。
 *
 * 时间编码照抄 CHGIS 约定：公元年用有符号整数，负数表示公元前（如 -722 = 鲁隐公元年），
 * 不设「公元前/公元后」布尔位，避免跨表拼接时正负号错位。
 */

// ---------- 坐标可信度分层（切片-05 §「坐标可信度分层」） ----------

/**
 * 学术渠道（三源交叉打分用）。与切片-05 所列三类来源一一对应，
 * [REFERENCE] 为今址参考层，独立成层、不参与三源打分。
 */
enum class PlaceChannel {
    /** 遗址实测：考古遗址实测点（Tier1）。 */
    ARCHAEOLOGY,

    /** 谭其骧《中国历史地图集》/ CHGIS 考证坐标（Tier2）。 */
    ATLAS,

    /** 文献推定 / 典籍地理注释（Tier3，精确到县）。 */
    TEXT,

    /** 今址参考层：现代同名异地对照，恒 low，永不进默认命中。 */
    REFERENCE,
}

/**
 * Tier 分层铁律：Tier3 文献推定只画色块，绝不画点——
 * 不允许把「大概在某县」伪装成「精确到百米」。
 */
enum class PlaceTier(val level: Int, val label: String, val defaultChannel: PlaceChannel) {

    /** Tier1：遗址实测（长安城遗址、郑韩故城、临淄、曲阜等考古实测点）。 */
    T1_SITE(1, "遗址实测", PlaceChannel.ARCHAEOLOGY),

    /** Tier2：谭其骧《中国历史地图集》/ CHGIS 考证坐标，必须标出处。 */
    T2_ATLAS(2, "谭其骧·CHGIS 考证", PlaceChannel.ATLAS),

    /** Tier3：文献推定、精确到县；只产出县界色块，不产出点位。 */
    T3_TEXT(3, "文献推定·精确到县", PlaceChannel.TEXT);

    companion object {
        fun of(level: Int): PlaceTier = entries.firstOrNull { it.level == level } ?: T3_TEXT
    }
}

/**
 * 出处标注（切片-02 §升格段「数据可信 = 命」：宁可 5000 条有出处，不要 9 万条没来路）。
 *
 * 本类型为冻结契约的一部分：新增来源种类前先回规格核对，不随意扩展。
 */
sealed class PlaceSource {

    /** 遗址实测。[site] 为遗址名，如「临淄齐国故城遗址」。 */
    data class Archaeology(val site: String) : PlaceSource()

    /** CHGIS 收录记录。[recordId] 透传 CHGIS place ID。 */
    data class Chgis(val recordId: String) : PlaceSource()

    /** 谭其骧《中国历史地图集》考证。[citation] 标注册次 / 图幅，如「谭图·先秦卷·晋图」。 */
    data class TanAtlas(val citation: String) : PlaceSource()

    /** 公版典籍地理推定。[citation] 标注到篇 / 卷，避开现代点校本版权表述。 */
    data class ClassicText(val citation: String) : PlaceSource()

    /** 今址参考层。[label] 为现代地名说明，如「今山西晋城市」。 */
    data class ModernReference(val label: String) : PlaceSource()

    /** 气泡 / 地名卡上直接展示的出处字符串，绝不允许为空。 */
    val label: String
        get() = when (this) {
            is Archaeology -> "考古遗址实测·$site"
            is Chgis -> "CHGIS #$recordId"
            is TanAtlas -> citation
            is ClassicText -> citation
            is ModernReference -> "今址参考层·$label"
        }
}

/**
 * 置信度口径（切片-05 + 模块契约 §时空锚定）：
 * - 3/3 = high，默认命中；
 * - 2/3 = medium，标「待校」，可进默认；
 * - ≤1/3 = low，标「待考·来源冲突」，不进默认命中；
 * - 今址参考层恒 low。
 */
enum class PlaceConfidence(
    val wire: String,
    val label: String,
    /** 是否允许进默认命中：只有 high / medium 可以；low 一律不进。 */
    val canDefault: Boolean,
) {
    HIGH("high", "", true),
    MEDIUM("medium", "待校", true),
    LOW("low", "待考·来源冲突", false);

    companion object {
        fun of(raw: String?): PlaceConfidence = when (raw?.trim()?.lowercase()) {
            "high" -> HIGH
            "medium" -> MEDIUM
            else -> LOW
        }

        /**
         * 三源交叉打分。
         * @param channels  该实体汇集到的去重学术渠道（实体行 + 名称快照行合并）
         * @param refLayer  是否今址参考层
         * @param tier      坐标层级 1 实测 / 2 考证 / 3 推定
         */
        fun grade(channels: Set<PlaceChannel>, refLayer: Boolean, tier: PlaceTier): PlaceConfidence {
            if (refLayer || PlaceChannel.REFERENCE in channels) return LOW
            val scholarly = channels - PlaceChannel.REFERENCE
            return when {
                // Tier1 遗址实测点按三源一致处理；其余按学术渠道交叉数：3/3 HIGH，2/3 MEDIUM，≤1/3 LOW
                tier == PlaceTier.T1_SITE -> HIGH
                scholarly.size >= 3 -> HIGH
                scholarly.size == 2 -> MEDIUM
                else -> LOW
            }
        }
    }
}

/** 点 / 面几何类型：'P' 点（城/关隘/渡口），'A' 面（诸侯国/郡/府/县）。 */
enum class PlaceKind(val code: Char) {
    POINT('P'),
    POLYGON('A');

    companion object {
        fun of(code: Char): PlaceKind = if (code == 'A') POLYGON else POINT
    }
}

// ---------- 两表本体 ----------

/**
 * `place` 表行：稳定时空实体。
 *
 * @param placeId        稳定主键；CHGIS 记录透传其 place ID，内置种子用 `seed:…` 前缀
 * @param kind           点 / 面
 * @param canonicalName  实体正名（当前主流称呼；历史改名见 [PlaceNameRow]）
 * @param fromYear       存在起年（公元年，负为公元前）
 * @param toYear         存在止年（公元年，负为公元前）
 * @param tier           坐标可信度分层
 * @param channels       汇集到的学术渠道（三源交叉打分用）
 * @param source         出处标注，不可为空
 * @param adminLevel     行政层级（对齐 CHGIS ADMIN_LEVEL，如 国 / 郡 / 县 / 城邑），可空
 * @param superior       上级政区实体名，可空
 * @param lng            经度（WGS84 近似）；Tier3 推定到县时可为 null，只给色块
 * @param lat            纬度；同 [lng]
 * @param polygon        GeoJSON 多边形（面实体 / 色块）；无真多边形时为 null，由 [radiusKm] 兜底
 * @param regionLabel    县界色块说明（Tier3 专用，如「今山西运城夏县一带」）
 * @param radiusKm       无真多边形时的色块兜底半径（km）；点位为 null
 * @param note           一句话沿革 / 治所迁移说明
 * @param aliases        其余别称（不随年代变化的归并称呼；年代改名走 place_name 表）
 * @param refLayer       今址参考层标记：true 时恒 low，永不进默认命中
 * @param confidenceOverride 显式置信度覆盖；空则按渠道 + tier 走三源打分
 */
data class PlaceEntity(
    val placeId: String,
    val kind: PlaceKind,
    val canonicalName: String,
    val fromYear: Int,
    val toYear: Int,
    val tier: PlaceTier,
    val channels: Set<PlaceChannel>,
    val source: PlaceSource,
    val adminLevel: String? = null,
    val superior: String? = null,
    val lng: Double? = null,
    val lat: Double? = null,
    val polygon: String? = null,
    val regionLabel: String? = null,
    val radiusKm: Double? = null,
    val note: String = "",
    val aliases: List<String> = emptyList(),
    val refLayer: Boolean = false,
    val confidenceOverride: PlaceConfidence? = null,
) {
    /** 三源交叉后的最终置信度：显式覆盖优先，否则按渠道 + tier 打分。 */
    val confidence: PlaceConfidence
        get() = confidenceOverride
            ?: PlaceConfidence.grade(channels, refLayer, tier)

    /** 年代区间是否覆盖给定公元年。 */
    fun inSpan(year: Int): Boolean = year in fromYear..toYear

    /** 与给定年代区间是否相交（《左传》这类书章给的是区间锚点，不是单年）。 */
    fun overlaps(range: IntRange): Boolean = fromYear <= range.last && toYear >= range.first
}

/**
 * `place_name` 表行：名称快照。同一实体在不同年代换名，每行自带起止年份与出处，
 * 消歧时先按年代选快照，再回到实体取坐标。
 *
 * @param name      名称（古名 / 别称）
 * @param placeId   回指 `place` 表实体
 * @param fromYear  该名称使用起年（负为公元前）
 * @param toYear    该名称使用止年（负为公元前）
 * @param dynasty   朝代图层名（展示用，可空）
 * @param source    本行名称的出处快照，不可为空
 */
data class PlaceNameRow(
    val name: String,
    val placeId: String,
    val fromYear: Int,
    val toYear: Int,
    val dynasty: String? = null,
    val source: PlaceSource,
)

/**
 * 地名候选条目（`find` 返回的扁平视图：实体字段 + 命中的名称快照合并）。
 *
 * 冻结对外契约：`PlaceEntry(placeId, name, fromYear, toYear, tier, source, ...)`，
 * 下游 C1 / C2 只读本类字段，不新增「凭地名猜坐标」的派生字段。
 */
data class PlaceEntry(
    val placeId: String,
    val name: String,
    val fromYear: Int,
    val toYear: Int,
    val tier: PlaceTier,
    val source: PlaceSource,
    val kind: PlaceKind,
    val confidence: PlaceConfidence,
    val channels: Set<PlaceChannel>,
    val refLayer: Boolean,
    val aliases: List<String>,
    val superior: String?,
    val era: String?,
    val note: String,
    val geo: PlaceGeo?,
) {
    /** 该候选是否允许成为当前锚点下的默认命中（非 low、非今址参考层）。 */
    val canDefault: Boolean get() = confidence.canDefault && !refLayer

    fun inSpan(year: Int): Boolean = year in fromYear..toYear

    fun overlaps(range: IntRange): Boolean = fromYear <= range.last && toYear >= range.first
}
