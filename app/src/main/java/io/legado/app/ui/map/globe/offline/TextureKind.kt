package io.legado.app.ui.map.globe.offline

/**
 * 地球仪所需的全部远程 / 本地素材种类（切片 20 §数据准备）。
 *
 * 每一类都必须有离线兜底：真实纹理未就位时，渲染层切到对应的程序化 / 种子降级源，
 * 不允许出现空贴图导致的空白球。
 */
enum class TextureKind(
    /** 数据来源口径（切片 20 表）。 */
    val sourceLabel: String,
    /** 无网 / 未缓存时的兜底策略。 */
    val fallback: FallbackKind,
    /** 所属离线兜底域：地球仪态 pass 或星空 pass（分域算档，§十三.8）。 */
    val domain: FallbackDomain,
) {
    /** Blue Marble 白天卫星纹理，多级金字塔 2K/4K/8K。 */
    DAY_SATELLITE("NASA Blue Marble", FallbackKind.PROCEDURAL_TERRAIN, FallbackDomain.EARTH),

    /** NASA Black Marble 城市夜景，只在暗半球叠加。 */
    NIGHT_CITY("NASA Black Marble", FallbackKind.PROCEDURAL_NIGHT_LIGHTS, FallbackDomain.EARTH),

    /** NASA Blue Marble 云层图，4K，独立 0.9 倍速旋转。 */
    CLOUDS("NASA 云层图", FallbackKind.PROCEDURAL_CLOUD_LAYER, FallbackDomain.EARTH),

    /** Natural Earth / ESA WorldCover 土地覆盖 PNG，7 种覆盖类型。 */
    LANDCOVER("Natural Earth / ESA WorldCover", FallbackKind.PROCEDURAL_COVER_BAND, FallbackDomain.EARTH),

    /** SRTM30 全球高程 PNG。 */
    ELEVATION("SRTM30 全球高程", FallbackKind.PROCEDURAL_HEIGHTMAP, FallbackDomain.EARTH),

    /** 星空纹理（程序化生成或 Stellarium 星图，2K）。 */
    STAR_SKY("Stellarium 星图 / 程序化", FallbackKind.PROCEDURAL_STAR_FIELD, FallbackDomain.SKY),

    /** Tycho-2 密度层 9 等（按需下载，不入库）；未缓存走程序化星场兜底。 */
    SKY_DENSITY_L9("Tycho-2 密度层 9 等（按需下载，不入库）", FallbackKind.PROCEDURAL_STAR_FIELD, FallbackDomain.SKY),

    /** Tycho-2 密度层 11.5 等（按需下载，不入库）；未缓存走程序化星场兜底。 */
    SKY_DENSITY_L11("Tycho-2 密度层 11.5 等（按需下载，不入库）", FallbackKind.PROCEDURAL_STAR_FIELD, FallbackDomain.SKY),

    /** 地名 atlas 纹理（中国 500 + 世界 500）。 */
    LABEL_ATLAS("CHGIS + Pleiades + Wikidata", FallbackKind.SEED_PLACE_ATLAS, FallbackDomain.EARTH),

    /** Natural Earth 河流矢量，转球面 CatmullRom 弧。 */
    RIVER_VECTOR("Natural Earth 河流矢量", FallbackKind.SEED_VECTOR_NONE, FallbackDomain.EARTH),

    /** 历史疆界 GeoJSON（中国 18 + 世界 12）。 */
    POWER_VECTOR("CHGIS + Natural Earth 疆界", FallbackKind.SEED_POWER_SEED, FallbackDomain.EARTH),

    /** 战役路线 / 推演战役 JSON。 */
    CAMPAIGN_ROUTES("Wikidata + Wikipedia / campaign.json", FallbackKind.SEED_CAMPAIGN_SEED, FallbackDomain.EARTH);
}

/**
 * 降级兜底类型。渲染层按它选择 pass：
 * 真实纹理 → 缓存纹理 → 程序化 / 种子数据；每一级都能成图，逐级退化但不空白。
 */
enum class FallbackKind {
    /** 用 fbm 地形源推算海陆 / 高程 / 覆盖，画程序化地球。 */
    PROCEDURAL_TERRAIN,

    /** 暗半球用种子都城金点点缀，不依赖夜景贴图。 */
    PROCEDURAL_NIGHT_LIGHTS,

    /** 用确定性噪声画薄云壳。 */
    PROCEDURAL_CLOUD_LAYER,

    /** 用噪声 band 分级覆盖类型。 */
    PROCEDURAL_COVER_BAND,

    /** 用 fbm 高程代替 SRTM。 */
    PROCEDURAL_HEIGHTMAP,

    /** 程序化均匀球面星点（StarField）。 */
    PROCEDURAL_STAR_FIELD,

    /** 内置种子地名 atlas（SeedAtlas）。 */
    SEED_PLACE_ATLAS,

    /** 无河流矢量时不画河流 pass，其余照常。 */
    SEED_VECTOR_NONE,

    /** 内置 12 套种子势力多边形。 */
    SEED_POWER_SEED,

    /** 内置 6 场种子战役。 */
    SEED_CAMPAIGN_SEED,
}
