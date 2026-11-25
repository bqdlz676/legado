package io.legado.app.help.bubble

import io.legado.app.help.artifact.ArtifactCategory
import io.legado.app.help.artifact.ArtifactFallback
import io.legado.app.help.artifact.ArtifactFallbackResult
import io.legado.app.help.artifact.ArtifactSlots
import io.legado.app.help.artifact.EmptyEntityProvider
import io.legado.app.help.artifact.EntityProvider
import io.legado.app.help.artifact.FallbackState
import io.legado.app.help.bubble.contract.MISSING_SOURCE
import io.legado.app.help.chrono.ContextAnchor

/**
 * D16 通用名物 → 既有 [BubbleProvider] 链的适配层（P06 接线）。
 *
 * 职责边界（09:31 / 选型 §3.6）：
 *  - 只遍历 [ArtifactSlots.entitySlots] 登记的 EntityProvider 类别；人物 / 地名 / 典故
 *  由既有槽位承接，本适配层不重复查询，杜绝同一段落出两个气泡。
 *  - [EntityProvider.query] 契约返回非空完整气泡；既有链靠 null 触发优先级下钻。适配层把
 *  B25 兜底壳（source=[MISSING_SOURCE]）识别为未命中并返回 null 交下一层；真实命中才映射回
 *  既有 [BubbleContent]。
 *  - 任何一路异常在内部 runCatching 兜住，不抛进 Resolver；未接数据时走 [EmptyEntityProvider]
 *  的 B25 五态兜底，不留空白态、不崩溃。
 *
 * 纯 JVM：不 import Android / Compose 类型；anchor 透传给 EntityProvider，年代过滤归后续真实实现。
 *
 * @param entity 通用名物查询入口；默认空数据实现，真实数据表组装单替换注入。
 */
class ArtifactBubbleProvider(
    private val entity: EntityProvider = EmptyEntityProvider(),
) : BubbleProvider {

    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val clean = BubbleSupport.cleanToken(token)
        if (clean.isEmpty()) return null

        // 只遍历登记表里路由到 EntityProvider 的类别；既有槽承接的三类不在此重复查（防双轨）。
        for (slot in ArtifactSlots.entitySlots) {
            val result = runCatching {
                entity.query(clean, slot.schemaType ?: "", anchor)
            }.getOrNull() ?: continue

            // 兜底壳（未命中 / 超时 / 离线 / 缺年份 / 消歧失败）不是真实命中：返回 null 下钻，
            // 原始兜底态与分流文案仍可经 [fallbackResult] 从结果壳回读，不丢状态。
            if (result.source == MISSING_SOURCE) continue
            return toLegacy(result)
        }
        return null
    }

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        // 名物专名多为 2~5 字（龙泉剑 / 赤兔马 / 白马寺 / 滕王阁 …），长词优先窗口与人物 / 地名槽同口径。
        for (span in BubbleSupport.windows(paragraph, pos, ENTITY_MAX_LEN)) {
            if (span.length < 2) continue
            query(span.token, anchor)?.let { return it }
        }
        return null
    }

    /**
     * B25 五态兜底直达入口：装配层 / 后续详情页按类别取兜底结果壳，回读 [ArtifactFallbackResult.state]
     * 与 [ArtifactFallbackResult.fallbackMessage] 分流文案。五态经同一结果壳回读，不另立第二状态源。
     */
    fun fallbackResult(name: String, category: ArtifactCategory, state: FallbackState): ArtifactFallbackResult =
        ArtifactFallback.notFound(name, category, state)

    /**
     * 冻结契约气泡 → 既有链气泡映射。动作按既有冻结 Action 四子类收敛；契约第五子类
     * OpenArtifact 在既有链暂无对应路由，暂置空丢弃，详情跳转归后续 UI 接线单（登记另单）。
     */
    internal fun toLegacy(content: io.legado.app.help.bubble.contract.BubbleContent): BubbleContent {
        val legacyActions = content.actions.mapNotNull { mapAction(it) }
        return BubbleContent(
            kind = content.kind.wire,
            token = content.token,
            phonetic = content.phonetic,
            headline = content.headline,
            body = content.body,
            detail = content.detail,
            actions = legacyActions,
            candidates = content.candidates.map { BubbleCandidate(query = it, display = it) },
            source = content.source,
        )
    }

    private fun mapAction(action: io.legado.app.help.bubble.contract.Action): Action? = when (action) {
        is io.legado.app.help.bubble.contract.Action.OpenMap -> Action.OpenMap(action.placeId)
        is io.legado.app.help.bubble.contract.Action.OpenPerson -> Action.OpenPerson(action.name)
        is io.legado.app.help.bubble.contract.Action.OpenTimeline -> Action.OpenTimeline(action.year)
        is io.legado.app.help.bubble.contract.Action.OpenRelation -> Action.OpenRelation(action.name)
        is io.legado.app.help.bubble.contract.Action.OpenArtifact -> null
    }

    private companion object {
        /** 名物专名查询窗口上限（2~5 字，对齐人物 / 地名槽词长口径）。 */
        const val ENTITY_MAX_LEN = 5
    }
}
