package io.legado.app.help.artifact

/**
 * D16 名物库 schema 表定义（锚点卡 §二；原文 09:19-24）。
 *
 * 本文件只落「表结构 + type 取值清单」，不造真实数据；草木 800 / 鸟兽 600 /
 * 药名 1800 / 山川 300+ 等数据量级由后续数据源单填充，本层不下载、不内置样例条目。
 *
 * 五张新表 type 取值与 09:19-24 逐字一致，顺序照抄原文，便于 grep 回证。
 */
data class SchemaTable(
    /** 表名（落库 / 回传字面值）。 */
    val name: String,
    /** 该表合法 type 取值，照抄 09:19-24 原文顺序。 */
    val types: List<String>,
) {

    /** [type] 是否为本表合法取值；空串 / 未知 type 一律 false。 */
    fun isValidType(type: String): Boolean = type in types
}

object ArtifactSchema {

    /** `artifact`（人造物，type=sword/horse/instrument/vessel/textile/vehicle/seal/jade/armor）。 */
    val ARTIFACT: SchemaTable = SchemaTable(
        name = "artifact",
        types = listOf("sword", "horse", "instrument", "vessel", "textile", "vehicle", "seal", "jade", "armor"),
    )

    /** `architecture`（建筑，type=temple/daoist/tower/palace/pavilion/bridge/tomb/academy）。 */
    val ARCHITECTURE: SchemaTable = SchemaTable(
        name = "architecture",
        types = listOf("temple", "daoist", "tower", "palace", "pavilion", "bridge", "tomb", "academy"),
    )

    /** `astronomy`（天文星宿，type=star/constellation/planet/asterism/calendar_term）。 */
    val ASTRONOMY: SchemaTable = SchemaTable(
        name = "astronomy",
        types = listOf("star", "constellation", "planet", "asterism", "calendar_term"),
    )

    /** `food`（饮食，type=wine/tea/dish/snack/ingredient）。 */
    val FOOD: SchemaTable = SchemaTable(
        name = "food",
        types = listOf("wine", "tea", "dish", "snack", "ingredient"),
    )

    /**
     * `landscape`（山川风物，type=mountain/river/lake/sea/waterfall/cave/boundary）。
     * 边界：不放 artifact，artifact 是人造物（09:23）；山川坐标归此表，城邑坐标归 place 表（09:37）。
     */
    val LANDSCAPE: SchemaTable = SchemaTable(
        name = "landscape",
        types = listOf("mountain", "river", "lake", "sea", "waterfall", "cave", "boundary"),
    )

    /** 本单新建的五张表。 */
    val NEW_TABLES: List<SchemaTable> = listOf(ARTIFACT, ARCHITECTURE, ASTRONOMY, FOOD, LANDSCAPE)

    /**
     * 既有表（09:24）：person+person_alias、place+place_name、plant、animal、medicine。
     * 这些表归 D1/D2/D3 原 Provider 路径，本层只登记名字，不重建、不双轨（09:31）。
     */
    val EXISTING_TABLES: List<String> = listOf(
        "person", "person_alias", "place", "place_name", "plant", "animal", "medicine",
    )

    /** 按表名取新表；既有表不在此返回（其结构归原模块）。 */
    fun table(name: String): SchemaTable? = NEW_TABLES.firstOrNull { it.name == name }

    /** 表名是否已知（新表 + 既有表）；未知表名供兜底路径识别。 */
    fun isKnownTable(name: String): Boolean =
        NEW_TABLES.any { it.name == name } || name in EXISTING_TABLES

    /** 校验「表名 + type」组合是否合法；未知表 / 未知 type 均返回 false，不抛异常。 */
    fun isValidType(tableName: String, type: String): Boolean {
        val table = table(tableName) ?: return false
        return table.isValidType(type)
    }
}
