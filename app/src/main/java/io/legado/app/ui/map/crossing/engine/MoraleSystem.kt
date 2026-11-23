package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.Corps

/**
 * 士气 / 疲劳 / 补给推进（纯 JVM，§三 第 5 步 状态更新）。
 *
 * - 疲劳 = 当日行军格数 / 日行上限 + 已有疲劳；疲劳 > 4 → 士气降一级。
 * - 士气 BROKEN → 军队解散（移出棋盘）。
 * - 补给断绝连续 > 3 回合 → 疲劳加速增长。
 * 本对象直接改运行态 [Corps]，所有钳制在本层收口，回合移动层不重复做边界判断。
 */
object MoraleSystem {

    data class TurnEffect(
        val fatigueAdded: Double,
        val moraleDropped: Boolean,
        val disbanded: Boolean,
        val supplyCutAccelerated: Boolean,
    )

    /**
     * 推进一支军队的回合状态。
     *
     * @param marchCells 本回合实际行军格数
     * @param dailyMaxCells 日行上限（格），由兵种速度与地形折算
     */
    fun advance(corps: Corps, marchCells: Int, dailyMaxCells: Int): TurnEffect {
        if (!corps.combatReady()) {
            return TurnEffect(0.0, false, false, false)
        }

        // 行军消耗补给；断粮回合计数。
        if (marchCells > 0 && corps.supply > 0) corps.supply -= 1
        if (corps.supply <= 0) corps.supplyCutTurns += 1 else corps.supplyCutTurns = 0
        val supplyCutAccelerated = corps.supplyCutTurns > SUPPLY_CUT_TURNS

        val marchRatio = if (dailyMaxCells > 0) marchCells.toDouble() / dailyMaxCells else 0.0
        val fatigueAdded = marchRatio + if (supplyCutAccelerated) SUPPLY_CUT_FATIGUE_ADD else 0.0
        corps.fatigue = (corps.fatigue + fatigueAdded).coerceIn(0.0, FATIGUE_MAX)

        var moraleDropped = false
        if (corps.fatigue > FATIGUE_MORALE_DROP) {
            val before = corps.morale
            corps.morale = corps.morale.downgrade()
            moraleDropped = corps.morale != before
        }

        val disbanded = if (corps.morale == io.legado.app.ui.map.crossing.model.Morale.BROKEN) {
            corps.disband()
            true
        } else false

        return TurnEffect(fatigueAdded, moraleDropped, disbanded, supplyCutAccelerated)
    }

    companion object {
        /** 疲劳上限（0..5）。 */
        const val FATIGUE_MAX: Double = 5.0

        /** 疲劳超过该值 → 士气降一级。 */
        const val FATIGUE_MORALE_DROP: Double = 4.0

        /** 断粮超过该连续回合数 → 疲劳加速。 */
        const val SUPPLY_CUT_TURNS: Int = 3

        /** 断粮每回合追加疲劳。 */
        const val SUPPLY_CUT_FATIGUE_ADD: Double = 0.5
    }
}
