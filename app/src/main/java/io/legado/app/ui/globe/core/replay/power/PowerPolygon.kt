package io.legado.app.ui.globe.core.replay.power

import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.geo.Geo
import io.legado.app.ui.globe.core.math.Vec3

/**
 * 势力范围多边形（纯 JVM，切片 18）。
 *
 * 切片 18：「12 套国外势力图层的 GeoJSON 多边形贴在球面上；势力多边形采样球面顶点
 * （128×128 网格），每个顶点查是否在某个势力多边形内 → 是就着色。」
 *
 * 顶点为公开经纬度（事实坐标，非文案）；多边形为历史地图集示意边界，tier 不代表精确疆界。
 * 点在多边形内用射线法（even-odd），纯几何，无 Android 依赖。
 */
data class PowerPolygon(
    val id: String,
    val name: String,
    val factionId: String,
    val colorHex: String,
    /** 色块不透明度 0..1（罗马暖金半透明）。 */
    val alpha: Float,
    /** 顶点序列：[纬度, 经度]，首尾可不闭合（判点算法自动回绕）。 */
    val pts: List<Pair<Double, Double>>,
) {

    /** 射线法判断 (lat, lng) 是否在多边形内。 */
    fun contains(lat: Double, lng: Double): Boolean {
        val n = pts.size
        if (n < 3) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val (yi, xi) = pts[i]
            val (yj, xj) = pts[j]
            val crossesLat = (yi > lat) != (yj > lat)
            if (crossesLat) {
                val xIntersect = (xj - xi) * (lat - yi) / (yj - yi) + xi
                if (lng < xIntersect) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** 球面方向向量 → 判点（先反算经纬度）。 */
    fun containsDir(dir: Vec3): Boolean {
        val (lat, lng) = Geo.latLng(dir)
        return contains(lat, lng)
    }
}

/** 网格命中：一个被势力覆盖的球面方向 + 所属势力。 */
data class PowerHit(
    val dir: Vec3,
    val power: PowerPolygon,
)

/** 贴球壳四边形：四个角点方向（未缩放半径）+ 势力色与透明度。 */
data class ShellQuad(
    val corners: List<Vec3>,
    val colorHex: String,
    val alpha: Float,
)

/**
 * 势力图层集合（纯 JVM）。按顺序判点，先命中者着色；重叠区以后列势力为准兜底。
 */
class PowerLayer(val powers: List<PowerPolygon>) {

    fun assign(lat: Double, lng: Double): PowerPolygon? =
        powers.firstOrNull { it.contains(lat, lng) }

    fun assignDir(dir: Vec3): PowerPolygon? {
        val (lat, lng) = Geo.latLng(dir)
        return assign(lat, lng)
    }

    /**
     * 采样球面网格（默认 128×128，切片 18）。
     * @return 所有落入某个势力内的格心方向；渲染层据此拼贴球壳色块。
     */
    fun sampleGrid(latSteps: Int = GlobeConfig.POWER_GRID_STEPS, lngSteps: Int = GlobeConfig.POWER_GRID_STEPS): List<PowerHit> {
        val hits = ArrayList<PowerHit>()
        if (powers.isEmpty()) return hits
        val latStep = 180.0 / latSteps
        val lngStep = 360.0 / lngSteps
        for (la in 0 until latSteps) {
            val lat = -90.0 + (la + 0.5) * latStep
            for (ln in 0 until lngSteps) {
                val lng = -180.0 + (ln + 0.5) * lngStep
                val power = assign(lat, lng) ?: continue
                hits.add(PowerHit(Geo.direction(lat, lng), power))
            }
        }
        return hits
    }

    /**
     * 生成贴球壳色块四边形（切片 18：128×128 网格采样判点着色）。
     * 一个格子四角归属同一势力时才出块；跨势力边界的格子跳过，避免串色。
     */
    fun shellQuads(
        latSteps: Int = GlobeConfig.POWER_GRID_STEPS,
        lngSteps: Int = GlobeConfig.POWER_GRID_STEPS,
    ): List<ShellQuad> {
        val quads = ArrayList<ShellQuad>()
        if (powers.isEmpty()) return quads
        val latStep = 180.0 / latSteps
        val lngStep = 360.0 / lngSteps
        for (la in 0 until latSteps) {
            val lat0 = -90.0 + la * latStep
            val lat1 = lat0 + latStep
            for (ln in 0 until lngSteps) {
                val lng0 = -180.0 + ln * lngStep
                val lng1 = lng0 + lngStep
                val corners = listOf(
                    lat0 to lng0, lat0 to lng1, lat1 to lng1, lat1 to lng0,
                )
                val assigned = corners.map { (lat, lng) -> assign(lat, lng) }
                val first = assigned.firstOrNull() ?: continue
                val same = assigned.all { it == first }
                if (!same) continue
                val dirs = corners.map { (lat, lng) -> Geo.direction(lat, lng) }
                quads.add(ShellQuad(dirs, first.colorHex, first.alpha))
            }
        }
        return quads
    }
}
