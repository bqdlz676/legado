package io.legado.app.ui.font

/**
 * 资产读取缝：把 Android [android.content.res.AssetManager] 隔离在接口之后，
 * 使注册表 / 缓存 / 兜底决策可在宿主 JVM 单测中用假实现跑通，不依赖 Robolectric。
 *
 * 所有方法均不得抛异常：读不到一律返回空集合 / null，由上层走兜底路径。
 */
interface FontAssetSource {

    /** 列出资产目录下的文件名（不含目录前缀）；目录不存在或不可读时返回空列表。 */
    fun listAssets(dir: String): List<String>

    /** 资产字节数；不存在或不可读时返回 null。 */
    fun assetSize(assetPath: String): Long?

    /** 读取资产全部字节；不存在或读取失败时返回 null。 */
    fun openAsset(assetPath: String): ByteArray?
}

/**
 * 字体句柄构造缝：Android 侧把字节包成 [android.graphics.Typeface]；
 * 单测侧用假句柄验证缓存与兜底编排。
 *
 * 返回 null 或抛异常都按「加载失败」处理，上层回落系统字体，不向上抛。
 */
fun interface FontHandleLoader {

    /** @return 加载成功的字体句柄；失败返回 null 或抛异常。 */
    fun load(role: FontRole, assetPath: String, bytes: ByteArray): Any?
}
