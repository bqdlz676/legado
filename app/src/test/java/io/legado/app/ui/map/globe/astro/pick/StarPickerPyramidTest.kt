package io.legado.app.ui.map.globe.astro.pick

import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.StarCatalog
import io.legado.app.ui.map.globe.astro.data.StarStore
import io.legado.app.ui.map.globe.astro.proj.StarProjectionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 拾取并入金字塔层验收（Q-502 / §九.3）：投影行星源把 L0 ∪ 已加载金字塔层的可见星
 * 写入同一缓冲后，[StarPicker] 网格分桶必须能命中只在金字塔层里的暗星；命中半径仍为
 * 20dp（密度 1f 时 20px），边界口径与既有 [StarPickerTest] 逐位一致，并列取最亮、
 * 并列星等取索引靠前。
 *
 * 纯 JVM：夹具目录 + 临时金字塔切片，不碰 Android Canvas / 触控管线。
 */
class StarPickerPyramidTest {

    private companion object {
        const val FOCAL_PX = 1000.0
        const val CX = 540.0
        const val CY = 960.0
        const val OBSERVER_LAT = 34.0
        const val OBSERVER_LON = 108.9
        const val LST_DEG = 0.0

        /** 夹具金字塔暗星主键（只在 L1 层，L0 目录内不存在）。 */
        const val PYRAMID_DARK_ID = 2001L
    }

    /** 投影 + 拾取重建结果：拾取器、可见星坐标 / 主键数组、金字塔暗星槽位。 */
    private class Rebuilt(
        val picker: StarPicker,
        val xs: FloatArray,
        val ys: FloatArray,
        val ids: LongArray,
        val pyramidSlot: Int,
    )

    private fun l0Row(id: Long, ra: String, dec: String, mag: String, bv: String): Array<String> =
        arrayOf(id.toString(), "TYC", ra, dec, mag, "", "", "", "", bv, "", "", "And")

    private fun pyrRow(id: Long, ra: String, dec: String, mag: String, bv: String): Array<String> =
        arrayOf(id.toString(), "TYC", ra, dec, mag, bv, "Ori")

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

    private fun projectAndRebuild(store: StarStore, magLimit: Float): Rebuilt {
        val source = StarProjectionSource(
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
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        val mags = ArrayList<Float>(); val ids = ArrayList<Long>()
        val sink = object : StarProjectionSource.ProjectionSink {
            override fun add(px: Float, py: Float, mag: Float, id: Long, colorBv: Double) {
                xs += px; ys += py; mags += mag; ids += id
            }
        }
        val visible = source.project(store, magLimit, sink)
        val picker = StarPicker(1f)
        picker.rebuild(
            xs.toFloatArray(), ys.toFloatArray(),
            mags.toFloatArray(), ids.toLongArray(), visible,
        )
        return Rebuilt(
            picker = picker,
            xs = xs.toFloatArray(),
            ys = ys.toFloatArray(),
            ids = ids.toLongArray(),
            pyramidSlot = ids.indexOf(PYRAMID_DARK_ID),
        )
    }

    @Test
    fun `拾取能命中只在金字塔层里的暗星`() {
        val catalog = StarCatalog.parseRows(listOf(
            l0Row(1001, "20.0", "30.0", "1.0", "0.12"),
        ))
        val store = StarStore(catalog)
        val dir = tmpDir("pick-pyr-")
        writeShard(dir, 1, listOf(pyrRow(PYRAMID_DARK_ID, "40.0", "20.0", "7.0", "0.50")))
        store.ensurePyramid(1, dir)

        val rebuilt = projectAndRebuild(store, 11.5f)
        assertTrue("金字塔暗星必须进入投影缓冲", rebuilt.pyramidSlot >= 0)

        // 触点取暗星自身坐标：网格分桶必须在 20dp 候选半径内找回它。
        val slot = rebuilt.pyramidSlot
        val hitId = rebuilt.picker.pickId(rebuilt.xs[slot], rebuilt.ys[slot])
        assertEquals("拾取应命中金字塔层暗星主键", PYRAMID_DARK_ID, hitId)
    }

    @Test
    fun `金字塔星命中边界沿用既有二十 dp 口径`() {
        val catalog = StarCatalog.parseRows(listOf(
            l0Row(1001, "20.0", "30.0", "1.0", "0.12"),
        ))
        val store = StarStore(catalog)
        val dir = tmpDir("pick-bound-")
        writeShard(dir, 1, listOf(pyrRow(PYRAMID_DARK_ID, "40.0", "20.0", "7.0", "0.50")))
        store.ensurePyramid(1, dir)
        val rebuilt = projectAndRebuild(store, 11.5f)
        val slot = rebuilt.pyramidSlot
        val px = rebuilt.xs[slot]
        val py = rebuilt.ys[slot]

        // 与 StarPickerTest.radiusBoundary 同口径：距星心 20px 内命中，21px 无命中。
        assertEquals(PYRAMID_DARK_ID, rebuilt.picker.pickId(px + 20f, py))
        assertEquals(-1L, rebuilt.picker.pickId(px + 21f, py))
    }
}
