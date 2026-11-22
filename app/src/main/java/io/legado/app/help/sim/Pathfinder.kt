package io.legado.app.help.sim

import kotlin.math.hypot

/**
 * 落文 · 寻路器（core:sim / §六）
 *
 * A* 代价（契约 §五）：step = 距离 × (1 + slope/45) × 路况系数。
 * 路况系数：缓坡 ≤8° → 1.0；中坡 ≤20° → 1.3；陡坡 → 1.8。
 *
 * 纯 Kotlin 实现：二叉堆 + 代际戳闭集，搜索缓冲可复用，避免每回合重复分配数组。
 */
object Pathfinder {

    data class PathResult(val path: List<Cell>, val cost: Double)

    private const val MAX_VISITED = 8000

    /** 可复用的 A* 搜索缓冲（单回合单线程顺序使用，不可并发共享）。 */
    class Scratch(val n: Int) {
        val g = FloatArray(n * n)
        val f = FloatArray(n * n)
        val prev = IntArray(n * n)
        val openGen = IntArray(n * n)
        val closedGen = IntArray(n * n)
        val heap = ArrayList<Long>()
        var stamp = 0
    }

    fun aStar(
        terrain: TerrainData,
        from: Cell,
        to: Cell,
        armyType: String,
        blocked: Set<Int>? = null,
        scratch: Scratch? = null,
    ): PathResult? {
        val n = terrain.n
        val sc = scratch ?: Scratch(n)
        sc.stamp++
        val stamp = sc.stamp

        val start = from.r * n + from.c
        val goal = to.r * n + to.c

        val g = sc.g
        val f = sc.f
        val prev = sc.prev
        val openGen = sc.openGen
        val closedGen = sc.closedGen
        val heap = sc.heap
        heap.clear()

        fun push(i: Int, fv: Float) {
            heap.add(pack(fv, i))
            var k = heap.size - 1
            while (k > 0) {
                val p = (k - 1) shr 1
                if (heapValue(heap[p]) <= heapValue(heap[k])) break
                val tmp = heap[p]; heap[p] = heap[k]; heap[k] = tmp
                k = p
            }
        }

        fun pop(): Long {
            val top = heap[0]
            val last = heap.removeAt(heap.size - 1)
            if (heap.isNotEmpty()) {
                heap[0] = last
                var k = 0
                while (true) {
                    val l = 2 * k + 1
                    val r = l + 1
                    var m = k
                    if (l < heap.size && heapValue(heap[l]) < heapValue(heap[m])) m = l
                    if (r < heap.size && heapValue(heap[r]) < heapValue(heap[m])) m = r
                    if (m == k) break
                    val tmp = heap[m]; heap[m] = heap[k]; heap[k] = tmp
                    k = m
                }
            }
            return top
        }

        fun heuristic(i: Int): Float {
            val r = i / n
            val c = i % n
            return hypot((r - to.r).toDouble(), (c - to.c).toDouble()).toFloat()
        }

        g[start] = 0f
        f[start] = heuristic(start)
        openGen[start] = stamp
        prev[start] = -1
        push(start, f[start])
        var visited = 0

        while (heap.isNotEmpty() && visited < MAX_VISITED) {
            val cur = heapIndex(pop())
            if (closedGen[cur] == stamp) continue
            closedGen[cur] = stamp
            visited++
            if (cur == goal) break
            val r = cur / n
            val c = cur % n
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val rr = r + dr
                val cc = c + dc
                if (!terrain.passable(rr, cc, armyType)) continue
                val ni = rr * n + cc
                if (blocked != null && ni in blocked) continue
                if (closedGen[ni] == stamp) continue
                val sl = terrain.slopeAt(rr, cc).toDouble()
                val terrainFactor = when {
                    sl <= 8.0 -> 1.0
                    sl <= 20.0 -> 1.3
                    else -> 1.8
                }
                val step = hypot(dr.toDouble(), dc.toDouble()) * (1 + sl / 45.0) * terrainFactor
                val ng = g[cur] + step
                if (openGen[ni] != stamp || ng < g[ni]) {
                    g[ni] = ng.toFloat()
                    f[ni] = (ng + heuristic(ni)).toFloat()
                    prev[ni] = cur
                    openGen[ni] = stamp
                    push(ni, f[ni])
                }
            }
        }

        if (openGen[goal] != stamp) return null
        val path = ArrayList<Cell>()
        var cur = goal
        while (cur != -1) {
            path.add(Cell(cur / n, cur % n))
            cur = prev[cur]
        }
        path.reverse()
        return PathResult(path, g[goal].toDouble())
    }

    /**
     * 侧翼迂回：在直达线两侧各取一个侧向中继点，分别 A*，连同直达路线一起取最便宜者。
     */
    fun flankPath(
        terrain: TerrainData,
        from: Cell,
        to: Cell,
        armyType: String,
        scratch: Scratch? = null,
    ): PathResult? {
        val direct = aStar(terrain, from, to, armyType, scratch = scratch)
        val candidates = ArrayList<PathResult>()
        direct?.let { candidates.add(it) }

        val n = terrain.n
        val midR = (from.r + to.r) / 2
        val midC = (from.c + to.c) / 2
        val dr = to.r - from.r
        val dc = to.c - from.c
        val len = hypot(dr.toDouble(), dc.toDouble()).coerceAtLeast(1.0)
        val perpR = -dc / len
        val perpC = dr / len
        val offset = (n * 0.12).toInt().coerceAtLeast(2)

        for (sign in listOf(-1.0, 1.0)) {
            val wr = (midR + perpR * offset * sign).toInt().coerceIn(0, n - 1)
            val wc = (midC + perpC * offset * sign).toInt().coerceIn(0, n - 1)
            val way = Cell(wr, wc)
            val leg1 = aStar(terrain, from, way, armyType, scratch = scratch) ?: continue
            val leg2 = aStar(terrain, way, to, armyType, scratch = scratch) ?: continue
            val merged = ArrayList(leg1.path)
            if (leg2.path.size > 1) merged.addAll(leg2.path.drop(1))
            candidates.add(PathResult(merged, leg1.cost + leg2.cost))
        }

        return candidates.minByOrNull { it.cost }
    }

    // f 值压缩进 long 堆：高 32 位存 f 序值，低 32 位存节点下标。
    private fun pack(fv: Float, i: Int): Long {
        val bits = fv.toBits().toLong()
        return (bits shl 32) or (i.toLong() and 0xFFFFFFFFL)
    }

    private fun heapValue(packed: Long): Float = Float.fromBits((packed ushr 32).toInt())

    private fun heapIndex(packed: Long): Int = (packed and 0xFFFFFFFFL).toInt()
}
