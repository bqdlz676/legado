package io.legado.app.ui.map.globe.astro.bench

import io.legado.app.data.entities.ConstellationLineEntity
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.layer.AsterismGroup
import io.legado.app.ui.map.globe.astro.layer.AsterismLayer
import io.legado.app.ui.map.globe.astro.layer.AsterismMember
import io.legado.app.ui.map.globe.astro.layer.ConstellationLayer
import io.legado.app.ui.map.globe.astro.layer.FieldAllocationLayer
import io.legado.app.ui.map.globe.astro.layer.FieldEntry
import io.legado.app.ui.map.globe.astro.pick.StarPicker
import io.legado.app.ui.map.globe.astro.proj.StarProjector
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 星空天象层四条性能 bench（规格 §十四.8、§十四.13-16）。
 *
 * 〔并入既有测试类口径〕本批为纯 JVM 单测，并入既有 `ui/map/globe` 测试集，不新建 CI job
 * （§十四 bench 段：B13 慢环节 + 红率，不单独开流水线）。
 *
 * 〔覆盖口径声明——不许把测不到的写成测到〕
 *  - 沙箱无 Android 运行时 / Skia Canvas：本批只测纯 CPU 编排路径（目录装载、投影、拾取
 *    重建、三 layer 路径计算）；Canvas 光栅、atlas 批绘、合成上屏不在覆盖范围内。
 *  - 真机（荣耀 WIN）帧耗需复测；本批通过 ≠ 真机帧耗达标，只作 CI 不退化的 CPU 卡口。
 *
 * 〔断言范式〕每条先 warmup [WARM_ROUNDS] 轮预热 JIT / 缓存，再采多轮样本取中位数；
 * 阈值按规格预算登记常量，中位数断言，禁止裸墙钟 `assertTrue(elapsed < 4)`。
 * 余量口径：纯 CPU 子集相对真机整帧留有 ≥3× 余量——投影 / 拾取只是整帧的一部分，
 * 断言线贴规格预算写，实测中位线应远低于预算线；CI 机器抖动 3 倍以内不致翻红。
 */
class SkyBenchTest {

    private companion object {
        /** 预热轮数：暖热 JIT 与数组缓存，避免冷启动样本拉高中位数。 */
        const val WARM_ROUNDS = 20

        /** 每轮 bench 采样次数；取中位数而非均值，抗单轮毛刺。 */
        const val MEASURE_ROUNDS = 100

        /** §十四.13 单帧实体星上限锚点：700 颗。 */
        const val BENCH_STAR_COUNT = 700

        /** 投影视口参数（与 SkyContractTest 同口径：1000px 焦距，540×960 中心）。 */
        const val FOCAL_PX = 1000.0
        const val CX = 540.0
        const val CY = 960.0
        const val OBSERVER_LAT = 34.0 // 长安示意纬度（地上观换算用，不参与天外投影）
        const val OBSERVER_LON = 108.9
        const val LST_DEG = 0.0

        // ── 规格预算（§十四.13-16）；断言线 = 规格预算，余量见类注释 ──
        /** §十四.13 星空投影 700 颗 < 4ms。 */
        const val PROJECT_BUDGET_MS = 4.0
        /** §十四.14 天球整帧 < 16ms（本用例只覆盖 CPU 部分）。 */
        const val FRAME_BUDGET_MS = 16.0
        /** §十四.15 双视角切换峰值 < 24ms。 */
        const val SWITCH_PEAK_BUDGET_MS = 24.0
        /** §十四.16 点击命中 < 5ms。 */
        const val PICK_BUDGET_MS = 5.0
    }

    /** 内置星表扁平数组（零对象装箱，与 StarCatalog 同口径）。 */
    private class Catalog(
        val ra: DoubleArray,
        val dec: DoubleArray,
        val mag: FloatArray,
        val id: LongArray,
    ) {
        val size: Int get() = id.size
    }

    private fun astroDir(): File {
        // 与 FontAssetBenchTest 同范式：从 user.dir 向上找含 app/src/main 的仓库根。
        val cwd = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(cwd) { it.parentFile }
            .first { File(it, "app/src/main/assets/astro/stars.csv").isFile }
        return File(root, "app/src/main/assets/astro")
    }

    private fun loadCatalog(): Catalog {
        val lines = File(astroDir(), "stars.csv").readLines()
        val ra = ArrayList<Double>()
        val dec = ArrayList<Double>()
        val mag = ArrayList<Float>()
        val id = ArrayList<Long>()
        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val f = line.split(",")
            if (f.size < 5) return@forEach
            ra += f[2].toDoubleOrNull() ?: return@forEach
            dec += f[3].toDoubleOrNull() ?: return@forEach
            mag += f[4].toFloatOrNull() ?: return@forEach
            id += f[0].toLongOrNull() ?: return@forEach
        }
        return Catalog(
            ra = ra.toDoubleArray(),
            dec = dec.toDoubleArray(),
            mag = mag.toFloatArray(),
            id = id.toLongArray(),
        )
    }

    private fun median(samples: List<Double>): Double {
        val sorted = samples.sorted()
        val mid = sorted.size / 2
        return if (sorted.isEmpty()) 0.0
        else if (sorted.size % 2 == 1) sorted[mid]
        else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** 批量投影：结果写入复用数组，返回可见颗数；函数体不 new 列表（§十二.2 零分配口径）。 */
    private fun projectBatch(
        cat: Catalog,
        n: Int,
        mode: SkyViewMode,
        yawDeg: Double,
        pitchDeg: Double,
        xs: FloatArray,
        ys: FloatArray,
        mags: FloatArray,
        ids: LongArray,
    ): Int {
        val out = FloatArray(3)
        var visible = 0
        for (i in 0 until n) {
            val ok = StarProjector.project(
                mode = mode,
                raDeg = cat.ra[i],
                decDeg = cat.dec[i],
                yawDeg = yawDeg,
                pitchDeg = pitchDeg,
                focalPx = FOCAL_PX,
                cx = CX,
                cy = CY,
                latDeg = OBSERVER_LAT,
                lonDeg = OBSERVER_LON,
                lstDeg = LST_DEG,
                out = out,
            )
            if (ok) {
                xs[visible] = out[0]
                ys[visible] = out[1]
                mags[visible] = cat.mag[i]
                ids[visible] = cat.id[i]
                visible++
            }
        }
        return visible
    }

    // ───────────────────── §十四.13 星空投影 700 颗 < 4ms ─────────────────────

    @Test
    fun `投影 700 颗中位数在 4ms 预算内`() {
        val cat = loadCatalog()
        val n = minOf(BENCH_STAR_COUNT, cat.size)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val mags = FloatArray(n)
        val ids = LongArray(n)

        // warmup 20 轮：暖热 JIT 与投影函数分支。
        repeat(WARM_ROUNDS) {
            projectBatch(cat, n, SkyViewMode.SKY_HEAVEN, 0.0, 0.0, xs, ys, mags, ids)
        }

        val samples = ArrayList<Double>(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) {
            val start = System.nanoTime()
            projectBatch(cat, n, SkyViewMode.SKY_HEAVEN, 0.0, 0.0, xs, ys, mags, ids)
            samples += (System.nanoTime() - start) / 1_000_000.0
        }

        // 断言：中位数 < 规格 4ms。余量理由：本用例只测纯 CPU 投影（无 Canvas 光栅 / GC 停顿），
        // 相对真机整帧 16ms 预算，投影项应占 ≤1/3；3× 余量保证 CI 抖动不翻红。
        val med = median(samples)
        assertTrue(
            "投影 $n 颗中位数应 < ${PROJECT_BUDGET_MS}ms，实际 ${med} ms",
            med < PROJECT_BUDGET_MS,
        )
    }

    // ───────────── §十四.14 天球整帧 < 16ms（仅 CPU 编排部分）─────────────

    @Test
    fun `整帧 CPU 部分中位数在 16ms 预算内`() {
        val cat = loadCatalog()
        val n = minOf(BENCH_STAR_COUNT, cat.size)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val mags = FloatArray(n)
        val ids = LongArray(n)
        val picker = StarPicker(1f)

        // 三 layer 从内置 CSV 装载一次（等价宿主 DAO 喂入；DAO 未就位时空表不崩）。
        val constLayer = ConstellationLayer().apply { loadLines(loadConstellationLines()) }
        val asterLayer = AsterismLayer().apply { loadGroups(loadAsterismGroups()) }
        val fieldLayer = FieldAllocationLayer().apply { loadEntries(loadFieldEntries()) }

        fun frameCpuPart() {
            val visible = projectBatch(cat, n, SkyViewMode.SKY_HEAVEN, 0.0, 0.0, xs, ys, mags, ids)
            picker.rebuild(xs, ys, mags, ids, visible)
            // 三 layer 本帧路径计算：可见线段 / 成员组 / 分野条目（纯 JVM 持有者，不碰 Canvas）。
            constLayer.visibleSegments()
            asterLayer.allGroups()
            fieldLayer.allEntries()
        }

        repeat(WARM_ROUNDS) { frameCpuPart() }

        val samples = ArrayList<Double>(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) {
            val start = System.nanoTime()
            frameCpuPart()
            samples += (System.nanoTime() - start) / 1_000_000.0
        }

        // 覆盖口径声明：整帧含 Canvas 光栅 / atlas 四芒批绘 / 合成上屏；CI 无 Skia 与 Android
        // Canvas，本用例只能覆盖 CPU 部分（目录数组 + 投影 + 拾取重建 + 三 layer 路径计算），
        // 光栅部分不测、不计入通过结论。16ms 为整帧规格预算，CPU 部分断言留足光栅余量。
        val med = median(samples)
        assertTrue(
            "整帧 CPU 部分中位数应 < ${FRAME_BUDGET_MS}ms，实际 ${med} ms",
            med < FRAME_BUDGET_MS,
        )
    }

    // ───────────── §十四.15 双视角切换峰值 < 24ms（过场代理口径）─────────────

    @Test
    fun `双视角切换峰值在 24ms 预算内`() {
        val cat = loadCatalog()
        val n = minOf(BENCH_STAR_COUNT, cat.size)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val mags = FloatArray(n)
        val ids = LongArray(n)

        // 800ms 过场（§十六.4 VIEW_SWITCH_MS）按 60fps 折算 ≈48 帧；逐帧步进取最大单步开销。
        val frames = (SkyParams.VIEW_SWITCH_MS / 16.67).toInt().coerceAtLeast(1)

        fun sweep(): Double {
            var maxStep = 0.0
            for (f in 0 until frames) {
                val t = f.toDouble() / (frames - 1).coerceAtLeast(1)
                val mode = if (t < 0.5) SkyViewMode.SKY_HEAVEN else SkyViewMode.SKY_EARTH
                val yaw = 0.0 + 60.0 * t
                val pitch = 0.0 + 20.0 * t
                val start = System.nanoTime()
                projectBatch(cat, n, mode, yaw, pitch, xs, ys, mags, ids)
                val step = (System.nanoTime() - start) / 1_000_000.0
                if (step > maxStep) maxStep = step
            }
            return maxStep
        }

        repeat(WARM_ROUNDS) { sweep() }

        val peaks = ArrayList<Double>(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) { peaks += sweep() }
        // 峰值口径：取各轮 sweep 峰值的中位数，避免单轮毛刺直接判红；阈值仍按规格 24ms。
        val peakMedian = median(peaks)

        // 覆盖口径声明：800ms spring 过场控制器（插值 / 相机飞行）尚未入库，本用例无法测真实
        // 过渡步进；当前以 StarProjector 在天外 / 地上两模式各 700 颗投影的最大单步开销作为
        // 峰值代理。spring 插值与相机飞行耗时待过场实现入库后另补用例，本结论不覆盖那部分。
        assertTrue(
            "双视角切换峰值中位数应 < ${SWITCH_PEAK_BUDGET_MS}ms，实际 ${peakMedian} ms",
            peakMedian < SWITCH_PEAK_BUDGET_MS,
        )
    }

    // ───────────────────── §十四.16 点击命中 < 5ms（8920 真实星表）─────────────

    @Test
    fun `全表命中中位数在 5ms 预算内`() {
        val cat = loadCatalog()
        val n = cat.size
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val mags = FloatArray(n)
        val ids = LongArray(n)
        val visible = projectBatch(cat, n, SkyViewMode.SKY_HEAVEN, 0.0, 0.0, xs, ys, mags, ids)
        assertTrue("内置星表应非空，实际 $visible 颗可见", visible > 0)

        val picker = StarPicker(1f)
        picker.rebuild(xs, ys, mags, ids, visible)

        // 触点取可见星自身坐标（保证命中），轮换扫过全表区域。
        fun pickOnce(round: Int) {
            val idx = (round * 7 + 3) % visible
            picker.pick(xs[idx], ys[idx])
        }

        repeat(WARM_ROUNDS) { picker.pick(xs[0], ys[0]) }

        val samples = ArrayList<Double>(MEASURE_ROUNDS)
        repeat(MEASURE_ROUNDS) { round ->
            val start = System.nanoTime()
            repeat(20) { pickOnce(round * 20 + it) }
            samples += (System.nanoTime() - start) / 1_000_000.0 / 20.0
        }

        // 断言：单次拾取中位数 < 5ms；8920 棵真实星表（读 stars.csv），非等价小样生成。
        val med = median(samples)
        assertTrue(
            "全表 $n 星单次命中中位数应 < ${PICK_BUDGET_MS}ms，实际 ${med} ms",
            med < PICK_BUDGET_MS,
        )
    }

    // ───────────────────── 内置 CSV 装载（bench 夹具，纯 JVM）─────────────────────

    private fun loadConstellationLines(): List<ConstellationLineEntity> {
        val out = ArrayList<ConstellationLineEntity>()
        File(astroDir(), "constellation_lines.csv").readLines().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val f = line.split(",")
            if (f.size < 4) return@forEach
            out += ConstellationLineEntity(
                fromStarId = f[0].toLongOrNull() ?: return@forEach,
                toStarId = f[1].toLongOrNull() ?: return@forEach,
                constellationId = f[2],
                style = if (f.size >= 4) f[3] else "constellation",
            )
        }
        return out
    }

    private fun loadAsterismGroups(): List<AsterismGroup> {
        val asterismLines = File(astroDir(), "asterisms.csv").readLines().drop(1)
        val membersByCode = HashMap<String, ArrayList<AsterismMember>>()
        File(astroDir(), "asterism_members.csv").readLines().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val f = line.split(",")
            if (f.size < 4) return@forEach
            membersByCode.getOrPut(f[0]) { ArrayList() } += AsterismMember(
                starId = f[1].toLongOrNull() ?: return@forEach,
                memberIndex = f[2],
                linkToNext = f[3].toBooleanStrictOrNull() ?: false,
            )
        }
        val groups = ArrayList<AsterismGroup>()
        asterismLines.forEach { line ->
            if (line.isBlank()) return@forEach
            val f = line.split(",")
            if (f.size < 3) return@forEach
            groups += AsterismGroup(
                code = f[0],
                name = f[1],
                parentGroup = f[2].ifEmpty { null },
                members = membersByCode[f[0]].orEmpty(),
            )
        }
        return groups
    }

    private fun loadFieldEntries(): List<FieldEntry> {
        val out = ArrayList<FieldEntry>()
        File(astroDir(), "field_allocations.csv").readLines().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val f = line.split(",")
            if (f.size < 5) return@forEach
            out += FieldEntry(
                asterismCode = f[0],
                regionName = f[1],
                stateName = f[2].ifEmpty { null },
                source = f[3].ifEmpty { null },
                isVariant = f[4].toBooleanStrictOrNull() ?: false,
            )
        }
        return out
    }
}
