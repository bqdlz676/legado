package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
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
        val path = astar(from, target, type, avoid, visibilityPenalty = emptyList())
        return if (path.isEmpty()) straightPath(from, to, type) else path
    }

    /**
     * 侧翼路径：A* 基础上，对敌军视线内的格追加视线惩罚，强制绕开敌方观察区。
     * 地形不通时同样退回直线折线兜底。
     */
    fun flankPath(from: Cell, to: Cell, type: ArmyType, enemyPositions: List<Cell>): List<Cell> {
        val target = nearestPassable(to, type) ?: return straightPath(from, to, type)
        val path = astar(from, target, type, emptySet(), enemyPositions)
        return if (path.isEmpty()) straightPath(from, to, type) else path
    }

    // ── A* ────────────────────────────────────────────────────

    private fun astar(
        from: Cell,
        to: Cell,
        type: ArmyType,
        avoid: Set<Cell>,
        enemyPositions: List<Cell>,
    ): List<Cell> {
        val n = map.gridN
        val startKey = encode(from, n)
        val cameFrom = HashMap<Long, Long>(n * 2)
        val gScore = HashMap<Long, Double>(n * 2).apply { this[startKey] = 0.0 }
        val open = PriorityQueue<Node>(compareBy { it.f })
        open += Node(startKey, heuristic(from, to))
        val closed = HashSet<Long>()

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

                val stepCost = stepCost(cur, next, type, enemyPositions)
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

    /** 单步代价：八向对角 ×√2，叠加地形进入代价与敌军视线惩罚。 */
    private fun stepCost(
        from: Cell,
        next: Cell,
        type: ArmyType,
        enemyPositions: List<Cell>,
    ): Double {
        val diagonal = from.col != next.col && from.row != next.row
        val base = TerrainCost.enterCost(next, type, map, weatherCondition)
        if (base >= TerrainCost.BLOCKED_COST) return TerrainCost.BLOCKED_COST
        var cost = base * (if (diagonal) DIAGONAL_FACTOR else 1.0)
        if (enemyPositions.isNotEmpty() && map.visible(next, from)) {
            // 侧翼绕路：走入敌军通视范围加价，但不堵死（被发现不等于不能走）。
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

    /** 直线兜底：朝目标逐格钳制；被堵时在可通行邻格中选最接近目标的一格。 */
    private fun straightPath(from: Cell, to: Cell, type: ArmyType): List<Cell> {
        val path = ArrayList<Cell>()
        var cur = from
        path += cur
        var guard = 0
        while (cur != to && guard < map.gridN * 2) {
            guard++
            val candidates = map.neighbors8(cur).filter { map.passable(it, type) }
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

        /** 侧翼路径：敌军视线内格的加价倍数。 */
        const val VISIBILITY_PENALTY: Double = 1.8

        /** 目标不可通行时向外搜索的半径（格）。 */
        const val SEARCH_RADIUS: Int = 4
    }
}
