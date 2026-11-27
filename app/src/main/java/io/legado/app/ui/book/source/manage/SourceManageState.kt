package io.legado.app.ui.book.source.manage

import io.legado.app.data.entities.BookSourceCheckState
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.components.ToastMessage

/**
 * 落文 · 书源管理域纯状态模型（Compose 状态提升到 ViewModel，UI 只渲染）。
 *
 * 口径：本文件不引任何 View / XML / Adapter；排序、筛选、分组、多选全部是纯数据，
 * 便于 JVM 单测直接构造断言。旧 [BookSourceSort] 枚举收口到这里，字段语义与旧管理页一一对应。
 */

/** 排序维度，与旧管理页菜单逐项对齐（手动 / 权重 / 名称 / 地址 / 更新时间 / 响应 / 启用态）。 */
enum class BookSourceSort {
    Default, Name, Url, Weight, Update, Enable, Respond
}

/** 顶部预设筛选（旧页搜索框快捷词 + 分组菜单）。token 为空表示不按预设过滤。 */
enum class SourcePreset(val token: String?) {
    ALL(null),
    ENABLED("enabled"),
    DISABLED("disabled"),
    LOGIN("need_login"),
    NO_GROUP("no_group"),
    EXPLORE_ENABLED("enabled_explore"),
    EXPLORE_DISABLED("disabled_explore"),
    /** 自定义分组：query 形如 `group:分组名`，token 存分组名。 */
    GROUP(null),
}

/** 校验状态筛选项：下标与旧页下拉框顺序一致，0 = 全部。 */
val checkStatusFilters: List<String> = listOf(
    "",
    BookSourceCheckState.NEEDS_CHECK,
    BookSourceCheckState.PASSED,
    BookSourceCheckState.FAILED,
    BookSourceCheckState.UNCHECKABLE,
)

/** 半屏 Sheet 目标：旧页的弹窗 / 下拉菜单全部收口成 Compose Sheet，避免再建 XML Dialog。 */
sealed interface ManageSheet {
    data object None : ManageSheet
    data object MainOptions : ManageSheet
    data object SortOptions : ManageSheet
    data object GroupFilters : ManageSheet
    data object GroupManage : ManageSheet
    data object ImportMenu : ManageSheet
    data object OnlineImport : ManageSheet
    data object CheckSource : ManageSheet
    data class ItemActions(val sourceUrl: String) : ManageSheet
    data class SelectionActions(val selection: List<BookSourcePart>) : ManageSheet
    data class AddToGroup(val selection: List<BookSourcePart>) : ManageSheet
    data class RemoveFromGroup(val selection: List<BookSourcePart>) : ManageSheet
    data class ExportResult(
        val fileName: String,
        val uriText: String,
        val isRemote: Boolean,
        val summary: String = "",
    ) : ManageSheet
}

/**
 * 管理页整页状态。
 *
 * @param query 搜索框原文（保留 `group:` 前缀等旧语义）
 * @param preset 由 query 解析出的预设筛选
 * @param groupName 预设为分组时的分组名
 * @param sort 当前排序维度
 * @param ascending 升降序
 * @param groupByDomain 是否按域名归组（旧 menu_group_sources_by_domain）
 * @param showCheckStatus 是否显示校验状态（偏好持久化）
 * @param showBookshelfCount 是否显示书架引用数（偏好持久化）
 * @param blockSourceNavigation 是否拦截书源跳转（偏好持久化；默认 false 与 AppConfig 偏好默认值对齐）
 * @param checkStatusFilter 校验状态筛选项下标（[checkStatusFilters]）
 * @param items 当前可见书源（已过滤 + 已排序）
 * @param groups 全部分组（分组菜单 / 分组管理用）
 * @param bookshelfCounts 书架引用数 Map（书源 url → 册数）
 * @param selected 多选集合（按 url 去重，与旧 adapter.selected 语义一致）
 * @param loading 首屏 / 刷新加载中
 * @param offline 网络不可用兜底标记（列表仍展示本地库数据）
 * @param error 最近一次失败文案；null = 无错误
 * @param toasts 轻提示队列
 * @param sheet 当前打开的半屏 Sheet
 * @param checking 是否有批量校验会话在跑（旧 Debug.isChecking 镜像）
 * @param checkMessage 校验进行中的进度文案（旧 Snackbar 文案）
 * @param checkCancelable 当前校验会话是否可取消
 */
data class SourceManageUiState(
    val query: String = "",
    val preset: SourcePreset = SourcePreset.ALL,
    val groupName: String? = null,
    val sort: BookSourceSort = BookSourceSort.Default,
    val ascending: Boolean = true,
    val groupByDomain: Boolean = false,
    val showCheckStatus: Boolean = false,
    val showBookshelfCount: Boolean = false,
    // 偏好开关镜像：VM init 从 AppConfig 读入，Compose 只渲染 state，不在组合期碰 AppConfig。
    val blockSourceNavigation: Boolean = false,
    val checkStatusFilter: Int = 0,
    val items: List<BookSourcePart> = emptyList(),
    val groups: List<String> = emptyList(),
    val bookshelfCounts: Map<String, Int> = emptyMap(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val offline: Boolean = false,
    val error: String? = null,
    val toasts: List<ToastMessage> = emptyList(),
    val sheet: ManageSheet = ManageSheet.None,
    val checking: Boolean = false,
    val checkMessage: String? = null,
    val checkCancelable: Boolean = false,
) {
    val selection: List<BookSourcePart>
        get() = items.filter { it.bookSourceUrl in selected }

    val inSelectionMode: Boolean
        get() = selected.isNotEmpty()

    /** 列表是否允许拖拽排序：仅手动排序且未按域名归组（旧 adapter 同款约束）。 */
    val canDrag: Boolean
        get() = sort == BookSourceSort.Default && !groupByDomain
}
