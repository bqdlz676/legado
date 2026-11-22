package io.legado.app.help

import android.content.Context
import io.legado.app.help.chrono.ContextAnchor

/**
 * 模块 8 · 点晋闭环 —— 地名坐标桥接层。
 *
 * 设计要点（对齐 core 契约 §一 / 数据 schema 契约）：
 *  - 不再读取无时间维度的旧 `place_geo.json`；坐标一律来自两表时空本体
 *    [PlaceTimeDict]（`place_entity.txt` 稳定实体 + `place_name.txt` 名称快照），
 *    按 [ContextAnchor] 锚点年代过滤：区间命中 → tier 高优先 → 区间窄优先 → 点优先。
 *  - 点面分离：点 = 城/国，面 = 郡/府（治所 seat 指回点）；同名异地按时间 + 上级政区分开。
 *  - 每条 [Geo] 透传 `source` 与 `confidence`（high | medium | low）；
 *    low 候选照收但 `isDefault = false`，不进默认命中。
 *  - 锚点年为空时不猜：返回候选中的首项仅作兜底参照，`isDefault = false`。
 */
object PlaceGeo {

    /**
     * 坐标 + 消歧元数据。
     *
     * @param id         实体 id（兜底手工坐标时为 null）
     * @param name       实体正名
     * @param lat        纬度
     * @param lng        经度
     * @param modern     今地 / 说明文案
     * @param zoom       地图初始缩放
     * @param kind       'P' 点（城/国）/ 'A' 面（郡/府）
     * @param tier       1 遗址实测 / 2 考证坐标 / 3 文献推定
     * @param start      存在起年（公元前为负）
     * @param end        存在止年
     * @param source     出处（CHGIS / 谭其骧 / ctext / 今址参考层 等）
     * @param confidence high | medium | low
     * @param aliases    别名列表
     * @param isDefault  是否为当前锚点下的默认命中
     */
    data class Geo(
        val id: String?,
        val name: String,
        val lat: Double,
        val lng: Double,
        val modern: String,
        val zoom: Int,
        val kind: Char = 'P',
        val tier: Int = 3,
        val start: Int? = null,
        val end: Int? = null,
        val source: String? = null,
        val confidence: String? = null,
        val aliases: List<String> = emptyList(),
        val isDefault: Boolean = true,
    ) {
        /** 兼容旧调用的兜底构造：无实体 id 的手工坐标 */
        constructor(name: String, lat: Double, lng: Double, modern: String, zoom: Int)
            : this(null, name, lat, lng, modern, zoom)

        val isPoint: Boolean get() = kind == 'P'
    }

    /** 预热两表本体（与 PlaceTimeDict 同一份缓存，不重复解析） */
    fun ensureLoaded(context: Context) {
        PlaceTimeDict.ensureLoaded(context)
    }

    /**
     * 按锚点年代消歧取最佳坐标。
     *
     * 链路：名字 → 两表候选 → 锚点年区间过滤 → tier / 区间宽度 / 点面排序。
     * 默认命中须同时满足：锚点年落在实体存在区间内、置信度非 low（契约 §一：
     * low 候选照收但 `isDefault = false`，不进默认命中）。
     * 锚点下有合格命中：返回该实体（`isDefault = true`）；
     * 命中不合格或锚点下无命中：退回候选首项作兜底参照（`isDefault = false`）；
     * 两表均无收录 / 名为空：返回 null（由上层走 Missing 兜底，不崩溃、不空白态）。
     */
    fun of(context: Context, name: String, year: Int? = ContextAnchor.getYear()): Geo? {
        if (name.isBlank()) return null
        val resolved = PlaceTimeDict.resolve(context, name, year)
        resolved.hit?.let { return fromEnt(it, isDefault = isDefaultCandidate(it, year)) }
        return resolved.others.firstOrNull()?.let { fromEnt(it, isDefault = false) }
    }

    /** 全部消歧候选（默认命中 + 同名异地），供气泡「切换同名」列表使用 */
    fun candidates(context: Context, name: String, year: Int? = ContextAnchor.getYear()): List<Geo> {
        if (name.isBlank()) return emptyList()
        val resolved = PlaceTimeDict.resolve(context, name, year)
        val defaultId = resolved.hit?.takeIf { isDefaultCandidate(it, year) }?.id
        return (listOfNotNull(resolved.hit) + resolved.others)
            .map { fromEnt(it, isDefault = it.id == defaultId) }
    }

    /**
     * 默认命中资格：置信度非 low，且锚点年落在存在区间内。
     * 锚点年为空时不猜（契约：无锚点不设默认）。
     */
    private fun isDefaultCandidate(e: PlaceTimeDict.Ent, year: Int?): Boolean {
        if (e.confidenceTag == "low") return false
        if (year == null) return false
        return year in e.start..e.end
    }

    /** 周边参照点：经纬度距离最近的若干实体，排除自身；面实体按其坐标点参与排序 */
    fun nearby(context: Context, geo: Geo, count: Int = 8): List<Geo> =
        PlaceTimeDict.all(context)
            .asSequence()
            .filter { it.id != geo.id }
            .sortedBy { dist2(it.lat, it.lng, geo.lat, geo.lng) }
            .take(count)
            .map { fromEnt(it, isDefault = false) }
            .toList()

    private fun fromEnt(e: PlaceTimeDict.Ent, isDefault: Boolean): Geo = Geo(
        id = e.id,
        name = e.name,
        lat = e.lat,
        lng = e.lng,
        modern = e.note,
        zoom = if (e.isPoint) 8 else 6,
        kind = e.kind,
        tier = e.tier,
        start = e.start,
        end = e.end,
        source = e.source,
        confidence = e.confidenceTag,
        aliases = e.aliases,
        isDefault = isDefault,
    )

    /** 平方距离（经度按 0.85 压扁，近似同纬度投影，仅用于就近排序） */
    private fun dist2(lat: Double, lng: Double, baseLat: Double, baseLng: Double): Double {
        val dx = (lng - baseLng) * 0.85
        val dy = lat - baseLat
        return dx * dx + dy * dy
    }
}
