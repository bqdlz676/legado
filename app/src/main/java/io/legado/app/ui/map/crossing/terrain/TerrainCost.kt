package io.legado.app.ui.map.crossing.terrain

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.TerrainKind

/**
 * 行军代价表（纯 JVM）。
 *
 * 寻路代价（§二 L64）：代价 = 距离 × 坡度惩罚 × 路况。
 * 行军速度（§三 L80）：基础速度 × 路况速度系数 × 天候折减(≤1) × 疲劳惩罚。
 *
 * 路况代价系数：平地 < 丘陵 < 山地 < 河流；涉渡点代价高于平地但可过，非涉渡水面由
 * [TerrainMap.passable] 直接判堵。天候在速度侧是 ≤1 的折减系数（坏天气只拖慢、绝不加速），
 * 在寻路代价侧取其倒数（坏天气路径更贵）；疲劳折减行军速度。
 * 所有系数收口在本对象，寻路与回合移动读同一张表，避免两处口径漂移。
 */
object TerrainCost {

    /** 路况基础代价系数（寻路 A* 用；越高越难走）。 */
    fun terrainFactor(kind: TerrainKind, isFord: Boolean): Double = when (kind) {
        TerrainKind.PLAIN -> 1.0
        TerrainKind.HILL -> 1.4
        TerrainKind.MOUNTAIN -> 2.0
        TerrainKind.SNOW -> 2.5
        TerrainKind.WATER -> if (isFord) 3.0 else 99.0
    }

    /** 坡度惩罚：坡度越陡代价越高，封顶 2 倍。 */
    fun slopePenalty(slopeDeg: Double): Double =
        (1.0 + slopeDeg / SLOPE_FULL_DEG).coerceIn(1.0, 2.0)

    /**
     * 天候折减系数（速度侧，恒 ≤1）：坏天气只拖慢行军；晴朗 / 缺省为基准 1.0，任何天气都不加速。
     *
     * 符号为什么是 ≤1（B2 锁定）：本系数在 [marchSpeed] 里以**乘法**进入速度预算，
     * 乘一个 ≤1 的数只会把速度往下压；若任一档取 >1，坏天气反而提速，即为方向反转
     * （旧提交 22cefd1 事故：rain 1.30 / snow 1.50 / fog 1.20 / wind 1.10，已回归）。
     *
     * 单调口径（规格 §三 L80 速度预算）：clear ≥ fog ≥ wind ≥ rain/mud > snow。
     * 取值依据（按通行受阻程度定性标定，非实测气象数据）：
     *  - clear / null / 未知 / 阴(overcast/cloudy)：1.00（基准，非恶劣天候，不折减也不加成）
     *  - fog / foggy / mist：0.90（能见度下降，小幅折减）
     *  - wind / windy：0.85（顶风阻力，折减略大于雾）
     *  - rain / rainy / mud：0.75（泥泞拖慢）
     *  - snow / snowy：0.60（积雪最滑，折减最大）
     * 寻路代价侧见 [enterCost]，取本系数倒数，不另立口径。
     */
    fun weatherModifier(condition: String?): Double = when (condition?.trim()?.lowercase()) {
        "fog", "foggy", "mist" -> 0.90
        "wind", "windy" -> 0.85
        "rain", "rainy", "mud" -> 0.75
        "snow", "snowy" -> 0.60
        else -> 1.0
    }

    /**
     * 路况速度系数：取 [terrainFactor] 的倒数——寻路代价越高的地形，日行格数越少
     * （平地 > 丘陵 > 山地 > 雪地）。非涉渡水面不可通行（由 [TerrainMap.passable] 判堵），
     * 不会站上 corps 的速度预算；WATER 此处按涉渡口径取代价系数。
     */
    private fun terrainSpeedFactor(kind: TerrainKind): Double =
        1.0 / terrainFactor(kind, kind == TerrainKind.WATER)

    /** 进入单元的综合代价（寻路用）；不可通行返回 [BLOCKED_COST]。 */
    fun enterCost(
        cell: Cell,
        type: ArmyType,
        map: TerrainMap,
        weatherCondition: String?,
    ): Double {
        if (!map.passable(cell, type)) return BLOCKED_COST
        val kind = map.kindAt(cell)
        val base = terrainFactor(kind, map.isFord(cell))
        val slope = slopePenalty(map.slopeDeg(cell))
        val weather = weatherModifier(weatherCondition)
        // 符号方向：代价侧取天候倒数——折减越小（天气越坏），base/weather 越大、路径越贵；
        // 这里不能写成 *weather，否则坏天气路径反而更便宜，与速度侧折减方向相反。
        return base * slope / weather
    }

    /**
     * 行军速度系数（回合移动用）：基础速度 × 天候折减(≤1) × 路况速度系数 × 疲劳惩罚。
     * 规格 §三 L80：行军速度 = 基础速度 × 路况系数 × 疲劳惩罚；天候在此基础上再乘一层折减。
     * 路况速度系数 [terrainSpeedFactor] 在此**相乘消费**（不是只算不用），返回值直接被
     * `dailyMaxCells` 取作日行预算；漏乘本因子会导致各类地形日行格数全部等于基础值。
     * 疲劳 0..5，每点折减 12%；断粮加速疲劳由 MoraleSystem 负责，这里只读当前疲劳。
     */
    fun marchSpeed(
        type: ArmyType,
        fatigue: Double,
        weatherCondition: String?,
        kind: TerrainKind,
    ): Double {
        val fatiguePenalty = 1.0 / (1.0 + fatigue * FATIGUE_SPEED_PENALTY)
        return type.speedBase * weatherModifier(weatherCondition) * terrainSpeedFactor(kind) * fatiguePenalty
    }

    /** 不可通行代价上限（A* 中视为堵死）。 */
    const val BLOCKED_COST: Double = 1e9

    /** 坡度达到该角度时惩罚翻倍封顶。 */
    const val SLOPE_FULL_DEG: Double = 45.0

    /** 每点疲劳对应的速度折减比例。 */
    const val FATIGUE_SPEED_PENALTY: Double = 0.12
}
