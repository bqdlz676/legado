package io.legado.app.ui.book.source.debug

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.Debug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 落文 · 书源调试主控。
 *
 * 保留 [Debug.Callback] 协议：Debug 执行层回调分项响应与日志；VM 只把结果改写为
 * 单向状态流，Screen 纯渲染。调试通道占用 / 抓取异常 / 超时被墙均通过 [SourceDebugUiState.error]
 * 与日志流透出，不做静默吞掉。
 */
class SourceDebugViewModel(application: Application) : BaseViewModel(application), Debug.Callback {

    private val _uiState = MutableStateFlow(SourceDebugUiState())
    val uiState: StateFlow<SourceDebugUiState> = _uiState.asStateFlow()

    fun init(sourceUrl: String?, finally: () -> Unit) {
        sourceUrl ?: run { finally(); return }
        execute {
            appDb.bookSourceDao.getBookSource(sourceUrl)
        }.onSuccess { source ->
            _uiState.update { it.copy(source = source) }
        }.onFinally {
            loadExploreKinds()
            finally()
        }
    }

    /** 载入发现项；发现解析失败时把错误写进日志，保留兜底文案。 */
    fun loadExploreKinds() {
        val source = _uiState.value.source ?: return
        execute {
            source.exploreKinds().filter { !it.url.isNullOrBlank() }
        }.onSuccess { kinds ->
            _uiState.update { state ->
                state.copy(
                    exploreKinds = kinds,
                    logs = if (kinds.firstOrNull()?.title?.startsWith("ERROR:") == true) {
                        state.logs + DebugLog(
                            -1,
                            "获取发现出错\n${kinds.firstOrNull()?.url ?: ""}",
                        )
                    } else {
                        state.logs
                    },
                )
            }
        }.onError { error ->
            _uiState.update { it.copy(error = error.localizedMessage ?: "调试失败") }
        }
    }

    fun refreshExploreKinds() {
        execute {
            _uiState.value.source?.clearExploreKindsCache()
            _uiState.update { it.copy(logs = emptyList(), helpOpen = true) }
            loadExploreKinds()
        }
    }

    fun setKeyword(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
    }

    fun setHelpOpen(open: Boolean) {
        _uiState.update { it.copy(helpOpen = open) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList(), error = null) }
    }

    /**
     * 发起一轮调试。
     * @param key 关键字 / 详情 URL / `发现标题::URL`；`++`、`--` 前缀分别指向目录页、正文页。
     */
    fun startDebug(
        key: String,
        onStart: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        val source = _uiState.value.source ?: return
        execute {
            check(Debug.tryAcquireCallback(this@SourceDebugViewModel)) {
                "调试通道占用中，请稍后重试"
            }
            try {
                Debug.startDebug(viewModelScope, source, key)
            } catch (throwable: Throwable) {
                Debug.cancelDebug(this@SourceDebugViewModel)
                throw throwable
            }
        }.onStart {
            _uiState.update { it.copy(loading = true, helpOpen = false, error = null) }
            onStart()
        }.onError {
            _uiState.update { state ->
                state.copy(loading = false, error = it.localizedMessage ?: "调试失败")
            }
            onError(it)
        }
    }

    override fun printLog(state: Int, msg: String) {
        when (state) {
            10 -> _uiState.update { it.copy(searchSrc = msg) }
            20 -> _uiState.update { it.copy(bookSrc = msg) }
            30 -> _uiState.update { it.copy(tocSrc = msg) }
            40 -> _uiState.update { it.copy(contentSrc = msg) }
            else -> {
                _uiState.update { it.copy(logs = it.logs + DebugLog(state, msg)) }
                if (state == -1 || state == 1000) {
                    _uiState.update { it.copy(loading = false) }
                    Debug.cancelDebug(this)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Debug.cancelDebug(this)
    }
}
