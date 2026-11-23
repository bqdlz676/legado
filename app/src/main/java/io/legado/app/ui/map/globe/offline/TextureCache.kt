package io.legado.app.ui.map.globe.offline

/**
 * 素材缓存台账（纯 JVM 元数据层，不持有 Android Bitmap）。
 *
 * 为什么只记元数据：真实位图的解码 / 回收属于 Android 渲染层；这里先把「哪一类素材
 * 已缓存、缓存句柄是什么」管起来，离线判断和降级策略在纯 JVM 下可单测。
 *
 * 预缓存语义（切片 19：Blue Marble 2K 缓存本地，首次打开 ≤800ms）：
 * 启动时优先把 [TextureKind.DAY_SATELLITE] 等核心种类标记为缓存命中；
 * 容量超限时按最少最近使用淘汰非核心种类，核心 2K 底图不参与淘汰。
 */
class TextureCache(private val maxSlots: Int = 12) {

    private data class Slot(
        val status: AssetStatus,
        val handle: Any?,
        val lastUsed: Long,
    )

    private val slots = LinkedHashMap<TextureKind, Slot>()

    /** 核心素材：离线兜底也必须尽量保留的 2K 底图 / 星空 / 种子 atlas。 */
    private val coreKinds = setOf(
        TextureKind.DAY_SATELLITE,
        TextureKind.STAR_SKY,
        TextureKind.LABEL_ATLAS,
        TextureKind.CAMPAIGN_ROUTES,
    )

    @Synchronized
    fun record(kind: TextureKind, status: AssetStatus, handle: Any? = null) {
        evictIfNeeded(kind)
        slots[kind] = Slot(status, handle, System.nanoTime())
    }

    /** 记录一次使用，刷新 LRU 顺序。 */
    @Synchronized
    fun touch(kind: TextureKind) {
        val slot = slots[kind] ?: return
        slots[kind] = slot.copy(lastUsed = System.nanoTime())
    }

    @Synchronized
    fun status(kind: TextureKind): AssetStatus? = slots[kind]?.status

    @Synchronized
    fun handle(kind: TextureKind): Any? {
        touch(kind)
        return slots[kind]?.handle
    }

    @Synchronized
    fun isRealCached(kind: TextureKind): Boolean =
        slots[kind]?.status?.hasRealTexture == true

    @Synchronized
    fun clear() = slots.clear()

    @Synchronized
    fun realTextureCount(): Int = slots.values.count { it.status.hasRealTexture }

    private fun evictIfNeeded(inserting: TextureKind) {
        if (!slots.containsKey(inserting) && slots.size >= maxSlots) {
            // 淘汰最久未用的非核心种类；核心种类即使超容量也保留，避免离线丢底图。
            val victim = slots.entries
                .filter { it.key !in coreKinds }
                .minByOrNull { it.value.lastUsed }
            if (victim != null) slots.remove(victim.key)
        }
    }
}
