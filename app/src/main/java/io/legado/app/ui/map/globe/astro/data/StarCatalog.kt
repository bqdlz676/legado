package io.legado.app.ui.map.globe.astro.data

import android.content.res.AssetManager
import io.legado.app.constant.AppLog
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import java.io.File
import java.security.MessageDigest

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

    // ───────────────────── 金字塔增量层（L1/L2，按需下载后并入） ─────────────────────
    // 口径（方案 §4.4）：增量追加为独立扁平原始类型数组，查询期按索引访问、不 new 实体列表；
    // L0 主键锚点不变，金字塔层只增暗星渲染点，不回写 L0、不覆盖既有索引。

    internal var pyramidRaDeg: DoubleArray = DoubleArray(0)
        private set
    internal var pyramidDecDeg: DoubleArray = DoubleArray(0)
        private set
    internal var pyramidMagVt: FloatArray = FloatArray(0)
        private set
    internal var pyramidIds: LongArray = LongArray(0)
        private set
    internal var pyramidConstellations: Array<String> = emptyArray()
        private set
    internal var pyramidColorBv: DoubleArray = DoubleArray(0)
        private set

    private val pyramidIdToIndex = HashMap<Long, Int>()
    private val loadedLevels = HashSet<Int>()
    private var pyramidBadCoord = 0
    private var pyramidNanMag = 0
    private var pyramidBandMag = 0
    private var pyramidDupId = 0

    /** 金字塔增量星数（已并入的 L1/L2 合计；L0 内置星不计入，[size] 语义不变）。 */
    val pyramidSize: Int get() = pyramidIds.size

    /** 金字塔层异常计数合计（越界坐标 + 非有限/越带星等 + 跨层重复 id）。 */
    val pyramidAnomalies: Int
        get() = pyramidBadCoord + pyramidNanMag + pyramidBandMag + pyramidDupId

    /** 金字塔导入留痕文本；未加载过任何层时为空串口径。 */
    val pyramidReport: String
        get() = "pyramid loaded=[${loadedLevels.sorted().joinToString(",")}] " +
            "badCoord=$pyramidBadCoord nanMag=$pyramidNanMag bandMag=$pyramidBandMag dupId=$pyramidDupId"

    /**
     * 合并前内存评估钩子（可注入便于纯 JVM 单测；生产默认按行均扁平字节估算对照空闲内存）。
     * 返回 false = 评估不足，拒绝合并该层并回 [PyramidResult.LowMemory]，不抛 OOM。
     */
    internal var pyramidMemoryGuard: (rows: Int) -> Boolean = { rows ->
        Runtime.getRuntime().freeMemory() >= rows * PYRAMID_ROW_BYTES
    }

    /** 按金字塔增量索引取 J2000 赤经（度，[0,360)）；越界返回 0 不崩。 */
    fun pyramidRaAt(i: Int): Double = if (i in 0 until pyramidSize) pyramidRaDeg[i] else 0.0

    /** 按金字塔增量索引取 J2000 赤纬（度，[-90,90]）。 */
    fun pyramidDecAt(i: Int): Double = if (i in 0 until pyramidSize) pyramidDecDeg[i] else 0.0

    /** 按金字塔增量索引取 VT 星等；越界返回 NaN。 */
    fun pyramidMagAt(i: Int): Float = if (i in 0 until pyramidSize) pyramidMagVt[i] else Float.NaN

    /** 按金字塔增量索引取稳定主键；越界返回 -1。 */
    fun pyramidIdAt(i: Int): Long = if (i in 0 until pyramidSize) pyramidIds[i] else -1L

    /** 按金字塔增量索引所属 88 星座三字母缩写；越界返回空串。 */
    fun pyramidConstellationAt(i: Int): String =
        if (i in 0 until pyramidSize) pyramidConstellations[i] else ""

    /** 按金字塔增量索引取 B-V 色指数；缺色 / 越界返回 NaN，渲染侧回落星等映射。 */
    fun pyramidBvAt(i: Int): Double =
        if (i in 0 until pyramidSize) pyramidColorBv[i] else Double.NaN

    /** 金字塔层主键 → 增量索引；查不到返回 -1。L0 主键仍走 [indexOfId]。 */
    fun pyramidIndexOfId(id: Long): Int = pyramidIdToIndex[id] ?: -1

    /**
     * 金字塔层（9 等 / 11.5 等）按需下载后的增量加载入口（方案 §4.1–§4.3）。
     *
     * 校验顺序：level 越界 / 目录为 null → [PyramidResult.Missing]；切片或旁证不存在 → Missing；
     * 旁证 sha256 先于解析，比对不符 → [PyramidResult.Corrupt] 并隔离坏片；解析零有效行 →
     * Corrupt；合并前内存评估不足 → [PyramidResult.LowMemory]；通过后扁平数组合并，返回
     * [PyramidResult.Loaded]。任何边界都不抛异常、不清空 L0，缺文件时内置亮星层照常可显示。
     *
     * @param level 1 = L1 (6.5, 9.0]，2 = L2 (9.0, 11.5]；0 / 负数 / 大于 [SkyParams.PYRAMID_LEVELS] 一律 Missing。
     * @param cacheDir 设备缓存根（宿主注入 `File(context.filesDir, "astro/pyramid")`；单测传临时目录）；null = Missing。
     */
    fun loadPyramid(level: Int, cacheDir: File?): PyramidResult {
        if (level < 1 || level > SkyParams.PYRAMID_LEVELS) return PyramidResult.Missing
        val dir = cacheDir ?: return PyramidResult.Missing
        // 已加载过该层：幂等返回，不重复追加数组（方案 §6.7 并发连点 / 重复调用）。
        if (loadedLevels.contains(level)) return PyramidResult.Loaded(level, 0)

        val fileName = "level_$level.csv"
        val shard = File(dir, fileName)
        val sidecar = File(dir, "$fileName.sha256")
        // 切片 / 旁证不存在 = 未下载或被系统清缓存：保留 L0，不弹错误态（§4.3 缺文件）。
        if (!shard.exists() || !sidecar.exists()) return PyramidResult.Missing

        val bytes = try {
            shard.readBytes()
        } catch (_: Exception) {
            isolateShard(dir, fileName)
            return PyramidResult.Corrupt
        }
        val expected = try {
            sidecar.readText(Charsets.UTF_8).trim().split(Regex("\\s+"))[0]
        } catch (_: Exception) {
            isolateShard(dir, fileName)
            return PyramidResult.Corrupt
        }
        // 旁证先于解析：校验和不符不进入合并，坏片改名隔离、不覆盖好片。
        if (!sha256Hex(bytes).equals(expected, ignoreCase = true)) {
            isolateShard(dir, fileName)
            return PyramidResult.Corrupt
        }

        val parsed = parsePyramidShard(bytes.decodeToString(), level)
        if (parsed.valid == 0) {
            isolateShard(dir, fileName)
            return PyramidResult.Corrupt
        }

        if (!pyramidMemoryGuard(parsed.valid)) return PyramidResult.LowMemory

        appendPyramid(parsed)
        loadedLevels.add(level)
        return PyramidResult.Loaded(level, parsed.valid)
    }

    /** 坏片隔离：CSV 与旁证改名 `.corrupt-<epoch>`，不覆盖好片；下次按缺文件走 Missing 重下。 */
    private fun isolateShard(dir: File, fileName: String) {
        val epoch = System.currentTimeMillis()
        runCatching {
            File(dir, fileName).renameTo(File(dir, "$fileName.corrupt-$epoch"))
            File(dir, "$fileName.sha256")
                .renameTo(File(dir, "$fileName.sha256.corrupt-$epoch"))
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 解析金字塔瘦表（7 列：id, tychoId, raDeg, decDeg, magVt, colorBv, constellation）。 */
    private fun parsePyramidShard(text: String, level: Int): PyramidParsed {
        // 星等带：L1 (6.5, 9.0]，L2 (9.0, 11.5]；下界排他、上界包含（方案 §1.3）。
        val loExclusive = if (level == 1) SkyParams.MAG_BRIGHT_ENTITY else LEVEL1_UPPER_MAG
        val hiInclusive = if (level == 1) LEVEL1_UPPER_MAG else SkyParams.MAG_DEPTH_LIMIT
        val raw = text.lineSequence().filter { it.isNotBlank() }.toList()
        val cap = (raw.size - 1).coerceAtLeast(0)
        val ids = LongArray(cap)
        val ra = DoubleArray(cap)
        val dec = DoubleArray(cap)
        val mag = FloatArray(cap)
        val bv = DoubleArray(cap)
        val cons = arrayOfNulls<String>(cap)

        var badCoord = 0
        var nanMag = 0
        var bandMag = 0
        var dupId = 0
        var k = 0
        var headerSkipped = false
        for (line in raw) {
            if (!headerSkipped) { headerSkipped = true; continue }
            val f = line.split(',').toTypedArray()
            if (f.size < 7) { badCoord++; continue }
            val id = f[0].toLongOrNull() ?: run { badCoord++; continue }
            val raD = f[2].toDoubleOrNull() ?: run { badCoord++; continue }
            val decD = f[3].toDoubleOrNull() ?: run { badCoord++; continue }
            val mV = f[4].toFloatOrNull()
            if (raD < 0.0 || raD >= 360.0 || decD < -90.0 || decD > 90.0) { badCoord++; continue }
            if (mV == null || !mV.isFinite()) { nanMag++; continue }
            if (!(mV > loExclusive && mV <= hiInclusive)) { bandMag++; continue }
            if (idToIndex.containsKey(id) || pyramidIdToIndex.containsKey(id)) { dupId++; continue }

            ids[k] = id
            ra[k] = raD
            dec[k] = decD
            mag[k] = mV
            bv[k] = f[5].toDoubleOrNull() ?: Double.NaN
            cons[k] = f[6]
            k++
        }

        pyramidBadCoord += badCoord
        pyramidNanMag += nanMag
        pyramidBandMag += bandMag
        pyramidDupId += dupId
        return PyramidParsed(
            ids.copyOf(k), ra.copyOf(k), dec.copyOf(k), mag.copyOf(k),
            bv.copyOf(k), cons.copyOf(k) as Array<String>, k,
        )
    }

    private fun appendPyramid(p: PyramidParsed) {
        val old = pyramidIds.size
        val n = p.valid
        pyramidRaDeg = pyramidRaDeg.copyOf(old + n)
        pyramidDecDeg = pyramidDecDeg.copyOf(old + n)
        pyramidMagVt = pyramidMagVt.copyOf(old + n)
        pyramidIds = pyramidIds.copyOf(old + n)
        pyramidColorBv = pyramidColorBv.copyOf(old + n)
        val cons = arrayOfNulls<String>(old + n)
        System.arraycopy(pyramidConstellations, 0, cons, 0, old)
        p.ids.copyInto(pyramidIds, old, 0, n)
        p.ra.copyInto(pyramidRaDeg, old, 0, n)
        p.dec.copyInto(pyramidDecDeg, old, 0, n)
        p.mag.copyInto(pyramidMagVt, old, 0, n)
        p.bv.copyInto(pyramidColorBv, old, 0, n)
        for (j in 0 until n) {
            cons[old + j] = p.cons[j]
            pyramidIdToIndex[p.ids[j]] = old + j
        }
        @Suppress("UNCHECKED_CAST")
        pyramidConstellations = cons as Array<String>
    }

    /** 金字塔解析中间产物（紧凑到有效行；无效行已分类计数留痕）。 */
    private class PyramidParsed(
        val ids: LongArray,
        val ra: DoubleArray,
        val dec: DoubleArray,
        val mag: FloatArray,
        val bv: DoubleArray,
        val cons: Array<String>,
        val valid: Int,
    )

    companion object {

        private const val ASSET_PATH = "astro/stars.csv"

        /** L1 星等上界 9.0 等（manifest `level1UpperMag`；提为 SkyParams 契约常量属交接项，本单不改契约层）。 */
        private const val LEVEL1_UPPER_MAG: Float = 9.0f

        /** 金字塔单行扁平数组估算字节（ra 8 + dec 8 + mag 4 + id 8 + bv 8 + 引用 12），内存评估用。 */
        private const val PYRAMID_ROW_BYTES: Long = 48L

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
