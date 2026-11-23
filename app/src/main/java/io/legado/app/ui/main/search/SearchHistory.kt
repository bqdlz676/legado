package io.legado.app.ui.main.search

/**
 * 落文 · 搜索历史 / 热门词条整理（纯 JVM，可单测）
 *
 * 历史最近在前、去重、去空白、截断到 [SearchTokens.HISTORY_LIMIT]；
 * 热门去空白去重后截断到 [SearchTokens.HOT_LIMIT]。空输入不写入历史；
 * 所有入参为 null / 空时安全回落空列表，不抛异常。
 */
object SearchHistory {

    /** 归一化历史词条；空白返回 null（不允许空白词条入库）。 */
    fun normalizeKey(raw: String?): String? {
        val key = SearchMatcher.normalize(raw ?: return null)
        return key.ifEmpty { null }
    }

    /** 清洗既有历史：去空白、去重、保序、截断。 */
    fun clean(keys: List<String>?, limit: Int = SearchTokens.HISTORY_LIMIT): List<String> {
        val cap = limit.coerceAtLeast(0)
        return keys.orEmpty()
            .mapNotNull { normalizeKey(it) }
            .distinct()
            .take(cap)
    }

    /** 写入一条历史：新词条置顶，重复词条前移，截断到上限。 */
    fun add(
        history: List<String>,
        key: String?,
        limit: Int = SearchTokens.HISTORY_LIMIT,
    ): List<String> {
        val normalized = normalizeKey(key) ?: return clean(history, limit)
        val rest = history.filter { SearchMatcher.normalize(it) != normalized }
        return (listOf(normalized) + rest).take(limit.coerceAtLeast(0))
    }

    /** 删除单条历史；大小写 / 空白归一化后比较。 */
    fun remove(history: List<String>, key: String): List<String> {
        val target = normalizeKey(key) ?: return history
        return history.filter { SearchMatcher.normalize(it) != target }
    }

    /** 清空历史。 */
    fun clear(): List<String> = emptyList()

    /** 热门词条：去空白、去重、保序、截断；入参为空回落空列表（面板按空态兜底）。 */
    fun hot(keys: List<String>?, limit: Int = SearchTokens.HOT_LIMIT): List<String> {
        val cap = limit.coerceAtLeast(0)
        return keys.orEmpty()
            .mapNotNull { normalizeKey(it) }
            .distinct()
            .take(cap)
    }
}
