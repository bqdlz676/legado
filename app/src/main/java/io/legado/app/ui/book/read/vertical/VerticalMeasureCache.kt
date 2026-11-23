package io.legado.app.ui.book.read.vertical

/**
 * 落文 · 竖排阅读引擎 —— 测量与字位缓存（sparse array 等价实现）。
 *
 * 规格性能项（切片-09 五、竖排文字测量优化）：
 * 首次分页后把每列每字的 x/y 缓存到 sparse array，重排速度 ×3。
 *
 * 缓存分两层：
 * - [advanceCache]：码位 → 字宽，换字号/字体时整体失效；
 * - [rectCache]：字符流下标 → [LayoutCell] 字位，重排时直接复用，跳过测量与几何计算。
 *
 * 为什么用 Int 键 HashMap 而不是 android.util.SparseArray：核心层必须在纯 JVM 单测里跑，
 * 不引用 android.util；两者都是“整数下标 → 值”的稀疏映射，接入 Android 后可按需替换，
 * 对外接口不变。
 *
 * @param advanceCapacity 字宽缓存上限，超出后整表失效，避免长章节无界增长。
 */
internal class VerticalMeasureCache(
    private val advanceCapacity: Int = 4096,
) {
    private val advanceCache = HashMap<Int, Float>()
    private val rectCache = HashMap<Int, LayoutCell>()

    /** 真实调用测量接口的次数（bench 对比缓存命中前后的测量开销）。 */
    var measureCalls: Int = 0
        private set

    /** 字宽缓存命中次数。 */
    var advanceHits: Int = 0
        private set

    /** 字位缓存命中次数。 */
    var rectHits: Int = 0
        private set

    /** 取码位字宽；命中缓存不再调用 [compute]。 */
    fun advance(codePoint: Int, compute: () -> Float): Float {
        advanceCache[codePoint]?.let {
            advanceHits++
            return it
        }
        val value = compute()
        if (advanceCache.size >= advanceCapacity) advanceCache.clear()
        advanceCache[codePoint] = value
        measureCalls++
        return value
    }

    /** 写入/刷新某字的布局结果。 */
    fun putRect(cell: LayoutCell) {
        rectCache[cell.offset] = cell
    }

    /** 取某字缓存字位；未命中返回 null。 */
    fun rect(offset: Int): LayoutCell? {
        rectCache[offset]?.let {
            rectHits++
            return it
        }
        return null
    }

    /** 缓存字位是否已覆盖 0..[count]-1 全部下标；缺任一下标即视为不齐全，走冷排版。 */
    fun coversRects(count: Int): Boolean {
        if (rectCache.size < count) return false
        for (i in 0 until count) {
            if (!rectCache.containsKey(i)) return false
        }
        return true
    }

    val cachedRectCount: Int get() = rectCache.size

    val cachedAdvanceCount: Int get() = advanceCache.size

    /** 清字位缓存（章节切换/尺寸变化时调用）。 */
    fun clearRects() {
        rectCache.clear()
        rectHits = 0
    }

    /** 清全部缓存（[VerticalLayoutEngine.invalidateCache] 调用）。 */
    fun clearAll() {
        advanceCache.clear()
        rectCache.clear()
        measureCalls = 0
        advanceHits = 0
        rectHits = 0
    }
}
