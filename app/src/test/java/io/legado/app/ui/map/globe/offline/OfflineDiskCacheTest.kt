package io.legado.app.ui.map.globe.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 判据 5（§十四.5 断网清缓存）真删除兜底用例。
 *
 * 既有 [OfflineFallbackTest] 的「清空缓存后全部回到兜底」走的是内存桩（`textureCache.clear()`），
 * 没有真实目录删除；本类用真实临时目录 + 真实 [File] 删除 / 截断 / 路径占用，
 * 走「缓存在 → 清空 → 重新解析 → 兜底可视」链路，三态边界：
 *  1. 缓存目录整个被删；
 *  2. 缓存标记文件被截断成 0 字节（损坏）；
 *  3. 缓存目录不可写（路径先被普通文件占用，目录创建失败，模拟权限故障）。
 *
 * 每例断言口径（规格 §十四.5：不空白、不崩、有兜底可视）：
 *  - 解析结果条数 = 素材种类数（不空白）；
 *  - 全过程不抛异常（不崩）；
 *  - 每类素材 [OfflineFallback.Resolution.useFallback] = true 且兜底源非空（有兜底源）。
 *
 * 注意：本类只验证纯 JVM 元数据层的兜底决策；真机飞行模式 + 冷启动链路仍归人判缺口，
 * 不在本类冒充运行时真机结论。
 */
class OfflineDiskCacheTest {

    private fun newTempDir(): File = Files.createTempDirectory("offline-disk-cache").toFile()

    @Test
    fun `缓存目录被整删后重新解析全部落兜底且不崩`() {
        val dir = newTempDir()
        val warm = OfflineFallback(TextureCache(baseDir = dir))
        warm.online = false
        TextureKind.entries.forEach {
            warm.textureCache.record(it, AssetStatus.READY_CACHED, "stub-handle")
        }
        assertTrue(warm.textureCache.isRealCached(TextureKind.DAY_SATELLITE))

        // 真实删除：整个缓存目录递归删掉（不是内存 clear 桩）。
        assertTrue(dir.deleteRecursively())
        assertFalse(dir.exists())

        // 重新解析：新台账挂同一被删目录，从磁盘重建视图。
        val reloaded = OfflineFallback(TextureCache(baseDir = dir))
        reloaded.online = false
        reloaded.textureCache.reloadFromDisk()

        val resolutions = reloaded.resolveAll()
        assertEquals(TextureKind.entries.size, resolutions.size)
        resolutions.forEach { res ->
            assertFalse("${res.kind} 目录被删后仍报真实纹理", res.status.hasRealTexture)
            assertTrue(res.useFallback)
            assertNotNull(res.fallback)
            assertTrue(res.fallback.ordinal >= 0)
        }
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, reloaded.degradation(FallbackDomain.EARTH))
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, reloaded.degradation(FallbackDomain.SKY))
    }

    @Test
    fun `缓存标记文件截断为零字节后视为损坏落兜底`() {
        val dir = newTempDir()
        val warm = OfflineFallback(TextureCache(baseDir = dir))
        warm.online = false
        warm.textureCache.record(TextureKind.STAR_SKY, AssetStatus.READY_CACHED, "handle")
        val marker = File(dir, "${TextureKind.STAR_SKY.name}.cache")
        assertTrue(marker.isFile)
        assertTrue(marker.length() > 0)

        // 真实截断：0 字节损坏文件（写入空字节数组，不是删除文件）。
        marker.outputStream().use { it.write(ByteArray(0)) }
        assertEquals(0L, marker.length())

        val reloaded = OfflineFallback(TextureCache(baseDir = dir))
        reloaded.online = false
        reloaded.textureCache.reloadFromDisk()

        val res = reloaded.resolve(TextureKind.STAR_SKY)
        assertFalse("0 字节损坏标记不应判为真实缓存", res.status.hasRealTexture)
        assertTrue(res.useFallback)
        assertNotNull(res.fallback)
        assertEquals(FallbackKind.PROCEDURAL_STAR_FIELD, res.fallback)
    }

    @Test
    fun `缓存目录不可写时记录不崩且重新解析落兜底`() {
        val parent = newTempDir()
        val blocked = File(parent, "blocked-dir")
        // 路径先占成普通文件：目录创建必然失败，模拟权限拒绝 / 目录不可写故障。
        blocked.writeText("occupied")
        assertTrue(blocked.isFile)

        val cache = TextureCache(baseDir = blocked)
        // 改坏推演：writeMarker 若不吞 IOException/SecurityException，下一行 record 直接抛红。
        cache.record(TextureKind.DAY_SATELLITE, AssetStatus.READY_CACHED, "handle")
        assertTrue(cache.isDiskFault)

        val reloaded = OfflineFallback(cache)
        reloaded.online = false
        reloaded.textureCache.reloadFromDisk()

        val resolutions = reloaded.resolveAll()
        assertEquals(TextureKind.entries.size, resolutions.size)
        resolutions.forEach { res ->
            assertFalse("目录不可写时不应留下真实纹理缓存", res.status.hasRealTexture)
            assertTrue(res.useFallback)
            assertNotNull(res.fallback)
        }
    }
}
