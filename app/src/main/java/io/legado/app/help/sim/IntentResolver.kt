package io.legado.app.help.sim

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 落文 · 意图裁决器（core:sim / §五，15 条规则 + 渡河 / 续行）
 *
 * 优先级（契约 §五）：
 * FIGHT > ROUT > BREAKOUT > AMBUSH > RETREAT >
 * HOLD / ADVANCE / SUPPORT / SEIZE / RUSH / CROSS > CONTINUE > HOLD
 *
 * 命中即返回；规则只给「意图 + 目标格 + 理由」，路径规划由上下文完成。
 */
data class ResolvedIntent(
    val intent: String,
    val target: Cell?,
    val reason: String,
)

/** 裁决所需的引擎上下文，由 SimEngine 实现，避免解析器反向依赖运行态细节。 */
interface IntentContext {
    val terrain: TerrainData
    val armies: List<SimArmy>
    val victorySide: String?
    fun relation(sideA: String, sideB: String): String
    fun distKm(a: SimArmy, b: SimArmy): Double
    fun rand(): Double
    fun toCell(lng: Double, lat: Double): Cell
    fun planPath(from: Cell, to: Cell, armyType: String): List<Cell>?
    fun flankPath(from: Cell, to: Cell, armyType: String): List<Cell>?
}

object IntentResolver {

    fun resolve(army: SimArmy, ctx: IntentContext): ResolvedIntent {
        val terrain = ctx.terrain
        val all = ctx.armies.filter { it.alive && it.id != army.id && ctx.relation(army.side, it.side) == "hostile" }
        val adjacent = neighbors(army, ctx).filter { ctx.relation(army.side, it.side) == "hostile" }

        // 规则 1：相邻有敌，应战 FIGHT。
        if (adjacent.isNotEmpty()) {
            val foe = adjacent.first()
            return ResolvedIntent("fight", Cell(foe.r, foe.c), "规则1：相邻有敌，应战")
        }

        // 规则 2：士气崩溃，溃逃 ROUT（向敌军质心反方向最远格）。
        if (army.morale == Morale.BROKEN) {
            return ResolvedIntent("rout", farthestCell(army, all, ctx), "规则2：士气崩溃，溃逃")
        }

        // 规则 3：被围（切比雪夫 3 格内 ≥3 敌），向最弱处突围 BREAKOUT。
        val around = all.filter { cheb(army, it.r, it.c) <= 3 }
        if (around.size >= 3) {
            val weakest = around.minByOrNull { it.strength }!!
            return ResolvedIntent("breakout", Cell(weakest.r, weakest.c), "规则3：被围，向敌最弱处突围")
        }

        val chokeNear = terrain.chokePoints.firstOrNull { cheb(army, it.r, it.c) <= 3 }
        val nearest = nearest(army, all, ctx)

        // 规则 4：据关隘设伏 AMBUSH（基础意图为 ambush）。
        if (chokeNear != null && nearest != null && nearest.second < 20 && army.intent == "ambush") {
            return ResolvedIntent("ambush", Cell(chokeNear.r, chokeNear.c), "规则4：据关隘设伏")
        }

        // 规则 5：断粮满三回合，撤退 RETREAT。
        if (army.supply <= 0 && army.noSupplyTurn >= 3) {
            return ResolvedIntent("retreat", null, "规则5：断粮，撤退")
        }

        // 规则 6：占据制高点据守 HOLD（自身在高处附近，且敌近）。
        val high = terrain.highGround.firstOrNull { cheb(army, it.r, it.c) <= 2 }
        if (high != null && nearest != null && nearest.second < 12 &&
            (army.intent == "hold" || army.morale == Morale.WAVERING)
        ) {
            return ResolvedIntent("hold", Cell(high.r, high.c), "规则6：占制高点据守")
        }

        // 规则 7：兵力三倍于敌，主动进军 ADVANCE。
        if (nearest != null && army.strength >= 3 * nearest.first.strength && nearest.second < 25) {
            val foe = nearest.first
            return ResolvedIntent("advance", Cell(foe.r, foe.c), "规则7：兵力三倍，主动进军")
        }

        // 规则 8：盟友被攻，支援 SUPPORT。
        val ally = ctx.armies.firstOrNull {
            it.alive && it.id != army.id && it.side != army.side &&
                ctx.relation(army.side, it.side) == "allied" && cheb(army, it.r, it.c) <= 1
        }
        if (ally != null) {
            val allyNeighbors = neighbors(ally, ctx)
            if (allyNeighbors.any { ctx.relation(ally.side, it.side) == "hostile" }) {
                return ResolvedIntent("support", Cell(ally.r, ally.c), "规则8：盟友被攻，支援")
            }
        }

        // 规则 9：抢占空关隘 SEIZE。
        if (chokeNear != null && nearest != null && nearest.second > 25) {
            return ResolvedIntent("seize", Cell(chokeNear.r, chokeNear.c), "规则9：抢占空关隘")
        }

        // 规则 10：追击绑定（情报驱动，不是死坐标）。
        if (army.hunt != null) {
            val prey = ctx.armies.firstOrNull { it.id == army.hunt && it.alive }
            if (prey != null) {
                val known = army.lastSeen[prey.id] ?: LastKnown(prey.r, prey.c, 0)
                val seen = army.lastSeen.containsKey(prey.id)
                val out = if (army.intent == "rush") "rush" else "advance"
                val reason = (if (seen) "追击：" else "追向最后已知位置：") + prey.name
                return ResolvedIntent(out, Cell(known.r, known.c), reason)
            }
            army.hunt = null
        }

        // 规则 11：目标点在河对岸 → 先抵最近渡口 CROSS，再渡河穿插。
        if (army.target != null) {
            val tc = ctx.toCell(army.target!![0], army.target!![1])
            if (lineCrossesRiver(army.r, army.c, tc.r, tc.c, terrain)) {
                val ford = nearestFord(army, terrain)
                if (ford != null) {
                    return ResolvedIntent("cross", Cell(ford.r, ford.c), "规则11：抵渡口渡河")
                }
            }

            val d = cheb(army, tc.r, tc.c)
            if (d <= 1) {
                // 到了不等于停战：附近还有敌军就就地接敌，避免各自站定僵死。
                if (nearest != null && nearest.second <= 20) {
                    val foe = nearest.first
                    return ResolvedIntent("advance", Cell(foe.r, foe.c), "规则12：目标已达，就近接敌")
                }
                return ResolvedIntent("hold", null, "规则12：目标已达")
            }

            // 规则 13：目标被敌占，进攻。
            val holder = all.firstOrNull { it.r == tc.r && it.c == tc.c }
            if (holder != null) {
                return ResolvedIntent("advance", Cell(holder.r, holder.c), "规则13：目标被敌占，进攻")
            }

            // 规则 14：接近逃脱点，急行军 RUSH。
            if (army.intent == "escape" && d <= 10) {
                return ResolvedIntent("rush", tc, "规则14：接近逃脱点，急行军")
            }
            if (army.intent == "hold") {
                return ResolvedIntent("hold", tc, "规则15：据守目标")
            }
            val out = if (army.intent == "rush") "rush" else "advance"
            val reason = if (army.intent == "escape") "规则15：执行突围（朝逃脱点）" else "规则15：执行进军"
            return ResolvedIntent(out, tc, reason)
        }

        // 规则 16：求胜方不设接敌半径，主动寻敌。
        if (ctx.victorySide != null && army.side == ctx.victorySide) {
            val foe = nearest?.first
            if (foe != null) {
                return ResolvedIntent("advance", Cell(foe.r, foe.c), "规则16：求胜方主动寻敌")
            }
        }

        // 规则 17：无敌目标，就近接敌；否则据守原地。
        if (nearest != null && nearest.second <= 20) {
            val foe = nearest.first
            return ResolvedIntent("advance", Cell(foe.r, foe.c), "规则17：就近接敌")
        }
        return ResolvedIntent("hold", null, "规则17：无目标，据守原地")
    }

    private fun cheb(a: SimArmy, r: Int, c: Int): Int =
        max(abs(a.r - r), abs(a.c - c))

    private fun neighbors(army: SimArmy, ctx: IntentContext): List<SimArmy> =
        ctx.armies.filter {
            it.alive && it.id != army.id &&
                max(abs(it.r - army.r), abs(it.c - army.c)) <= 1
        }

    private fun nearest(army: SimArmy, list: List<SimArmy>, ctx: IntentContext): Pair<SimArmy, Double>? {
        if (list.isEmpty()) return null
        var best: SimArmy? = null
        var bd = Double.POSITIVE_INFINITY
        for (x in list) {
            val d = ctx.distKm(army, x)
            if (d < bd) { bd = d; best = x }
        }
        return best!! to bd
    }

    private fun farthestCell(army: SimArmy, list: List<SimArmy>, ctx: IntentContext): Cell {
        if (list.isEmpty()) return Cell(army.r, army.c)
        val sumR = list.sumOf { it.r }
        val sumC = list.sumOf { it.c }
        val ar = sumR.toDouble() / list.size
        val ac = sumC.toDouble() / list.size
        var dr = army.r - ar
        var dc = army.c - ac
        if (dr == 0.0 && dc == 0.0) {
            dr = ctx.rand() - 0.5
            dc = ctx.rand() - 0.5
        }
        val k = max(abs(dr), abs(dc)).coerceAtLeast(1.0)
        val rr = (army.r + dr / k * 8).roundToInt().coerceIn(0, ctx.terrain.n - 1)
        val cc = (army.c + dc / k * 8).roundToInt().coerceIn(0, ctx.terrain.n - 1)
        return Cell(rr, cc)
    }

    private fun lineCrossesRiver(r0: Int, c0: Int, r1: Int, c1: Int, terrain: TerrainData): Boolean {
        val steps = max(2, hypot((r1 - r0).toDouble(), (c1 - c0).toDouble()).roundToInt())
        for (s in 1 until steps) {
            val t = s.toDouble() / steps
            val rr = (r0 + (r1 - r0) * t).roundToInt()
            val cc = (c0 + (c1 - c0) * t).roundToInt()
            if (terrain.isRiver(rr, cc)) return true
        }
        return false
    }

    private fun nearestFord(army: SimArmy, terrain: TerrainData): PassPoint? {
        return terrain.fords.minByOrNull { hypot((it.r - army.r).toDouble(), (it.c - army.c).toDouble()) }
    }
}
