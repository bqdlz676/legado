package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.visionRadiusCells
import io.legado.app.ui.map.crossing.terrain.TerrainCost
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

/**
 * 寻路器（纯 JVM，§二 `AStar(from, to, armyType)` / `flankPath`）。
 *
 * 代价 = 距离 × 坡度惩罚 × 路况（平地 < 丘陵 < 山地 < 河流），天候在路况上再折一层；
 * 不可通行格（非涉渡水面、超坡度）直接堵死。启发式用八向（octile）直线距离 × 最低路况系数，
 * 对应规格「大圆 / 直线 + 地形代价」中的直线口径；经纬度大圆距离由 [GeoProjection] 负责。
 *
 * 兜底铁律：A* 搜不到通路时，退回「朝目标逐格钳制」的直线折线，保证引擎每回合都能给出
 * 一条可走路径，绝不返回空路径把军队晾在原地；被堵格沿可通行邻格侧向绕开。
 */
class Pathfinder(
    private val map: TerrainMap,
    private val weatherCondition: String?,
) {

    /** A* 主入口：返回从 [from] 到 [to] 的单元序列（含起点，不含终点重复）；[avoid] 为强制绕开格。 */
    fun find(from: Cell, to: Cell, type: ArmyType, avoid: Set<Cell> = emptySet()): List<Cell> {
        if (from == to) return listOf(from)
        val target = nearestPassable(to, type) ?: return straightPath(from, to, type)
        val path = astar(from, target, type, avoid, emptyList(), strictView = false)
        return if (path.isEmpty()) straightPath(from, to, type) else path
    }

    /**
     * 侧翼路径（List 形态入口，旧版兼容）：敌军半径统一取 [visionRadiusCells]
     * （缺省回退为本方兵种半径 [ArmyType.visionRadiusCells]）；新代码请使用逐敌半径重载 [enemies]。
     * 需要判断本次侧翼是否被迫退化（视野封锁绕不开）时，请直接用 [flankRoute] 读 `degraded`。
     */
    fun flankPath(
        from: Cell,
        to: Cell,
        type: ArmyType,
        enemyPositions: List<Cell>,
        visionRadiusCells: Int = type.visionRadiusCells,
    ): List<Cell> = flankRoute(from, to, type, enemyPositions.map { EnemySight(it, visionRadiusCells) }).path

    /**
     * 侧翼路径（List 形态入口，逐敌半径，B8R2）：[enemies] 每条携带敌军自身 `visionRadiusCells`，
     * 混编兵种时闸门按各敌自身半径计算，不再套用本方兵种统一半径。
     */
    fun flankPath(
        from: Cell,
        to: Cell,
        type: ArmyType,
        enemies: List<EnemySight>,
    ): List<Cell> = flankRoute(from, to, type, enemies).path

    /**
     * 侧翼路径（带降级标志，旧版兼容）：敌军半径统一取 [visionRadiusCells]，内部折算为逐敌条目后走核心链路。
     */
    fun flankRoute(
        from: Cell,
        to: Cell,
        type: ArmyType,
        enemyPositions: List<Cell>,
        visionRadiusCells: Int = type.visionRadiusCells,
    ): FlankRoute = flankRoute(from, to, type, enemyPositions.map { EnemySight(it, visionRadiusCells) })

    /**
     * 侧翼路径（带降级标志，逐敌半径，B8R2）：A* 基础上**强制避开**任一敌军自身可见半径内的格；
     * 敌军所在格绝对不入路。
     *
     * 视线口径（与 `CampaignController.updateIntelligence` 的情报口径一致，§三）：
     * - [TerrainMap.visible] 只判两点之间有无山脊遮挡（两点 LOS），**本身不携带半径**；
     * - 半径闸门用八向格距 [cellDist] ≤ 该敌 [EnemySight.radiusCells]，两点同时满足才算「被敌军看见」；
     * - 每条敌军半径取自其自身 `Corps.visionRadiusCells`，混编时不得用本方兵种半径统一套用。
     *
     * 退化口径（绕不开视野封锁时的三级兜底，逐级放宽，绝不返回空路径）：
     * 1. 严格模式 A*：视野内格直接剪枝；搜到通路 → [FlankRoute.degraded] = false；
     * 2. 严格模式无路 → 放宽为「视野代价最小路径」：视野内格保留但乘 [VISIBILITY_PENALTY] 加价，
     *    此时路径仍可能暴露，`degraded = true`；
     * 3. 加价模式仍无路 → 退回朝目标逐格钳制的直线折线，`degraded = true`。
     * 起点 / 目标格不受视野剪枝（否则路径无从起步或永远无法落点）；敌军所在格在两种模式下都排除。
     */
    fun flankRoute(
        from: Cell,
        to: Cell,
        type: ArmyType,
        enemies: List<EnemySight>,
    ): FlankRoute {
        if (from == to) return FlankRoute(listOf(from), degraded = false, reason = "起点即终点")
        val target = nearestPassable(to, type)
        if (target == null) {
            return FlankRoute(
                straightPath(from, to, type, enemies.map { it.position }.toSet()),
                degraded = true,
                reason = "目标周边无可通行落点，已退化为直线折线兜底",
            )
        }
        val strict = astar(from, target, type, emptySet(), enemies, strictView = true)
        if (strict.isNotEmpty()) return FlankRoute(strict, degraded = false, reason = "")
        val penalized = astar(from, target, type, emptySet(), enemies, strictView = false)
        if (penalized.isNotEmpty()) {
            return FlankRoute(
                penalized,
                degraded = true,
                reason = "敌军视野封锁全部绕行通路，已退化为视野代价最小路径（仍可能暴露）",
            )
        }
        return FlankRoute(
            straightPath(from, to, type, enemies.map { it.position }.toSet()),
            degraded = true,
            reason = "视野与地形双重封锁，已退化为朝目标钳制的直线折线兜底",
        )
    }

    // ── A* ────────────────────────────────────────────────────

    private fun astar(
        from: Cell,
        to: Cell,
        type: ArmyType,
        avoid: Set<Cell>,
        enemies: List<EnemySight>,
        strictView: Boolean,
    ): List<Cell> {
        val n = map.gridN
        val startKey = encode(from, n)
        val cameFrom = HashMap<Long, Long>(n * 2)
        val gScore = HashMap<Long, Double>(n * 2).apply { this[startKey] = 0.0 }
        val open = PriorityQueue<Node>(compareBy { it.f })
        open += Node(startKey, heuristic(from, to))
        val closed = HashSet<Long>()
        // 敌军所在格集合只用于排阵；半径闸门走 [enemies] 每条自身半径，不能再合并成单一 viewRadius。
        val enemySet = enemies.map { it.position }.toSet()

        while (open.isNotEmpty()) {
            val current = open.poll()
            if (current.key in closed) continue
            closed += current.key
            val cur = decode(current.key, n)
            if (cur == to) return reconstruct(cameFrom, current.key, n)

            for (next in map.neighbors8(cur)) {
                val key = encode(next, n)
                if (key in closed) continue
                if (next in avoid) continue
                if (!map.passable(next, type)) continue
                // 敌军所在格任何模式下都不入路（§包抄：避开敌方视野，更不得踏上敌军阵地）。
                if (next in enemySet) continue

                // 逐敌闸门：每条敌军用其自身半径判定，混编时高视野 / 低视野各算各的（B8R2）。
                val watched = enemies.any { watchedBy(it.position, next, it.radiusCells) }
                // 严格侧翼模式：视野内格剪枝（等价 BLOCKED），而不是加价放行。
                if (strictView && watched) continue

                val stepCost = stepCost(cur, next, type, watched)
                val tentative = (gScore[current.key] ?: Double.MAX_VALUE) + stepCost
                if (tentative < (gScore[key] ?: Double.MAX_VALUE)) {
                    cameFrom[key] = current.key
                    gScore[key] = tentative
                    open += Node(key, tentative + heuristic(next, to))
                }
            }
        }
        return emptyList()
    }

    /**
     * 纯函数：[enemy] 能否看见 [cell]。口径 = 八向格距 ≤ [viewRadius] 且 [map.visible] 两点通视；
     * [viewRadius] 由调用方按该敌军自身 `visionRadiusCells` 传入（逐敌闸门，B8R2）。
     * 抽出为公共纯函数以便单测直接断言视野判定，不依赖 A* 搜索结果。
     */
    fun watchedBy(enemy: Cell, cell: Cell, viewRadius: Int): Boolean {
        if (cellDist(enemy, cell) > viewRadius) return false
        return map.visible(enemy, cell)
    }

    /** 单步代价：八向对角 ×√2，叠加地形进入代价；[watched] 时在退化模式下叠加视线加价。 */
    private fun stepCost(
        from: Cell,
        next: Cell,
        type: ArmyType,
        watched: Boolean,
    ): Double {
        val diagonal = from.col != next.col && from.row != next.row
        val base = TerrainCost.enterCost(next, type, map, weatherCondition)
        if (base >= TerrainCost.BLOCKED_COST) return TerrainCost.BLOCKED_COST
        var cost = base * (if (diagonal) DIAGONAL_FACTOR else 1.0)
        if (watched) {
            // 仅退化（非严格）模式到达这里：被发现不等于不能走，加价诱导绕路但不堵死。
            cost *= VISIBILITY_PENALTY
        }
        return cost
    }

    private fun heuristic(a: Cell, b: Cell): Double {
        val dc = abs(a.col - b.col)
        val dr = abs(a.row - b.row)
        val octile = max(dc, dr) + (DIAGONAL_FACTOR - 1.0) * min(dc, dr)
        return octile * MIN_TERRAIN_FACTOR
    }

    /** 目标格不可通行时，取周边最近可通行格作为落点。 */
    private fun nearestPassable(target: Cell, type: ArmyType): Cell? {
        if (map.passable(target, type)) return target
        for (radius in 1..SEARCH_RADIUS) {
            var best: Cell? = null
            var bestDist = Double.MAX_VALUE
            for (dc in -radius..radius) for (dr in -radius..radius) {
                val c = Cell(target.col + dc, target.row + dr)
                if (!map.inBounds(c) || !map.passable(c, type)) continue
                val d = (dc * dc + dr * dr).toDouble()
                if (d < bestDist) {
                    bestDist = d
                    best = c
                }
            }
            if (best != null) return best
        }
        return null
    }

    /** 直线兜底：朝目标逐格钳制；被堵时在可通行邻格中选最接近目标的一格；[blocked] 为额外禁入格。 */
    private fun straightPath(
        from: Cell,
        to: Cell,
        type: ArmyType,
        blocked: Set<Cell> = emptySet(),
    ): List<Cell> {
        val path = ArrayList<Cell>()
        var cur = from
        path += cur
        var guard = 0
        while (cur != to && guard < map.gridN * 2) {
            guard++
            val candidates = map.neighbors8(cur).filter { map.passable(it, type) && it !in blocked }
            if (candidates.isEmpty()) break
            cur = candidates.minByOrNull { cellDist(it, to) } ?: break
            if (path.contains(cur)) break
            path += cur
        }
        return path
    }

    /** 八向格距（启发式与兜底共用）。 */
    fun cellDist(a: Cell, b: Cell): Double {
        val dc = abs(a.col - b.col)
        val dr = abs(a.row - b.row)
        return max(dc, dr) + (DIAGONAL_FACTOR - 1.0) * min(dc, dr)
    }

    private fun reconstruct(cameFrom: Map<Long, Long>, endKey: Long, n: Int): List<Cell> {
        val reversed = ArrayList<Cell>()
        var key: Long? = endKey
        while (key != null) {
            reversed += decode(key, n)
            key = cameFrom[key]
        }
        return reversed.asReversed()
    }

    private fun encode(c: Cell, n: Int): Long = c.row.toLong() * n + c.col

    private fun decode(key: Long, n: Int): Cell = Cell((key % n).toInt(), (key / n).toInt())

    private data class Node(val key: Long, val f: Double)

    private fun abs(v: Int): Int = if (v < 0) -v else v

    companion object {
        /** 对角步长系数（√2 近似）。 */
        const val DIAGONAL_FACTOR: Double = 1.41421356

        /** 启发式用最低路况系数（平地 1.0），保证 A* 可采纳。 */
        const val MIN_TERRAIN_FACTOR: Double = 1.0

        /** 退化模式下：敌军视线内格的加价倍数（严格模式不使用，视野内格直接剪枝）。 */
        const val VISIBILITY_PENALTY: Double = 1.8

        /** 目标不可通行时向外搜索的半径（格）。 */
        const val SEARCH_RADIUS: Int = 4
    }
}

/**
 * 侧翼寻路的单条敌军视野条目：[position] 为敌军所在格（感知位置），
 * [radiusCells] 为该敌军自身 `visionRadiusCells`。混编兵种时各敌半径不同，
 * 闸门必须逐敌取自身半径（B8R2），不得退回本方兵种统一半径。
 */
data class EnemySight(
    val position: Cell,
    val radiusCells: Int,
)

/**
 * 侧翼寻路结果。[path] 全程非空（三级兜底保证）；[degraded] = true 表示严格避视野无路、
 * 已按退化口径放宽（加价绕行或直线折线）；[reason] 记录本次退化口径，供日志 / 调试断言。
 */
data class FlankRoute(
    val path: List<Cell>,
    val degraded: Boolean,
    val reason: String,
)
