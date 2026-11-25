package io.legado.app.ui.main.search

import io.legado.app.data.entities.SearchKeyword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 覆盖验收报告判据 6 必做后续三条 + 验收 N3 单条删除两情形：
 * 1. 有历史 → hot 来自真实日志档（USAGE / HISTORY），不回落精选兜底；
 * 2. 双空 → 落 SEED 档且 `hotSource` 标出是兜底（不是热搜）；
 * 3. 清空历史 → 流重发后重新计算，回落到 SEED 兜底且热门行非空；
 * 4. 单条删除 · 历史档词：删后流重发「少一条」快照，该词从热门行同步剔除；
 * 5. 单条删除 · 与种子兜底重合的词：删空后档位必须记为 SEED 兜底，不冒充历史 / 高频；
 * 6. 浮层关闭：close() 取消两路收集，流更新不再回填；重建后重开收集恢复（D3 收口）。
 *
 * 超时约定（验收 N4 收口）：仓内无 `kotlinx-coroutines-test`，未用虚拟时间调度器；
 * 每一处 `state.first { 谓词 }` 都包 [withTimeout]（5_000ms，与仓内
 * `ChapterDownloadStateTest` / `HttpTtsDownloadTest` 既有超时口径一致），收集器缺席
 * 或谓词永不命中时快速报红，而不是 `runBlocking` 内挂死。5s 为 CI 慢机器余量：
 * 本模型是 `Unconfined` 单线程 + `MutableStateFlow` 同步回放，收敛为微秒级，5s 留有
 * 三个数量级余量；超时只兜底「收集器没接上」这类确定性缺陷，不掩盖真实断言失败。
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
        val settled = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.USAGE } }
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

        val settled = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.SEED } }
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

        val before = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.USAGE } }
        assertTrue("清空前热门行应来自真实日志档", before.hot.isNotEmpty())

        // 等价于 DAO deleteAll() 后两路 Room 流重发空快照。
        timeFlow.value = emptyList()
        usageFlow.value = emptyList()

        val after = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.SEED } }
        assertTrue("清空回落后精选兜底词不允许为空面板", after.hot.isNotEmpty())
        assertEquals(SearchHotResolver.SEED_KEYS.take(SearchTokens.HOT_LIMIT), after.hot)
    }

    @Test
    fun `removing a history-only word drops it from the hot row`() = runBlocking {
        val state = MutableStateFlow(SearchUiState())
        // 情形一：被删词来自历史档。频次路为空 → 历史回流档（HISTORY）；
        // 「春秋」不在 SEED_KEYS 内，删后不得由兜底词表重新带出。
        val timeFlow = MutableStateFlow(listOf(keyword("春秋"), keyword("左传")))
        val usageFlow = MutableStateFlow<List<SearchKeyword>>(emptyList())

        val binder = SearchHotBinder(
            uiState = state,
            timeFlow = timeFlow,
            usageFlow = usageFlow,
            scope = this,
            dispatcher = Dispatchers.Unconfined,
        )
        binder.start()

        val before = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.HISTORY } }
        assertEquals(SearchHotSource.HISTORY, before.hotSource)
        assertEquals(listOf("春秋", "左传"), before.hot)
        assertEquals(listOf("春秋", "左传"), before.history)

        // 等价于 DAO delete(SearchKeyword("春秋")) 后时间序流重发「少了一条」的新快照。
        timeFlow.value = listOf(keyword("左传"))

        val after = withTimeout(5_000) {
            state.first { it.history == listOf("左传") && it.hotSource == SearchHotSource.HISTORY }
        }
        assertEquals(SearchHotSource.HISTORY, after.hotSource)
        assertEquals(listOf("左传"), after.hot)
        assertFalse("被删的历史档词不得残留在热门行", after.hot.contains("春秋"))
    }

    @Test
    fun `removing last history word overlapping seed keeps fallback source marker`() = runBlocking {
        val state = MutableStateFlow(SearchUiState())
        // 情形二：被删词与种子兜底重合。「论语」属 SEED_KEYS，先以历史档出现；
        // 删空后两路皆空，热门行回落到种子兜底，档位必须记为 SEED，不冒充历史 / 高频。
        val timeFlow = MutableStateFlow(listOf(keyword("论语")))
        val usageFlow = MutableStateFlow<List<SearchKeyword>>(emptyList())

        val binder = SearchHotBinder(
            uiState = state,
            timeFlow = timeFlow,
            usageFlow = usageFlow,
            scope = this,
            dispatcher = Dispatchers.Unconfined,
        )
        binder.start()

        val before = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.HISTORY } }
        assertEquals(SearchHotSource.HISTORY, before.hotSource)
        assertEquals(listOf("论语"), before.hot)

        // 等价于 DAO delete(SearchKeyword("论语")) 后历史流重发空快照，频次路本就为空。
        timeFlow.value = emptyList()

        val after = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.SEED } }
        assertEquals(SearchHotSource.SEED, after.hotSource)
        assertEquals(SearchHotResolver.SEED_KEYS.take(SearchTokens.HOT_LIMIT), after.hot)
        // 兜底词表仍含「论语」字样，但来源标记必须是 SEED 兜底，不能记成 HISTORY / USAGE。
        assertTrue("种子兜底词表应仍收录论语", after.hot.contains("论语"))
    }

    // 6. 浮层关闭释放收集（验收 D3 收口）：close() 取消两路收集协程，此后上游 DAO 流
    //    再发快照也不再回填 UI；重建 binder 重新 start 后收集恢复，流更新再次回填。
    @Test
    fun `close cancels both collectors and restart resumes collection`() = runBlocking {
        val state = MutableStateFlow(SearchUiState())
        val timeFlow = MutableStateFlow(listOf(keyword("春秋")))
        val usageFlow = MutableStateFlow(listOf(keyword("史记", usage = 3)))

        val binder = SearchHotBinder(
            uiState = state,
            timeFlow = timeFlow,
            usageFlow = usageFlow,
            scope = this,
            dispatcher = Dispatchers.Unconfined,
        )
        binder.start()
        val before = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.USAGE } }
        assertTrue("关闭前两路收集正常回填", before.history.isNotEmpty())

        // 关闭浮层：定向取消两路收集（不取消注入的测试作用域本身）。
        binder.close()
        val closedSnapshot = state.value
        timeFlow.value = emptyList()
        usageFlow.value = emptyList()
        // 确定性冻结断言（验收 N4 收口，替代原 delay(200) 时序余量）：测试注入
        // Dispatchers.Unconfined，收集协程在调用线程内联执行；close() 取消句柄后，
        // 若 Job 未被取消，流重发会在 setter 线程同步回放空快照并回填状态，下面的
        // 相等性断言立即变红；若已取消，状态冻结为关闭前快照，无需任何等待余量。
        assertEquals("关闭后流更新不得再回填 UI 状态", closedSnapshot, state.value)

        // 重开浮层：VM.attach 幂等分支重建 binder 重新收集，流更新再次回填。
        val restarted = SearchHotBinder(
            uiState = state,
            timeFlow = timeFlow,
            usageFlow = usageFlow,
            scope = this,
            dispatcher = Dispatchers.Unconfined,
        )
        restarted.start()
        timeFlow.value = listOf(keyword("左传"))
        val after = withTimeout(5_000) { state.first { it.history == listOf("左传") } }
        assertEquals("重开后收集恢复，历史行按新快照回填", listOf("左传"), after.history)
    }
}
