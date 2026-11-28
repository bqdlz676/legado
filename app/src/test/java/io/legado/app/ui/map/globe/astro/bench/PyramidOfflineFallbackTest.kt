package io.legado.app.ui.map.globe.astro.bench

import io.legado.app.ui.map.globe.astro.data.PyramidResult
import io.legado.app.ui.map.globe.astro.data.StarCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * 判据 5（规格 §十四.5）断网清缓存兜底四态单测。
 *
 * 〔覆盖口径〕缓存目录不存在、gz 片缺失但内置层在、gz 片损坏（截断解压失败 / 旁证校验和
 * 不符）、缓存目录不可写。每态断言：返回明确的 [PyramidResult] 枚举值、内置 8920 颗仍可
 * 查询显示、不抛异常、不出空白占位。
 *
 * 〔与既有 StarCatalogPyramidTest 分工〕既有类用 2 颗夹具行锁四态分支与合并计数；本类用
 * 仓库真实内置 stars.csv（8920 颗）锁定「降级之后内置层一颗不丢」这条兜底结论，两类互补
 * 不重复。纯 JVM：经 [StarCatalog.parseRows] 读 CSV，不碰 AssetManager / Room / 网络。
 *
 * 〔改坏会红推演〕每条用例注释写明：生产兜底路径若被改坏（抛异常、清掉 L0、误判 Loaded、
 * 目录不可写时崩在隔离改名），对应的哪一行断言会变红。
 */
class PyramidOfflineFallbackTest {

    /** 内置星表锚点：stars.csv 有效行 8920（本单开工前静态核算 body=8920 bad=0 dup=0）。 */
    private companion object {
        const val BUILTIN_STAR_COUNT = 8920
    }

    private fun astroDir(): File {
        // 与 SkyBenchTest 同范式：从 user.dir 向上找含 app/src/main 的仓库根。
        val cwd = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(cwd) { it.parentFile }
            .first { File(it, "app/src/main/assets/astro/stars.csv").isFile }
        return File(root, "app/src/main/assets/astro")
    }

    /** 解析真实内置星表；CSV 行序与 parseRows 的 13 列口径一致。 */
    private fun builtinCatalog(): StarCatalog {
        val rows = astroDir().resolve("stars.csv").readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",").toTypedArray() }
        return StarCatalog.parseRows(rows)
    }

    private fun tmpDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "$name-${System.nanoTime()}")
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun gzBytes(rows: List<String>): ByteArray {
        val header = "id,tychoId,raDeg,decDeg,magVt,colorBv,constellation"
        val plain = (listOf(header) + rows).joinToString("\n").toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(plain) }
            out.toByteArray()
        }
    }

    private fun pyrRow(id: Long, ra: String, dec: String, mag: String): String =
        "$id,TYC,$ra,$dec,$mag,0.3,Ori"

    /** 兜底不变量：内置层非占位、颗数守恒、首颗锚点可查且数值在界内。 */
    private fun assertBuiltinIntact(c: StarCatalog) {
        assertFalse("降级后不得进入占位空目录", c.isPlaceholder)
        assertEquals("降级后内置星数必须守恒", BUILTIN_STAR_COUNT, c.size)
        val firstId = c.idAt(0)
        assertTrue("首颗锚点必须可反查", c.indexOfId(firstId) >= 0)
        assertTrue("赤经必须在 0..360 度", c.raAt(0) in 0.0..360.0)
        assertTrue("星等必须有限", c.magAt(0).isFinite())
        assertEquals("金字塔增量不得并入", 0, c.pyramidSize)
    }

    @Test
    fun `态1 缓存目录不存在 返回 Missing 内置 8920 颗不动`() {
        val c = builtinCatalog()
        assertBuiltinIntact(c)
        val missingDir = File(System.getProperty("java.io.tmpdir"), "no-such-${System.nanoTime()}")
        assertFalse(missingDir.exists())

        // 改坏会红：缺目录路径若抛 NPE，本用例直接炸；若缺文件被误判 Loaded，
        // 下一行 assertEquals(Missing) 变红；若兜底清了 L0，assertBuiltinIntact 变红。
        assertEquals(PyramidResult.Missing, c.loadPyramid(1, missingDir))
        assertBuiltinIntact(c)
    }

    @Test
    fun `态2 gz 片缺失但内置层在 返回 Missing 不空屏`() {
        val c = builtinCatalog()
        val emptyDir = tmpDir("pyramid-fallback-missing-")

        // 改坏会红：空目录下若拼成 level_1.csv.gz 读文件而未判存在，会抛 IO 异常；
        // 若缺片被当 Corrupt 或 Loaded，assertEquals(Missing) 变红。
        assertEquals(PyramidResult.Missing, c.loadPyramid(1, emptyDir))
        assertBuiltinIntact(c)
    }

    @Test
    fun `态3a gz 片截断解压失败 返回 Corrupt 隔离坏片`() {
        val c = builtinCatalog()
        val dir = tmpDir("pyramid-fallback-trunc-")
        val good = gzBytes(listOf(pyrRow(5001, "12.5", "30.0", "8.0")))
        // 截断到六成字节：GZIP 流不完整，gunzip 必抛异常；旁证按截断后字节算，
        // 让校验和这一关先过，确保走到解压损坏分支而不是旁证缺失分支。
        val truncated = good.copyOf((good.size * 0.6).toInt())
        val name = "level_1.csv.gz"
        File(dir, name).writeBytes(truncated)
        File(dir, "$name.sha256").writeText("${sha256(truncated)}  $name\n", Charsets.UTF_8)

        // 改坏会红：解压异常若未被收敛而向上抛，本用例炸；若截断片被解析合并，
        // 返回值不是 Corrupt 且 pyramidSize 非 0，下面两行变红。
        assertEquals(PyramidResult.Corrupt, c.loadPyramid(1, dir))
        assertBuiltinIntact(c)
    }

    @Test
    fun `态3b 旁证校验和不符 返回 Corrupt 不合并坏片`() {
        val c = builtinCatalog()
        val dir = tmpDir("pyramid-fallback-badsum-")
        val good = gzBytes(listOf(pyrRow(5002, "13.5", "-10.0", "8.5")))
        val name = "level_1.csv.gz"
        File(dir, name).writeBytes(good)
        // 旁证写成另一内容的哈希：与落盘字节必然不符。
        File(dir, "$name.sha256").writeText("${sha256("tampered".toByteArray())}  $name\n", Charsets.UTF_8)

        // 改坏会红：若合并前不校验 sha256，坏片进入解析，返回值不是 Corrupt，下一行变红。
        assertEquals(PyramidResult.Corrupt, c.loadPyramid(1, dir))
        assertBuiltinIntact(c)
    }

    @Test
    fun `态4 缓存目录不可写 不抛异常 内置层仍可显示`() {
        val c = builtinCatalog()
        val dir = tmpDir("pyramid-fallback-ro-")
        val good = gzBytes(listOf(pyrRow(5003, "14.5", "20.0", "9.0")))
        val name = "level_1.csv.gz"
        File(dir, name).writeBytes(good)
        File(dir, "$name.sha256").writeText("${sha256("other".toByteArray())}  $name\n", Charsets.UTF_8)
        // 去掉目录写权限：坏片隔离改名在不可写目录内必失败，生产路径已用 runCatching 收敛。
        // 边界：JVM 以 root 运行时 chmod 不拦截 root 写权限，此时本态退化为「坏片可隔离」；
        // 真机文件权限拒绝语义需 instrumented 环境复测，见证据报告未验证项。
        dir.setReadable(true, true)
        dir.setWritable(false, true)
        dir.setExecutable(true, true)

        // 改坏会红：隔离改名若未包异常，目录不可写时抛 SecurityException / IO 异常，本用例炸；
        // 返回值只允许 Corrupt（校验和不符走到隔离）或 Missing（读不出片），不许是 Loaded。
        val result = c.loadPyramid(1, dir)
        assertTrue(
            "不可写目录只允许 Corrupt 或 Missing，实际 $result",
            result == PyramidResult.Corrupt || result == PyramidResult.Missing,
        )
        assertBuiltinIntact(c)
    }

    @Test
    fun `空缓存句柄 null 返回 Missing 内置层不动`() {
        val c = builtinCatalog()
        // 改坏会红：cacheDir=null 若未判空直接拼路径，抛 NPE；误判 Loaded 则下一行变红。
        assertEquals(PyramidResult.Missing, c.loadPyramid(1, null))
        assertBuiltinIntact(c)
    }
}
