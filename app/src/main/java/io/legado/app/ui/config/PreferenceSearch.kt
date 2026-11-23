package io.legado.app.ui.config

/**
 * 落文 · 设置项搜索匹配（纯 JVM 顶层函数，无 Android / Compose 依赖，可直接被单测断言）。
 *
 * 规格（`PreferenceSearchTest` 5 条断言）：
 * - 大小写不敏感：query 与标题 / 说明 / 关键词统一转小写后做子串包含；
 * - query 去首尾空白后为空 → 一律 false（纯空格、空串不命中任何行）；
 * - [title] / [summary] / [keywords] 任一字段子串包含 query 即 true；
 * - [summary] 为 null 时跳过该字段；keywords 元素为空白 / null 防御性跳过。
 *
 * 注意：本函数只回答「单行是否命中」，分组裁剪、空结果兜底由渲染层（`settings/SettingList`）负责。
 */
fun configPreferenceMatches(
    query: String,
    title: String,
    summary: String?,
    keywords: List<String>,
): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return false

    fun fieldHit(field: String?): Boolean =
        !field.isNullOrBlank() && field.lowercase().contains(normalizedQuery)

    if (fieldHit(title)) return true
    if (fieldHit(summary)) return true
    return keywords.any { fieldHit(it) }
}
