package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 规则意图裁决器（纯 JVM，§三 规则意图轮询 + §五 手动干预可覆盖）。
 *
 * 优先级从高到低：FIGHT > ROUT > BREAKOUT > AMBUSH > RETREAT > SUPPORT >
 * ADVANCE > SEIZE > CONTINUE > HOLD。裁决只输出「意图 + 目标格 + 理由」，
 * 不直接移动军队；寻路由 [Pathfinder] 负责，决策与地形解耦。
 *
 * 为什么不用模型 / 不联网：规格要求纯规则层，决策代码收口在本对象，换战役不改裁决表。
 */
object IntentResolver {

    data class Decision(
        val intent: Intent,
        val target: Cell?,
        val reason: String,
    )

    fun resolve(
        corps: Corps,
        allies: List<Corps>,
        enemies: List<Corps>,
        map: TerrainMap,
        chokeCells: List<Cell>,
        supplyCells: List<Cell>,
    ): Decision {
        if (!corps.combatReady()) return Decision(Intent.HOLD, null, "已失去作战能力，据守")

        val aliveEnemies = enemies.filter { it.combatReady() }
        val nearestEnemy = aliveEnemies.minByOrNull { octile(corps.position, it.position) }
        val nearestEnemyDist = nearestEnemy?.let { octile(corps.position, it.position) } ?: Int.MAX_VALUE

        // 1. 相邻格有敌军 → 应战（遭遇战优先级最高）。
        if (nearestEnemy != null && nearestEnemyDist <= ADJACENT_CELLS) {
            return Decision(Intent.FIGHT, nearestEnemy.position, "相邻敌军，应战")
        }

        // 2. 士气溃散 → 溃逃，朝远离所有敌军的方向。
        if (corps.morale == Morale.BROKEN) {
            val escape = farthestFromEnemies(corps.position, aliveEnemies, map)
            return Decision(Intent.ROUT, escape, "士气溃散，脱离战场")
        }

        // 3. 四向被围 → 突围，朝敌兵力最弱的方向。
        if (surrounded(corps.position, aliveEnemies, map)) {
            val weakest = aliveEnemies.minByOrNull { it.strength }
            return Decision(Intent.BREAKOUT, weakest?.position, "四面受围，向最弱方向突围")
        }

        // 4. 据守关隘 + 敌军逼近 + 未暴露 → 设伏，原地不暴露。
        if (corps.position in chokeCells && nearestEnemyDist < AMBUSH_ENEMY_RANGE && !corps.exposed) {
            return Decision(Intent.AMBUSH, null, "关隘设伏，待敌深入")
        }

        // 5. 补给断绝超过 3 回合 → 撤退，朝最近补给点（盟友 / 己方据点）。
        if (corps.supply <= 0 && corps.supplyCutTurns > SUPPLY_CUT_TURNS) {
            val supply = (allies.filter { it.combatReady() }.map { it.position } + supplyCells)
                .minByOrNull { octile(corps.position, it) }
            return Decision(Intent.RETREAT, supply, "补给断绝，向补给点撤退")
        }

        // 6. 据制高点 + 敌军逼近 → 据守防御加成。
        if (corps.highGround && nearestEnemyDist < HOLD_HIGH_GROUND_RANGE) {
            return Decision(Intent.HOLD, null, "占据制高点，凭险据守")
        }

        // 7. 兵力三倍于最近敌军 → 进军压迫。
        if (nearestEnemy != null && corps.strength >= nearestEnemy.strength * STRENGTH_SUPERIORITY) {
            return Decision(Intent.ADVANCE, nearestEnemy.position, "兵力优势，进军压迫")
        }

        // 8. 相邻盟友遭攻击 → 支援。
        val distressedAlly = allies.firstOrNull { ally ->
            ally.combatReady() && octile(corps.position, ally.position) <= ADJACENT_CELLS &&
                aliveEnemies.any { octile(ally.position, it.position) <= ADJACENT_CELLS }
        }
        if (distressedAlly != null) {
            return Decision(Intent.SUPPORT, distressedAlly.position, "盟友遭攻，前往支援")
        }

        // 9. 未持关隘 + 敌军较远 + 有关隘空着 → 抢占关隘。
        if (corps.position !in chokeCells && nearestEnemyDist > SEIZE_ENEMY_RANGE) {
            val freeChoke = chokeCells
                .filter { c -> allies.none { it.combatReady() && it.position == c } }
                .minByOrNull { octile(corps.position, it) }
            if (freeChoke != null) return Decision(Intent.SEIZE, freeChoke, "抢占空关隘")
        }

        // 10. 以上均不触发：维持当前意图；无意图则据守。
        return if (corps.intent == Intent.HOLD) {
            Decision(Intent.HOLD, null, "无战术触发，原地据守")
        } else {
            Decision(Intent.CONTINUE, corps.target?.let { mapCellOf(it, map) }, "维持当前意图")
        }
    }

    // ── 辅助判定 ──────────────────────────────────────────────

    /** 四向是否均有敌军（被包围）。 */
    private fun surrounded(pos: Cell, enemies: List<Corps>, map: TerrainMap): Boolean {
        val dirs = listOf(
            Cell(pos.col - 1, pos.row), Cell(pos.col + 1, pos.row),
            Cell(pos.col, pos.row - 1), Cell(pos.col, pos.row + 1),
        )
        var count = 0
        for (d in dirs) {
            if (!map.inBounds(d)) {
                // 棋盘边缘视同该方向受压（无路可退），贴边军队按被围处理。
                count++
                continue
            }
            if (enemies.any { it.position == d }) count++
        }
        return count >= SURROUND_DIRS
    }

    /** 溃逃落点：四邻中离所有敌军最近距离最远的一格。 */
    private fun farthestFromEnemies(from: Cell, enemies: List<Corps>, map: TerrainMap): Cell? {
        val options = map.neighbors8(from).filter { map.passable(it, io.legado.app.ui.map.crossing.model.ArmyType.REGULAR) }
        if (options.isEmpty()) return null
        return options.maxByOrNull { c ->
            if (enemies.isEmpty()) Double.MAX_VALUE
            else enemies.minOf { octile(c, it.position) }
        }
    }

    private fun mapCellOf(geo: io.legado.app.ui.map.crossing.model.Geo, map: TerrainMap): Cell? =
        map.projection.toCell(geo)

    private fun octile(a: Cell, b: Cell): Int {
        val dc = abs(a.col - b.col)
        val dr = abs(a.row - b.row)
        return max(dc, dr) + min(dc, dr)
    }

    /** 相邻判定半径（格）。 */
    const val ADJACENT_CELLS: Int = 1

    /** 设伏：敌军进入该距离（格）内才触发。 */
    const val AMBUSH_ENEMY_RANGE: Int = 5

    /** 断粮超过该连续回合数 → 撤退。 */
    const val SUPPLY_CUT_TURNS: Int = 3

    /** 制高点据守：敌军该距离（格）内依托防御。 */
    const val HOLD_HIGH_GROUND_RANGE: Int = 3

    /** 兵力优势倍数：达到该倍数主动进军。 */
    const val STRENGTH_SUPERIORITY: Double = 3.0

    /** 抢占关隘：敌军超过该距离（格）才考虑夺隘。 */
    const val SEIZE_ENEMY_RANGE: Int = 5

    /** 被包围所需受压方向数。 */
    const val SURROUND_DIRS: Int = 3
}
