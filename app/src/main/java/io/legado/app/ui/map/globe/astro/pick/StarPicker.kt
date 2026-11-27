package io.legado.app.ui.map.globe.astro.pick

/**
 * 星空拾取：24dp 网格分桶 + 20dp 候选半径 + 重叠取最亮（§九.3）。
 *
 * 〔为什么网格分桶〕整帧可能有 700 颗星（§十四.13 bench），逐颗算距离在拾取热路径上
 * 不可接受；把可见星按屏幕 24dp 格分桶后，命中只需扫候选半径覆盖的 3×3 邻格，
 * 平均复杂度 O(k)，k 为邻格内候选数；退化（星点全挤一格）最坏 O(n)，与逐颗扫描同界。
 *
 * 〔零分配口径（§十四.16 命中 <5ms）〕[rebuild] 复用既有桶列表与原始数组引用，
 * 不在拾取帧里 new 列表 / 装箱；[pick] 只读数组、只返回索引，不产出对象。
 *
 * 〔坐标口径〕本类只认屏幕 px；dp→px 换算在构造时按 [density] 一次完成，
 * 命中半径与格宽不在热路径上重复乘密度。
 */
class StarPicker(
    private val density: Float,
) {

    companion object {
        /** 分桶网格边长 24dp（§九.3）。 */
        const val GRID_CELL_DP: Float = 24f

        /** 候选拾取半径 20dp（§九.3）。 */
        const val PICK_RADIUS_DP: Float = 20f
    }

    private val cellPx: Float = (GRID_CELL_DP * density).coerceAtLeast(1f)
    private val radiusPx: Float = PICK_RADIUS_DP * density
    private val radiusSq: Float = radiusPx * radiusPx

    /** 桶：打包格坐标 → 星索引列表；rebuild 期复用列表，避免每帧 new 容器。 */
    private val buckets = HashMap<Long, MutableList<Int>>()

    private var xs: FloatArray = FloatArray(0)
    private var ys: FloatArray = FloatArray(0)
    private var mags: FloatArray = FloatArray(0)
    private var ids: LongArray = LongArray(0)
    private var count: Int = 0

    /** 候选半径 px（渲染层画选中 / 悬停光环时同口径取值）。 */
    fun hitRadiusPx(): Float = radiusPx

    /** 当前桶内可见星数。 */
    fun size(): Int = count

    /**
     * 用本帧可见星重建分桶。数组由调用方复用传入；本方法只保存引用、不拷贝。
     *
     * @param x 屏幕 x px（长度 ≥ n）
     * @param y 屏幕 y px（长度 ≥ n）
     * @param mag 星等（越小越亮；重叠时取最小）
     * @param id 稳定星表主键
     * @param n 可见条数；越界 / 负值得 0
     */
    fun rebuild(x: FloatArray, y: FloatArray, mag: FloatArray, id: LongArray, n: Int) {
        buckets.values.forEach { it.clear() }
        count = n.coerceAtLeast(0).coerceAtMost(minOf(x.size, y.size, mag.size, id.size))
        xs = x
        ys = y
        mags = mag
        ids = id
        for (i in 0 until count) {
            val key = pack(cellOf(xs[i]), cellOf(ys[i]))
            buckets.getOrPut(key) { ArrayList() }.add(i)
        }
    }

    /**
     * 拾取触点：20dp 半径内取最亮一颗；无命中返回 -1。
     * 重叠口径（§九.3）：同半径内多颗时 mag 最小者胜；星等并列取索引靠前者，结果确定可复测。
     */
    fun pick(touchX: Float, touchY: Float): Int {
        if (count == 0 || !touchX.isFinite() || !touchY.isFinite()) return -1
        val cx = cellOf(touchX)
        val cy = cellOf(touchY)
        var best = -1
        var bestMag = Float.POSITIVE_INFINITY
        for (gx in cx - 1..cx + 1) {
            for (gy in cy - 1..cy + 1) {
                val bucket = buckets[pack(gx, gy)] ?: continue
                for (idx in bucket) {
                    val dx = xs[idx] - touchX
                    val dy = ys[idx] - touchY
                    if (dx * dx + dy * dy > radiusSq) continue
                    val m = mags[idx]
                    if (m.isNaN() || m >= bestMag) continue // 严格小于才替换：星等并列时保留先扫到的（索引靠前），结果确定可复测
                    bestMag = m
                    best = idx
                }
            }
        }
        return best
    }

    /** 拾取主键；无命中返回 -1L。 */
    fun pickId(touchX: Float, touchY: Float): Long {
        val i = pick(touchX, touchY)
        return if (i >= 0) ids[i] else -1L
    }

    /** 格坐标：负坐标向下取整（floor 而非截断），保证桶覆盖全平面不出现半格。 */
    private fun cellOf(v: Float): Int = kotlin.math.floor(v / cellPx).toInt()

    /** 格坐标打包成 Long 键；负格位用无符号低位保存，避免符号位污染。 */
    private fun pack(cx: Int, cy: Int): Long =
        (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
}
