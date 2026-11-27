package io.legado.app.ui.map.globe.astro.data

import android.content.res.AssetManager
import io.legado.app.constant.AppLog
import io.legado.app.ui.map.globe.astro.contract.SkyParams

/**
 * 内置层星表（数据层，不碰渲染）。
 *
 * 规格出处：§四 体积口径「star+star_name <1.5MB」；§十二 第 2 条「一帧 700 颗若每帧
 *  new 列表，软渲染 GC 抖动吃满 16ms；标量零分配」。本类把内置 CSV 一次性解析为扁平
 *  原始数组（DoubleArray / FloatArray / LongArray），不保留 9,000 个实体对象；取数
 *  走按索引的原始类型访问器，每帧不产生装箱分配。
 *
 * 空态口径（§十三 第 1 条、§七 第 6 条）：内置表缺失 / 损坏 / 解析为空时，不抛异常、
 *  不返回 null，而是进入 `isPlaceholder = true` 的空目录星表；上层见到该标志后回落到
 *  既有 `StarField`（种子 9173）程序化兜底，并显示「星表未载，当前为示意星空」。
 *
 * 导入留痕（D12 / §十四.6）：解析时统计总行数 / 越界坐标 / NaN 星等 / 重复主键，
 *  写入 [importReport] 可读记录并经既有 [AppLog] 落一条日志；异常计数另暴露为
 *  [importAnomalies]，上层错误态据此回填，不许静默吞掉坏行。
 */
class StarCatalog private constructor(
    /** 内置星数（占位态为 0）。 */
    val size: Int,
    /**  true = 内置表不可用，上层必须走程序化兜底。 */
    val isPlaceholder: Boolean,
    internal val raDeg: DoubleArray,
    internal val decDeg: DoubleArray,
    internal val magVt: FloatArray,
    internal val ids: LongArray,
    internal val constellations: Array<String>,
    internal val colorBv: DoubleArray,
    /** 导入校验留痕文本（行数 / 越界坐标 / NaN 星等 / 重复 id）；占位态也写明原因。 */
    val importReport: String,
    /** 异常计数合计（越界坐标 + NaN 星等 + 重复主键）；0 = 全量通过。 */
    val importAnomalies: Int,
    private val idToIndex: Map<Long, Int>
) {

    /** 按索引取 J2000 赤经（度，[0,360)）。越界调用由调用方保证；返回 0 不崩。 */
    fun raAt(i: Int): Double = if (i in 0 until size) raDeg[i] else 0.0

    /** 按索引取 J2000 赤纬（度，[-90,90]）。 */
    fun decAt(i: Int): Double = if (i in 0 until size) decDeg[i] else 0.0

    /** 按索引取 VT 星等（越小越亮）。 */
    fun magAt(i: Int): Float = if (i in 0 until size) magVt[i] else Float.NaN

    /** 按索引取稳定星表主键。 */
    fun idAt(i: Int): Long = if (i in 0 until size) ids[i] else -1L

    /** 按索引取所属 88 星座三字母缩写。 */
    fun constellationAt(i: Int): String = if (i in 0 until size) constellations[i] else ""

    /**
     * 按索引取 B-V 色指数（星等色温度，§六.14）；缺色 / 解析失败返回 [Double.NaN]，
     * 渲染侧见到 NaN 必须回落星等映射，不猜色温。越界调用同样返回 NaN。
     */
    fun bvAt(i: Int): Double = if (i in 0 until size) colorBv[i] else Double.NaN

    /** 主键 → 目录索引；查不到返回 -1。映射在加载期一次性建好，查询期只读。 */
    fun indexOfId(id: Long): Int = idToIndex[id] ?: -1

    /**
     * 金字塔层（9 等 / 11.5 等）按需下载后的增量加载入口。
     *
     * TODO(接线/网络单)：`level` 对应 `SkyParams.PYRAMID_LEVELS` 两级；下载缓存校验通过后
     *  把暗星增量合并进索引结构。本轮按规格留空实现：不抛异常、不返回 null、不触发上层
     *  空指针；离线或未下载时内置亮星层照常工作（§十三 第 2 条：断网线框全内置、亮星全在）。
     */
    fun loadPyramid(level: Int) {
        //  deliberate no-op：金字塔数据不入库、不进 APK（§四 第 3 条），接入时在此追加合并逻辑。
    }

    companion object {

        private const val ASSET_PATH = "astro/stars.csv"

        /**
         * 从 `assets/astro/stars.csv` 加载内置星表。
         *
         * 任何失败（assets 缺失、文件读不出、列不全、零行）都收敛为占位态星表，绝不向上抛；
         * 失败原因与计数写进 [importReport] 并落 AppLog 留痕，不静默。
         */
        fun fromAssets(assets: AssetManager?): StarCatalog {
            if (assets == null) return placeholder("assets=null：目录句柄缺失，回落占位态")
            val rows: List<Array<String>> = try {
                assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                    val out = ArrayList<Array<String>>()
                    var headerSkipped = false
                    for (line in lines) {
                        if (!headerSkipped) { headerSkipped = true; continue }
                        if (line.isBlank()) continue
                        out.add(line.split(',').toTypedArray())
                    }
                    out
                }
            } catch (e: Exception) {
                val report = "assets 读取异常：${e.javaClass.simpleName}，回落占位态"
                runCatching { AppLog.put("星表导入留痕 · $report") }
                return placeholder(report)
            }

            if (rows.isEmpty()) return placeholder("rows=0：内置表为空，回落占位态")
            val catalog = parseRows(rows)
            runCatching { AppLog.put("星表导入留痕 · ${catalog.importReport}") }
            return catalog
        }

        /**
         * 纯解析入口（不碰 AssetManager，便于 JVM 单测构造夹具行）。
         * 坏行跳过并计数，不整表回退；有效行为 0 时收敛占位态。
         */
        internal fun parseRows(rows: List<Array<String>>): StarCatalog {
            val n = rows.size
            val ra = DoubleArray(n)
            val dec = DoubleArray(n)
            val mag = FloatArray(n)
            val ids = LongArray(n)
            val cons = arrayOfNulls<String>(n)
            val bv = DoubleArray(n)
            val idToIndex = HashMap<Long, Int>(n)

            var badCoord = 0
            var nanMag = 0
            var dupId = 0
            var k = 0
            val seen = HashSet<Long>()

            for (f in rows) {
                // 列序与生成脚本一致：id,tychoId,raDeg,decDeg,magVt,bayer,flamsteed,
                // hip,hd,colorBv,spectralType,distanceLy,constellation
                if (f.size < 13) { badCoord++; continue }
                val id = f[0].toLongOrNull() ?: run { badCoord++; continue }
                val raD = f[2].toDoubleOrNull() ?: run { badCoord++; continue }
                val decD = f[3].toDoubleOrNull() ?: run { badCoord++; continue }
                val mV = f[4].toFloatOrNull()
                if (raD < 0.0 || raD >= 360.0 || decD < -90.0 || decD > 90.0) { badCoord++; continue }
                if (mV == null || !mV.isFinite()) { nanMag++; continue }
                if (!seen.add(id)) { dupId++; continue }

                ids[k] = id
                ra[k] = raD
                dec[k] = decD
                mag[k] = mV
                cons[k] = f[12]
                bv[k] = f[9].toDoubleOrNull() ?: Double.NaN
                idToIndex[id] = k
                k++
            }

            val anomalies = badCoord + nanMag + dupId
            val report = "rows=$n badCoord=$badCoord nanMag=$nanMag dupId=$dupId"
            if (k == 0) return placeholder("$report · 有效行 0，回落占位态")

            @Suppress("UNCHECKED_CAST")
            return StarCatalog(
                size = k,
                isPlaceholder = false,
                raDeg = ra.copyOf(k),
                decDeg = dec.copyOf(k),
                magVt = mag.copyOf(k),
                ids = ids.copyOf(k),
                constellations = cons.copyOf(k) as Array<String>,
                colorBv = bv.copyOf(k),
                importReport = report,
                importAnomalies = anomalies,
                idToIndex = idToIndex,
            )
        }

        private fun placeholder(report: String): StarCatalog = StarCatalog(
            size = 0,
            isPlaceholder = true,
            raDeg = DoubleArray(0),
            decDeg = DoubleArray(0),
            magVt = FloatArray(0),
            ids = LongArray(0),
            constellations = emptyArray(),
            colorBv = DoubleArray(0),
            importReport = report,
            importAnomalies = 0,
            idToIndex = emptyMap()
        )

        /** 内置层星等上限锚点（6.5 等），与契约常量单源一致，留作上层裁剪参照。 */
        const val BUILTIN_MAG_LIMIT: Float = SkyParams.MAG_BRIGHT_ENTITY
    }
}
