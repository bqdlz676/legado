package io.legado.app.ui.map.globe.offline

/**
 * 单个素材的就位状态。
 *
 * 为什么允许 PENDING：切片 19 要求首次打开 2K 纹理缓存本地、≤800ms；下载未完成前
 * 渲染层不能干等，先画兜底层，纹理就位后再替换。状态机保证任何分支都有可画结果。
 */
enum class AssetStatus {
    /** 本地预缓存命中（离线可直接用）。 */
    READY_CACHED,

    /** 本会话在线拉取成功。 */
    READY_REMOTE,

    /** 尚未下载；渲染先用兜底层，就位后替换。 */
    PENDING_DOWNLOAD,

    /** 已降级到程序化 / 种子兜底（无真实纹理，但不空白）。 */
    READY_FALLBACK,
    ;

    /** 是否拿到了真实纹理（缓存或在线）。 */
    val hasRealTexture: Boolean
        get() = this == READY_CACHED || this == READY_REMOTE
}
