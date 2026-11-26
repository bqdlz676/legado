package io.legado.app.help.artifact.registry

import io.legado.app.help.artifact.ArtifactCategory
import io.legado.app.help.artifact.ArtifactRoute

/**
 * D16 十七类注册表（唯一单源；F-07 定稿名单，`docs/口径冻结-2026-11-24-15点后.md:20`；
 * 原文 09:11 / 09:13 / 09:68 / 09:72 / 09:75）。
 *
 * 为什么注册表与 Provider 分离：加新类别只在此追加一条 [ArtifactRegistration]，
 * 不改 Provider 路由代码、不改 UI（09:13）；Provider 只读本表做路由，不内嵌类清单。
 *
 * 与契约层枚举的关系：[ArtifactCategory] 是契约层先行落的 17 项骨架；F-07 其后定稿
 * 「典故不入此列、名酒/名食拆类、名锦名瓷服饰合并」，枚举回填归后续裁决片。本表按 F-07
 * 定稿登记，可回链的条目挂 [ArtifactRegistration.category]，暂不可回链的为 null，不硬挂旧枚举。
 *
 * 纯 JVM：不 import Android / Compose 类型。
 */
object ArtifactRegistry {

    /** 公版合成口径统一许可注记；全量数据接入时由数据片逐源补登许可状态（09:75）。 */
    private const val PD_SYNTH = "公版古籍辑录（著作权过期）；种子为公版合成口径，全量许可归数据片逐源登记"
    private const val PD_CC = "公版古籍辑录；第三方历史条目按原条目 CC BY-SA 标注；种子为公版合成口径"

    /** 十七类登记，顺序照抄 F-07 定稿名单，序号仅为清点。 */
    val REGISTRATIONS: List<ArtifactRegistration> = listOf(
        ArtifactRegistration(
            id = "person",
            displayName = "人物",
            schemaTable = "person",
            schemaTypes = emptyList(),
            assetPath = "artifact/seed/person.json",
            lazy = true,
            source = "既有 D2 人物库（CBDB 公版合成口径，本片不重复标注全量）",
            license = "既有库许可随 D2 登记；本片不重建人物数据（09:31 不双轨）",
            route = ArtifactRoute.EXISTING_SLOT,
            category = ArtifactCategory.PERSON,
            basis = "09:24 既有 person 表；D2 PersonProvider 原路径；09:31 不重构不双轨",
        ),
        ArtifactRegistration(
            id = "place",
            displayName = "地名",
            schemaTable = "place",
            schemaTypes = emptyList(),
            assetPath = "artifact/seed/place.json",
            lazy = true,
            source = "既有 D3 地名库（本片不重复标注全量）",
            license = "既有库许可随 D3 登记；本片不重建地名数据（09:31 不双轨）",
            route = ArtifactRoute.EXISTING_SLOT,
            category = ArtifactCategory.PLACE,
            basis = "09:24 既有 place 表；D3 PlaceProvider 原路径；山川另归 landscape 表",
        ),
        ArtifactRegistration(
            id = "plant",
            displayName = "草木",
            schemaTable = "plant",
            schemaTypes = listOf("plant"),
            assetPath = "artifact/seed/plant.json",
            lazy = true,
            source = "《毛诗草木鸟兽虫鱼疏》(陆玑·三国) / 《尔雅》/ ctext 草木标签",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.PLANT,
            basis = "09:29 规划 PlantProvider 草木约 800 种；09:68 草木出处",
        ),
        ArtifactRegistration(
            id = "animal",
            displayName = "鸟兽",
            schemaTable = "animal",
            schemaTypes = listOf("animal"),
            assetPath = "artifact/seed/animal.json",
            lazy = true,
            source = "《毛诗草木鸟兽虫鱼疏》/ 《山海经》/ ctext 鸟兽标签",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.ANIMAL,
            basis = "09:29 规划 AnimalProvider 鸟兽约 600 种；09:68 鸟兽出处",
        ),
        ArtifactRegistration(
            id = "medicine",
            displayName = "药名",
            schemaTable = "medicine",
            schemaTypes = listOf("medicine"),
            assetPath = "artifact/seed/medicine.json",
            lazy = true,
            source = "《神农本草经》(辑佚) / 《本草纲目》(1596)",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.MEDICINE,
            basis = "09:29 规划 MedicineProvider 药名约 1800 种；09:68 药名出处",
        ),
        ArtifactRegistration(
            id = "sword",
            displayName = "名剑",
            schemaTable = "artifact",
            schemaTypes = listOf("sword"),
            assetPath = "artifact/seed/sword.json",
            lazy = false,
            source = "《古今刀剑录》(陶弘景) / 《越绝书》",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.SWORD,
            basis = "09:30 其余类别走通用 EntityProvider；artifact type=sword（09:19）",
        ),
        ArtifactRegistration(
            id = "horse",
            displayName = "名马",
            schemaTable = "artifact",
            schemaTypes = listOf("horse"),
            assetPath = "artifact/seed/horse.json",
            lazy = false,
            source = "《相马经》/ 《史记》/ 《三国志》",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.HORSE,
            basis = "09:30 通用 EntityProvider；artifact type=horse（09:19）",
        ),
        ArtifactRegistration(
            id = "temple",
            displayName = "名寺(古刹)",
            schemaTable = "architecture",
            schemaTypes = listOf("temple"),
            assetPath = "artifact/seed/temple.json",
            lazy = false,
            source = "《洛阳伽蓝记》/ 《高僧传》/ Wikipedia 公开历史建筑条目",
            license = PD_CC,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.TEMPLE,
            basis = "09:30 通用 EntityProvider；architecture type=temple（09:20）",
        ),
        ArtifactRegistration(
            id = "daoist",
            displayName = "名道观",
            schemaTable = "architecture",
            schemaTypes = listOf("daoist"),
            assetPath = "artifact/seed/daoist.json",
            lazy = false,
            source = "《云笈七签》",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.DAOIST,
            basis = "09:30 通用 EntityProvider；architecture type=daoist（09:20）",
        ),
        ArtifactRegistration(
            id = "tower",
            displayName = "名楼(台阁)",
            schemaTable = "architecture",
            schemaTypes = listOf("tower", "pavilion"),
            assetPath = "artifact/seed/tower.json",
            lazy = false,
            source = "方志 / 《洛阳伽蓝记》/ 历代诗文题咏",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.TOWER,
            basis = "09:30 通用 EntityProvider；architecture type=tower/pavilion（09:20）",
        ),
        ArtifactRegistration(
            id = "instrument",
            displayName = "名琴(乐器)",
            schemaTable = "artifact",
            schemaTypes = listOf("instrument"),
            assetPath = "artifact/seed/instrument.json",
            lazy = false,
            source = "《琴史》(朱长文)",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.INSTRUMENT,
            basis = "09:30 通用 EntityProvider；artifact type=instrument（09:19）",
        ),
        ArtifactRegistration(
            id = "vessel",
            displayName = "名鼎(礼器)",
            schemaTable = "artifact",
            schemaTypes = listOf("vessel"),
            assetPath = "artifact/seed/vessel.json",
            lazy = false,
            source = "《金石录》(赵明诚) / 《史记》/ 出土器铭",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.VESSEL,
            basis = "09:30 通用 EntityProvider；artifact type=vessel（09:19）",
        ),
        ArtifactRegistration(
            id = "astronomy",
            displayName = "星宿(天文)",
            schemaTable = "astronomy",
            schemaTypes = listOf("star", "constellation", "planet", "asterism", "calendar_term"),
            assetPath = "artifact/seed/astronomy.json",
            lazy = false,
            source = "《史记·天官书》/ 《步天歌》(丹元子)",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.ASTRONOMY,
            basis = "09:30 通用 EntityProvider；astronomy 表五星宿子类（09:21）",
        ),
        ArtifactRegistration(
            id = "wine",
            displayName = "名酒",
            schemaTable = "food",
            schemaTypes = listOf("wine"),
            assetPath = "artifact/seed/wine.json",
            lazy = false,
            source = "《北山酒经》/ 《齐民要术》/ 历代诗文",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.FOOD,
            basis = "F-07 名酒 / 名食拆类；food type=wine（09:22）",
        ),
        ArtifactRegistration(
            id = "dish",
            displayName = "名食",
            schemaTable = "food",
            schemaTypes = listOf("dish"),
            assetPath = "artifact/seed/dish.json",
            lazy = false,
            source = "《随园食单》/ 《齐民要术》/ 《礼记·内则》",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = null,
            basis = "F-07 名酒 / 名食拆类；food type=dish（09:22）；枚举回填前 category 为 null",
        ),
        ArtifactRegistration(
            id = "textile",
            displayName = "名锦名瓷服饰",
            schemaTable = "artifact",
            schemaTypes = listOf("textile"),
            assetPath = "artifact/seed/textile.json",
            lazy = false,
            source = "《蜀锦谱》/ 《陶说》/ 《天工开物》",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.TEXTILE,
            basis = "F-07 合并项「名锦名瓷服饰」；artifact type=textile（09:19）",
        ),
        ArtifactRegistration(
            id = "landscape",
            displayName = "山川风物",
            schemaTable = "landscape",
            schemaTypes = listOf("mountain", "river", "lake", "sea", "waterfall", "cave", "boundary"),
            assetPath = "artifact/seed/landscape.json",
            lazy = true,
            source = "《史记·封禅书》/ 《尔雅·释山·释地》/ 《禹贡》/ 《周礼·职方氏》/ 《山海经》/ 《水经注》/ 《方舆纪要》/ 《云笈七签》",
            license = PD_SYNTH,
            route = ArtifactRoute.ENTITY_PROVIDER,
            category = ArtifactCategory.LANDSCAPE,
            basis = "09:23/37 独立 landscape 表；山川坐标归 LandscapeStore，城邑归 PlaceStore 两域不混",
        ),
    )

    /** 按机器 id 取登记；未登记返回 null（未知类别走兜底，不抛异常）。 */
    fun byId(id: String): ArtifactRegistration? = REGISTRATIONS.firstOrNull { it.id == id }

    /**
     * 按 schema type（代表 type 或子类型）或机器 id 反查登记。
     *
     * 为什么 type 与 id 都可查：适配层按 slot.schemaType 透传 type，索引页按类别 id 直查；
     * 两个入口共用同一张表，避免路由判断散落两处。子类型（constellation / river / pavilion
     * 等）归入所属类别，未在任何登记 schemaTypes 内的 type 返回 null 走兜底，不串类。
     */
    fun byType(type: String): ArtifactRegistration? =
        REGISTRATIONS.firstOrNull { type in it.schemaTypes } ?: byId(type)

    /** 按表名取该表全部登记；同表多类（如 food 名酒/名食）各自独立成条。 */
    fun byTable(tableName: String): List<ArtifactRegistration> =
        REGISTRATIONS.filter { it.schemaTable == tableName }

    /** 走通用 EntityProvider 的登记；既有槽类别不在此列，防双轨。 */
    val entityRegistrations: List<ArtifactRegistration> =
        REGISTRATIONS.filter { it.route == ArtifactRoute.ENTITY_PROVIDER }

    /** 既有槽承接的登记（人物 / 地名）；适配层禁止对其二次出气泡。 */
    val existingRegistrations: List<ArtifactRegistration> =
        REGISTRATIONS.filter { it.route == ArtifactRoute.EXISTING_SLOT }

    /** F-07 定稿逐字名单，供清点与单测逐字对照。 */
    val DISPLAY_NAMES: List<String> = REGISTRATIONS.map { it.displayName }
}
