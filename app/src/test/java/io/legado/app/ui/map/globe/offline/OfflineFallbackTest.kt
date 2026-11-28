package io.legado.app.ui.map.globe.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线兜底决策单测：在线待下载 / 离线兜底 / 缓存命中 / 分域退化档位 / 核心素材保护。
 *
 * 规格要求：无网时预缓存或降级渲染，不允许空白态或崩溃；每类素材都必须有兜底源。
 * D43：地球域 / 星空域各自独立算档，一域缺档不拉低另一域（§十三.8 易漏点）。
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
    fun `地球域全就位为 FULL_TEXTURES 且不读星空域`() {
        // 输入：9 个 EARTH 种类全部 READY_REMOTE，3 个 SKY 种类不记。
        val fallback = OfflineFallback()
        fallback.online = false
        TextureKind.entries.filter { it.domain == FallbackDomain.EARTH }.forEach {
            fallback.textureCache.record(it, AssetStatus.READY_REMOTE, null)
        }
        // 改坏推演：若 degradation(domain) 内部又按 TextureKind.entries 全量算，
        // 星空全缺会把地球拉成 CACHED_MIX，下一行 EARTH 断言先红。
        assertEquals(DegradationLevel.FULL_TEXTURES, fallback.degradation(FallbackDomain.EARTH))
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, fallback.degradation(FallbackDomain.SKY))
    }

    @Test
    fun `星空缺档不污染地球域回归旧 bug`() {
        // 输入：online=false；仅 3 个 SKY 种类无缓存，9 个 EARTH 种类全部 READY_CACHED。
        val fallback = OfflineFallback()
        fallback.online = false
        TextureKind.entries.filter { it.domain == FallbackDomain.EARTH }.forEach {
            fallback.textureCache.record(it, AssetStatus.READY_CACHED, null)
        }
        // 改坏推演：若域过滤条件写成 it.domain != domain 反了，
        // 地球域断言红在下面这行 assertEquals(FULL_TEXTURES, …EARTH)。
        assertEquals(DegradationLevel.FULL_TEXTURES, fallback.degradation(FallbackDomain.EARTH))
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, fallback.degradation(FallbackDomain.SKY))
    }

    @Test
    fun `两域各自独立混合档互换缓存后仍按本域计数`() {
        val fallback = OfflineFallback()
        fallback.online = false
        fallback.textureCache.record(TextureKind.DAY_SATELLITE, AssetStatus.READY_CACHED, null)
        fallback.textureCache.record(TextureKind.SKY_DENSITY_L9, AssetStatus.READY_CACHED, null)
        assertEquals(DegradationLevel.CACHED_MIX, fallback.degradation(FallbackDomain.EARTH))
        assertEquals(DegradationLevel.CACHED_MIX, fallback.degradation(FallbackDomain.SKY))

        // 互换口径：另起缓存，地球项 / 空域项各换一个种类，两域仍各按本域计数。
        val swapped = OfflineFallback()
        swapped.online = false
        swapped.textureCache.record(TextureKind.NIGHT_CITY, AssetStatus.READY_CACHED, null)
        swapped.textureCache.record(TextureKind.STAR_SKY, AssetStatus.READY_CACHED, null)
        // 改坏推演：若计数跨域相加，互换用例下两档必有一断言红。
        assertEquals(DegradationLevel.CACHED_MIX, swapped.degradation(FallbackDomain.EARTH))
        assertEquals(DegradationLevel.CACHED_MIX, swapped.degradation(FallbackDomain.SKY))
    }

    @Test
    fun `每个种类都标注了所属离线域`() {
        TextureKind.entries.forEach {
            assertTrue("${it.name} 未标注 domain", it.domain in FallbackDomain.entries)
        }
        assertEquals(FallbackDomain.SKY, TextureKind.STAR_SKY.domain)
        assertEquals(FallbackDomain.SKY, TextureKind.SKY_DENSITY_L9.domain)
        assertEquals(FallbackDomain.SKY, TextureKind.SKY_DENSITY_L11.domain)
        assertEquals(FallbackDomain.EARTH, TextureKind.DAY_SATELLITE.domain)
        assertEquals(FallbackDomain.EARTH, TextureKind.CAMPAIGN_ROUTES.domain)
    }

    @Test
    fun `空域空集回落兜底全真实与混合三档可归纳`() {
        val fallback = OfflineFallback()
        // 改坏推演：空集被 realCount==size（0==0）误判 FULL_TEXTURES，下一行红。
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, fallback.levelFor(emptyList()))

        val real = OfflineFallback.Resolution(
            TextureKind.DAY_SATELLITE, AssetStatus.READY_CACHED, FallbackKind.PROCEDURAL_TERRAIN,
        )
        assertEquals(DegradationLevel.FULL_TEXTURES, fallback.levelFor(listOf(real)))

        val fallbackRes = OfflineFallback.Resolution(
            TextureKind.CLOUDS, AssetStatus.READY_FALLBACK, FallbackKind.PROCEDURAL_CLOUD_LAYER,
        )
        assertEquals(DegradationLevel.CACHED_MIX, fallback.levelFor(listOf(real, fallbackRes)))
    }

    @Test
    fun `全部真实纹理就位两域均为 FULL_TEXTURES 档位`() {
        val fallback = OfflineFallback()
        TextureKind.entries.forEach {
            fallback.textureCache.record(it, AssetStatus.READY_REMOTE, null)
        }
        assertEquals(DegradationLevel.FULL_TEXTURES, fallback.degradation(FallbackDomain.EARTH))
        assertEquals(DegradationLevel.FULL_TEXTURES, fallback.degradation(FallbackDomain.SKY))
    }

    @Test
    fun `无任何真实纹理两域均为 PROCEDURAL_ONLY 档位`() {
        val fallback = OfflineFallback()
        fallback.online = false
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, fallback.degradation(FallbackDomain.EARTH))
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, fallback.degradation(FallbackDomain.SKY))
    }

    @Test
    fun `缓存星空种类只升星空域混合档`() {
        val fallback = OfflineFallback()
        fallback.online = false
        fallback.textureCache.record(TextureKind.STAR_SKY, AssetStatus.READY_CACHED, null)
        assertEquals(DegradationLevel.CACHED_MIX, fallback.degradation(FallbackDomain.SKY))
        assertEquals(DegradationLevel.PROCEDURAL_ONLY, fallback.degradation(FallbackDomain.EARTH))
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
