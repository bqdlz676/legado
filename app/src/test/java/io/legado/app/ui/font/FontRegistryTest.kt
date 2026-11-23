package io.legado.app.ui.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * U9 字体注册表单测：命中 / 缺失 / 损坏 / 超体积 / 候选优先级 / 缓存 / 兜底路径。
 * 纯 JVM 跑通，不依赖 Robolectric 与 Android 框架。
 */
class FontRegistryTest {

    private class FakeSource(
        val files: Map<String, ByteArray> = emptyMap(),
        var listCalls: Int = 0,
    ) : FontAssetSource {
        override fun listAssets(dir: String): List<String> {
            listCalls++
            return files.keys.map { it.substringAfterLast('/') }
        }

        override fun assetSize(assetPath: String): Long? =
            files[assetPath]?.size?.toLong()

        override fun openAsset(assetPath: String): ByteArray? = files[assetPath]
    }

    private class FakeLoader(
        val handle: Any = MARKER_HANDLE,
        val failure: Throwable? = null,
    ) : FontHandleLoader {
        var loadCalls = 0
        override fun load(role: FontRole, assetPath: String, bytes: ByteArray): Any? {
            loadCalls++
            if (failure != null) throw failure
            return handle
        }
    }

    private companion object {
        val MARKER_HANDLE = Any()
        const val DIR = FontRole.ASSET_DIR
    }

    @Before
    fun setUp() {
        FontRegistry.resetForTest()
    }

    @Test
    fun `body subset hit returns loaded with marker handle`() {
        val source = FakeSource(mapOf("$DIR/NotoSerifSC-Regular.otf" to ByteArray(40_000)))
        val loader = FakeLoader()

        val result = FontRegistry.resolve(FontRole.BODY_SERIF, source, loader)

        assertTrue(result is FontLoadResult.Loaded)
        val loaded = result as FontLoadResult.Loaded
        assertSame(MARKER_HANDLE, loaded.handle)
        assertEquals("NotoSerifSC-Regular.otf", loaded.assetName)
        assertEquals(40_000L, loaded.bytes)
        assertEquals(FontFallbackKind.SERIF, result.fallback)
    }

    @Test
    fun `missing asset falls back to serif and is probed only once`() {
        val source = FakeSource(emptyMap())
        val loader = FakeLoader()

        val first = FontRegistry.resolve(FontRole.BODY_SERIF, source, loader)
        val second = FontRegistry.resolve(FontRole.BODY_SERIF, source, loader)

        assertTrue(first is FontLoadResult.MissingAsset)
        assertSame(first, second)
        assertEquals(1, source.listCalls)
        assertEquals(0, loader.loadCalls)
        assertEquals(FontFallbackKind.SERIF, first.fallback)
    }

    @Test
    fun `corrupt asset load failure falls back and caches failure`() {
        val source = FakeSource(mapOf("$DIR/NotoSansSC-Light.ttf" to ByteArray(10_000)))
        val loader = FakeLoader(failure = IOException("broken font"))

        val first = FontRegistry.resolve(FontRole.LABEL_SANS, source, loader)
        val second = FontRegistry.resolve(FontRole.LABEL_SANS, source, loader)

        assertTrue(first is FontLoadResult.LoadFailed)
        assertSame(first, second)
        assertEquals(1, loader.loadCalls)
        assertEquals(FontFallbackKind.SANS, first.fallback)
    }

    @Test
    fun `oversize body subset is rejected and falls back`() {
        val oversize = ByteArray((FontRole.BODY_SUBSET_BUDGET_BYTES + 1).toInt())
        val source = FakeSource(mapOf("$DIR/NotoSerifSC-Regular.ttf" to oversize))
        val loader = FakeLoader()

        val result = FontRegistry.resolve(FontRole.BODY_SERIF, source, loader)

        assertTrue(result is FontLoadResult.RejectedOversize)
        val rejected = result as FontLoadResult.RejectedOversize
        assertEquals(FontRole.BODY_SUBSET_BUDGET_BYTES, rejected.budgetBytes)
        assertEquals(oversize.size.toLong(), rejected.bytes)
        assertEquals(0, loader.loadCalls)
    }

    @Test
    fun `number subset respects 20KB budget`() {
        val within = ByteArray(15 * 1024)
        val source = FakeSource(mapOf("$DIR/Inter-Nums.ttf" to within))
        val loader = FakeLoader()

        val ok = FontRegistry.resolve(FontRole.NUMBER_TABULAR, source, loader)
        assertTrue(ok is FontLoadResult.Loaded)

        FontRegistry.resetForTest()
        val tooBig = ByteArray((FontRole.NUMBER_SUBSET_BUDGET_BYTES + 1).toInt())
        val bigSource = FakeSource(mapOf("$DIR/Inter-Nums.ttf" to tooBig))
        val rejected = FontRegistry.resolve(FontRole.NUMBER_TABULAR, bigSource, loader)
        assertTrue(rejected is FontLoadResult.RejectedOversize)
    }

    @Test
    fun `candidate priority falls through to second existing file`() {
        // 第一候选 otf 缺失，第二候选 ttf 命中。
        val source = FakeSource(mapOf("$DIR/NotoSerifSC-Regular.ttf" to ByteArray(30_000)))
        val loader = FakeLoader()

        val result = FontRegistry.resolve(FontRole.BODY_SERIF, source, loader)

        assertTrue(result is FontLoadResult.Loaded)
        assertEquals("NotoSerifSC-Regular.ttf", (result as FontLoadResult.Loaded).assetName)
    }

    @Test
    fun `unreadable asset size is treated as load failure`() {
        val files = mapOf("$DIR/JetBrainsMono-Regular.ttf" to ByteArray(8_000))
        val source = object : FontAssetSource {
            override fun listAssets(dir: String): List<String> =
                files.keys.map { it.substringAfterLast('/') }

            override fun assetSize(assetPath: String): Long? = null

            override fun openAsset(assetPath: String): ByteArray? = files[assetPath]
        }
        val loader = FakeLoader()

        val result = FontRegistry.resolve(FontRole.MONO, source, loader)

        assertTrue(result is FontLoadResult.LoadFailed)
        assertEquals(FontFallbackKind.MONO, result.fallback)
    }

    @Test
    fun `resetForTest clears cached results`() {
        val source = FakeSource(emptyMap())
        val loader = FakeLoader()
        FontRegistry.resolve(FontRole.MONO, source, loader)
        assertTrue(FontRegistry.cachedResult(FontRole.MONO) is FontLoadResult.MissingAsset)

        FontRegistry.resetForTest()

        assertNull(FontRegistry.cachedResult(FontRole.MONO))
    }

    @Test
    fun `role contracts match spec slice 14`() {
        assertEquals(FontRole.BODY_SUBSET_BUDGET_BYTES, 200L * 1024)
        assertEquals(FontRole.BODY_SUBSET_COMMON_CHARS, 2500)
        assertEquals(FontRole.LABEL_SANS_SIZE_SP, 10)

        FontRole.entries.forEach { role ->
            assertTrue("${role.name} 候选列表不能为空", role.candidateAssets.isNotEmpty())
            assertEquals(
                "${role.name} 候选文件名不得重复",
                role.candidateAssets.size,
                role.candidateAssets.distinct().size,
            )
        }

        assertEquals(FontLoadStrategy.PRELOAD_SWAP, FontRole.BODY_SERIF.strategy)
        assertEquals(FontLoadStrategy.BLOCKING_TITLE, FontRole.TITLE_SERIF.strategy)
        assertEquals(FontLoadStrategy.LAZY, FontRole.LABEL_SANS.strategy)
        assertEquals(FontLoadStrategy.ON_DEMAND, FontRole.NUMBER_TABULAR.strategy)
        assertEquals(FontLoadStrategy.ON_DEMAND, FontRole.MONO.strategy)
        assertEquals(10, FontRole.LABEL_SANS.sizeSp)
    }
}
