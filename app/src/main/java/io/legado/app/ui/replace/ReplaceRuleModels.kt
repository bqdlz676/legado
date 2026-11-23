package io.legado.app.ui.replace

import io.legado.app.data.entities.ReplaceRule

/**
 * 落文 · 替换规则列表 UI 模型（实体展示镜像，纯数据）。
 */
data class ReplaceRuleUi(
    val id: Long,
    val name: String,
    val group: String?,
    val pattern: String,
    val replacement: String,
    val scope: String?,
    val scopeTitle: Boolean,
    val scopeSource: Boolean,
    val scopeContent: Boolean,
    val excludeScope: String?,
    val isEnabled: Boolean,
    val isRegex: Boolean,
    val timeoutMillisecond: Long,
    val order: Int,
) {
    constructor(rule: ReplaceRule) : this(
        id = rule.id,
        name = rule.name,
        group = rule.group,
        pattern = rule.pattern,
        replacement = rule.replacement,
        scope = rule.scope,
        scopeTitle = rule.scopeTitle,
        scopeSource = rule.scopeSource,
        scopeContent = rule.scopeContent,
        excludeScope = rule.excludeScope,
        isEnabled = rule.isEnabled,
        isRegex = rule.isRegex,
        timeoutMillisecond = rule.timeoutMillisecond,
        order = rule.order,
    )

    fun toEntity(): ReplaceRule = ReplaceRule(
        id = id,
        name = name,
        group = group,
        pattern = pattern,
        replacement = replacement,
        scope = scope,
        scopeTitle = scopeTitle,
        scopeSource = scopeSource,
        scopeContent = scopeContent,
        excludeScope = excludeScope,
        isEnabled = isEnabled,
        isRegex = isRegex,
        timeoutMillisecond = timeoutMillisecond,
        order = order,
    )
}

/** 列表筛选口径（与旧 SearchView 查询分支一一对应）。 */
sealed class ReplaceFilter {
    object All : ReplaceFilter()
    object Enabled : ReplaceFilter()
    object Disabled : ReplaceFilter()
    object NoGroup : ReplaceFilter()
    data class Group(val name: String) : ReplaceFilter()
    data class Text(val key: String) : ReplaceFilter()
}

data class ReplaceRuleListState(
    val rules: List<ReplaceRuleUi> = emptyList(),
    val groups: List<String> = emptyList(),
    val query: String = "",
    val activeFilter: ReplaceFilter = ReplaceFilter.All,
    val selection: Set<Long> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
    val confirmDelete: ReplaceRuleUi? = null,
    val groupManageOpen: Boolean = false,
    val importUrlOpen: Boolean = false,
    val manualReplace: Boolean = false,
    val helpText: String? = null,
) {
    val selectionCount: Int get() = selection.size
    val selectionMode: Boolean get() = selection.isNotEmpty()
}
