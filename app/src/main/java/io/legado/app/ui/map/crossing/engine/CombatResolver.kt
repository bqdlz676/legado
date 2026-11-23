package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.Corps

/**
 * 遭遇战结算（纯 JVM，§三 第 4 步 兰彻斯特简化）。
 *
 * 战斗力 = 兵力平方 × 修正系数：伏击方伤害 ×2；制高点防御 ×1.5。
 * 伤亡按双方战斗力占比分摊基础交战损耗；败方后撤 1 格 + 士气降一级（由调用侧执行）。
 * 本对象只算损耗数值，不改军队状态，便于单测穷举兵力比 / 伏击 / 高地组合。
 */
object CombatResolver {

    data class Outcome(
        val attackerLoss: Double,
        val defenderLoss: Double,
        val attackerWon: Boolean,
        val defenderRetreated: Boolean,
        val ambushed: Boolean,
        val highGroundDef: Boolean,
    )

    fun resolve(
        attacker: Corps,
        defender: Corps,
        ambushed: Boolean,
        highGroundDef: Boolean,
    ): Outcome {
        val effA = attacker.strength * attacker.strength * if (ambushed) AMBUSH_MULTIPLIER else 1.0
        val effD = defender.strength * defender.strength * if (highGroundDef) HIGH_GROUND_MULTIPLIER else 1.0
        val effSum = effA + effD

        // 基础交战损耗池：双方当前兵力均值 × 损耗率，再按战斗力占比分配。
        val pool = ((attacker.strength + defender.strength) * 0.5) * CASUALTY_RATE
        val attackerLoss = if (effSum <= 0.0) pool * 0.5 else pool * (effD / effSum)
        val defenderLoss = if (effSum <= 0.0) pool * 0.5 else pool * (effA / effSum)

        val attackerWon = effA >= effD
        val defenderRetreated = !attackerWon && defenderLoss <= attackerLoss
        return Outcome(
            attackerLoss = attackerLoss.coerceAtMost(attacker.strength),
            defenderLoss = defenderLoss.coerceAtMost(defender.strength),
            attackerWon = attackerWon,
            defenderRetreated = defenderRetreated,
            ambushed = ambushed,
            highGroundDef = highGroundDef,
        )
    }

    /** 伏击方伤害倍率（§三：伏击中伏击方优先判定、伤害 +2x）。 */
    const val AMBUSH_MULTIPLIER: Double = 2.0

    /** 制高点防御倍率（§三：一方在制高点 → 防御 +1.5x）。 */
    const val HIGH_GROUND_MULTIPLIER: Double = 1.5

    /** 单场遭遇基础损耗率（占双方兵力均值的比例）。 */
    const val CASUALTY_RATE: Double = 0.4
}
