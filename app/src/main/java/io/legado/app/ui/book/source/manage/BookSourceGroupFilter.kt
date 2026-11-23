package io.legado.app.ui.book.source.manage

/**
 * 判断筛选词是否指向一个已不存在的分组。
 *
 * 判定规则：仅当 [filter] 以 `group:` 开头、且冒号之后的分组名不在 [groups] 中时返回 true；
 * 其余情况（null、普通关键词、前缀存在但分组名仍在集合内）一律返回 false。
 *
 * 纯函数，不持有状态；分组删除后的兜底回落由调用方据此执行。
 */
fun isMissingBookSourceGroupFilter(filter: String?, groups: Set<String>): Boolean {
    if (filter == null) return false
    if (!filter.startsWith("group:")) return false
    val groupName = filter.removePrefix("group:")
    return groupName !in groups
}
