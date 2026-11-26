package io.legado.app.help.artifact.provider

import io.legado.app.help.artifact.ArtifactCategory
import io.legado.app.help.artifact.ArtifactFallback
import io.legado.app.help.artifact.ArtifactRoute
import io.legado.app.help.artifact.EntityProvider
import io.legado.app.help.artifact.FallbackState
import io.legado.app.help.artifact.entry.ArtifactEntry
import io.legado.app.help.artifact.policy.ArtifactLoadStrategy
import io.legado.app.help.artifact.registry.ArtifactRegistration
import io.legado.app.help.artifact.registry.ArtifactRegistry
import io.legado.app.help.bubble.contract.Action
import io.legado.app.help.bubble.contract.BubbleContent

/**
 * D16 通用 [EntityProvider] 注册表路由实现（本片第 2 片：十七类注册 + 路由 + 懒加载 + 种子）。
 *
 * 职责链（每一步都有兜底，绝不抛异常到 UI、绝不返回 null）：
 *  1. 注册表路由：type / id 经 [ArtifactRegistry.byType] 反查登记；查不到 → 「未收录」气泡。
 *  2. 双轨隔离：既有槽类别（人物 / 地名）由 D2 / D3 原 Provider 承接，本实现只回兜底壳，
 *     不对其二次出气泡（09:31 不重构不双轨）。
 *  3. 懒加载：按 [ArtifactLoadStrategy] 命中才 load；策略可空（内存种子视为已驻留）。
 *  4. 条目读取：[ArtifactSeedSource] 取本类条目；表缺失 / 资产损坏 / 查不到 → 「未收录」气泡。
 *  5. 同名消歧：多条同名取首条并标疑似，候选裸串进 [BubbleContent.candidates] 交 Sheet 点选。
 *  6. 最小共同气泡字段：壳内只放名称 + 一句话；完整释义走详情 Sheet（09:73）。
 *
 * 跳转挂点（本片只挂数据与 Action，不接 UI）：
 *  - 命中条目带 [Action.OpenArtifact]（详情 Sheet 一级跳转）；
 *  - 条目 [ArtifactEntry.relatedPlaceId] 非空时挂 [Action.OpenMap]（详情 → 有地名再定位的二级跳转）；
 *  - 按钮渲染、Sheet 展开归后续 UI 片。
 *
 * 纯 JVM：不 import Android / Compose 类型；anchor 在此片仅透传，年代锚过滤归后续消歧链。
 *
 * @param seedSource  条目读取源；默认内存种子表，全量数据片替换注入。
 * @param loadStrategy 懒加载策略；null 时视为种子已驻留，不触发加载。
 */
class RegistryEntityProvider(
    private val seedSource: ArtifactSeedSource = InMemoryArtifactSeedSource,
    private val loadStrategy: ArtifactLoadStrategy? = null,
) : EntityProvider {

    override fun query(name: String, type: String, anchor: Any?): BubbleContent {
        val clean = name.trim()
        if (clean.isEmpty()) {
            return ArtifactFallback.notFound("", null, FallbackState.NO_DATA).content
        }

        // 路由：注册表缺类（未知 type / 未知 id）→ 未收录兜底，不落到别的类别。
        val registration = ArtifactRegistry.byType(type)
            ?: return ArtifactFallback.notFound(clean, null, FallbackState.NO_DATA).content

        // 双轨隔离：既有槽类别不走本通用 Provider，兜底壳由适配层识别后交原槽。
        if (registration.route == ArtifactRoute.EXISTING_SLOT) {
            return ArtifactFallback.notFound(clean, registration.category, FallbackState.NO_DATA).content
        }

        // 懒加载：命中才 load；策略缺失视为已驻留；加载异常不阻断查询链路。
        registration.category?.let { category ->
            runCatching {
                if (loadStrategy != null && !loadStrategy.isLoaded(category)) {
                    loadStrategy.loadIfNeeded(category)
                }
            }
        }

        // 条目读取：表缺失 / 资产损坏 / 解析异常统一退为空列表 → 未收录气泡。
        val entries = runCatching { seedSource.entriesFor(registration.id) }
            .getOrDefault(emptyList())
        if (entries.isEmpty()) {
            return ArtifactFallback.notFound(clean, registration.category, FallbackState.NO_DATA).content
        }

        val matched = entries.filter { it.name == clean || clean in it.aliases }
        if (matched.isEmpty()) {
            return ArtifactFallback.notFound(clean, registration.category, FallbackState.NO_DATA).content
        }

        val first = matched.first()
        val suspected = matched.size > 1
        val candidates = matched.map { candidateLabel(it) }
        return buildContent(registration, first, candidates, suspected)
    }

    /**
     * 按注册 id 直查（索引页 / 搜索入口用）；未登记 id 走未收录兜底，不抛异常。
     */
    fun queryByRegistration(name: String, registrationId: String, anchor: Any?): BubbleContent {
        val registration = ArtifactRegistry.byId(registrationId)
            ?: return ArtifactFallback.notFound(name.trim(), null, FallbackState.NO_DATA).content
        return query(name, registration.schemaType ?: registration.id, anchor)
    }

    /** 候选裸串：主名 + 别名出处线索；详情点选归半屏 Sheet。 */
    private fun candidateLabel(entry: ArtifactEntry): String {
        val aliasHint = entry.aliases.firstOrNull()?.let { "（别名 $it）" } ?: ""
        return "${entry.name}$aliasHint"
    }

    /** 组装最小共同气泡；动作挂点顺序：先详情 OpenArtifact，后地名 OpenMap。 */
    private fun buildContent(
        registration: ArtifactRegistration,
        entry: ArtifactEntry,
        candidates: List<String>,
        suspected: Boolean,
    ): BubbleContent {
        val actions = mutableListOf<Action>()
        // 一级跳转挂点：名物详情 Sheet。
        actions.add(Action.OpenArtifact(entry.id, registration.schemaType ?: entry.type))
        // 二级跳转挂点：有关联地名才挂地图；无地名不造占位地图按钮。
        entry.relatedPlaceId?.let { placeId ->
            actions.add(Action.OpenMap(placeId))
        }

        val body = if (suspected) {
            "${entry.body}（同名多条，取首条，疑似待核）"
        } else {
            entry.body
        }

        return BubbleContent(
            kind = registration.fallbackKind,
            token = entry.name,
            phonetic = null,
            headline = entry.headline.ifBlank { entry.name },
            body = body,
            detail = entry.detail,
            actions = actions,
            candidates = candidates,
            source = entry.source,
        )
    }

    /** 供装配层回读条目归属类别（接线 / 索引页分组用）；未登记返回 null。 */
    fun categoryOf(type: String): ArtifactCategory? =
        ArtifactRegistry.byType(type)?.category
}
