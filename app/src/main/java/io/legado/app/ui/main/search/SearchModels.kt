package io.legado.app.ui.main.search

/**
 * 落文 · 搜索页 UI 模型（状态提升，不直接碰 DAO / assets）
 *
 * 主控 merge 时把数据层（书库 / 章节正文 / 历史关键词）映射成这里的模型即可，
 * 本子域只负责排版、高亮与动效。任何字段为空都由条目与空态兜底，不抛异常、不留白。
 *
 * 切片-13：纯文本列表，不配图；命中文字片段用朱砂高亮。
 */

/** 命中字段类型：书名 / 作者 / 正文片段。 */
enum class SearchField {
    BOOK_TITLE,
    AUTHOR,
    CONTENT;

    companion object {
        /** 未知来源兜底为书名命中，不允许 null 穿透。 */
        fun safeOf(ordinal: Int?): SearchField =
            entries.getOrElse(ordinal ?: -1) { BOOK_TITLE }
    }
}

/**
 * 一条搜索结果（纯文本，无封面图）。
 *
 * @param id 稳定唯一标识；未知由调用方按 title+author 生成，禁止空 id 穿透。
 * @param title 书名 / 词条名
 * @param author 作者；无作者留空，条目按空兜底不渲染副行
 * @param snippet 正文命中片段；无片段留空
 * @param field 命中主字段，用于排序与副文提示
 */
data class SearchResultItem(
    val id: String,
    val title: String,
    val author: String = "",
    val snippet: String = "",
    val field: SearchField = SearchField.BOOK_TITLE,
)

/**
 * 高亮片段：[text] 为原文切片，[highlighted] 为 true 时朱砂着色。
 * 纯数据，Compose 逐条上色；查询为空时整段为普通墨色。
 */
data class MatchSegment(
    val text: String,
    val highlighted: Boolean,
)

/**
 * 搜索页整体加载态（七态 + 离线兜底）。
 *
 * 默认 / 悬浮 / 按压 / 禁用 / 加载 / 空状态 / 离线：按压悬浮在条目手势层处理；
 * 这里管整页的空闲、加载、内容、空、错、离线。
 */
sealed interface SearchLoadState {

    /** 查询为空 / 未提交：展示历史 + 热门，输入框下方引导小字兜底。 */
    object Idle : SearchLoadState

    /** 检索中：三个云灰小点依次亮灭 600ms 循环，不放菊花。 */
    object Loading : SearchLoadState

    /** 有结果。 */
    data class Content(val results: List<SearchResultItem>) : SearchLoadState

    /** 有查询词但无结果：给可重试的空态，不空白不崩溃。 */
    data class Empty(val query: String) : SearchLoadState

    /** 检索失败：保留缓存结果（可为空），带可重试入口。 */
    data class Error(
        val message: String,
        val cached: List<SearchResultItem> = emptyList(),
    ) : SearchLoadState

    /** 网络断开：保留本地缓存，展示离线兜底文案，可重试。 */
    data class Offline(val cached: List<SearchResultItem> = emptyList()) : SearchLoadState
}

/**
 * 列表分页（加载更多）状态：到底触发 / 在途 / 失败可重试 / 收口。
 *
 * 与整页 [SearchLoadState] 正交：整页态管首屏，分页态只管列表底部的下一页请求。
 */
sealed interface SearchPagingState {

    /** 空闲：可接受下一次到底触发。 */
    object Idle : SearchPagingState

    /** 下一页在途：并发保护，重复滚动到底不重复请求。 */
    object LoadingMore : SearchPagingState

    /** 上一页失败：保留现有列表，底部脚注给可重试入口。 */
    data class LoadMoreError(val message: String) : SearchPagingState

    /** 已收口：远端告知无更多结果，到底不再请求（不许无限刷）。 */
    object Ended : SearchPagingState
}

/**
 * 整页 UI 状态（纯数据，归约器产出，Compose 只读）。
 *
 * @param query 输入框当前文本（未 trim 原文；匹配前由 matcher 归一化）
 * @param focused 输入框是否聚焦（聚焦 spring 浮起动画依据）
 * @param loadState 整页加载态
 * @param history 搜索历史（最近在前）
 * @param hot 热门词条
 * @param hotSource 热门词条来源档位（高频 / 历史回流 / 内置精选 / 防御空态），UI 据此区分标题与点色
 * @param lastResults 上一次成功结果；离线 / 失败时作为缓存兜底保留，不丢失
 * @param hasMore 远端是否还有下一页；false 后到底不再请求
 * @param paging 加载更多分页态（空闲 / 在途 / 失败可重试 / 收口）
 */
data class SearchUiState(
    val query: String = "",
    val focused: Boolean = false,
    val loadState: SearchLoadState = SearchLoadState.Idle,
    val history: List<String> = emptyList(),
    val hot: List<String> = emptyList(),
    val hotSource: SearchHotSource = SearchHotSource.EMPTY,
    val lastResults: List<SearchResultItem> = emptyList(),
    val hasMore: Boolean = true,
    val paging: SearchPagingState = SearchPagingState.Idle,
) {
    /** 当前应展示在列表区的结果：Content 取本次结果，离线 / 失败取缓存，其余为空。 */
    val visibleResults: List<SearchResultItem>
        get() = when (val s = loadState) {
            is SearchLoadState.Content -> s.results
            is SearchLoadState.Error -> s.cached
            is SearchLoadState.Offline -> s.cached
            else -> emptyList()
        }

    /** 是否处于需要阻止条目点击的兜底态（离线 / 失败 / 加载中）。 */
    val isBlocking: Boolean
        get() = when (loadState) {
            is SearchLoadState.Loading,
            is SearchLoadState.Error,
            is SearchLoadState.Offline -> true
            else -> false
        }

    /**
     * 是否允许触发下一页：
     * 必须有内容展示、远端还有更多、且当前没有在途翻页请求；
     * 分页失败态（LoadMoreError）允许重试，不在此拦阻。
     */
    val canLoadMore: Boolean
        get() = loadState is SearchLoadState.Content &&
            hasMore &&
            paging !is SearchPagingState.LoadingMore
}

/**
 * 翻页触发策略（纯 JVM，可单测）：去抖窗口判定，不碰协程 / 网络。
 */
internal object SearchPagingPolicy {

    /**
     * 距上一次触发不足 [debounceMs] 视为连发，抑制本次触发。
     * [lastAtMillis] 为 0（从未触发）时不做去抖。
     */
    fun withinDebounce(lastAtMillis: Long, nowMillis: Long, debounceMs: Long): Boolean =
        lastAtMillis > 0 && nowMillis - lastAtMillis < debounceMs
}
