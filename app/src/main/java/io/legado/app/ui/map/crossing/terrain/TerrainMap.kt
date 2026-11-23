package io.legado.app.ui.map.crossing.terrain

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.RidgeDef
import io.legado.app.ui.map.crossing.model.RiverDef
import io.legado.app.ui.map.crossing.model.TerrainKind
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 地形分析层（纯 JVM，§二 地形通用算法）。
 *
 * 所有输出都是**相对地形**，不依赖具体坐标：坡度 / 涉渡点 / 关隘 / 制高点 / 通视性 / 可通行性
 * 全部由高程栅格 + 河流折线推导；换一场仗只换栅格与 JSON 河流点列，本类不改。
 *
 * 兜底：栅格为合成场时 [HeightRaster.degraded] 仍为 true；河流点列缺失按无河处理，不崩。
 */
class TerrainMap(
    val raster: HeightRaster,
    val projection: GeoProjection,
    rivers: List<RiverDef>,
    val ridges: List<RidgeDef> = emptyList(),
) {

    val gridN: Int get() = projection.gridN

    /** 河流覆盖单元集（行优先编码）。 */
    private val riverCells: Set<Long> = rasterizeRivers(rivers)

    /** 缓存：涉渡点 / 关隘在构造时算一次，回合内只读。 */
    val fordCells: List<Cell> = detectFords()
    val chokeCells: List<Cell> = detectChokes()

    // ── 基础查询 ──────────────────────────────────────────────

    fun inBounds(cell: Cell): Boolean =
        cell.col in 0 until gridN && cell.row in 0 until gridN

    fun elevationMeters(cell: Cell): Double =
        if (inBounds(cell)) raster.metersAt(cell.col, cell.row) else 0.0

    /** 坡度（度）：与四邻最大高差 / 水平距离取反正切；边界按内侧邻域估算。 */
    fun slopeDeg(cell: Cell): Double {
        if (!inBounds(cell)) return 0.0
        val e0 = elevationMeters(cell)
        var maxSlope = 0.0
        for (n in neighbors4(cell)) {
            val de = kotlin.math.abs(elevationMeters(n) - e0)
            val slope = Math.toDegrees(atan(de / projection.cellSizeMeters.coerceAtLeast(1.0)))
            maxSlope = max(maxSlope, slope)
        }
        return maxSlope
    }

    fun kindAt(cell: Cell): TerrainKind = when {
        isWater(cell) -> TerrainKind.WATER
        else -> TerrainKind.fromMeters(elevationMeters(cell))
    }

    fun isWater(cell: Cell): Boolean {
        if (!inBounds(cell)) return false
        if (elevationMeters(cell) <= HeightRaster.OCEAN_ELEVATION_METERS + 0.5) return true
        return encode(cell) in riverCells
    }

    fun isFord(cell: Cell): Boolean = fordCells.contains(cell)

    /** 河流覆盖单元清单（壳层画河线 / 渡口金柱消费；纯只读快照）。 */
    fun riverCells(): List<Cell> = riverCells.map { key ->
        Cell((key % gridN).toInt(), (key / gridN).toInt())
    }

    // ── 通行性（§二 traversable） ─────────────────────────────

    /**
     * 坡度阈值：步兵 ≤45°，骑兵 ≤30°，辎重 / 民兵 ≤15°。
     * 非涉渡水面不可通行；涉渡点可过；积雪仅骑兵可强行。
     */
    fun passable(cell: Cell, type: ArmyType): Boolean {
        if (!inBounds(cell)) return false
        if (isWater(cell) && !isFord(cell)) return false
        val slope = slopeDeg(cell)
        val slopeLimit = when (type) {
            ArmyType.CAVALRY -> 30.0
            ArmyType.MILITIA -> 15.0
            else -> 45.0
        }
        if (slope > slopeLimit) return false
        if (kindAt(cell) == TerrainKind.SNOW && type == ArmyType.MILITIA) return false
        return true
    }

    // ── 地形分析（§二 fords / chokePoints / highGround / visibility） ──

    /** 制高点：本格高程比半径内所有邻格高出 [thresholdMeters]，即局部峰顶。 */
    fun highGround(cell: Cell, radius: Int = 3, thresholdMeters: Double = 300.0): Boolean {
        if (!inBounds(cell)) return false
        val e0 = elevationMeters(cell)
        var maxNeighbor = Double.MIN_VALUE
        forEachInRadius(cell, radius) { n ->
            maxNeighbor = max(maxNeighbor, elevationMeters(n))
        }
        return e0 > maxNeighbor + thresholdMeters
    }

    /** 通视性：两点连线上无山脊遮挡；采样点地形高出视线连线 [LOS_MARGIN_M] 即被遮。 */
    fun visible(a: Cell, b: Cell): Boolean {
        if (!inBounds(a) || !inBounds(b)) return false
        val dist = hypot((b.col - a.col).toDouble(), (b.row - a.row).toDouble())
        val samples = max(2, dist.toInt() * 2)
        val ea = elevationMeters(a)
        val eb = elevationMeters(b)
        for (i in 1 until samples) {
            val t = i.toDouble() / samples
            val col = (a.col + (b.col - a.col) * t).toInt()
            val row = (a.row + (b.row - a.row) * t).toInt()
            val lineElev = ea + (eb - ea) * t
            val terrainElev = raster.metersAt(col, row)
            if (terrainElev > lineElev + LOS_MARGIN_M) return false
        }
        return true
    }

    fun neighbors4(cell: Cell): List<Cell> {
        val out = ArrayList<Cell>(4)
        val cands = listOf(
            Cell(cell.col - 1, cell.row), Cell(cell.col + 1, cell.row),
            Cell(cell.col, cell.row - 1), Cell(cell.col, cell.row + 1),
        )
        for (c in cands) if (inBounds(c)) out.add(c)
        return out
    }

    fun neighbors8(cell: Cell): List<Cell> {
        val out = ArrayList<Cell>(8)
        for (dc in -1..1) for (dr in -1..1) {
            if (dc == 0 && dr == 0) continue
            val c = Cell(cell.col + dc, cell.row + dr)
            if (inBounds(c)) out.add(c)
        }
        return out
    }

    // ── 内部推导 ──────────────────────────────────────────────

    private fun detectFords(): List<Cell> {
        val result = ArrayList<Cell>()
        for (row in 0 until gridN) {
            for (col in 0 until gridN) {
                val cell = Cell(col, row)
                if (!isWater(cell)) continue
                val dry = neighbors8(cell).count { !isWater(it) }
                val wet = neighbors8(cell).count { isWater(it) }
                // 河面 ≥2 格连续、两岸都有干地 → 窄河涉渡点；坡度平缓优先
                if (wet >= 2 && dry >= 2 && slopeDeg(cell) <= FORD_SLOPE_DEG) {
                    result.add(cell)
                }
            }
        }
        return result
    }

    private fun detectChokes(): List<Cell> {
        val result = ArrayList<Cell>()
        for (row in 0 until gridN) {
            for (col in 0 until gridN) {
                val cell = Cell(col, row)
                val kind = kindAt(cell)
                if (kind != TerrainKind.HILL && kind != TerrainKind.MOUNTAIN) continue
                val dry = neighbors8(cell).count { passable(it, ArmyType.REGULAR) }
                val high = neighbors8(cell).count { elevationMeters(it) > elevationMeters(cell) }
                // 相邻高坡夹出的狭窄通道：可通行邻格 ≤3，且两侧有高坡
                if (dry <= CHOKE_MAX_PASSABLE && high >= 2) result.add(cell)
            }
        }
        return result
    }

    private fun rasterizeRivers(rivers: List<RiverDef>): Set<Long> {
        val set = HashSet<Long>()
        for (river in rivers) {
            val pts = river.pts
            if (pts.size < 2) continue
            for (i in 0 until pts.size - 1) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                val x0 = (p0.lng * gridN).toInt().coerceIn(0, gridN - 1)
                val y0 = (p0.lat * gridN).toInt().coerceIn(0, gridN - 1)
                val x1 = (p1.lng * gridN).toInt().coerceIn(0, gridN - 1)
                val y1 = (p1.lat * gridN).toInt().coerceIn(0, gridN - 1)
                val steps = max(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))
                for (s in 0..steps) {
                    val t = if (steps == 0) 0.0 else s.toDouble() / steps
                    val col = (x0 + (x1 - x0) * t).toInt()
                    val row = (y0 + (y1 - y0) * t).toInt()
                    set.add(encode(Cell(col, row)))
                }
            }
        }
        return set
    }

    private fun forEachInRadius(cell: Cell, radius: Int, block: (Cell) -> Unit) {
        for (dc in -radius..radius) for (dr in -radius..radius) {
            if (dc == 0 && dr == 0) continue
            val c = Cell(cell.col + dc, cell.row + dr)
            if (inBounds(c)) block(c)
        }
    }

    private fun encode(cell: Cell): Long = cell.row.toLong() * gridN + cell.col

    companion object {
        /** 通视遮挡余量（米）：地形高出视线连线该幅度才算山脊遮挡。 */
        const val LOS_MARGIN_M: Double = 40.0

        /** 涉渡点允许的最大坡度（度）。 */
        const val FORD_SLOPE_DEG: Double = 12.0

        /** 关隘：可通行邻格上限，超出则不算狭窄通道。 */
        const val CHOKE_MAX_PASSABLE: Int = 3
    }
}
