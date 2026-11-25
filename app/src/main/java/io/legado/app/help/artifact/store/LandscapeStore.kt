package io.legado.app.help.artifact.store

/**
 * 山川风物坐标条目（独立 `landscape` 表域；09:23/37）。
 *
 * 边界：山川坐标数据归 D16（存 landscape 表）；城邑坐标归地名库（存 place 表）。
 * 地图引擎各取各的——山川标签读 [LandscapeStore]，城池标签读 [PlaceStore]，两域不混读。
 *
 * 纯 JVM 数据类：坐标 / 年代不可考时为 null，不猜值；source 标注出处与许可（09:75）。
 */
data class LandscapeEntry(
    val id: String,
    val name: String,
    /** landscape 表 type：mountain/river/lake/sea/waterfall/cave/boundary。 */
    val type: String,
    val longitude: Double?,
    val latitude: Double?,
    /** 起始公元年，负数为公元前；年代不可考为 null（古湖泊随 18 朝代滑条联动的年代字段）。 */
    val start: Int?,
    val end: Int?,
    val source: String,
)

/**
 * 山川坐标分域读取接口（只定义契约，不实现真实数据源；真实 landcape 数据由后续组装单接入）。
 *
 * 为什么与 [PlaceStore] 分域：09:37 明确城池标签读 PlaceStore、山川标签读 LandscapeStore；
 * 若合并成一个地理 Store，城邑与山川的坐标口径、年代锚会互相串域，故接口层先拆开。
 */
interface LandscapeStore {

    /** 按名查山川条目；无命中返回空列表，不返回 null、不抛异常。 */
    fun queryByName(name: String): List<LandscapeEntry>
}
