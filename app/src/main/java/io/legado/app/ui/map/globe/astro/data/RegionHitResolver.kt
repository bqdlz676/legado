package io.legado.app.ui.map.globe.astro.data

import io.legado.app.ui.map.globe.geo.Geo

/**
 * 分野州域示意锚点（§十一.4 tier3）：周代州域无真实边界多边形，一期用锚点经纬度 +
 * 半径做最近邻命中；锚点表为近似示意，不冒充疆域轮廓。
 *
 * @property regionName 州域名（与 `field_allocations.csv` 的 regionName 同词表）
 * @property latDeg 代表点纬度（度，北纬为正）
 * @property lngDeg 代表点经度（度，东经为正）
 * @property radiusDeg 命中半径（度，球面夹角口径；示意覆盖范围，非疆界）
 */
data class RegionAnchor(
    val regionName: String,
    val latDeg: Double,
    val lngDeg: Double,
    val radiusDeg: Double,
)

/**
 * 地球侧点按 → 分野州域名反查（§十一.2 反向链路第一跳）。
 *
 * 〔解耦口径〕输入只收经纬度标量，输出只州域名字符串；不引用星空层类型，
 * 避免地球命中逻辑耦合进 astro 层。纯 JVM：不引 Canvas / Android，可直接单测。
 *
 * 〔消歧口径〕一锚点命中即返回；多锚点同时落在半径内取球面夹角最近者；
 * 夹角并列时取表内先出现者（列表序即优先级序）。
 */
class RegionHitResolver(
    private val anchors: List<RegionAnchor>,
) {

    /**
     * 最近邻命中：触点与锚点夹角 ≤ 锚点半径 → 返回该州域名。
     * 无锚点 / 全超半径 / 输入非有限值 → null（调用方保持地球仪态，不触发星空写回）。
     */
    fun resolve(latDeg: Double, lngDeg: Double): String? {
        if (!latDeg.isFinite() || !lngDeg.isFinite()) return null
        if (anchors.isEmpty()) return null
        val target = Geo.direction(latDeg, lngDeg)
        var bestName: String? = null
        var bestAngleDeg = Double.POSITIVE_INFINITY
        for (anchor in anchors) {
            if (!anchor.latDeg.isFinite() || !anchor.lngDeg.isFinite() ||
                !anchor.radiusDeg.isFinite() || anchor.radiusDeg <= 0.0
            ) continue
            val angleDeg = Geo.centralAngle(
                target, Geo.direction(anchor.latDeg, anchor.lngDeg),
            ) * Geo.RAD2DEG
            // 严格小于：并列夹角保留表内先出现者；半径内才纳入候选。
            if (angleDeg <= anchor.radiusDeg && angleDeg < bestAngleDeg) {
                bestName = anchor.regionName
                bestAngleDeg = angleDeg
            }
        }
        return bestName
    }

    companion object {
        /**
         * 内置示意锚点表（tier3，§十一.4）：13 个分野州域各一代表点 + 命中半径。
         *
         * 〔口径声明〕锚点为大致地理中心，半径为示意覆盖范围，不代表周代真实疆界；
         * 数据修订走本表，不另建资产 CSV（与 D38 数据单避让）。词表与
         * `assets/astro/field_allocations.csv` 的 13 个 regionName 一一对应。
         */
        fun defaultAnchors(): List<RegionAnchor> = listOf(
            RegionAnchor("兖州", 35.40, 116.80, 8.0),
            RegionAnchor("豫州", 34.70, 113.70, 8.0),
            RegionAnchor("幽州", 39.90, 116.40, 9.0),
            RegionAnchor("江", 31.30, 120.60, 7.0),
            RegionAnchor("扬州", 32.40, 119.00, 7.0),
            RegionAnchor("青州", 36.60, 118.50, 6.0),
            RegionAnchor("并州", 37.90, 112.50, 8.0),
            RegionAnchor("徐州", 34.20, 117.20, 7.0),
            RegionAnchor("冀州", 37.70, 115.40, 8.0),
            RegionAnchor("益州", 30.60, 104.10, 9.0),
            RegionAnchor("雍州", 34.34, 108.94, 8.0),
            RegionAnchor("三河", 34.60, 112.40, 7.0),
            RegionAnchor("荆州", 30.60, 114.30, 8.0),
        )
    }
}
