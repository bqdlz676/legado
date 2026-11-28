package io.legado.app.ui.map.globe.astro.proj

import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.StarCatalog
import io.legado.app.ui.map.globe.astro.data.StarStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * StarProjectionSource 双层合并投影验收（Q-502 / 方案 §4.4：L0 → 已加载 L1 → L2）。
 *
 * 纯 JVM：经 [StarCatalog.parseRows] 夹具行构造 L0 目录，金字塔切片写临时目录后
 * [StarStore.ensurePyramid] 并入。覆盖——未加载金字塔时与既有 L0 直扫逐位一致（零回归）；
 * 加载 L1 后星数单调增加且连续裁剪不放出 mag > magLimit；加载 L2 后深度上限 11.5 内
 * 含 L2 暗星，越限星一颗不出。
 *
 * 每条用例注释写明「改坏会红在哪一行」。
 */
class StarProjectionSourceTest {

    private companion object {
        /** 投影视口参数（与 SkyBenchTest 同口径：1000px 焦距，540×960 中心）。 */
        const val FOCAL_PX = 1000.0
        const val CX = 540.0
        const val CY = 960.0
        const val OBSERVER_LAT = 34.0
        const val OBSERVER_LON = 108.9
        const val LST_DEG = 0.0

        /** 内置星表锚点：stars.csv 有效行 8920（与 PyramidOfflineFallbackTest 同口径）。 */
        const val BUILTIN_STAR_COUNT = 8920
    }

    /** 投影出口夹具：顺序收集可见星标量，不接 Android 缓冲。 */
    private class Sink : StarProjectionSource.ProjectionSink {
        val ids = ArrayList<Long>()
        val mags = ArrayList<Float>()
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val count: Int get() = ids.size

        override fun add(px: Float, py: Float, mag: Float, id: Long, colorBv: Double) {
            xs += px; ys += py; mags += mag; ids += id
        }
    }

    private fun source() = StarProjectionSource(
        mode = SkyViewMode.SKY_HEAVEN,
        yawDeg = 0.0,
        pitchDeg = 0.0,
        focalPx = FOCAL_PX,
        cx = CX,
        cy = CY,
        latDeg = OBSERVER_LAT,
        lonDeg = OBSERVER_LON,
        lstDeg = LST_DEG,
    )

    private fun l0Row(id: Long, ra: String, dec: String, mag: String, bv: String): Array<String> =
        arrayOf(id.toString(), "TYC", ra, dec, mag, "", "", "", "", bv, "", "", "And")

    private fun pyrRow(id: Long, ra: String, dec: String, mag: String, bv: String): Array<String> =
        arrayOf(id.toString(), "TYC", ra, dec, mag, bv, "Ori")

    private fun store(vararg rows: Array<String>): StarStore =
        StarStore(StarCatalog.parseRows(rows.toList()))

    private fun tmpDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "$name-${System.nanoTime()}")
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeShard(dir: File, level: Int, rows: List<Array<String>>) {
        val header = "id,tychoId,raDeg,decDeg,magVt,colorBv,constellation"
        val body = rows.joinToString("\n") { it.joinToString(",") }
        val f = File(dir, "level_$level.csv")
        f.writeText("$header\n$body\n", Charsets.UTF_8)
        val hex = sha256(f.readBytes())
        File(dir, "level_$level.csv.sha256").writeText("$hex  level_$level.csv\n", Charsets.UTF_8)
    }

    /** 既有 L0 直扫参照（旧渲染器循环口径）：只扫 L0，连续裁剪 + 前半球判定。 */
    private fun referenceL0(catalog: StarCatalog, magLimit: Float): Sink {
        val sink = Sink()
        val out = FloatArray(3)
        for (i in 0 until catalog.size) {
            val mag = catalog.magAt(i)
            if (mag > magLimit) continue
            val ok = StarProjector.project(
                mode = SkyViewMode.SKY_HEAVEN,
                raDeg = catalog.raAt(i),
                decDeg = catalog.decAt(i),
                yawDeg = 0.0,
                pitchDeg = 0.0,
                focalPx = FOCAL_PX,
                cx = CX,
                cy = CY,
                latDeg = OBSERVER_LAT,
                lonDeg = OBSERVER_LON,
                lstDeg = LST_DEG,
                out = out,
            )
            if (ok) sink.add(out[0], out[1], mag, catalog.idAt(i), catalog.bvAt(i))
        }
        return sink
    }

    // ── 用例①：未加载金字塔时投影星数与 L0 直扫一致（零回归）─────────────────────

    @Test
    fun `未加载金字塔时投影结果与 L0 直扫逐位一致`() {
        // 1003 赤经 200° 落背面半球：参照与源都应剔除，证明前半球判定口径未变。
        val s = store(
            l0Row(1001, "20.0", "30.0", "1.0", "0.12"),
            l0Row(1002, "21.0", "-30.0", "2.0", "0.30"),
            l0Row(1003, "200.0", "10.0", "3.0", "0.40"),
        )
        assertEquals(0, s.pyramidSize)
        assertTrue("未加载金字塔时 hasPyramid 应为 false", !s.hasPyramid)

        val ref = referenceL0(s.catalog, 11.5f)
        val got = Sink()
        val visible = source().project(s, 11.5f, got)

        assertEquals("可见颗数必须与旧 L0 直扫一致", ref.count, visible)
        assertEquals("可见颗数必须与旧 L0 直扫一致", ref.count, got.count)
        assertEquals("主键集合必须与旧 L0 直扫一致", ref.ids.toSet(), got.ids.toSet())
    }

    @Test
    fun `真实内置表八千九百二十颗未加载金字塔时零回归`() {
        val catalog = loadBuiltinCatalog()
        val s = StarStore(catalog)
        assertEquals(BUILTIN_STAR_COUNT, s.size)
        assertEquals(0, s.pyramidSize)

        val ref = referenceL0(catalog, 11.5f)
        val got = Sink()
        val visible = source().project(s, 11.5f, got)

        assertEquals("全表可见颗数必须与旧 L0 直扫一致", ref.count, visible)
        assertEquals("全表可见颗数必须与旧 L0 直扫一致", ref.count, got.count)
    }

    // ── 用例②：加载 L1 后星数单调增加，magLimit 9.0 只含 ≤9.0 的星 ────────────────

    @Test
    fun `加载 L1 后星数单调增加且裁剪不越 9 等`() {
        val s = store(
            l0Row(1001, "20.0", "30.0", "1.0", "0.12"),
            l0Row(1002, "21.0", "-30.0", "2.0", "0.30"),
        )
        val before = Sink()
        source().project(s, 9.0f, before)

        val dir = tmpDir("proj-l1-")
        writeShard(dir, 1, listOf(
            pyrRow(2001, "40.0", "20.0", "7.0", "0.50"),
            pyrRow(2002, "41.0", "-20.0", "8.5", "0.60"),
        ))
        val loaded = s.ensurePyramid(1, dir)
        assertTrue("L1 切片应加载成功，实际 $loaded", s.hasPyramid)

        val after = Sink()
        source().project(s, 9.0f, after)

        assertTrue("并入 L1 后可见星数必须单调增加", after.count > before.count)
        assertTrue("L1 两颗暗星必须进入投影", after.ids.contains(2001L))
        assertTrue("L1 两颗暗星必须进入投影", after.ids.contains(2002L))
        assertTrue("magLimit=9.0 时不得放出越限星", after.mags.all { it <= 9.0f })
    }

    // ── 用例③：加载 L2 后 magLimit 11.5 含 L2，且无 mag > 11.5 的星 ──────────────

    @Test
    fun `加载 L2 后深度上限十一点五内含 L2 暗星`() {
        val s = store(
            l0Row(1001, "20.0", "30.0", "1.0", "0.12"),
        )
        val dir = tmpDir("proj-l2-")
        writeShard(dir, 1, listOf(pyrRow(2001, "40.0", "20.0", "7.0", "0.50")))
        writeShard(dir, 2, listOf(pyrRow(3001, "50.0", "10.0", "10.5", "0.80")))
        s.ensurePyramid(1, dir)
        s.ensurePyramid(2, dir)

        val deep = Sink()
        source().project(s, 11.5f, deep)
        assertTrue("magLimit=11.5 时 L2 暗星必须进入投影", deep.ids.contains(3001L))
        assertTrue("深度上限内不得放出越限星", deep.mags.all { it <= 11.5f })

        val shallow = Sink()
        source().project(s, 9.0f, shallow)
        assertTrue("magLimit=9.0 时 L2 暗星必须被连续裁剪", !shallow.ids.contains(3001L))
        assertTrue("magLimit=9.0 时不得放出越限星", shallow.mags.all { it <= 9.0f })
    }

    /** 读仓库真实内置 stars.csv 构造 L0 目录（与 SkyBenchTest.loadCatalog 同范式）。 */
    private fun loadBuiltinCatalog(): StarCatalog {
        val cwd = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(cwd) { it.parentFile }
            .first { File(it, "app/src/main/assets/astro/stars.csv").isFile }
        val lines = File(root, "app/src/main/assets/astro/stars.csv").readLines()
        val rows = ArrayList<Array<String>>()
        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val f = line.split(",")
            if (f.size < 13) return@forEach
            rows += f.toTypedArray()
        }
        return StarCatalog.parseRows(rows)
    }
}
