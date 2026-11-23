package io.legado.app.help.place

/**
 * 地名坐标 / 色块 / 投影信息（切片-05 §「点面分离」「Tier3 只画色块，绝不画点」）。
 *
 * 冻结对外 API：[of] 按 [PlaceEntity.placeId] 取地理信息；查无此实体返回 null，
 * 由下游走 Missing 兜底，不崩溃、不空白态。
 *
 * 点面铁律：
 * - 只有 Tier1 / Tier2 的点实体才允许落点位；
 * - Tier3 文献推定只给县界色块（[regionLabel] / [polygon] / [radiusKm]），
 *   [canPlotPoint] 恒 false，下游禁止把它画成百米精度的点；
 * - 今址参考层坐标仅供对照，渲染时独立成层，不与古地图层混用。
 */
data class PlaceGeo(
    val placeId: String,
    val kind: PlaceKind,
    val tier: PlaceTier,
    val source: PlaceSource,
    val confidence: PlaceConfidence,
    val channels: Set<PlaceChannel>,
    val refLayer: Boolean,
    val lng: Double?,
    val lat: Double?,
    val polygon: String?,
    val regionLabel: String?,
    val radiusKm: Double?,
    val projection: String = "等经纬度投影（WGS84 近似）",
) {

    /**
     * 是否允许绘制点位。
     * 为什么这样判：Tier3 只推定到县，给点就是把「大概在某县」伪装成「精确到百米」；
     * 面实体、缺坐标、今址参考层同样不落点。
     */
    val canPlotPoint: Boolean
        get() = kind == PlaceKind.POINT &&
            tier.level <= 2 &&
            lng != null && lat != null &&
            !refLayer

    /** 是否只画色块 / 面：面实体、Tier3、或无点位坐标的实体。 */
    val isBlock: Boolean
        get() = kind == PlaceKind.POLYGON || tier == PlaceTier.T3_TEXT || !canPlotPoint

    companion object {

        /**
         * 按实体主键取地理信息；查无返回 null。
         * 转发到纯 Kotlin 目录 [PlaceDirectory]，本 companion 是冻结的对外入口。
         */
        fun of(placeId: String): PlaceGeo? = PlaceDirectory.geoOf(placeId)
    }
}
