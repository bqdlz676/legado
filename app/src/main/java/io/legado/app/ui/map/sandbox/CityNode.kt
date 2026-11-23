package io.legado.app.ui.map.sandbox

/**
 * 城池级别（切片-06 §二 三级城池渲染）。
 * 都城打 12 单位金光柱 + 柱顶篆印章 + 金粉；郡邑打 6 单位铜光柱 + 静止光点；
 * 关隘 / 渡口不打柱，只画暖金脉冲圆点。
 */
enum class CityTier {
    CAPITAL,
    COUNTY,
    PASS_FERRY,
}

/**
 * 古城池节点（纯 JVM）。
 *
 * @param occupied 是否被敌军占领：占领后光柱暖金转朱砂红，柱旁粒子由上升变下落
 * @param fromYear / toYear 城池起止年代；null 表示不设界，供地名时空本体按年代显隐
 */
data class CityNode(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val tier: CityTier,
    val occupied: Boolean = false,
    val fromYear: Int? = null,
    val toYear: Int? = null,
) {

    fun eraActive(year: Int?): Boolean {
        if (year == null) return true
        if (fromYear != null && year < fromYear) return false
        if (toYear != null && year > toYear) return false
        return true
    }

    /** 光柱高度：都城 12 / 郡邑 6 / 关隘渡口 0（不打柱）。 */
    fun pillarHeight(): Double = when (tier) {
        CityTier.CAPITAL -> SandboxConfig.CAPITAL_PILLAR_HEIGHT
        CityTier.COUNTY -> SandboxConfig.COUNTY_PILLAR_HEIGHT
        CityTier.PASS_FERRY -> SandboxConfig.PASS_PILLAR_HEIGHT
    }

    /**
     * 柱旁金粉粒子数：每柱 8-12 粒；关隘渡口无柱无粒子。
     * 数量按城池 id 稳定散列到区间内，避免每次重建场景粒子数跳动。
     */
    fun particleCount(): Int {
        if (tier == CityTier.PASS_FERRY) return 0
        val span = SandboxConfig.PILLAR_PARTICLES_MAX - SandboxConfig.PILLAR_PARTICLES_MIN
        val hash = (id.hashCode() and 0x7FFFFFFF) % (span + 1)
        return SandboxConfig.PILLAR_PARTICLES_MIN + hash
    }

    /** 光柱 / 圆点配色：被占领走朱砂，否则按级别取金 / 铜。 */
    fun pillarColor(): Long = when {
        occupied -> SandboxTokens.textAccent
        tier == CityTier.COUNTY -> SandboxTokens.brandCopper
        else -> SandboxTokens.brandGold
    }

    /** 占领态粒子是否下落：true 下落，false 上升消散。 */
    fun particlesFall(): Boolean = occupied

    /** 柱底离地高度：地表上方浮 1 单位，不陷进地形。 */
    val baseLift: Double get() = SandboxConfig.PILLAR_BASE_LIFT
}

/**
 * 标签 atlas 条目（切片-06 §二 城池标签）。
 *
 * 所有城池竖排小字合并进同一张 atlas 纹理、一次 draw call；条目只记录行号与位置，
 * 纹理烘焙由渲染层完成，模型层不持有 Bitmap。
 *
 * @param row 在 atlas 中的行序（从上到下）
 */
data class LabelAtlasEntry(
    val city: CityNode,
    val row: Int,
)
