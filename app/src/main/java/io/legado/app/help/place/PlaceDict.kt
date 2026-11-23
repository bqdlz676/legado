package io.legado.app.help.place

/**
 * 模块 D3 · 地名 Provider 对外门面（冻结契约，下游 C1 时空锚定 / C2 气泡解析按此对接）。
 *
 * 冻结 API（签名不许改）：
 * - [find]：`find(name: String): List<PlaceEntry>` —— 返回该名全部候选（按可信度排序）；
 * - [PlaceGeo.of]：`of(placeId: String): PlaceGeo?` —— 返回坐标 / 色块 / 投影信息。
 *
 * 本对象只做转发，索引与消歧逻辑在 [PlaceDirectory]；纯 Kotlin stdlib，无 Android / UI 依赖。
 */
object PlaceDict {

    /** 按名称取全部候选（含同名异地、今址参考层），按可信度排序；查无返回空列表，不塞假数据。 */
    fun find(name: String): List<PlaceEntry> = PlaceDirectory.find(name)

    /** 按名称 + 单年锚点过滤；锚点年落在区间内者排前。 */
    fun find(name: String, year: Int?): List<PlaceEntry> =
        PlaceDirectory.find(name, year = year)

    /** 按名称 + 年代区间锚点过滤（如《左传》锚 -722~-453）；区间相交者排前。 */
    fun find(name: String, range: IntRange?): List<PlaceEntry> =
        PlaceDirectory.find(name, range = range)

    /** 按主键取地理信息；与 [PlaceGeo.of] 同一数据源。 */
    fun geoOf(placeId: String): PlaceGeo? = PlaceDirectory.geoOf(placeId)

    /** 按主键取实体；查无返回 null。 */
    fun entity(placeId: String): PlaceEntity? = PlaceDirectory.entity(placeId)

    /** 18 套朝代图层（区间与切片-05 表格逐字一致）。 */
    fun layers(): List<PlaceLayer> = PlaceLayers.ALL

    /** 全部在册条目（自检 / 遍历出处用）。 */
    fun all(): List<PlaceEntry> = PlaceDirectory.allEntries()

    /** 注册外部实体与名称快照（CHGIS 资产解析层调用）；校验不通过由目录层拒绝。 */
    fun register(records: List<PlaceEntity>, names: List<PlaceNameRow> = emptyList()) =
        PlaceDirectory.register(records, names)

    /**
     * 从候选中挑当前锚点下的默认命中：须 [PlaceEntry.canDefault]（非 low、非今址参考层）。
     * 无合格命中返回 null——不猜，由下游走「还有 N 个同名」候选切换。
     */
    fun defaultOf(candidates: List<PlaceEntry>): PlaceEntry? =
        candidates.firstOrNull { it.canDefault }
}
