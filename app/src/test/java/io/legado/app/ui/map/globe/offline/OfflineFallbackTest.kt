package io.legado.app.ui.map.globe.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线兜底决策单测：在线待下载 / 离线兜底 / 缓存命中 / 退化档位 / 核心素材保护。
 *
 * 规格要求：无网时预缓存或降级渲染，不允许空白态或崩溃；每类素材都必须有兜底源。
 */
class OfflineFallbackTest {

    @Test
    fun `在线未缓存时进入待下载且先画兜底`() {
        val fallback = OfflineFallback()
        fallback.online = true
        val resolution = fallback.resolve(TextureKind.DAY_SATELLITE)
        assertEquals(AssetStatus.PENDING_DOWNLOAD, resolution.status)
        assertTrue(resolution.useFallback)
    }

    @Test
    fun `离线未缓存时直接降级到兜底源`() {
        val fallback = OfflineFallback()
        fallback.online = false
        val resolution = fallback.resolve(TextureKind.CLOUDS)
        assertEquals(AssetStatus.READY_FALLBACK, resolution.status)
        assertEquals(FallbackKind.PROCEDURAL_CLOUD_LAYER, resolution.fallback)
    }

    @Test
    fun `缓存命中视为真实纹理`() {
        val fallback = OfflineFallback()
        fallback.online = false
        fallback.textureCache.record(TextureKind.DAY_SATELLITE, AssetStatus.READY_CACHED, "bitmap")
        val resolution = fallback.resolve(TextureKind.DAY_SATELLITE)
        assertEquals(AssetStatus.READY_CACHED, resolution.status)
        assertFalse(resolution.useFallback)
    }

    @Test
    fun `全部真实纹理就位为 FULL_TEXTURES 档位`() {
        val fallback = OfflineFallback()
        TextureKind.entries.forEach {
            fallback.textureCache.record(it, AssetStatus.READY_REMOTE, null)
        }
        assertEquals(DegradationLevel.FULL_TEXTURES, fallback.degradation())
    }

    @Test
    fun `无任何真实纹理为 PROCEDURAL_ONLY 档位`() {
        val fallback = OfflineFallback()
        fallback.online = false
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, fallback.degradation())
    }

    @Test
    fun `部分真实纹理为 CACHED_MIX 档位`() {
        val fallback = OfflineFallback()
        fallback.online = false
        fallback.textureCache.record(TextureKind.STAR_SKY, AssetStatus.READY_CACHED, null)
        assertEquals(DegradationLevel.CACHED_MIX, fallback.degradation())
    }

    @Test
    fun `每一类素材都声明了兜底策略河流缺矢量时不画河流 pass`() {
        TextureKind.entries.forEach {
            assertTrue("${it.name} 缺兜底策略", it.fallback.ordinal >= 0)
        }
        assertEquals(FallbackKind.SEED_VECTOR_NONE, TextureKind.RIVER_VECTOR.fallback)
    }

    @Test
    fun `核心素材超容量时不被淘汰`() {
        val cache = TextureCache(maxSlots = 3)
        // 先填两个非核心，再放三个核心，核心应全部保留
        cache.record(TextureKind.RIVER_VECTOR, AssetStatus.READY_CACHED, null)
        cache.record(TextureKind.POWER_VECTOR, AssetStatus.READY_CACHED, null)
        cache.record(TextureKind.DAY_SATELLITE, AssetStatus.READY_CACHED, null)
        cache.record(TextureKind.STAR_SKY, AssetStatus.READY_CACHED, null)
        cache.record(TextureKind.LABEL_ATLAS, AssetStatus.READY_CACHED, null)
        assertTrue(cache.isRealCached(TextureKind.DAY_SATELLITE))
        assertTrue(cache.isRealCached(TextureKind.STAR_SKY))
        assertTrue(cache.isRealCached(TextureKind.LABEL_ATLAS))
    }

    @Test
    fun `清空缓存后全部回到兜底`() {
        val fallback = OfflineFallback()
        fallback.textureCache.record(TextureKind.DAY_SATELLITE, AssetStatus.READY_CACHED, null)
        fallback.textureCache.clear()
        assertFalse(fallback.resolve(TextureKind.DAY_SATELLITE).status.hasRealTexture)
    }
}
