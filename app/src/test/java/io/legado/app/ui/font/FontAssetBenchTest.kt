package io.legado.app.ui.font

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * U9 字体真实资产 bench（切片-08：字体首屏子集 ≤ 200KB；首屏渲染 < 400ms）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 *  - 冷路径：注册表缓存清空后，对真实打包子集做首次 resolve（列目录 + 读字节 + 句柄构造缝）；
 *  - 热路径：同一角色反复取用，走注册表缓存命中；
 *  - 本 bench 在宿主 JVM 跑，只验证编排路径与真实资产读取不超时；荣耀 WIN 真机上的
 *    AssetManager 读取 / Typeface 解析耗时仍需真机复测，未在本机验证的部分不计入通过结论。
 */
class FontAssetBenchTest {

    private companion object {
        const val COLD_BUDGET_MS = 100.0
        const val WARM_BUDGET_MS = 2.0
        const val WARM_ROUNDS = 2_000
        const val WARM_DROP = 200
    }

    private fun repositoryRoot(): File {
        val userDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDirectory) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
    }

    private val fontsDir = File(repositoryRoot(), "app/src/main/assets/fonts")

    private inner class RealAssetSource : FontAssetSource {
        override fun listAssets(dir: String): List<String> =
            fontsDir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

        override fun assetSize(assetPath: String): Long? {
            val name = assetPath.substringAfterLast('/')
            return File(fontsDir, name).takeIf { it.isFile }?.length()
        }

        override fun openAsset(assetPath: String): ByteArray? {
            val name = assetPath.substringAfterLast('/')
            return File(fontsDir, name).takeIf { it.isFile }?.readBytes()
        }
    }

    private val loader = FontHandleLoader { _, _, _ -> Any() }

    @Before
    fun setUp() {
        FontRegistry.resetForTest()
    }

    @Test
    fun `cold resolve against real packaged subsets stays under budget`() {
        val source = RealAssetSource()
        listOf(FontRole.BODY_SERIF, FontRole.TITLE_SERIF, FontRole.NUMBER_TABULAR).forEach { role ->
            FontRegistry.resetForTest()
            val start = System.nanoTime()
            val result = FontRegistry.resolve(role, source, loader)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
            assertTrue(
                "$role 冷路径真实资产解析应 ≤ ${COLD_BUDGET_MS}ms，实际 ${elapsedMs}ms",
                elapsedMs < COLD_BUDGET_MS,
            )
            assertTrue("$role 真实子集应命中加载", result is FontLoadResult.Loaded)
        }
    }

    @Test
    fun `warm cached resolve against real assets under 2ms per op`() {
        val source = RealAssetSource()
        FontRegistry.resolve(FontRole.BODY_SERIF, source, loader)

        repeat(WARM_DROP) { FontRegistry.resolve(FontRole.BODY_SERIF, source, loader) }
        val start = System.nanoTime()
        repeat(WARM_ROUNDS) { FontRegistry.resolve(FontRole.BODY_SERIF, source, loader) }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val perCallMs = totalMs / WARM_ROUNDS.toDouble()

        assertTrue(
            "热路径缓存命中均值应 ≤ ${WARM_BUDGET_MS}ms，实际 ${perCallMs}ms",
            perCallMs < WARM_BUDGET_MS,
        )
    }

    @Test
    fun `real packaged subset bytes stay within role budgets`() {
        FontRole.entries.forEach { role ->
            val packaged = role.candidateAssets.firstNotNullOfOrNull { candidate ->
                File(fontsDir, candidate).takeIf { it.isFile }
            }
            assertTrue("${role.name} 未找到打包子集", packaged != null)
            role.maxSubsetBytes?.let { budget ->
                assertTrue(
                    "${role.name} ${packaged!!.length()}B 超红线 ${budget}B",
                    packaged.length() <= budget,
                )
            }
        }
    }
}
