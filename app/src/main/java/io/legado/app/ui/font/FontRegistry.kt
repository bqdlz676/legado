package io.legado.app.ui.font

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 字体注册表：角色 → 加载结果的进程级缓存与决策编排（纯 Kotlin，可在 JVM 单测跑通）。
 *
 * 编排顺序（每个角色只探一次，缺失 / 失败也缓存，避免每帧扫资产目录）：
 *  1. 查缓存，命中直接返回；
 *  2. 列资产目录，按 [FontRole.candidateAssets] 优先级找第一个存在的候选；
 *  3. 体积红线校验：超出 [FontRole.maxSubsetBytes] 判 [FontLoadResult.RejectedOversize]；
 *  4. 读字节并交 [FontHandleLoader] 构造句柄；构造失败判 [FontLoadResult.LoadFailed]；
 *  5. 全部异常路径都不抛出，取用侧按 [FontLoadResult.fallback] 回落系统字体。
 *
 * 线程安全：缓存为 [ConcurrentHashMap]，[resolve] 可在主线程 / 懒加载线程并发调用。
 */
object FontRegistry {

    private val cache = ConcurrentHashMap<FontRole, FontLoadResult>()

    /**
     * 解析指定角色字体。重复调用走缓存；首次调用执行完整探测链。
     */
    fun resolve(
        role: FontRole,
        source: FontAssetSource,
        loader: FontHandleLoader,
    ): FontLoadResult {
        cache[role]?.let { return it }
        val result = load(role, source, loader)
        cache[role] = result
        return result
    }

    /** 读取当前缓存结果；未探测过返回 null。 */
    fun cachedResult(role: FontRole): FontLoadResult? = cache[role]

    /** 单测 / 进程重置用：清空全部缓存。 */
    fun resetForTest() {
        cache.clear()
    }

    private fun load(
        role: FontRole,
        source: FontAssetSource,
        loader: FontHandleLoader,
    ): FontLoadResult {
        val available = runCatching { source.listAssets(FontRole.ASSET_DIR) }
            .getOrDefault(emptyList())
            .toSet()

        val assetName = role.candidateAssets.firstOrNull { candidate ->
            available.any { it.equals(candidate, ignoreCase = true) }
        } ?: return FontLoadResult.MissingAsset(role)

        val assetPath = "${FontRole.ASSET_DIR}/$assetName"

        val size = runCatching { source.assetSize(assetPath) }.getOrNull()
            ?: return FontLoadResult.LoadFailed(
                role, assetName, IOException("asset size unreadable: $assetPath"),
            )

        role.maxSubsetBytes?.let { budget ->
            if (size > budget) {
                return FontLoadResult.RejectedOversize(role, assetName, size, budget)
            }
        }

        val bytes = runCatching { source.openAsset(assetPath) }.getOrNull()
            ?: return FontLoadResult.LoadFailed(
                role, assetName, IOException("asset unreadable: $assetPath"),
            )

        val handle = runCatching { loader.load(role, assetPath, bytes) }
            .getOrNull()
            ?: return FontLoadResult.LoadFailed(
                role, assetName, IllegalStateException("font handle construction returned null"),
            )

        return FontLoadResult.Loaded(role, handle, assetName, size)
    }
}
