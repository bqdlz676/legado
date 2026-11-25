package io.legado.app.ui.main.search

import io.legado.app.data.entities.SearchKeyword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 热门词双路流接线单测（ViewModel/Flow 层端到端断言）。
 *
 * 与 [SearchHotResolverTest] 的纯函数级近似不同：这里经 [SearchHotBinder] 的真实收集
 * 循环与 publishHot 回填路径，驱动注入的 fake 流（时间序 / 频次序两路），直接断言
 * `uiState.hot` / `uiState.hotSource`。纯 JVM：作用域与调度器由测试注入，不碰
 * `viewModelScope`、`Dispatchers.Main` 与任何 Android framework。
 *
 * 覆盖验收报告判据 6 必做后续三条：
 * 1. 有历史 → hot 来自真实日志档（USAGE / HISTORY），不回落精选兜底；
 * 2. 双空 → 落 SEED 档且 `hotSource` 标出是兜底（不是热搜）；
 * 3. 清空历史 → 流重发后重新计算，回落到 SEED 兜底且热门行非空。
 */
class SearchHotBinderTest {

    private fun keyword(word: String, usage: Int = 1, lastUseTime: Long = 0L) =
        SearchKeyword(word = word, usage = usage, lastUseTime = lastUseTime)

    @Test
    fun `history present publishes hot from real log source not seed`() = runBlocking {
        val state = MutableStateFlow(SearchUiState())
        val timeFlow = MutableStateFlow(listOf(keyword("春秋")))
        val usageFlow = MutableStateFlow(listOf(keyword("史记", usage = 3), keyword("左传", usage = 2)))

        val binder = SearchHotBinder(
            uiState = state,
            timeFlow = timeFlow,
            usageFlow = usageFlow,
            scope = this,
            dispatcher = Dispatchers.Unconfined,
        )
        binder.start()

        // 等频次序流也送达后稳定在 USAGE：时间流先到的中间帧可能是 HISTORY 档，
        // 谓词不能只看 hot 非空，否则会在中间帧提前命中。
        val settled = state.first { it.hotSource == SearchHotSource.USAGE }
        // 有真实日志时热门行必须来自本机频次档，不允许落到 SEED / EMPTY。
        assertEquals(SearchHotSource.USAGE, settled.hotSource)
        assertEquals(listOf("史记", "左传"), settled.hot)
        // 时间序流同时回填历史行，验证历史行补丁路径未被热门回填覆盖。
        assertEquals(listOf("春秋"), settled.history)
    }

    @Test
    fun `both flows empty fall back to seed marked as fallback not trending`() = runBlocking {
        val state = MutableStateFlow(SearchUiState())
        val timeFlow = MutableStateFlow<List<SearchKeyword>>(emptyList())
        val usageFlow = MutableStateFlow<List<SearchKeyword>>(emptyList())

        val binder = SearchHotBinder(
            uiState = state,
            timeFlow = timeFlow,
            usageFlow = usageFlow,
            scope = this,
            dispatcher = Dispatchers.Unconfined,
        )
        binder.start()

        val settled = state.first { it.hotSource == SearchHotSource.SEED }
        assertTrue("双空时精选兜底词不允许为空面板", settled.hot.isNotEmpty())
        assertEquals(SearchHotResolver.SEED_KEYS.take(SearchTokens.HOT_LIMIT), settled.hot)
    }

    @Test
    fun `clearing history recomputes and falls back to seed`() = runBlocking {
        val state = MutableStateFlow(SearchUiState())
        val timeFlow = MutableStateFlow(listOf(keyword("春秋")))
        val usageFlow = MutableStateFlow(listOf(keyword("史记", usage = 3), keyword("左传", usage = 2)))

        val binder = SearchHotBinder(
            uiState = state,
            timeFlow = timeFlow,
            usageFlow = usageFlow,
            scope = this,
            dispatcher = Dispatchers.Unconfined,
        )
        binder.start()

        val before = state.first { it.hotSource == SearchHotSource.USAGE }
        assertTrue("清空前热门行应来自真实日志档", before.hot.isNotEmpty())

        // 等价于 DAO deleteAll() 后两路 Room 流重发空快照。
        timeFlow.value = emptyList()
        usageFlow.value = emptyList()

        val after = state.first { it.hotSource == SearchHotSource.SEED }
        assertTrue("清空回落后精选兜底词不允许为空面板", after.hot.isNotEmpty())
        assertEquals(SearchHotResolver.SEED_KEYS.take(SearchTokens.HOT_LIMIT), after.hot)
    }
}
