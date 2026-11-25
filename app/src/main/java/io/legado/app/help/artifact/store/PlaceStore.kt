package io.legado.app.help.artifact.store

/**
 * 城邑 / 地名坐标条目（`place` 表域，归 D3 地名库；09:37）。
 *
 * 本文件只在 D16 契约层登记分域读取接口，供地图引擎城池标签消费；
 * 真实 place 数据源与实现归 D3 地名模块，本层不重建、不双轨（09:31）。
 */
data class PlaceEntry(
    val id: String,
    val name: String,
    val longitude: Double?,
    val latitude: Double?,
    val source: String,
)

/**
 * 城邑坐标分域读取接口（与 [LandscapeStore] 分立）。
 * 地图引擎城池标签读本接口；山川标签读 [LandscapeStore]，互不串域。
 */
interface PlaceStore {

    /** 按名查城邑条目；无命中返回空列表，不返回 null、不抛异常。 */
    fun queryByName(name: String): List<PlaceEntry>
}
