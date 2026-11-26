package io.legado.app.help.artifact.provider

import io.legado.app.help.artifact.entry.ArtifactEntry
import io.legado.app.help.artifact.seed.ArtifactSeedTable

/**
 * 名物条目读取接口（注册表 id → 条目列表）。
 *
 * 为什么抽象成接口：本片种子为内存表；后续数据片替换为资产文件 / 按需下载实现时，
 * Provider 不随数据源改动（09:13 加新类别只加数据表，不改 Provider）。
 *
 * 兜底约定：实现侧遇到表缺失、资产损坏、解析异常时返回空列表，不抛异常；
 * 由 Provider 统一回「未收录」气泡（09:76 边界：不许空白态、不许崩溃）。
 */
interface ArtifactSeedSource {

    /** 按注册 id 取条目；无命中 / 表缺失 / 资产损坏均返回空列表。 */
    fun entriesFor(registrationId: String): List<ArtifactEntry>
}

/**
 * 内置内存种子源（本片默认实现）。
 *
 * [runCatching] 兜底：种子表是纯内存 Kotlin 数据，理论上不抛；保留捕获是为对齐
 * 「资产损坏不冒泡到 UI」的边界口径，后续文件源实现沿用同一约定。
 */
object InMemoryArtifactSeedSource : ArtifactSeedSource {

    override fun entriesFor(registrationId: String): List<ArtifactEntry> =
        runCatching { ArtifactSeedTable.forRegistration(registrationId) }
            .getOrDefault(emptyList())
}
