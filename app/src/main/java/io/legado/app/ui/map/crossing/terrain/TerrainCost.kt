package io.legado.app.ui.map.crossing.terrain

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.TerrainKind

/**
 * 行军代价表（纯 JVM，§二 代价 = 距离 × 坡度惩罚 × 路况）。
 *
 * 路况系数：平地 < 丘陵 < 山地 < 河流；涉渡点代价高于平地但可过，非涉渡水面由
 * [TerrainMap.passable] 直接判堵。天候在路况系数上再乘一层；疲劳折减行军速度。
 * 所有系数收口在本对象，寻路与回合移动读同一张表，避免两处口径漂移。
 */
object TerrainCost {

    /** 路况基础系数。 */
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

    /** 天候系数：雨 / 雪 / 雾拖慢行军；晴朗无风不加成。 */
    fun weatherModifier(condition: String?): Double = when (condition?.trim()?.lowercase()) {
        "rain", "rainy", "mud" -> 1.30
        "snow", "snowy" -> 1.50
        "fog", "foggy", "mist" -> 1.20
        "wind", "windy" -> 1.10
        else -> 1.0
    }

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
        return base * slope * weather
    }

    /**
     * 行军速度系数（回合移动用）：基础速度 × 天候 × 疲劳惩罚。
     * 疲劳 0..5，每点折减 12%；断粮加速疲劳由 MoraleSystem 负责，这里只读当前疲劳。
     */
    fun marchSpeed(type: ArmyType, fatigue: Double, weatherCondition: String?): Double {
        val fatiguePenalty = 1.0 / (1.0 + fatigue * FATIGUE_SPEED_PENALTY)
        return type.speedBase * weatherModifier(weatherCondition) * fatiguePenalty
    }

    /** 不可通行代价上限（A* 中视为堵死）。 */
    const val BLOCKED_COST: Double = 1e9

    /** 坡度达到该角度时惩罚翻倍封顶。 */
    const val SLOPE_FULL_DEG: Double = 45.0

    /** 每点疲劳对应的速度折减比例。 */
    const val FATIGUE_SPEED_PENALTY: Double = 0.12
}
