package io.legado.app.help.artifact

/**
 * D16 十七类 → 气泡槽位的唯一装配登记表（P06 单源）。
 *
 * 为什么只有这一处定义：09:31 冻结「D1/D2/D3 走原 Provider 路径不双轨」；人物 / 地名 / 典故
 * 已被既有槽位承接，适配层不得对这三类二次出气泡；其余类别由通用 [EntityProvider] 泛化
 * 入口承接。本表是「类 → 承接方」的唯一定义，Resolver 与后续 UI / 名物索引页都只读本表，
 * 不许在别处另写一份类槽映射（单源断言见 ArtifactSlotsTest）。
 *
 * 纯 JVM：不 import Android / Compose 类型，普通 JVM 单测可直接遍历断言。
 */
enum class ArtifactRoute {

    /** 既有七槽 Provider 承接（人物 / 地名 / 典故）；不经过 EntityProvider，防双轨气泡。 */
    EXISTING_SLOT,

    /** 通用 EntityProvider 泛化入口承接；数据未接时由 EmptyEntityProvider 走 B25 五态兜底。 */
    ENTITY_PROVIDER,
}

/**
 * 单条类槽登记。
 *
 * @param category    十七类条目（[ArtifactCategory]）。
 * @param route       承接路由：既有槽 or 通用实体入口。
 * @param schemaTable 落库 schema 表名（既有表或新表）；典故冲突裁决回填前为 null。
 * @param schemaType  传给 [EntityProvider.query] 的代表 type；多子类别时取首项，全量子类见 [ArtifactSchema]。
 * @param existingSlot 既有槽位名（date/allusion/person/place/word/char/missing）；ENTITY_PROVIDER 路径为 null。
 * @param basis       归属依据（原文锚点 / 既有 Provider 与接线单），供验收逐行回源。
 */
data class ArtifactSlot(
    val category: ArtifactCategory,
    val route: ArtifactRoute,
    val schemaTable: String?,
    val schemaType: String?,
    val existingSlot: String?,
    val basis: String,
)

object ArtifactSlots {

    /** 既有七槽合法槽位名清单（装配完整性断言与回证用）。 */
    const val SLOT_DATE = "date"
    const val SLOT_ALLUSION = "allusion"
    const val SLOT_PERSON = "person"
    const val SLOT_PLACE = "place"
    const val SLOT_WORD = "word"
    const val SLOT_CHAR = "char"
    const val SLOT_MISSING = "missing"

    /** 既有七槽槽位名全集；登记表里 existingSlot 取值必须落在此集合内。 */
    val EXISTING_SLOT_NAMES: Set<String> = setOf(
        SLOT_DATE, SLOT_ALLUSION, SLOT_PERSON, SLOT_PLACE, SLOT_WORD, SLOT_CHAR, SLOT_MISSING,
    )

    private val ALL: List<ArtifactSlot> = listOf(
        ArtifactSlot(
            category = ArtifactCategory.PERSON,
            route = ArtifactRoute.EXISTING_SLOT,
            schemaTable = "person",
            schemaType = null,
            existingSlot = SLOT_PERSON,
            basis = "09:24 既有 person 表；D2 PersonProvider 原路径；09:31 不重构不双轨",
        ),
        ArtifactSlot(
            category = ArtifactCategory.PLACE,
            route = ArtifactRoute.EXISTING_SLOT,
            schemaTable = "place",
            schemaType = null,
            existingSlot = SLOT_PLACE,
            basis = "09:24 既有 place 表；D3 PlaceProvider 原路径；山川另归 landscape 表，不并入地名槽",
        ),
        ArtifactSlot(
            category = ArtifactCategory.PLANT,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "plant",
            schemaType = "plant",
            existingSlot = null,
            basis = "09:29 规划 PlantProvider 草木 800 种尚未落地；本单由通用 EntityProvider 占位承接，数据组装单替换",
        ),
        ArtifactSlot(
            category = ArtifactCategory.ANIMAL,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "animal",
            schemaType = "animal",
            existingSlot = null,
            basis = "09:29 规划 AnimalProvider 鸟兽 600 种尚未落地；EntityProvider 占位承接",
        ),
        ArtifactSlot(
            category = ArtifactCategory.MEDICINE,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "medicine",
            schemaType = "medicine",
            existingSlot = null,
            basis = "09:29 规划 MedicineProvider 药名 1800 种尚未落地；EntityProvider 占位承接",
        ),
        ArtifactSlot(
            category = ArtifactCategory.SWORD,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "artifact",
            schemaType = "sword",
            existingSlot = null,
            basis = "09:30 其余类别走通用 EntityProvider，不加新 Provider",
        ),
        ArtifactSlot(
            category = ArtifactCategory.HORSE,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "artifact",
            schemaType = "horse",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接（artifact horse）",
        ),
        ArtifactSlot(
            category = ArtifactCategory.TEMPLE,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "architecture",
            schemaType = "temple",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接（architecture temple）",
        ),
        ArtifactSlot(
            category = ArtifactCategory.DAOIST,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "architecture",
            schemaType = "daoist",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接（architecture daoist）",
        ),
        ArtifactSlot(
            category = ArtifactCategory.TOWER,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "architecture",
            schemaType = "tower",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接；pavilion 并入本类，子类见 ArtifactSchema ARCHITECTURE",
        ),
        ArtifactSlot(
            category = ArtifactCategory.INSTRUMENT,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "artifact",
            schemaType = "instrument",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接（artifact instrument）",
        ),
        ArtifactSlot(
            category = ArtifactCategory.VESSEL,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "artifact",
            schemaType = "vessel",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接（artifact vessel）",
        ),
        ArtifactSlot(
            category = ArtifactCategory.ASTRONOMY,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "astronomy",
            schemaType = "star",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接；star/constellation/planet/asterism/calendar_term 子类见 ArtifactSchema",
        ),
        ArtifactSlot(
            category = ArtifactCategory.FOOD,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "food",
            schemaType = "wine",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接；wine/tea/dish/snack/ingredient 子类见 ArtifactSchema",
        ),
        ArtifactSlot(
            category = ArtifactCategory.TEXTILE,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "artifact",
            schemaType = "textile",
            existingSlot = null,
            basis = "09:30 通用 EntityProvider 承接（artifact textile）",
        ),
        ArtifactSlot(
            category = ArtifactCategory.ALLUSION,
            route = ArtifactRoute.EXISTING_SLOT,
            schemaTable = null,
            schemaType = null,
            existingSlot = SLOT_ALLUSION,
            basis = "P04 已接 WordProvider matchAllusion；09:11 列入 vs F-07 剔除典故冲突待裁；裁决回填前不挂 D16 表，适配层不对典故二次出气泡",
        ),
        ArtifactSlot(
            category = ArtifactCategory.LANDSCAPE,
            route = ArtifactRoute.ENTITY_PROVIDER,
            schemaTable = "landscape",
            schemaType = "mountain",
            existingSlot = null,
            basis = "09:23/37 独立 landscape 表；山川坐标归 LandscapeStore，城邑归 PlaceStore，两域不混",
        ),
    )

    /** 十七类 → 槽位登记；键唯一，装配完整性与单源断言以此为准。 */
    val byCategory: Map<ArtifactCategory, ArtifactSlot> = ALL.associateBy { it.category }

    /** 走通用 EntityProvider 的类别；适配层只遍历这一组，其余类别不重复查询。 */
    val entitySlots: List<ArtifactSlot> = ALL.filter { it.route == ArtifactRoute.ENTITY_PROVIDER }

    /** 既有槽承接的类别；适配层禁止对这三组二次出气泡，防双轨。 */
    val existingSlots: List<ArtifactSlot> = ALL.filter { it.route == ArtifactRoute.EXISTING_SLOT }

    /** 按类别取登记；十七类之外的未知类别调用方应先走 [ArtifactCategory.byWire] 判空。 */
    fun slotOf(category: ArtifactCategory): ArtifactSlot = byCategory.getValue(category)
}
