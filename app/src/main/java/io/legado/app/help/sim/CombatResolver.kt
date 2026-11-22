package io.legado.app.help.sim

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 落文 · 遭遇战斗结算（core:sim / §五，兰彻斯特平方律简化）
 *
 * 攻方战力 = strength × (伏击 ×2) × (骑打步 ×1.3)
 * 守方战力 = strength × (制高点 ×1.5) × (据守 ×1.3)
 * 伤亡比   = 守² / (攻² + 守²)
 * 单次遭遇损耗系数 0.35：连续压迫才见分晓，不是一照面双双蒸发。
 */
object CombatResolver {

    const val LOSS_SCALE = 0.35

    data class CombatResult(
        val winnerId: String,
        val loserId: String,
        val attackerId: String,
        val defenderId: String,
        val attackerLoss: Int,
        val defenderLoss: Int,
        val pos: DoubleArray,
    )

    fun resolve(attacker: SimArmy, defender: SimArmy, terrain: TerrainData): CombatResult {
        val ambush = if (attacker.intentActive == "ambush") 2.0 else 1.0
        val cavalryVsFoot = if (attacker.type == "cavalry" && defender.type != "cavalry") 1.3 else 1.0
        val attPow = attacker.strength * ambush * cavalryVsFoot

        val hold = if (defender.intentActive == "hold") 1.3 else 1.0
        val highGround = if (terrain.highGroundNear(defender.r, defender.c, 1) != null) 1.5 else 1.0
        val defPow = defender.strength * hold * highGround

        val ratio = (defPow * defPow) / (attPow * attPow + defPow * defPow + 1e-9)

        val attLoss = min(
            attacker.strength,
            max(1, (attacker.strength * ratio * LOSS_SCALE).roundToInt()),
        )
        val defLoss = min(
            defender.strength,
            max(1, (defender.strength * (1 - ratio) * LOSS_SCALE).roundToInt()),
        )

        attacker.strength = max(0, attacker.strength - attLoss)
        defender.strength = max(0, defender.strength - defLoss)

        val winner: SimArmy
        val loser: SimArmy
        if (defPow >= attPow) {
            winner = defender; loser = attacker
        } else {
            winner = attacker; loser = defender
        }

        loser.morale = MoraleSystem.down(loser.morale)
        winner.morale = MoraleSystem.up(winner.morale)

        // 败方沿远离胜方方向后撤一格。
        val dr = Integer.signum(loser.r - winner.r)
        val dc = Integer.signum(loser.c - winner.c)
        loser.r = (loser.r + (dr.let { if (it == 0) 1 else it })).coerceIn(0, terrain.n - 1)
        loser.c = (loser.c + dc).coerceIn(0, terrain.n - 1)
        loser.path = null

        if (loser.strength <= 0) loser.alive = false
        if (attacker.strength <= 0) attacker.alive = false
        if (defender.strength <= 0) defender.alive = false

        val pos = terrain.toLngLat(Cell(winner.r, winner.c))
        return CombatResult(
            winnerId = winner.id,
            loserId = loser.id,
            attackerId = attacker.id,
            defenderId = defender.id,
            attackerLoss = attLoss,
            defenderLoss = defLoss,
            pos = pos,
        )
    }

    /** 邻接判定：切比雪夫距离 ≤1（同格不同部队仍接战；调用方已按 id 排除自身）。 */
    fun adjacent(a: SimArmy, b: SimArmy): Boolean =
        max(kotlin.math.abs(a.r - b.r), kotlin.math.abs(a.c - b.c)) <= 1
}
