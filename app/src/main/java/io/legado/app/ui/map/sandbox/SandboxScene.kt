package io.legado.app.ui.map.sandbox

/**
 * 沙盘场景聚合（纯 JVM，切片-06 §一~§四 + 性能硬上限表）。
 *
 * 持有势力 / 城池 / 河流 / 遗址 / 关隘 / 尾迹六类图层，按当前年代过滤，并把切片-06
 * 的性能配额（势力 ≤12、都城光柱 ≤8、光柱粒子 ≤96、河流 ≤8、标签 1 张 atlas、
 * draw call ≤20）在模型层就核算清楚；渲染层只取核算结果，不在绘制时再临时砍对象。
 *
 * 边界兜底：任一图层为空都不抛异常，对应渲染 pass 跳过；年代未锚定时全部保留。
 */
class SandboxScene(
    val powers: PowerLayer,
    val cities: List<CityNode>,
    val rivers: List<RiverLine>,
    val sites: List<SitePin>,
    val chokes: List<ChokePoint>,
    val trails: List<MarchTrail>,
    val elevation: TerrainElevation = TerrainElevation.Flat,
) {

    /** 当前锚定年代（公元前为负）；null 表示未锚定，图层不做年代过滤。 */
    var year: Int? = null

    // ── 势力图层 ──

    fun visiblePowers(): List<PowerPolygon> = powers.visible(year)

    /** 势力交界渐变配对（shader 2fps 流动的输入）。 */
    fun blendZones(): List<Pair<PowerPolygon, PowerPolygon>> = powers.blendZones(year)

    // ── 城池图层 ──

    /** 城池渲染条目：[pillar] = true 竖立光柱；false 降级为地面亮点（关隘圆点 / 超额都城）。 */
    data class CityRender(val city: CityNode, val pillar: Boolean)

    fun cityRender(): List<CityRender> {
        val active = cities.filter { it.eraActive(year) }
        val capitals = active.filter { it.tier == CityTier.CAPITAL }
        val others = active.filter { it.tier != CityTier.CAPITAL }
        // 切片-06：同一时刻最多 8 根都城光柱，其余降级为亮点；郡邑 / 关隘本就不占都城配额。
        val withPillar = capitals.take(SandboxConfig.MAX_CAPITAL_PILLARS)
        val downgraded = capitals.drop(SandboxConfig.MAX_CAPITAL_PILLARS)
        return withPillar.map { CityRender(it, true) } +
            others.map { CityRender(it, false) } +
            downgraded.map { CityRender(it, false) }
    }

    /** 有光柱的城池（渲染光柱 mesh 用）。 */
    fun pillarCities(): List<CityNode> = cityRender().filter { it.pillar }.map { it.city }

    /**
     * 单根光柱在 96 粒总预算内可分配的粒子数。
     * 先按原始 8-12 粒统计总量；超预算时按比例削减，优先保证每柱不下限 1 粒，
     * 避免极端数据下粒子总数冲破切片-06 上限。
     */
    fun budgetedParticles(city: CityNode): Int {
        val pillars = pillarCities()
        val raw = pillars.associateWith { it.particleCount() }
        val total = raw.values.sum()
        if (total <= SandboxConfig.MAX_PILLAR_PARTICLES) return raw[city] ?: 0
        val scale = SandboxConfig.MAX_PILLAR_PARTICLES.toDouble() / total
        val per = ((raw[city] ?: 0) * scale).toInt()
        return per.coerceIn(1, SandboxConfig.PILLAR_PARTICLES_MAX)
    }

    /** 标签 atlas 条目：全部城池竖排小字合进同一张纹理，行序即渲染排布顺序。 */
    fun labelAtlas(): List<LabelAtlasEntry> =
        cityRender().mapIndexed { index, render -> LabelAtlasEntry(render.city, index) }

    // ── 河流图层 ──

    fun visibleRivers(): List<RiverLine> {
        val active = rivers.filter { it.eraActive(year) && it.isValid() }
        // 同名河（黄河三版改道）每代只选一条；选版规则见 [RiverLine.selectVersion]。
        val selected = active.groupBy { it.name }.values.mapNotNull {
            RiverLine.selectVersion(it, year)
        }
        return selected.take(SandboxConfig.MAX_RIVERS)
    }

    // ── 叠加图层 ──

    fun visibleSites(): List<SitePin> = sites

    fun visibleChokes(): List<ChokePoint> = chokes

    fun visibleTrails(): List<MarchTrail> = trails

    // ── 性能核算 ──

    /**
     * draw call 估算：势力色块 / 光柱 / 河流各占 1 次；遗址方钉、关隘暗红短线同为
     * 同色同宽小图元，各自整批合批 1 次；标签 atlas 合批 1 次；尾迹逐条计。
     * 关隘短线若逐条计会把种子场景顶破 ≤20 红线，故与方钉走同一合批口径。
     * 超出 [SandboxConfig.MAX_DRAW_CALLS] 时渲染层按势力 → 河流 → 尾迹顺序降配合批；
     * 模型层只如实上报，不偷偷改数据。
     */
    fun estimateDrawCalls(): Int {
        var calls = 0
        calls += visiblePowers().size
        calls += pillarCities().size
        calls += visibleRivers().size
        calls += visibleTrails().size
        if (visibleChokes().isNotEmpty()) calls += 1 // 关隘短线合批
        calls += 1 // 遗址方钉合批
        calls += SandboxConfig.LABEL_ATLAS_COUNT // 标签 atlas 合批
        return calls
    }

    /** 是否超出 draw call 红线（供单测 / 渲染层降级判断）。 */
    fun overDrawCallBudget(): Boolean = estimateDrawCalls() > SandboxConfig.MAX_DRAW_CALLS

    /** 雾气起始距离（世界单位）：地形边缘 260 单位处开始起雾。 */
    val fogStartUnits: Double get() = SandboxConfig.FOG_START_UNITS
}
