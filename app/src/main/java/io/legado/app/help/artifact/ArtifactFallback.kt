package io.legado.app.help.artifact

import io.legado.app.help.bubble.contract.Action
import io.legado.app.help.bubble.contract.BubbleContent
import io.legado.app.help.bubble.contract.Kind
import io.legado.app.help.bubble.contract.MISSING_SOURCE

/**
 * D16 兜底边界四态（锚点卡 §四；本单在接口层给出可执行回退，不造假数据）。
 *
 * - [NO_DATA]            空数据：库内无此条目，返回结构完整的空壳气泡。
 * - [CACHE_MISS_OFFLINE] 断网：本地缓存未命中，标记离线缓存未命中态，不阻塞气泡壳。
 * - [YEAR_MISSING]       缺年份：年代锚缺失，不做年代过滤（不过滤 ≠ 猜年代）。
 * - [AMBIGUOUS]          同名消歧失败：返回候选列表 + 默认候选下标，交半屏 Sheet 由用户点选。
 */
enum class FallbackState {
    NO_DATA,
    CACHE_MISS_OFFLINE,
    YEAR_MISSING,
    AMBIGUOUS,
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
     * @param state    兜底态，供接线层区分空数据 / 离线缓存未命中 / 缺年份。
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
     * @param candidates   候选裸串列表；无候选时退化为 [FallbackState.NO_DATA] 空壳。
     * @param defaultIndex 默认候选下标；null 表示无默认项，须由用户点选。
     *                     逐项 isDefault 由接线层 `Candidate` 契约携带，气泡层只保顺序与下标。
     */
    fun ambiguous(
        name: String,
        category: ArtifactCategory?,
        candidates: List<String>,
        defaultIndex: Int?,
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
}
