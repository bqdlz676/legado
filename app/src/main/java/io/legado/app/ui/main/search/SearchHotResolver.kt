package io.legado.app.ui.main.search

/**
 * 落文 · 搜索热门词条来源标记（三档回落 + 防御空态）
 *
 * 三档回落来源在 UI 上以分区标题 / 前置点色区分：回落词不冒充远程热搜，
 * 用户能分辨当前看到的是「本机高频」「最近搜过」还是「内置精选」。
 */
enum class SearchHotSource {
    /** 本机高频搜索词（按 usage 降序）：真实本地搜索日志，离线可用。 */
    USAGE,

    /** 无高频词时回落：最近搜索历史（按最后使用时间）回流到热门行。 */
    HISTORY,

    /** 历史也为空时回落：随包精选典籍词（离线内置，不对外声称实时热搜）。 */
    SEED,

    /** 三档皆空（防御态，正常不可达）：面板回落引导文案，不留白。 */
    EMPTY,
}

/** 一条已解析的热门词条集合：[keys] 已清洗去重截断，[source] 标明来源档位。 */
data class SearchHot(
    val keys: List<String>,
    val source: SearchHotSource,
)

/**
 * 落文 · 热门词条分层解析（纯 JVM，可单测）
 *
 * 数据优先级（高 → 低）：
 * 1. [USAGE] 本机搜索频次榜（search_keywords.usage 降序）——真实本地数据；
 * 2. [HISTORY] 最近搜索历史回流——本机时间序日志；
 * 3. [SEED] 随包精选典籍词——离线内置兜底；
 * 4. [EMPTY] 防御空态，UI 回落引导句。
 *
 * 清洗 / 去重 / 截断统一走 [SearchHistory.hot]；本对象不碰网络、不碰 DAO，
 * 入参为 null / 空时安全回落，不抛异常。
 */
object SearchHotResolver {

    /**
     * 内置精选词：逐词对应随包 `assets/books/` 内典籍（论语集注 / 诗经 / 楚辞补注 /
     * 庄子集释 / 春秋左传 / 孟子 / 老子道德经王弼注 / 尔雅 / 尚书 / 礼记），
     * 离线可用；仅作历史全空时的末档兜底，不声称实时热门。
     */
    val SEED_KEYS: List<String> = listOf(
        "论语", "诗经", "楚辞", "庄子", "左传",
        "孟子", "老子", "尔雅", "尚书", "礼记",
    )

    /**
     * 解析热门词条。
     *
     * @param usage 本机高频词（usage 降序，DAO 原始字串，允许 null）
     * @param history 最近搜索历史（时间序，允许 null）
     * @param limit 热门行展示上限，默认 [SearchTokens.HOT_LIMIT]
     */
    fun resolve(
        usage: List<String>?,
        history: List<String>?,
        limit: Int = SearchTokens.HOT_LIMIT,
    ): SearchHot {
        val historyKeys = SearchHistory.clean(history)

        // 第一档：本机高频词；剔除已在历史行展示的词条，避免同一词在两行重复出现。
        val usageKeys = SearchHistory.hot(usage, limit).filter { candidate ->
            historyKeys.none { history ->
                SearchMatcher.normalize(history) == SearchMatcher.normalize(candidate)
            }
        }
        if (usageKeys.isNotEmpty()) {
            return SearchHot(usageKeys, SearchHotSource.USAGE)
        }

        // 第二档：最近搜索历史回流（历史本身已清洗，这里再按热门上限截断）。
        if (historyKeys.isNotEmpty()) {
            return SearchHot(historyKeys.take(limit.coerceAtLeast(0)), SearchHotSource.HISTORY)
        }

        // 第三档：随包精选词；清洗后仍为空（防御）→ EMPTY，面板回落引导句。
        val seedKeys = SearchHistory.hot(SEED_KEYS, limit)
        return if (seedKeys.isNotEmpty()) {
            SearchHot(seedKeys, SearchHotSource.SEED)
        } else {
            SearchHot(emptyList(), SearchHotSource.EMPTY)
        }
    }
}
