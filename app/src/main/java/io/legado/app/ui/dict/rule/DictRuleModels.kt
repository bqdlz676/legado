package io.legado.app.ui.dict.rule

import io.legado.app.data.entities.DictRule

/**
 * 落文 · 词典规则列表 UI 模型（纯数据，不碰 Room 实体之外的 View 类型）。
 *
 * [DictRuleUi] 是实体的展示镜像；列表、选择、编辑弹窗全部只读这份快照，
 * 写操作由 [DictRuleListViewModel] 翻译回 DAO 调用。
 */
data class DictRuleUi(
    val name: String,
    val urlRule: String,
    val showRule: String,
    val enabled: Boolean,
    val sortNumber: Int,
) {
    constructor(rule: DictRule) : this(
        name = rule.name,
        urlRule = rule.urlRule,
        showRule = rule.showRule,
        enabled = rule.enabled,
        sortNumber = rule.sortNumber,
    )

    fun toEntity(): DictRule = DictRule(
        name = name,
        urlRule = urlRule,
        showRule = showRule,
        enabled = enabled,
        sortNumber = sortNumber,
    )
}

/** 列表页整页状态：加载 / 内容 / 空 / 错误四态 + 选择态 + 弹窗路由。 */
data class DictRuleListState(
    val rules: List<DictRuleUi> = emptyList(),
    val selection: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
    /** 正在编辑的规则；null 表示编辑 Sheet 关闭。 */
    val editTarget: DictRuleUi? = null,
    /** 新建标记：[editTarget] 非空且 [creating] 为 true 时走插入分支。 */
    val creating: Boolean = false,
    /** 待确认删除的规则（单条）。 */
    val confirmDelete: DictRuleUi? = null,
    /** 帮助文档正文（资产 md 读出）；null 表示帮助 Sheet 关闭。 */
    val helpText: String? = null,
) {
    val selectionCount: Int get() = selection.size
    val selectionMode: Boolean get() = selection.isNotEmpty()
}
