package io.legado.app.ui.map.globe.astro.data

import io.legado.app.data.dao.AsterismDao
import io.legado.app.data.dao.FieldAllocationDao
import io.legado.app.data.dao.StarDao
import io.legado.app.data.entities.AsterismMemberEntity
import io.legado.app.data.entities.FieldAllocationEntity
import io.legado.app.data.entities.StarEntity
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
    private val fieldDao: FieldAllocationDao? = null
) {

    /** 内置星数（占位态为 0）。 */
    val size: Int get() = catalog.size

    /** 内置表是否不可用（true → 上层回落到 `StarField` 种子 9173 兜底）。 */
    val isPlaceholder: Boolean get() = catalog.isPlaceholder

    /**
     * 矩形拾取：把落在赤经 [raMin, raMax]、赤纬 [decMin, decMax] 内的目录索引写入 [out]。
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
        return count
    }

    /**
     * 最近邻：返回距给定赤道坐标角距离最近的目录索引；空表返回 -1。
     * 距离用单位向量点积比较（弦距单调对应角距），避免逐颗 acos。
     */
    fun nearest(raDeg: Double, decDeg: Double): Int {
        val n = catalog.size
        if (n == 0) return -1
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
        return best
    }

    /** 主键 → 目录索引；查不到返回 -1。 */
    fun byId(id: Long): Int = catalog.indexOfId(id)

    /** 按星官取成员列表（关系路径，点选时调用）；无 DAO 回落空列表。 */
    fun byAsterism(asterismId: Long): List<AsterismMemberEntity> =
        asterismDao?.membersOf(asterismId) ?: emptyList()

    /** 按州域名反查分野条目（关系路径）；无 DAO 回落空列表。 */
    fun byRegion(regionName: String): List<FieldAllocationEntity> =
        fieldDao?.byRegionName(regionName) ?: emptyList()

    /** 主键取完整实体（关系路径）；无 DAO 或查不到返回 null。 */
    fun entityById(id: Long): StarEntity? = starDao?.byId(id)
}
