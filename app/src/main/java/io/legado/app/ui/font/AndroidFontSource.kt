package io.legado.app.ui.font

import android.content.Context

/**
 * [FontAssetSource] 的 Android 实现：从 [android.content.res.AssetManager] 读内置字体子集。
 * 全程 runCatching，资产异常不外抛；读不到返回空 / null，由注册表走兜底。
 */
internal class AndroidFontSource(context: Context) : FontAssetSource {

    private val assets = context.applicationContext.assets

    override fun listAssets(dir: String): List<String> = runCatching {
        assets.list(dir)?.toList().orEmpty()
    }.getOrDefault(emptyList())

    override fun assetSize(assetPath: String): Long? = runCatching {
        // 子集 ≤ 200KB，流内 available 即全长；不用 openFd，避免压缩资产打不开。
        assets.open(assetPath).use { it.available().toLong() }
    }.getOrNull()

    override fun openAsset(assetPath: String): ByteArray? = runCatching {
        assets.open(assetPath).use { it.readBytes() }
    }.getOrNull()
}
