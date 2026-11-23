package io.legado.app.ui.map.globe.offline

/**
 * 离线兜底决策（纯 JVM，切片 19 / 20 性能与数据准备段）。
 *
 * 三级退化链，逐级可画、不允许空白态：
 *  1. 真实纹理：在线拉取或本地预缓存命中；
 *  2. 缓存纹理：离线时只读本机已缓存的种类；
 *  3. 程序化 / 种子：真实与缓存都没有时，切到 fbm 地形、噪声云层、种子地名 / 战役 / 势力。
 *
 * 为什么把决策从渲染层抽出来：渲染 pass 选择必须可单测；网络状态变化时只改这里的
 * online 标志，渲染层每帧读 [Resolution] 即可，不必在绘制代码里散落网络判断。
 */
class OfflineFallback(private val cache: TextureCache = TextureCache()) {

    /** 当前网络是否在线；由 Android 网络观察层写入，纯逻辑层不感知实现。 */
    var online: Boolean = true

    val textureCache: TextureCache get() = cache

    /** 单个素材的解析结果。 */
    data class Resolution(
        val kind: TextureKind,
        val status: AssetStatus,
        val fallback: FallbackKind,
    ) {
        /** 渲染层是否应走程序化 / 种子兜底。 */
        val useFallback: Boolean get() = !status.hasRealTexture
    }

    /**
     * 解析某类素材的就位状态。任何分支都返回非空 Resolution：
     * - 缓存命中 → 真实纹理；
     * - 在线未缓存 → 先 PENDING_DOWNLOAD，渲染用兜底，下载完成后回填缓存；
     * - 离线未缓存 → READY_FALLBACK，直接走降级源。
     */
    fun resolve(kind: TextureKind): Resolution {
        val cached = cache.status(kind)
        if (cached != null && cached.hasRealTexture) {
            return Resolution(kind, cached, kind.fallback)
        }
        val status = if (online) AssetStatus.PENDING_DOWNLOAD else AssetStatus.READY_FALLBACK
        return Resolution(kind, status, kind.fallback)
    }

    /** 全量素材解析（场景装配 / 自检用）。 */
    fun resolveAll(): List<Resolution> = TextureKind.entries.map { resolve(it) }

    /** 整体退化档位。 */
    fun degradation(): DegradationLevel {
        val resolutions = resolveAll()
        val realCount = resolutions.count { it.status.hasRealTexture }
        return when {
            realCount == resolutions.size -> DegradationLevel.FULL_TEXTURES
            realCount > 0 -> DegradationLevel.CACHED_MIX
            else -> DegradationLevel.PROCEDURAL_ONLY
        }
    }

    /** 真实纹理种类数（性能 / 自检报告用）。 */
    fun realTextureCount(): Int = resolveAll().count { it.status.hasRealTexture }

    /** 兜底种类数。 */
    fun fallbackCount(): Int = resolveAll().count { it.useFallback }
}

/**
 * 整体渲染档位：决定球体 pass 组合与纹理精度上限。
 */
enum class DegradationLevel {
    /** 全部真实纹理就位，按 LOD 上 2K/4K/8K。 */
    FULL_TEXTURES,

    /** 部分真实纹理命中，其余走兜底；未就位的种类不上高精纹理。 */
    CACHED_MIX,

    /** 无任何真实纹理，全程序化 / 种子；仍保证地球仪可转、可回放、不空白。 */
    PROCEDURAL_ONLY,
}
