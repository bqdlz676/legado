package io.legado.app.ui.main.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 热门词条分层解析单测：覆盖三档回落分支——
 * 1. 有本机高频词 → USAGE；
 * 2. 无高频词 → 回落最近搜索历史（HISTORY）；
 * 3. 历史也为空 → 回落内置精选词（SEED）。
 * 另覆盖：高频词与历史重复时下沉历史档、精选词自身去重且不超上限。
 */
class SearchHotResolverTest {

    @Test
    fun `usage keywords resolve as USAGE and drop history duplicates`() {
        val resolved = SearchHotResolver.resolve(
            usage = listOf("史记", "史记", " 左传 "),
            history = listOf("春秋"),
        )
        assertEquals(SearchHotSource.USAGE, resolved.source)
        assertEquals(listOf("史记", "左传"), resolved.keys)
    }

    @Test
    fun `empty usage falls back to recent history with HISTORY source`() {
        val resolved = SearchHotResolver.resolve(
            usage = emptyList(),
            history = listOf(" 史记 ", "左传", ""),
        )
        assertEquals(SearchHotSource.HISTORY, resolved.source)
        assertEquals(listOf("史记", "左传"), resolved.keys)
    }

    @Test
    fun `empty usage and history fall back to seed with SEED source`() {
        val resolved = SearchHotResolver.resolve(usage = null, history = emptyList())
        assertEquals(SearchHotSource.SEED, resolved.source)
        assertTrue("精选兜底词不允许为空面板", resolved.keys.isNotEmpty())
        assertEquals(SearchTokens.HOT_LIMIT, resolved.keys.size)
        assertEquals(SearchHotResolver.SEED_KEYS.take(SearchTokens.HOT_LIMIT), resolved.keys)
    }

    @Test
    fun `usage fully duplicated by history falls through to HISTORY`() {
        val resolved = SearchHotResolver.resolve(
            usage = listOf("史记"),
            history = listOf("史记", "左传"),
        )
        assertEquals(SearchHotSource.HISTORY, resolved.source)
        assertEquals(listOf("史记", "左传"), resolved.keys)
    }

    @Test
    fun `seed keys are deduped within hot limit and map to bundled classics`() {
        assertEquals(SearchHotResolver.SEED_KEYS.distinct(), SearchHotResolver.SEED_KEYS)
        assertTrue(SearchHotResolver.SEED_KEYS.size <= SearchTokens.HOT_LIMIT)
        val cleaned = SearchHistory.hot(SearchHotResolver.SEED_KEYS)
        assertEquals(SearchHotResolver.SEED_KEYS, cleaned)
    }

    @Test
    fun `blank only usage and history resolve to non empty seed`() {
        val resolved = SearchHotResolver.resolve(
            usage = listOf("  ", ""),
            history = listOf("   "),
        )
        assertEquals(SearchHotSource.SEED, resolved.source)
        assertTrue(resolved.keys.isNotEmpty())
    }
}
