package io.legado.app.help.sim

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 落文 · 地形分析器（core:sim / §六）
 *
 * 纯 Kotlin、零 Android 依赖，可在 JVM 单测中直接构造。
 *
 * 通行坡度硬阈值（契约 §六）：
 *  - 步兵（elite / regular）≤ 45°
 *  - 骑兵（cavalry）      ≤ 30°
 *  - 辎重（militia）      ≤ 15°
 *
 * 高程来源优先级：外部解码好的高度图像素 → campaign.terrain.heightPixels（JSON 内嵌）
 * → 程序化起伏（脊线 / 河谷按 seed 可复现）。任何来源都不伪装成实测。
 */

/** 网格坐标（r 行向下，c 列向右）。 */
data class Cell(val r: Int, val c: Int)

/** 经纬度包围盒。 */
data class BBox(
    val west: Double,
    val east: Double,
    val south: Double,
    val north: Double,
)

/** 制高点记录：相对邻域高差越大，advantage 越高（封顶 1.6）。 */
data class HighGround(val r: Int, val c: Int, val advantage: Double)

/** 渡口 / 关隘记录。 */
data class PassPoint(val r: Int, val c: Int)

/** 分析后的只读地形网格。 */
class TerrainData(
    val n: Int,
    val bbox: BBox,
    val cellKm: Double,
    val heights: FloatArray,
    val slope: FloatArray,
    /** 通行等级：3=≤15°（辎重可过） 2=≤30°（马可过） 1=≤45°（步可过） 0=不可通行 */
    val traversable: ByteArray,
    val river: ByteArray,
    val fords: List<PassPoint>,
    val chokePoints: List<PassPoint>,
    val highGround: List<HighGround>,
    val heightSource: String,
    val heightLabel: String,
    val minEl: Double,
    val maxEl: Double,
) {
    fun idx(r: Int, c: Int): Int = r * n + c

    fun inBounds(r: Int, c: Int): Boolean = r in 0 until n && c in 0 until n

    fun heightAt(r: Int, c: Int): Float {
        val rr = max(0, min(n - 1, r))
        val cc = max(0, min(n - 1, c))
        return heights[idx(rr, cc)]
    }

    /** 通行判定：河流不可直穿；坡度按兵种阈值卡。 */
    fun passable(r: Int, c: Int, armyType: String): Boolean {
        if (!inBounds(r, c)) return false
        if (river[idx(r, c)].toInt() != 0) return false
        val need = when (armyType) {
            "cavalry" -> 2   // 马 ≤30°
            "militia" -> 3   // 辎 ≤15°
            else -> 1        // 步 ≤45°
        }
        return traversable[idx(r, c)].toInt() >= need
    }

    fun slopeAt(r: Int, c: Int): Float {
        if (!inBounds(r, c)) return 90f
        return slope[idx(r, c)]
    }

    fun isRiver(r: Int, c: Int): Boolean = inBounds(r, c) && river[idx(r, c)].toInt() != 0

    fun highGroundNear(r: Int, c: Int, radius: Int = 1): HighGround? =
        highGround.firstOrNull { hypot((it.r - r).toDouble(), (it.c - c).toDouble()) <= radius }

    /**
     * 视线：沿两点连线采样中点高程，若中段明显高出两端连线（山脊遮挡）则不可见。
     */
    fun visible(a: Cell, b: Cell): Boolean {
        val steps = max(2, hypot((b.r - a.r).toDouble(), (b.c - a.c).toDouble()).roundToInt())
        var maxMid = Double.NEGATIVE_INFINITY
        for (s in 1 until steps) {
            val t = s.toDouble() / steps
            val rr = (a.r + (b.r - a.r) * t).roundToInt()
            val cc = (a.c + (b.c - a.c) * t).roundToInt()
            maxMid = max(maxMid, heightAt(rr, cc).toDouble())
        }
        val ha = heightAt(a.r, a.c).toDouble()
        val hb = heightAt(b.r, b.c).toDouble()
        val line = ha + (hb - ha) * 0.5
        return maxMid <= line + 60
    }

    fun toLngLat(cell: Cell): DoubleArray {
        val lng = bbox.west + cell.c.toDouble() / (n - 1) * (bbox.east - bbox.west)
        val lat = bbox.north - cell.r.toDouble() / (n - 1) * (bbox.north - bbox.south)
        return doubleArrayOf(lng, lat)
    }

    fun toCell(lng: Double, lat: Double): Cell {
        val c = ((lng - bbox.west) / (bbox.east - bbox.west) * (n - 1)).roundToInt()
        val r = ((bbox.north - lat) / (bbox.north - bbox.south) * (n - 1)).roundToInt()
        return Cell(max(0, min(n - 1, r)), max(0, min(n - 1, c)))
    }
}

object TerrainAnalyzer {

    /** 栅格化战役地形，输出可被寻路 / 情报 / 战斗直接消费的 TerrainData。 */
    fun analyze(campaign: CampaignSpec, n: Int, overridePixels: IntArray? = null): TerrainData {
        val focus = campaign.terrainFocus ?: FocusSpec(
            center = listOf(0.0, 0.0), radius = 50.0,
        )
        val bbox = bboxOf(focus)
        val cellKm = (focus.radius * 2.0) / n
        val terrain = campaign.terrain ?: TerrainSpec()

        val hm = buildHeight(campaign, terrain, n, overridePixels)
        val h = hm.heights

        val slope = FloatArray(n * n)
        val trav = ByteArray(n * n)
        for (r in 0 until n) {
            for (c in 0 until n) {
                val i = r * n + c
                val hx = (h[r * n + min(n - 1, c + 1)] - h[r * n + max(0, c - 1)]) / (2 * cellKm * 1000)
                val hy = (h[min(n - 1, r + 1) * n + c] - h[max(0, r - 1) * n + c]) / (2 * cellKm * 1000)
                val deg = atan(hypot(hx, hy)) * 180.0 / Math.PI
                slope[i] = deg.toFloat()
                trav[i] = when {
                    deg <= 15.0 -> 3
                    deg <= 30.0 -> 2
                    deg <= 45.0 -> 1
                    else -> 0
                }
            }
        }

        val river = rasterizeRivers(terrain.rivers, n)

        // 渡口：河格、坡度 ≤10°、上下两侧都可通行。
        val fords = ArrayList<PassPoint>()
        for (r in 1 until n - 1) {
            for (c in 1 until n - 1) {
                if (river[r * n + c].toInt() == 0) continue
                if (slope[r * n + c] <= 10f &&
                    trav[(r - 1) * n + c].toInt() >= 1 && trav[(r + 1) * n + c].toInt() >= 1
                ) {
                    fords.add(PassPoint(r, c))
                }
            }
        }

        // 制高点：10 格邻域内相对高差 > 300m。
        val high = ArrayList<HighGround>()
        var r = 0
        while (r < n) {
            var c = 0
            while (c < n) {
                var mn = h[r * n + c]
                for (dr in -5..5) for (dc in -5..5) {
                    val rr = max(0, min(n - 1, r + dr))
                    val cc = max(0, min(n - 1, c + dc))
                    mn = min(mn, h[rr * n + cc])
                }
                val diff = h[r * n + c] - mn
                if (diff > 300f) {
                    high.add(HighGround(r, c, min(1.6, 1.0 + diff / 1200f)))
                }
                c += 3
            }
            r += 3
        }

        // 关隘：自身平缓可通行，南北两格坡度都 >40°，形成窄口。
        val choke = ArrayList<PassPoint>()
        for (rr in 2 until n - 2) {
            for (c in 2 until n - 2) {
                val i = rr * n + c
                if (trav[i].toInt() >= 1 && slope[i] <= 25f) {
                    if (slope[(rr - 2) * n + c] > 40f && slope[(rr + 2) * n + c] > 40f) {
                        choke.add(PassPoint(rr, c))
                    }
                }
            }
        }

        return TerrainData(
            n = n,
            bbox = bbox,
            cellKm = cellKm,
            heights = h,
            slope = slope,
            traversable = trav,
            river = river,
            fords = fords,
            chokePoints = choke,
            highGround = high,
            heightSource = hm.source,
            heightLabel = hm.label,
            minEl = hm.minEl,
            maxEl = hm.maxEl,
        )
    }

    private fun bboxOf(focus: FocusSpec): BBox {
        val lng = focus.center[0]
        val lat = focus.center[1]
        val dLat = focus.radius / 111.0
        val dLng = focus.radius / (111.0 * cos(lat * Math.PI / 180.0))
        return BBox(lng - dLng, lng + dLng, lat - dLat, lat + dLat)
    }

    private data class HeightResult(
        val heights: FloatArray,
        val source: String,
        val label: String,
        val minEl: Double,
        val maxEl: Double,
    )

    private fun buildHeight(
        campaign: CampaignSpec,
        terrain: TerrainSpec,
        n: Int,
        overridePixels: IntArray?,
    ): HeightResult {
        val raw = FloatArray(n * n)

        val embedded = terrain.heightPixels
        val realPixels = when {
            overridePixels != null && overridePixels.size == n * n -> overridePixels
            embedded != null && embedded.size == n * n -> embedded
            else -> null
        }

        if (realPixels != null) {
            val lo = terrain.minEl ?: 0.0
            val hi = terrain.maxEl ?: 3000.0
            for (i in raw.indices) {
                raw[i] = (lo + realPixels[i] / 255.0 * (hi - lo)).toFloat()
            }
            return HeightResult(raw, "png", terrain.source ?: "高程图", lo, hi)
        }

        // 程序化：低频起伏 + 脊线 / 点状山峰高斯包 + 河谷下切（可复现 seed）。
        val seed = terrain.seed ?: 202
        val rand = mulberry32(seed)
        val ph = listOf(rand() * 6.28, rand() * 6.28, rand() * 6.28)
        for (y in 0 until n) {
            for (x in 0 until n) {
                val u = x.toDouble() / n
                val v = y.toDouble() / n
                var h = 900.0 +
                    420 * sin(u * 6.28 * 1.3 + ph[0]) * cos(v * 6.28 * 0.9 + ph[1]) +
                    260 * sin((u + v) * 6.28 * 2.1 + ph[2]) +
                    140 * sin(u * 6.28 * 4.7) * sin(v * 6.28 * 3.9)
                for (rg in terrain.ridges.orEmpty()) {
                    if (rg.a != null && rg.b != null) {
                        val d = distToSeg(u, v, rg.a[0], rg.a[1], rg.b[0], rg.b[1])
                        val w = rg.w ?: 0.2
                        h += (rg.h ?: 0.0) * exp(-(d * d) / (w * w))
                    } else if (rg.x != null) {
                        val d = hypot(u - rg.x, v - (rg.y ?: 0.0))
                        val w = rg.w ?: 0.2
                        h += (rg.h ?: 0.0) * exp(-(d * d) / (w * w))
                    }
                }
                raw[y * n + x] = max(0f, h.toFloat())
            }
        }
        // 河谷：沿折线把地形真刻下去，渡口与「不可直穿」才有意义。
        for (rv in terrain.rivers.orEmpty()) {
            val w = rv.w ?: 0.018
            val deep = rv.depth ?: 420.0
            val segs = segmentsOf(rv)
            for ((p0, p1) in segs) {
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        val d = distToSeg(x / n.toDouble(), y / n.toDouble(), p0[0], p0[1], p1[0], p1[1])
                        if (d < w) raw[y * n + x] = (raw[y * n + x] - deep * (1 - d / w)).toFloat()
                    }
                }
            }
        }
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (v in raw) {
            lo = min(lo, v.toDouble())
            hi = max(hi, v.toDouble())
        }
        return HeightResult(raw, "procedural", "程序化示意地形（非实测）", kotlin.math.round(lo), kotlin.math.round(hi))
    }

    private fun rasterizeRivers(rivers: List<RiverSpec>?, n: Int): ByteArray {
        val mask = ByteArray(n * n)
        for (rv in rivers.orEmpty()) {
            val segs = segmentsOf(rv)
            val steps = n * 2
            for ((p0, p1) in segs) {
                for (s in 0..steps) {
                    val t = s.toDouble() / steps
                    val u = p0[0] + (p1[0] - p0[0]) * t
                    val v = p0[1] + (p1[1] - p0[1]) * t
                    val c = (u * (n - 1)).roundToInt()
                    val r = (v * (n - 1)).roundToInt()
                    if (c in 0 until n && r in 0 until n) mask[r * n + c] = 1
                }
            }
        }
        return mask
    }

    private fun segmentsOf(rv: RiverSpec): List<Pair<List<Double>, List<Double>>> {
        val out = ArrayList<Pair<List<Double>, List<Double>>>()
        val pts = rv.pts
        if (pts != null && pts.size > 1) {
            for (i in 0 until pts.size - 1) out.add(pts[i] to pts[i + 1])
        } else if (rv.a != null && rv.b != null) {
            out.add(rv.a to rv.b)
        }
        return out
    }

    private fun distToSeg(
        px: Double, py: Double,
        ax: Double, ay: Double, bx: Double, by: Double,
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 1e-9) 0.0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / len2))
        val x = ax + t * dx
        val y = ay + t * dy
        return hypot(px - x, py - y)
    }

    /** mulberry32：可复现伪随机，与旧 JS 引擎同算法。 */
    internal fun mulberry32(seed: Long): () -> Double {
        var a = seed and 0xFFFFFFFFL
        return {
            a = (a + 0x6D2B79F5L) and 0xFFFFFFFFL
            var t = a
            t = (t xor (t ushr 15)) * (1L or t) and 0xFFFFFFFFL
            t = (t + (t xor (t ushr 7)) * (61L or t) and 0xFFFFFFFFL) xor t
            ((t xor (t ushr 14)) ushr 0) / 4294967296.0
        }
    }
}
