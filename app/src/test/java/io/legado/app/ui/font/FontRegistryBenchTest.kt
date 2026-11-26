package io.legado.app.ui.font

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.legado.app.SlowBenchTest
import org.junit.experimental.categories.Category

/**
 * U9 字体加载编排 bench（切片-08：字体首屏子集 ≤ 200KB；首屏渲染 < 400ms）。
 *
 * 口径说明（避免把宿主 JVM 数字当真机结论）：
 * - 冷路径：注册表缓存清空后的首次 resolve，对应真机「启动探测资产目录」路径；
 * - 热路径：同一角色反复取用，走注册表缓存命中；
 * - 本 bench 在宿主 JVM 上跑，只验证编排路径不超时；荣耀 WIN 真机上的资产读取 /
 *   Typeface 构造耗时仍需真机复测，未在本机验证的部分不计入通过结论。
 */
@Category(SlowBenchTest::class)
class FontRegistryBenchTest {

    private companion object {
        const val COLD_BUDGET_MS = 100.0
        const val WARM_BUDGET_MS = 2.0
        const val WARM_ROUNDS = 2_000
        const val WARM_DROP = 200
        const val BATCH_ROUNDS = 5_000
        const val DIR = FontRole.ASSET_DIR
    }

    private val loadedSource = object : FontAssetSource {
        private val files = mapOf("$DIR/NotoSerifSC-Regular.otf" to ByteArray(40_000))
        override fun listAssets(dir: String): List<String> =
            files.keys.map { it.substringAfterLast('/') }
        override fun assetSize(assetPath: String): Long? = files[assetPath]?.size?.toLong()
        override fun openAsset(assetPath: String): ByteArray? = files[assetPath]
    }

    private val emptySource = object : FontAssetSource {
        override fun listAssets(dir: String): List<String> = emptyList()
        override fun assetSize(assetPath: String): Long? = null
        override fun openAsset(assetPath: String): ByteArray? = null
    }

    private val loader = FontHandleLoader { _, _, _ -> Any() }

    @Before
    fun setUp() {
        FontRegistry.resetForTest()
    }

    private inline fun bench(
        name: String,
        warmup: Int = WARM_DROP,
        loops: Int = WARM_ROUNDS,
        op: () -> Unit,
    ): Double {
        repeat(warmup) { op() }
        val start = System.nanoTime()
        repeat(loops) { op() }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val avgMs = totalMs / loops
        println("[BENCH] $name loops=$loops total=${"%.2f".format(totalMs)}ms avg=${"%.4f".format(avgMs)}ms/op")
        return avgMs
    }

    @Test
    fun `cold first resolve stays under budget`() {
        val start = System.nanoTime()
        FontRegistry.resolve(FontRole.BODY_SERIF, loadedSource, loader)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("冷路径首次解析应 ≤ ${COLD_BUDGET_MS}ms，实际 ${elapsedMs}ms", elapsedMs < COLD_BUDGET_MS)
    }

    @Test
    fun `warm cached resolve under 2ms per op`() {
        val avgMs = bench("warm cached resolve") {
            FontRegistry.resolve(FontRole.BODY_SERIF, loadedSource, loader)
        }
        assertTrue("热路径缓存命中均值应 ≤ ${WARM_BUDGET_MS}ms，实际 ${avgMs}ms", avgMs < WARM_BUDGET_MS)
    }

    @Test
    fun `batch cached resolve stays under budget per call`() {
        repeat(WARM_DROP) {
            FontRegistry.resolve(FontRole.LABEL_SANS, emptySource, loader)
        }
        val start = System.nanoTime()
        repeat(BATCH_ROUNDS) {
            FontRegistry.resolve(FontRole.LABEL_SANS, emptySource, loader)
        }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val perCallMs = totalMs / BATCH_ROUNDS.toDouble()

        assertTrue("批量单次取用应 ≤ ${WARM_BUDGET_MS}ms，实际 ${perCallMs}ms（总 ${totalMs}ms）", perCallMs < WARM_BUDGET_MS)
    }

    @Test
    fun `missing asset cold path also stays under budget`() {
        val start = System.nanoTime()
        FontRegistry.resolve(FontRole.MONO, emptySource, loader)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue("缺失兜底冷路径应 ≤ ${COLD_BUDGET_MS}ms，实际 ${elapsedMs}ms", elapsedMs < COLD_BUDGET_MS)
    }
}
