package io.legado.app.ui.map.sandbox

/**
 * 地表采样点：经纬度 + 地形高度（世界单位）。
 *
 * 切片-06 §一：多边形不是浮在空中的线框，每个顶点都要落到地形表面上；
 * 渲染层按这些点拼贴贴合起伏的半透明色块。
 */
data class SurfacePoint(val lat: Double, val lng: Double, val height: Double)

/**
 * 势力范围多边形（纯 JVM，切片-06 §一）。
 *
 * @param role 己方 / 敌方 / 盟友 / 中立，决定填充色、边界色与不透明度
 * @param fromYear 起始年份（公元前为负）；null 表示不设下界
 * @param toYear 截止年份；null 表示不设上界
 * @param pts 顶点序列 [纬度, 经度]，首尾可不闭合，判点算法自动回绕
 */
data class PowerPolygon(
    val id: String,
    val name: String,
    val role: FactionRole,
    val fromYear: Int?,
    val toYear: Int?,
    val pts: List<LatLng>,
) {

    /** 该势力在给定年份是否可见；年份为 null（未锚定年代）时全部保留。 */
    fun eraActive(year: Int?): Boolean {
        if (year == null) return true
        if (fromYear != null && year < fromYear) return false
        if (toYear != null && year > toYear) return false
        return true
    }

    /** 填充不透明度：己方 0.12 / 敌方 0.10 / 盟友 0.08 / 中立 0（只画边界）。 */
    fun fillAlpha(): Float = when (role) {
        FactionRole.OWN -> SandboxConfig.OWN_FILL_ALPHA
        FactionRole.ENEMY -> SandboxConfig.ENEMY_FILL_ALPHA
        FactionRole.ALLY -> SandboxConfig.ALLY_FILL_ALPHA
        FactionRole.NEUTRAL -> SandboxConfig.NEUTRAL_FILL_ALPHA
    }

    /** 射线法（even-odd）判断 (lat, lng) 是否在多边形内；顶点不足 3 个时兜底为不在内。 */
    fun contains(lat: Double, lng: Double): Boolean {
        val n = pts.size
        if (n < 3) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val yi = pts[i].lat; val xi = pts[i].lng
            val yj = pts[j].lat; val xj = pts[j].lng
            val crossesLat = (yi > lat) != (yj > lat)
            if (crossesLat) {
                val xIntersect = (xj - xi) * (lat - yi) / (yj - yi) + xi
                if (lng < xIntersect) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** 把多边形顶点贴到地形表面：逐点取高度，生成地表采样序列。 */
    fun sampleSurface(elevation: TerrainElevation): List<SurfacePoint> =
        pts.map { p -> SurfacePoint(p.lat, p.lng, elevation.heightAt(p.lat, p.lng)) }

    /** 经纬度包围盒（快速排斥用，交界渐变配对前先粗筛）。 */
    fun bounds(): Pair<LatLng, LatLng> {
        val lats = pts.map { it.lat }
        val lngs = pts.map { it.lng }
        return LatLng(lats.min(), lngs.min()) to LatLng(lats.max(), lngs.max())
    }

    /** 包围盒是否与另一多边形相交（粗筛；精细交叠由渲染层 shader 处理）。 */
    fun boundsOverlap(other: PowerPolygon): Boolean {
        val (aMin, aMax) = bounds()
        val (bMin, bMax) = other.bounds()
        return aMin.lng <= bMax.lng && aMax.lng >= bMin.lng &&
            aMin.lat <= bMax.lat && aMax.lat >= bMin.lat
    }
}

/**
 * 势力图层集合（纯 JVM）。
 *
 * 按年代过滤后受 [SandboxConfig.MAX_POWER_SECTORS] 配额约束：超出时先保己方 / 敌方，
 * 盟友与中立依次裁掉，避免一屏十几块半透明色块互相叠脏。判点按顺序先命中者归属。
 */
class PowerLayer(val sectors: List<PowerPolygon>) {

    /** 年代过滤后的可见势力；year 为 null 时不过滤。 */
    fun visible(year: Int?): List<PowerPolygon> {
        val filtered = sectors.filter { it.eraActive(year) }
        if (filtered.size <= SandboxConfig.MAX_POWER_SECTORS) return filtered
        val priority = listOf(FactionRole.OWN, FactionRole.ENEMY, FactionRole.ALLY, FactionRole.NEUTRAL)
        val kept = ArrayList<PowerPolygon>()
        for (role in priority) {
            filtered.filter { it.role == role }.forEach {
                if (kept.size < SandboxConfig.MAX_POWER_SECTORS) kept.add(it)
            }
            if (kept.size >= SandboxConfig.MAX_POWER_SECTORS) break
        }
        return kept
    }

    /** 判点：返回该位置归属的势力；无归属返回 null。 */
    fun assign(lat: Double, lng: Double, year: Int?): PowerPolygon? =
        visible(year).firstOrNull { it.contains(lat, lng) }

    /**
     * 交界渐变配对：包围盒相交的可见势力两两成对。
     * 切片-06 §一：交叠处自动叠加一层微弱渐变，2fps 流动；配对结果供渲染层挂 shader。
     */
    fun blendZones(year: Int?): List<Pair<PowerPolygon, PowerPolygon>> {
        val list = visible(year)
        val zones = ArrayList<Pair<PowerPolygon, PowerPolygon>>()
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                if (list[i].boundsOverlap(list[j])) zones.add(list[i] to list[j])
            }
        }
        return zones
    }
}
