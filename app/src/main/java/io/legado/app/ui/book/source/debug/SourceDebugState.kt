package io.legado.app.ui.book.source.debug

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ExploreKind

/**
 * 落文 · 书源调试域纯状态模型（Compose 状态提升；不含 View / Adapter / Binding）。
 *
 * 调试分项：搜索(10) / 详情(20) / 目录(30) / 正文(40) 原始响应单独留存，
 * 其余进度与失败原因进入日志流；state 取 -1 / 1000 表示本轮调试结束。
 */
data class DebugLog(
    val state: Int,
    val message: String,
)

data class SourceDebugUiState(
    val source: BookSource? = null,
    val logs: List<DebugLog> = emptyList(),
    val loading: Boolean = false,
    val helpOpen: Boolean = true,
    val keyword: String = "",
    val exploreKinds: List<ExploreKind> = emptyList(),
    val searchSrc: String? = null,
    val bookSrc: String? = null,
    val tocSrc: String? = null,
    val contentSrc: String? = null,
    val error: String? = null,
) {
    /** 当前选中的发现项（默认第一项；ERROR 开头表示发现解析失败）。 */
    val activeExplore: ExploreKind? get() = exploreKinds.firstOrNull()
}
