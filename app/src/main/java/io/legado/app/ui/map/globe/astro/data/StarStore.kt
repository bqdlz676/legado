package io.legado.app.ui.map.globe.astro.data

import io.legado.app.data.dao.AsterismDao
import io.legado.app.data.dao.FieldAllocationDao
import io.legado.app.data.dao.StarDao
import io.legado.app.data.entities.AsterismMemberEntity
import io.legado.app.data.entities.FieldAllocationEntity
import io.legado.app.data.entities.StarEntity
import io.legado.app.ui.map.globe.astro.layer.FieldEntry
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * 星表读取与索引门面（数据层，不碰渲染、不碰接线）。
 *
 * 两类查询分开走：
 *  - 热路径（拾取 / 裁剪，每帧可能调用）：直接在 [StarCatalog] 的扁平数组上扫，结果写进
 *    调用方传入的可复用缓冲，查询期不 new 列表（§十二 第 2 条零分配口径）。
 *  - 关系路径（按星官 / 按州域反查）：委托 Room DAO；这些发生在点选交互时，不在帧循环内。
 *
 * DAO 可空：纯 JVM 单测或未建库场景下只走内置目录；关系查询回落空列表，不崩。
 */
class StarStore(
    val catalog: StarCatalog,
    private val starDao: StarDao? = null,
    private val asterismDao: AsterismDao? = null,
    private val fieldDao: FieldAllocationDao? = null,
    /** 分野条目（内置 CSV 解析；DAO 未就位时回落这份内置表）。 */
    val fieldEntries: List<FieldEntry> = emptyList(),
    /** 星官内码 → 成员星主键列表（内置锚点成员；示意轮廓几何来源）。 */
    val memberIdsByAsterism: Map<String, List<Long>> = emptyMap(),
) {

    /** 内置星数（占位态为 0）。 */
    val size: Int get() = catalog.size

    /** 金字塔增量层已并入星数（L1/L2 合计；不含 L0 内置星）。 */
    val pyramidSize: Int get() = catalog.pyramidSize

    /** 内置表是否不可用（true → 上层回落到 `StarField` 种子 9173 兜底）。 */
    val isPlaceholder: Boolean get() = catalog.isPlaceholder

    /**
     * 最近一次金字塔层加载结果（含四态）；未触发过为 null。
     * 错误态 / 进度观测据此回填渲染可见状态；只留痕，不清空 L0。
     */
    var lastPyramidResult: PyramidResult? = null
        private set

    /**
     * 矩形拾取：把落在赤经 [raMin, raMax]、赤纬 [decMin, decMax] 内的目录索引写入 [out]。
     *
     * 双层扫描口径（A 件）：任一时刻星表 = L0 内置层 ∪ 已并入的金字塔 L1/L2；先扫 L0，
     * 再叠加扫金字塔层。金字塔命中的索引加 [PYRAMID_IDX_BASE] 高位偏移编码，与 L0 索引
     * 区分；解码用 [isPyramidIndex] / [pyramidSlot]。
     *
     * @return 命中条数（写入 `out[0..count-1]`）；`out` 容量不足时只写到容量上限。
     *
     * 口径：赤经跨 0°/360° 的矩形由上层拆成两段分别调用，本方法只做单调区间比较。
     */
    fun queryRect(
        raMin: Double,
        raMax: Double,
        decMin: Double,
        decMax: Double,
        out: IntArray
    ): Int {
        var count = 0
        val n = catalog.size
        for (i in 0 until n) {
            val ra = catalog.raDeg[i]
            val dec = catalog.decDeg[i]
            if (ra < raMin || ra > raMax) continue
            if (dec < decMin || dec > decMax) continue
            if (count >= out.size) break
            out[count++] = i
        }
        // 叠加金字塔层：索引高位编码，避免与 L0 索引歧义。
        for (i in 0 until catalog.pyramidSize) {
            val ra = catalog.pyramidRaAt(i)
            val dec = catalog.pyramidDecAt(i)
            if (ra < raMin || ra > raMax) continue
            if (dec < decMin || dec > decMax) continue
            if (count >= out.size) break
            out[count++] = PYRAMID_IDX_BASE + i
        }
        return count
    }

    /**
     * 最近邻：返回距给定赤道坐标角距离最近的目录索引；空表返回 -1。
     * 距离用单位向量点积比较（弦距单调对应角距），避免逐颗 acos。
     * 双层扫描：L0 与金字塔层一起参与最近邻比较，金字塔命中返回高位编码索引。
     */
    fun nearest(raDeg: Double, decDeg: Double): Int {
        val n = catalog.size
        val p = catalog.pyramidSize
        if (n == 0 && p == 0) return -1
        val ra = Math.toRadians(raDeg)
        val dec = Math.toRadians(decDeg)
        val sinDec = sin(dec)
        val cosDec = cos(dec)
        var best = -1
        var bestDot = Double.NEGATIVE_INFINITY
        for (i in 0 until n) {
            val ri = Math.toRadians(catalog.raDeg[i])
            val di = Math.toRadians(catalog.decDeg[i])
            val dot = sinDec * sin(di) + cosDec * cos(di) * cos(ri - ra)
            if (dot > bestDot) {
                bestDot = dot
                best = i
            }
        }
        for (i in 0 until p) {
            val ri = Math.toRadians(catalog.pyramidRaAt(i))
            val di = Math.toRadians(catalog.pyramidDecAt(i))
            val dot = sinDec * sin(di) + cosDec * cos(di) * cos(ri - ra)
            if (dot > bestDot) {
                bestDot = dot
                best = PYRAMID_IDX_BASE + i
            }
        }
        return best
    }

    /** 主键 → 目录索引（先 L0 后金字塔）；查不到返回 -1。金字塔命中返回高位编码索引。 */
    fun byId(id: Long): Int {
        val l0 = catalog.indexOfId(id)
        if (l0 >= 0) return l0
        val p = catalog.pyramidIndexOfId(id)
        return if (p >= 0) PYRAMID_IDX_BASE + p else -1
    }

    /** 编码索引是否指向金字塔层。 */
    fun isPyramidIndex(index: Int): Boolean = index >= PYRAMID_IDX_BASE

    /** 金字塔编码索引 → 增量层槽位；非金字塔索引返回 -1。 */
    fun pyramidSlot(index: Int): Int = if (isPyramidIndex(index)) index - PYRAMID_IDX_BASE else -1

    /**
     * 金字塔层按需加载门面（A 件触发链入口）：转发 [StarCatalog.loadPyramid]，并把四态结果
     * 记入 [lastPyramidResult]。任何异常（IO / 解析 / 内存钩子越界）都收敛为回落 L0：
     * 不向上抛、不清空内置层、不砍功能；缺片 / 坏片时 L0 亮星照常可显示。
     *
     * @param level 1 = L1 (6.5, 9.0]，2 = L2 (9.0, 11.5]。
     * @param cacheDir 金字塔缓存根；null 回 Missing。
     */
    fun ensurePyramid(level: Int, cacheDir: File?): PyramidResult {
        val result = runCatching { catalog.loadPyramid(level, cacheDir) }
            .getOrDefault(PyramidResult.Missing)
        lastPyramidResult = result
        return result
    }

    /** 绑定金字塔加载进度观测（B 件）；null 解除。转发到目录内部进度钩子。 */
    fun bindPyramidProgress(listener: ((level: Int, done: Long, total: Long) -> Unit)?) {
        catalog.pyramidProgress = listener
    }

    /** 按金字塔增量索引取赤经；越界回 0。 */
    fun pyramidRaAt(i: Int): Double = catalog.pyramidRaAt(i)

    /** 按金字塔增量索引取赤纬；越界回 0。 */
    fun pyramidDecAt(i: Int): Double = catalog.pyramidDecAt(i)

    /** 按金字塔增量索引取星等；越界回 NaN。 */
    fun pyramidMagAt(i: Int): Float = catalog.pyramidMagAt(i)

    /** 按金字塔增量索引取主键；越界回 -1。 */
    fun pyramidIdAt(i: Int): Long = catalog.pyramidIdAt(i)

    /** 按星官取成员列表（关系路径，点选时调用）；无 DAO 回落空列表。 */
    fun byAsterism(asterismId: Long): List<AsterismMemberEntity> =
        asterismDao?.membersOf(asterismId) ?: emptyList()

    /** 按州域名反查分野条目（关系路径）；无 DAO 回落空列表。 */
    fun byRegion(regionName: String): List<FieldAllocationEntity> =
        fieldDao?.byRegionName(regionName) ?: emptyList()

    /** 主键取完整实体（关系路径）；无 DAO 或查不到返回 null。 */
    fun entityById(id: Long): StarEntity? = starDao?.byId(id)

    companion object {
        /**
         * 金字塔层索引高位编码基准（0x4000_0000）：L0 索引落在 [0, 9k] 量级，金字塔命中索引
         * 加此偏移后与 L0 区间彻底分离；值仍在 Int 正上限内，不会越界。选 2^30 而非 2^31 是
         * 留半幅余量，避免槽位累加后触碰 Int.MIN_VALUE 符号位。
         */
        const val PYRAMID_IDX_BASE: Int = 0x4000_0000
    }
}
