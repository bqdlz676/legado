package io.legado.app.ui.map.crossing.terrain

/**
 * 高程栅格实例级缓存（Q-245 从 [HeightMapDecoder] 拆出）。
 *
 * 职责边界：本对象只包「按源字节哈希复用解码结果」，不碰 PNG 解码算法、不碰合成回落场。
 * 沙盘 / 回放共用解码器单例，同一高程源二次解码直接命中缓存栅格，免去二次 inflate 与扫描线反滤波。
 *
 * 并发口径：[getOrDecode] 与 [resetCache] 均 `@Synchronized`（锁本对象监视器）；
 * 解码生产者 [producer] 在锁内执行，与拆分前 `cachedDecode` 的锁边界等价——纯解码、无外部回调，
 * 不会在持锁期间回调 UI 或其它组件。哈希计算留在解码器侧（纯 CPU、局部变量），不占缓存锁。
 *
 * 失败口径：生产者返回 null（空字节 / 坏 Base64 / 非 PNG / 畸形栅格）只记未命中，
 * 绝不把失败结果或合成回落场写进缓存——坏数据下次仍须重新判失败，不能缓存成命中。
 */
internal object HeightMapRasterCache {

    /** 缓存槽上限：最多留 2 份最近使用的真实解码栅格；超出按 LRU 逐出。 */
    private const val CACHE_CAPACITY: Int = 2

    /** 逐出记录环形上限：只留最近 16 条，避免长进程内日志无限增长。 */
    private const val EVICT_LOG_LIMIT: Int = 16

    /**
     * 槽位身份记录：高程源内容 sha256 前 16 位 + 解码栅格尺寸（宽 × 高）。
     *
     * 注意：map 索引键只有 [contentHash]（解码前即可按哈希命中，见 [rasterCache]）；
     * 宽 / 高随槽位记录在本结构里，供逐出台账排障与尺寸核对，不参与命中查找。
     * 同 16 位哈希前缀但尺寸不同的源会在索引层相撞——64 位前缀碰撞概率极低，残余风险见修复报告。
     */
    private data class CacheKey(val contentHash: String, val width: Int, val height: Int)

    private data class CacheSlot(val key: CacheKey, val raster: HeightMapRaster, val lastUsed: Long)

    /** 哈希索引：解码前即可按源字节哈希命中；尺寸留在 [CacheKey]。 */
    private val rasterCache = LinkedHashMap<String, CacheSlot>()

    /** 命中计数：缓存直接返回栅格、未走解码本体。 */
    var cacheHits: Int = 0
        private set

    /** 未命中计数：实际进入解码本体（含解码失败、未入缓存的坏数据）。 */
    var cacheMisses: Int = 0
        private set

    /** LRU 逐出计数。 */
    var cacheEvictions: Int = 0
        private set

    /** 当前在册槽位数（可观测）。 */
    val cacheSize: Int get() = rasterCache.size

    /** 最近逐出记录（最新在后，纯 JVM 文本台账，不引 android.util.Log）。 */
    val cacheEvictLog: List<String>
        get() = evictLog.toList()

    private val evictLog = mutableListOf<String>()

    /**
     * 单一缓存入口：先按 [contentHash] 查缓存；命中直接复用栅格，未命中才调 [producer] 解码。
     *
     * @return 命中或解码成功的栅格；生产者返回 null / 抛异常时记未命中并返回 null，不写槽位。
     */
    @Synchronized
    fun getOrDecode(contentHash: String, producer: () -> HeightMapRaster?): HeightMapRaster? {
        rasterCache[contentHash]?.let { slot ->
            // LRU 命中：摘出重插刷新顺序，时间戳更新后直接返回缓存栅格。
            rasterCache.remove(contentHash)
            rasterCache[contentHash] = slot.copy(lastUsed = System.nanoTime())
            cacheHits++
            return slot.raster
        }
        val raster = try { producer() } catch (_: Throwable) { null }
        cacheMisses++
        if (raster == null) return null
        val key = CacheKey(contentHash, raster.width, raster.height)
        evictIfNeeded(key)
        rasterCache[contentHash] = CacheSlot(key, raster, System.nanoTime())
        return raster
    }

    /** 超容量时逐出最久未用槽位；逐出原因写入 [evictLog] 台账。 */
    private fun evictIfNeeded(insertingKey: CacheKey) {
        while (rasterCache.size >= CACHE_CAPACITY) {
            val victim = rasterCache.entries.minByOrNull { it.value.lastUsed } ?: break
            rasterCache.remove(victim.key)
            cacheEvictions++
            val victimKey = victim.value.key
            val msg = "DEM 缓存 LRU 逐出 ${victimKey.contentHash}（${victimKey.width}×${victimKey.height}），" +
                "容量上限 $CACHE_CAPACITY，新槽 ${insertingKey.contentHash}（${insertingKey.width}×${insertingKey.height}）"
            evictLog.add(msg)
            if (evictLog.size > EVICT_LOG_LIMIT) evictLog.removeAt(0)
        }
    }

    /** 测试 / 排障用：清空缓存与命中计数，保证用例间、战役间互不污染。 */
    @Synchronized
    fun resetCache() {
        rasterCache.clear()
        evictLog.clear()
        cacheHits = 0
        cacheMisses = 0
        cacheEvictions = 0
    }
}
