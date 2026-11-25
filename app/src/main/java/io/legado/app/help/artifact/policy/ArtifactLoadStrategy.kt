package io.legado.app.help.artifact.policy

import io.legado.app.help.artifact.ArtifactCategory

/**
 * 名物库加载 / 缓存状态（09:72 懒加载：Provider 按命中才 load；CBDB 8.9 万 /
 * zh_ci 28 万 / 本草 1800 等大库按需下载 + 预缓存，不全量首触驻留）。
 *
 * [OFFLINE_CACHE_MISS] 即断网兜底标记：网络不可达且本地缓存未命中时置此态，
 * 气泡壳仍走空数据回退，不阻塞阅读链路。
 *
 * [TIMEOUT] 即加载超时标记（B23）：按需 / 在线加载超过 [ArtifactLoadStrategy.LOAD_TIMEOUT_MS]
 * 仍未返回时置此态，接线层改走缓存 / 降级兜底，不返回 null、不抛异常到 UI。
 */
enum class CacheStatus {
    /** 本地 / 内存缓存命中。 */
    HIT,

    /** 缓存未命中，可走在线加载。 */
    MISS,

    /** 断网且本地缓存未命中；后续重试由接线层策略决定，本层不自动联网。 */
    OFFLINE_CACHE_MISS,

    /** 加载超时；走缓存 / 降级兜底，后续重试由接线层策略决定，本层不自动联网。 */
    TIMEOUT,
}

/**
 * 懒加载策略接口（本单只定义，不实现真实下载 / 预缓存）。
 *
 * 为什么独立成策略接口：数据量级差异大（草木 800 / 鸟兽 600 / 药名 1800 / 山川 300+），
 * 各库加载时机、预缓存档位由组装单按类别注入；契约层先把「是否已载 / 按需载 / 缓存态」
 * 三个动作钉死，避免后续实现各自发明加载口径。
 */
interface ArtifactLoadStrategy {

    /** 该类别当前是否已驻留内存；未驻留时查询方应先 [loadIfNeeded]。 */
    fun isLoaded(category: ArtifactCategory): Boolean

    /** 按需加载：未载则载，已载则不重复驻留；真实下载 / 预缓存实现归后续数据组装单。 */
    fun loadIfNeeded(category: ArtifactCategory)

    /** 最近一次该类别的缓存状态；断网缓存未命中取 [CacheStatus.OFFLINE_CACHE_MISS]，超时取 [CacheStatus.TIMEOUT]。 */
    fun cacheStatus(category: ArtifactCategory): CacheStatus

    companion object {

        /**
         * 按需 / 在线加载超时阈值（毫秒）。全包唯一超时阈值常量（B23 判据 4）：
         * 策略实现、兜底构造器与单测只引用本值，不得在包内 / 包外另立第二份超时毫秒数。
         */
        const val LOAD_TIMEOUT_MS: Long = 5_000L
    }
}
