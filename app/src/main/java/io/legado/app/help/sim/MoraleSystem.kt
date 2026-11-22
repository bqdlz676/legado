package io.legado.app.help.sim

/**
 * 落文 · 士气 / 疲劳 / 补给系统（core:sim / §五）
 *
 * 士气阶梯：high → steady → wavering → broken（broken 即溃散出局）。
 * 疲劳 ≥4 时按概率掉士气；补给每回合消耗，断粮累计三回合触发撤退意图。
 */
enum class Morale(val label: String) {
    HIGH("high"),
    STEADY("steady"),
    WAVERING("wavering"),
    BROKEN("broken");

    companion object {
        fun fromString(raw: String?): Morale = when (raw?.lowercase()) {
            "high" -> HIGH
            "wavering" -> WAVERING
            "broken" -> BROKEN
            else -> STEADY
        }
    }
}

object MoraleSystem {

    fun down(m: Morale): Morale = when (m) {
        Morale.HIGH -> Morale.STEADY
        Morale.STEADY -> Morale.WAVERING
        Morale.WAVERING -> Morale.BROKEN
        Morale.BROKEN -> Morale.BROKEN
    }

    fun up(m: Morale): Morale = when (m) {
        Morale.HIGH -> Morale.HIGH
        Morale.STEADY -> Morale.HIGH
        Morale.WAVERING -> Morale.STEADY
        Morale.BROKEN -> Morale.WAVERING
    }

    /** 每回合状态结算：补给消耗、疲劳累积、疲劳压士气、溃散 / 覆灭出局。 */
    fun tick(army: SimArmy, rand: () -> Double) {
        if (army.supply > 0) {
            army.supply = (army.supply - 0.2).coerceAtLeast(0.0)
        } else {
            army.noSupplyTurn += 1
        }
        if (army.fatigue >= 4 && rand() < 0.7) {
            army.morale = down(army.morale)
        }
        if (army.morale == Morale.BROKEN) army.alive = false
        if (army.strength <= 0) army.alive = false
    }
}
