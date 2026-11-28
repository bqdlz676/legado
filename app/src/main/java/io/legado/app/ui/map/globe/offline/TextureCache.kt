package io.legado.app.ui.map.globe.offline

import java.io.File
import java.io.IOException

/**
 * 素材缓存台账（纯 JVM 元数据层，不持有 Android Bitmap）。
 *
 * 为什么只记元数据：真实位图的解码 / 回收属于 Android 渲染层；这里先把「哪一类素材
 * 已缓存、缓存句柄是什么」管起来，离线判断和降级策略在纯 JVM 下可单测。
 *
 * 预缓存语义（切片 19：Blue Marble 2K 缓存本地，首次打开 ≤800ms）：
 * 启动时优先把 [TextureKind.DAY_SATELLITE] 等核心种类标记为缓存命中；
 * 容量超限时按最少最近使用淘汰非核心种类，核心 2K 底图不参与淘汰。
 *
 * 磁盘台账（判据 5 · §十四.5）：注入 [baseDir] 后，真实纹理命中会落一个
 * `<kind>.cache` 标记文件；冷启动 / 清缓存后走 [reloadFromDisk] 从磁盘重建内存视图。
 * 目录被整删、标记文件截断成 0 字节、目录不可写三种故障都必须落到兜底，不允许空白态。
 * [baseDir] 为 null 时退回既有纯内存行为，既有调用方不受影响。
 */
class TextureCache(
    private val maxSlots: Int = 12,
    private val baseDir: File? = null,
) {

    private data class Slot(
        val status: AssetStatus,
        val handle: Any?,
        val lastUsed: Long,
    )

    private val slots = LinkedHashMap<TextureKind, Slot>()

    /** 磁盘侧是否出现过写入故障（权限 / 不可写）；只作观测标记，不影响兜底结论。 */
    @Volatile
    private var diskFault = false

    /** 核心素材：离线兜底也必须尽量保留的 2K 底图 / 星空 / 种子 atlas。 */
    private val coreKinds = setOf(
        TextureKind.DAY_SATELLITE,
        TextureKind.STAR_SKY,
        TextureKind.LABEL_ATLAS,
        TextureKind.CAMPAIGN_ROUTES,
    )

    @Synchronized
    fun record(kind: TextureKind, status: AssetStatus, handle: Any? = null) {
        writeMarker(kind, status)
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
    fun clear() {
        slots.clear()
        val dir = baseDir ?: return
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (ioe: IOException) {
            diskFault = true
        }
    }

    @Synchronized
    fun realTextureCount(): Int = slots.values.count { it.status.hasRealTexture }

    /** 磁盘侧是否出过写入故障（权限拒绝 / 目录不可写）。 */
    val isDiskFault: Boolean get() = diskFault

    /**
     * 冷启动 / 清缓存后从磁盘台账重建内存视图。
     * - 目录不存在或不是目录（被整删 / 路径被普通文件占用）→ 空视图，全部落兜底；
     * - 标记文件 0 字节（截断损坏）→ 不计缓存；
     * - 非空标记文件 → 记 [AssetStatus.READY_CACHED]，句柄不跨冷启动保留。
     */
    @Synchronized
    fun reloadFromDisk() {
        slots.clear()
        val dir = baseDir ?: return
        if (!dir.isDirectory) return
        for (kind in TextureKind.entries) {
            val file = File(dir, "${kind.name}.cache")
            if (file.isFile && file.length() > 0) {
                slots[kind] = Slot(AssetStatus.READY_CACHED, null, System.nanoTime())
            }
        }
    }

    private fun markerFile(kind: TextureKind): File? =
        baseDir?.let { File(it, "${kind.name}.cache") }

    private fun writeMarker(kind: TextureKind, status: AssetStatus) {
        val file = markerFile(kind) ?: return
        try {
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            if (status.hasRealTexture) {
                file.writeText(status.name)
            } else if (file.exists()) {
                file.delete()
            }
        } catch (ioe: IOException) {
            // 目录不可写 / 磁盘故障：磁盘侧不可用，内存台账照常更新，解析仍可落兜底，不向上抛。
            diskFault = true
        } catch (se: SecurityException) {
            // 权限被安全管理器拒绝时同口径兜底，不让写入故障击穿渲染链路。
            diskFault = true
        }
    }

    private fun evictIfNeeded(inserting: TextureKind) {
        if (slots.containsKey(inserting) || slots.size < maxSlots) return
        // 淘汰最久未用的非核心种类；核心种类即使超容量也保留，避免离线丢底图。
        val victim = slots.entries
            .filter { it.key !in coreKinds }
            .minByOrNull { it.value.lastUsed }
        if (victim != null) slots.remove(victim.key)
    }
}
