package io.legado.app.ui.map.globe.offline

/**
 * 离线兜底分域（规格 §十二.8 / §十三.8）：地球域与星空域各自独立算档，互不串档。
 *
 * 为什么分域：旧聚合口径把 12 个种类混在同一个集合里算三档，星空瓦片缺失会把地球
 * 纹理误判成程序化档；分域后地球 / 星空各按本域种类集合独立算 FULL_TEXTURES /
 * CACHED_MIX / PROCEDURAL_ONLY，一域缺档不再拉低另一域。
 */
enum class FallbackDomain {
    /** 地球仪态 pass：卫星 / 夜景 / 云层 / 覆盖 / 高程 / 地名 / 河流 / 疆界 / 战役路线。 */
    EARTH,

    /** 星空 pass：星场背景与两档 Tycho-2 密度层。 */
    SKY,
}
