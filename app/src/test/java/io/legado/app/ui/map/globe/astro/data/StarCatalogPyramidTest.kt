package io.legado.app.ui.map.globe.astro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * 金字塔按需加载四态单验收（D38 / 方案 §4.5）。
 *
 * 纯 JVM：经 [StarCatalog.parseRows] 夹具行构造 L0 目录，缓存切片写临时目录，
 * 不碰 AssetManager / Android。覆盖：Missing（缺目录 / 缺旁证 / 越界 level）、Corrupt
 * （校验和不符 / 零有效行）、LowMemory（注入评估器）、Loaded（合并计数 / 跨层重复 /
 * 幂等 / 星等带边界 / 坏片隔离不毁好片）。
 *
 * 每条用例注释写明「改坏会红在哪一行」。
 */
class StarCatalogPyramidTest {

    private fun tmpDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "$name-${System.nanoTime()}")
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    private fun l0Row(
        id: Long, ra: String, dec: String, mag: String, bv: String, cons: String = "And",
    ): Array<String> = arrayOf(
        id.toString(), "TYC", ra, dec, mag, "", "", "", "", bv, "", "", cons,
    )

    private fun pyrRow(
        id: Long, ra: String, dec: String, mag: String, bv: String, cons: String = "Ori",
    ): Array<String> = arrayOf(id.toString(), "TYC", ra, dec, mag, bv, cons)

    private fun catalog(): StarCatalog = StarCatalog.parseRows(
        listOf(
            l0Row(1001, "20.0", "30.0", "1.0", "0.12"),
            l0Row(1002, "21.0", "31.0", "2.0", "0.30"),
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeShard(
        dir: File,
        level: Int,
        rows: List<Array<String>>,
        withSidecar: Boolean = true,
        tamper: Boolean = false,
    ) {
        val header = "id,tychoId,raDeg,decDeg,magVt,colorBv,constellation"
        val body = rows.joinToString("\n") { it.joinToString(",") }
        val f = File(dir, "level_$level.csv")
        f.writeText("$header\n$body\n", Charsets.UTF_8)
        if (withSidecar) {
            val hex = sha256(f.readBytes())
            File(dir, "level_$level.csv.sha256")
                .writeText("$hex  level_$level.csv\n", Charsets.UTF_8)
        }
        if (tamper) {
            // 旁证按原内容写入后再翻转末字节：校验和必然不符，且不改动旁证文件。
            val b = f.readBytes()
            b[b.size - 1] = (b[b.size - 1] + 1).toByte()
            f.writeBytes(b)
        }
    }

    private fun writeGzShard(dir: File, level: Int, rows: List<Array<String>>) {
        // 与仓库发布片同形态：gzip 压缩 CSV，旁证按 gz 落盘字节算 sha256。
        val header = "id,tychoId,raDeg,decDeg,magVt,colorBv,constellation"
        val body = rows.joinToString("\n") { it.joinToString(",") }
        val plain = "$header\n$body\n".toByteArray(Charsets.UTF_8)
        val gz = ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(plain) }
            out.toByteArray()
        }
        val name = "level_$level.csv.gz"
        File(dir, name).writeBytes(gz)
        File(dir, "$name.sha256").writeText("${sha256(gz)}  $name\n", Charsets.UTF_8)
    }

    @Test
    fun `缺目录与 null 缓存返回 Missing 内置星数不变`() {
        val c = catalog()
        // 改坏会红在哪一行：缺文件路径若抛 NPE，下一行 loadPyramid 即炸；
        // 若缺文件被误判 Loaded，assertEquals(Missing) 变红。
        assertEquals(PyramidResult.Missing, c.loadPyramid(1, null))
        val dir = tmpDir("pyramid-missing-")
        assertEquals(PyramidResult.Missing, c.loadPyramid(1, dir))
        assertEquals(2, c.size)
        assertEquals(0, c.pyramidSize)
    }

    @Test
    fun `level 越界返回 Missing 不触发 IO`() {
        val c = catalog()
        val dir = tmpDir("pyramid-oob-")
        // 改坏：若越界 level 被拼成 level_0 / level_3 读文件，空目录下会抛异常或误 Loaded，
        // 下面三条 assertEquals 变红。
        assertEquals(PyramidResult.Missing, c.loadPyramid(0, dir))
        assertEquals(PyramidResult.Missing, c.loadPyramid(-1, dir))
        assertEquals(PyramidResult.Missing, c.loadPyramid(3, dir))
        assertEquals(0, c.pyramidSize)
    }

    @Test
    fun `校验和不符返回 Corrupt 且 L0 仍可查`() {
        val c = catalog()
        val dir = tmpDir("pyramid-badsum-")
        writeShard(dir, 1, listOf(pyrRow(2001, "12.5", "30.0", "8.0", "0.5")), tamper = true)
        // 改坏：若合并前不校验 sha256，坏片被解析合并，下一行断言 Corrupt 变红。
        assertEquals(PyramidResult.Corrupt, c.loadPyramid(1, dir))
        assertEquals(0, c.pyramidSize)
        assertTrue("L0 锚点星仍可查", c.indexOfId(1001) >= 0)
    }

    @Test
    fun `零有效行返回 Corrupt 异常计数大于零 目录尺寸不变`() {
        val c = catalog()
        val dir = tmpDir("pyramid-empty-")
        writeShard(dir, 1, listOf(pyrRow(2002, "400.0", "30.0", "8.0", "0.5")))
        // 改坏：若空片被当成功合并，Loaded.added=0 且空目录被误判健康，下一行变红。
        assertEquals(PyramidResult.Corrupt, c.loadPyramid(1, dir))
        assertEquals(0, c.pyramidSize)
        assertTrue(c.pyramidAnomalies > 0)
        assertEquals(2, c.size)
    }

    @Test
    fun `低内存拒绝合并 增量数组长度为零`() {
        val c = catalog()
        val dir = tmpDir("pyramid-lowmem-")
        writeShard(dir, 1, listOf(pyrRow(2003, "12.5", "30.0", "8.0", "0.5")))
        c.pyramidMemoryGuard = { _ -> false }
        // 改坏：若跳过内存评估直接合并，注入路径下本应 LowMemory 却返回 Loaded，下一行变红。
        assertEquals(PyramidResult.LowMemory, c.loadPyramid(1, dir))
        assertEquals(0, c.pyramidSize)
    }

    @Test
    fun `合法 L1 成功合并计数 新增星可查`() {
        val c = catalog()
        val dir = tmpDir("pyramid-ok-")
        writeShard(
            dir, 1, listOf(
                pyrRow(2004, "12.5", "30.0", "8.0", "0.5"),
                pyrRow(2005, "13.5", "-10.0", "8.5", ""),
            ),
        )
        // 改坏：若合并只追加部分行或索引未建，added 与查询命中数对不上，下两行变红。
        assertEquals(PyramidResult.Loaded(1, 2), c.loadPyramid(1, dir))
        assertEquals(2, c.pyramidSize)
        val idx = c.pyramidIndexOfId(2004)
        assertTrue(idx >= 0)
        assertEquals(12.5, c.pyramidRaAt(idx), 1e-9)
        assertEquals(8.0f, c.pyramidMagAt(idx), 1e-6f)
    }

    @Test
    fun `跨层重复 id 不计入 added 重复计数留痕`() {
        val c = catalog()
        val dir = tmpDir("pyramid-dup-")
        writeShard(
            dir, 1, listOf(
                pyrRow(1001, "12.5", "30.0", "8.0", "0.5"), // 与 L0 同 id
                pyrRow(2006, "13.5", "-10.0", "8.5", ""),
            ),
        )
        // 改坏：若重复 id 覆盖索引，拾取错指旧星；added 应为 1 而非 2，下一行变红。
        assertEquals(PyramidResult.Loaded(1, 1), c.loadPyramid(1, dir))
        assertEquals(1, c.pyramidSize)
        assertEquals(-1, c.pyramidIndexOfId(1001))
        assertTrue(c.pyramidReport.contains("dupId=1"))
    }

    @Test
    fun `坏片隔离不毁好片`() {
        val c = catalog()
        val dir = tmpDir("pyramid-isolate-")
        writeShard(dir, 1, listOf(pyrRow(2007, "12.5", "30.0", "8.0", "0.5")), tamper = true)
        assertEquals(PyramidResult.Corrupt, c.loadPyramid(1, dir))
        // 改坏：若损坏处理清空整个缓存目录，L1 隔离文件不存在，下一行断言变红。
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("level_1.csv.corrupt-") })
        // 好片 L2 仍可正常加载，不被 L1 坏片波及。
        writeShard(dir, 2, listOf(pyrRow(2008, "200.0", "-10.0", "10.5", "")))
        assertEquals(PyramidResult.Loaded(2, 1), c.loadPyramid(2, dir))
        assertEquals(1, c.pyramidSize)
    }

    @Test
    fun `重复调用幂等 不重复追加`() {
        val c = catalog()
        val dir = tmpDir("pyramid-idem-")
        writeShard(dir, 1, listOf(pyrRow(2009, "12.5", "30.0", "8.0", "0.5")))
        assertEquals(PyramidResult.Loaded(1, 1), c.loadPyramid(1, dir))
        // 改坏：若重复调用再次追加数组，第二次返回 added=1 且 pyramidSize=2，下两行变红。
        assertEquals(PyramidResult.Loaded(1, 0), c.loadPyramid(1, dir))
        assertEquals(1, c.pyramidSize)
    }

    // 星等带边界：L1 (6.5, 9.0]，L2 (9.0, 11.5]；下界排他、上界包含，越带行被拒。
    @Test
    fun `星等带边界 L1 L2 上下界包含与越带行被拒`() {
        val c1 = catalog()
        val d1 = tmpDir("pyramid-band1-")
        writeShard(
            d1, 1, listOf(
                pyrRow(3001, "10.0", "10.0", "6.500", "0.3"),  // 下界排他 → 拒
                pyrRow(3002, "11.0", "10.0", "9.000", "0.3"),  // 上界 inclusive → 收
                pyrRow(3003, "12.0", "10.0", "9.500", "0.3"),  // 越带 → 拒
            ),
        )
        // 改坏：带边界写反（如 L1 收到 9.5），有效行数对不上，下一行变红。
        assertEquals(PyramidResult.Loaded(1, 1), c1.loadPyramid(1, d1))

        val c2 = catalog()
        val d2 = tmpDir("pyramid-band2-")
        writeShard(
            d2, 2, listOf(
                pyrRow(3004, "20.0", "-10.0", "9.000", "0.3"),  // 下界排他 → 拒
                pyrRow(3005, "21.0", "-10.0", "11.500", "0.3"), // 上界 inclusive → 收
                pyrRow(3006, "22.0", "-10.0", "11.600", "0.3"), // 越带 → 拒
            ),
        )
        assertEquals(PyramidResult.Loaded(2, 1), c2.loadPyramid(2, d2))
    }

    @Test
    fun `缺旁证文件返回 Missing 不合并`() {
        val c = catalog()
        val dir = tmpDir("pyramid-nosidecar-")
        writeShard(dir, 1, listOf(pyrRow(2010, "12.5", "30.0", "8.0", "0.5")), withSidecar = false)
        // 改坏：若旁证缺失被当成坏片合并或误判 Corrupt，下一行断言 Missing 变红。
        assertEquals(PyramidResult.Missing, c.loadPyramid(1, dir))
        assertEquals(0, c.pyramidSize)
    }

    @Test
    fun `缺文件 损坏 低内存后内置星数不变 降级不空白`() {
        val c = catalog()
        val missing = tmpDir("pyramid-deg-m-")
        assertEquals(PyramidResult.Missing, c.loadPyramid(1, missing))
        assertEquals(2, c.size)

        val corrupt = tmpDir("pyramid-deg-c-")
        writeShard(corrupt, 1, listOf(pyrRow(2011, "400.0", "30.0", "8.0", "0.5")))
        assertEquals(PyramidResult.Corrupt, c.loadPyramid(1, corrupt))
        assertEquals(2, c.size)

        val lowmem = tmpDir("pyramid-deg-l-")
        writeShard(lowmem, 1, listOf(pyrRow(2012, "12.5", "30.0", "8.0", "0.5")))
        c.pyramidMemoryGuard = { false }
        assertEquals(PyramidResult.LowMemory, c.loadPyramid(1, lowmem))
        assertEquals(2, c.size)
    }

    @Test
    fun `gz 发布片解压合并 校验和按 gz 落盘字节比对`() {
        val c = catalog()
        val dir = tmpDir("pyramid-gz-")
        writeGzShard(
            dir, 1, listOf(
                pyrRow(5001, "12.5", "30.0", "8.0", "0.5"),
                pyrRow(5002, "13.5", "-10.0", "8.5", ""),
            ),
        )
        // 改坏：若按解压后字节比对旁证，gz 校验和必不符而误判 Corrupt；
        // 若不解压直接解析，首行表头 gzip 字节解析零有效行，Loaded 变红。
        assertEquals(PyramidResult.Loaded(1, 2), c.loadPyramid(1, dir))
        assertEquals(2, c.pyramidSize)
        val idx = c.pyramidIndexOfId(5001)
        assertTrue(idx >= 0)
        assertEquals(12.5, c.pyramidRaAt(idx), 1e-9)
    }

    @Test
    fun `远程钩子补片成功合并 下载失败回 Missing 不清 L0`() {
        val ok = catalog()
        val dir = tmpDir("pyramid-remote-")
        var fetched = 0
        ok.pyramidRemoteBase = "https://raw.example.invalid/legado/main/data/pyramid"
        ok.pyramidRemoteFetcher = { lv, d, _ ->
            fetched += 1
            writeGzShard(d, lv, listOf(pyrRow(5003, "12.5", "30.0", "8.0", "0.5")))
            true
        }
        // 本地无片 → 钩子拉片落缓存 → 走既有校验合并。
        assertEquals(PyramidResult.Loaded(1, 1), ok.loadPyramid(1, dir))
        assertEquals(1, fetched)
        assertEquals(1, ok.pyramidSize)

        val fail = catalog()
        val dirFail = tmpDir("pyramid-remote-fail-")
        fail.pyramidRemoteBase = "https://raw.example.invalid/legado/main/data/pyramid"
        fail.pyramidRemoteFetcher = { _, _, _ -> false }
        // 下载失败属瞬时网络态：回 Missing，不判 Corrupt、不清内置层。
        assertEquals(PyramidResult.Missing, fail.loadPyramid(1, dirFail))
        assertEquals(0, fail.pyramidSize)
        assertEquals(2, fail.size)
    }
}
