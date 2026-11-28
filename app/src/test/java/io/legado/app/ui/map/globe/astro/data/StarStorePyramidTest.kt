package io.legado.app.ui.map.globe.astro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * StarStore 双层扫描与金字塔门验收（A/B 件）。
 *
 * 纯 JVM：经 [StarCatalog.parseRows] 夹具行构造 L0 目录，缓存切片写临时目录。覆盖——
 * 坏片经 [StarStore.ensurePyramid] 回 Corrupt 且 L0 不丢；进度回调上报真实行数比值；
 * 查询在 L0 ∪ 已加载金字塔层上叠加扫描，金字塔命中走高位编码索引并可解码。
 *
 * 每条用例注释写明「改坏会红在哪一行」。
 */
class StarStorePyramidTest {

    private fun tmpDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "$name-${System.nanoTime()}")
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    private fun l0Row(id: Long, ra: String, dec: String, mag: String, bv: String): Array<String> =
        arrayOf(id.toString(), "TYC", ra, dec, mag, "", "", "", "", bv, "", "", "And")

    private fun pyrRow(id: Long, ra: String, dec: String, mag: String, bv: String): Array<String> =
        arrayOf(id.toString(), "TYC", ra, dec, mag, bv, "Ori")

    private fun catalog(): StarCatalog = StarCatalog.parseRows(
        listOf(
            l0Row(1001, "20.0", "30.0", "1.0", "0.12"),
            l0Row(1002, "21.0", "31.0", "2.0", "0.30"),
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeShard(dir: File, level: Int, rows: List<Array<String>>, tamper: Boolean = false) {
        val header = "id,tychoId,raDeg,decDeg,magVt,colorBv,constellation"
        val body = rows.joinToString("\n") { it.joinToString(",") }
        val f = File(dir, "level_$level.csv")
        f.writeText("$header\n$body\n", Charsets.UTF_8)
        val hex = sha256(f.readBytes())
        File(dir, "level_$level.csv.sha256").writeText("$hex  level_$level.csv\n", Charsets.UTF_8)
        if (tamper) {
            val b = f.readBytes()
            b[b.size - 1] = (b[b.size - 1] + 1).toByte()
            f.writeBytes(b)
        }
    }

    @Test
    fun `坏片经门面回 Corrupt 内置星仍可查`() {
        val store = StarStore(catalog())
        val dir = tmpDir("store-bad-")
        writeShard(dir, 1, listOf(pyrRow(2001, "400.0", "30.0", "8.0", "0.5")), tamper = true)
        // 改坏：若门面把异常向上抛，下一行断言前先红；若坏片清空 L0，byId 查不到 1001。
        assertEquals(PyramidResult.Corrupt, store.ensurePyramid(1, dir))
        assertEquals(PyramidResult.Corrupt, store.lastPyramidResult)
        assertTrue(store.byId(1001) >= 0)
        assertEquals(2, store.size)
    }

    @Test
    fun `进度回调上报真实行数比值不伪造总量`() {
        val cat = catalog()
        val store = StarStore(cat)
        val events = mutableListOf<Pair<Long, Long>>()
        store.bindPyramidProgress { _, done, total -> events += done to total }
        val dir = tmpDir("store-progress-")
        writeShard(
            dir, 1, listOf(
                pyrRow(2002, "12.5", "30.0", "8.0", "0.5"),
                pyrRow(2003, "13.5", "-10.0", "8.5", ""),
            ),
        )
        assertEquals(PyramidResult.Loaded(1, 2), store.ensurePyramid(1, dir))
        assertTrue("解析段必须回传总行数", events.any { it.second > 0 })
        assertTrue("并入行数不得超过总行数", events.all { it.first <= it.second })
        val last = events.last()
        assertEquals(2L, last.first)
    }

    @Test
    fun `双层扫描矩形拾取与最近邻叠加金字塔层`() {
        val store = StarStore(catalog())
        val dir = tmpDir("store-dual-")
        writeShard(dir, 1, listOf(pyrRow(2004, "12.5", "30.0", "8.0", "0.5")))
        store.ensurePyramid(1, dir)

        val out = IntArray(8)
        val count = store.queryRect(10.0, 15.0, 20.0, 40.0, out)
        assertEquals(1, count)
        val idx = out[0]
        // 改坏：若查询不扫金字塔层，矩形内命中为 0，下一行 count 断言先红；
        // 若编码基准错位，isPyramidIndex 判定变红。
        assertTrue(store.isPyramidIndex(idx))
        assertEquals(0, store.pyramidSlot(idx))
        assertEquals(2004L, store.pyramidIdAt(store.pyramidSlot(idx)))

        val nearest = store.nearest(12.5, 30.0)
        assertTrue(store.isPyramidIndex(nearest))
        assertEquals(0, store.pyramidSlot(nearest))
    }
}
