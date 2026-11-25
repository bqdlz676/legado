package io.legado.app.help.artifact

import io.legado.app.help.artifact.policy.ArtifactLoadStrategy
import io.legado.app.help.bubble.contract.Action
import io.legado.app.help.bubble.contract.BubbleContent
import io.legado.app.help.bubble.contract.Kind
import io.legado.app.help.bubble.contract.MISSING_SOURCE

/**
 * D16 兜底边界态（锚点卡 §四；本单在接口层给出可执行回退，不造假数据）。
 *
 * - [NO_DATA]            空数据：库内无此条目，返回结构完整的空壳气泡。
 * - [CACHE_MISS_OFFLINE] 断网：本地缓存未命中，标记离线缓存未命中态，不阻塞气泡壳。
 * - [YEAR_MISSING]       缺年份：年代锚缺失，不做年代过滤（不过滤 ≠ 猜年代）。
 * - [AMBIGUOUS]          同名消歧失败：返回候选裸串列表，交半屏 Sheet 由用户点选。
 * - [TIMEOUT]            加载超时：按需 / 在线加载超过 [ArtifactLoadStrategy.LOAD_TIMEOUT_MS]，
 *                         走缓存 / 降级兜底，产出结构完整空壳气泡，不返回 null、不抛到 UI（B23）。
 */
enum class FallbackState {
    NO_DATA,
    CACHE_MISS_OFFLINE,
    YEAR_MISSING,
    AMBIGUOUS,
    TIMEOUT,
}

/**
 * 空数据 / 兜底气泡构造器。所有回退路径产出的 [BubbleContent] 字段齐全：
 * source 固定为 [MISSING_SOURCE]（「未命中」），candidates / actions 为空列表，
 * phonetic 为 null；绝不返回 null、绝不抛异常。
 */
object ArtifactFallback {

    /**
     * 构造未命中兜底气泡。
     *
     * @param name     原文命中名；空白时 token / headline 落空串，不猜名。
     * @param category 命中类别；未知类别传 null，气泡类型退化为 [Kind.WORD]。
     * @param state    兜底态，供接线层区分空数据 / 离线缓存未命中 / 缺年份 / 超时。
     */
    fun notFound(
        name: String,
        category: ArtifactCategory?,
        state: FallbackState,
    ): BubbleContent {
        val safeName = name.ifBlank { "" }
        return BubbleContent(
            kind = category?.fallbackKind ?: Kind.WORD,
            token = safeName,
            phonetic = null,
            headline = safeName,
            body = "",
            detail = "",
            actions = emptyList<Action>(),
            candidates = emptyList(),
            source = MISSING_SOURCE,
        )
    }

    /**
     * 构造同名多候选气泡（消歧失败回退）。
     *
     * @param candidates 候选裸串列表；无候选时退化为 [FallbackState.NO_DATA] 空壳。
     *
     * 口径（B22 收口，判据 1「下标该不该存在」判定为：气泡契约不需要）：
     * 冻结 [BubbleContent] 的 `candidates` 为裸串 `List<String>`，契约层不为其扩字段；
     * 「哪个候选为默认项」由接线层在既有 `Candidate.isDefault` 契约侧展开，详情与点选
     * 走半屏 Sheet 另行定位（09:73）。本兜底构造器不产出 Candidate 对象，默认下标在本层
     * 无落点，故不设该形参，不留无用参数。
     */
    fun ambiguous(
        name: String,
        category: ArtifactCategory?,
        candidates: List<String>,
    ): BubbleContent {
        if (candidates.isEmpty()) {
            return notFound(name, category, FallbackState.AMBIGUOUS)
        }
        val safeName = name.ifBlank { "" }
        return BubbleContent(
            kind = category?.fallbackKind ?: Kind.WORD,
            token = safeName,
            phonetic = null,
            headline = safeName,
            body = "",
            detail = "",
            actions = emptyList<Action>(),
            candidates = candidates,
            source = MISSING_SOURCE,
        )
    }

    /**
     * 构造加载超时兜底气泡（B23 收口）。
     *
     * 按需 / 在线加载超过 [ArtifactLoadStrategy.LOAD_TIMEOUT_MS] 仍未返回时，接线层改走
     * 缓存 / 降级路径，经本构造器产出结构完整的空壳气泡：不返回 null、不抛异常到 UI；
     * 超时边界以 [FallbackState.TIMEOUT] 显式标记，可由纯 JVM 单测直接构造断言。
     */
    fun timeout(name: String, category: ArtifactCategory?): BubbleContent =
        notFound(name, category, FallbackState.TIMEOUT)
}
