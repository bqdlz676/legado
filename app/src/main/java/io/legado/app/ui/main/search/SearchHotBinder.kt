package io.legado.app.ui.main.search

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.SearchKeyword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * 落文 · 热门词双路流接线器（流来源 / 作用域 / 调度器均可注入，纯 JVM 可单测）
 *
 * 职责：订阅本机搜索日志的时间序流（历史行）与频次序流（热门行），任一刷新即按
 * [SearchHotResolver] 重算热门档，原子回填到 [uiState]。本类是热门接线的唯一出口；
 * [InkSearchViewModel] 只负责用 appDb 真实流与 `viewModelScope` 构造本类，不在
 * ViewModel 内重复收集 / 回填逻辑。
 *
 * 线程与缓存说明（验收观察项 A 落地）：
 * 两路收集器运行在各自协程上。旧实现把 `historyKeys` / `usageKeys` 存为普通跨协程
 * 共享 `var`：写无同步、读无可见性保证，并发「新搜一词 + 清空历史」时另一路可能读到
 * 尚未发布的陈旧缓存，且普通 var 的写入不保证对另一线程立即可见。这里改为
 * [AtomicReference] 持有不可变快照，单路更新走 `updateAndGet` 原子合并，重算前一次性
 * 取快照；最坏情况仍是一帧级陈旧（下一次流重发即收敛，状态写入本身走
 * [MutableStateFlow.update] 原子更新），但消除了跨协程可见性窗口与交错写坏列表的风险。
 *
 * @param uiState 页面状态流；热门档 / 历史行回填走其原子 [MutableStateFlow.update]
 * @param timeFlow 时间序历史流（DAO `flowByTime`，最近在前）
 * @param usageFlow 频次序热门流（DAO `flowByUsage`，usage 降序）
 * @param scope 收集协程作用域；生产传 `viewModelScope`，测试传注入的测试作用域
 * @param dispatcher 收集协程调度器；生产默认 [Dispatchers.IO]，与原 `execute` 的 IO 执行上下文一致
 * @param onError 上游异常回调；生产记 [AppLog]，测试可覆盖为断言不抛
 */
internal class SearchHotBinder(
    private val uiState: MutableStateFlow<SearchUiState>,
    private val timeFlow: Flow<List<SearchKeyword>>,
    private val usageFlow: Flow<List<SearchKeyword>>,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineContext = Dispatchers.IO,
    private val onError: (Throwable) -> Unit = {
        AppLog.put("搜索热门词流订阅失败\n${it.localizedMessage}", it)
    },
) {

    /** 双路词表不可变快照；[AtomicReference] 保证跨协程可见性与原子合并，不暴露可写引用。 */
    private val snapshot = AtomicReference(HotSnapshot(emptyList(), emptyList()))

    /** 启动两路收集器；重复调用会再起两路收集，生产侧仅在 `attach()` 首次初始化时调用一次。 */
    fun start() {
        scope.launch(dispatcher) {
            timeFlow
                .catch { onError(it) }
                .collect { keywords ->
                    val history = SearchHistory.clean(keywords.map { it.word })
                    snapshot.updateAndGet { old -> old.copy(history = history) }
                    publishHot { it.copy(history = history) }
                }
        }
        scope.launch(dispatcher) {
            usageFlow
                .catch { onError(it) }
                .collect { keywords ->
                    val usage = keywords.map { it.word }
                    snapshot.updateAndGet { old -> old.copy(usage = usage) }
                    publishHot { it }
                }
        }
    }

    /**
     * 按当前双路快照重算热门档并回填状态。
     * [statePatch] 允许历史流同时更新历史行；频次流只刷热门行，不动历史。
     */
    private fun publishHot(statePatch: (SearchUiState) -> SearchUiState) {
        val current = snapshot.get()
        val hot = SearchHotResolver.resolve(current.usage, current.history)
        uiState.update { state ->
            statePatch(state).copy(hot = hot.keys, hotSource = hot.source)
        }
    }

    /** 双路词表快照；字段不可变，合并时整体替换，避免跨协程读到半更新状态。 */
    private data class HotSnapshot(
        val history: List<String>,
        val usage: List<String>,
    )
}
