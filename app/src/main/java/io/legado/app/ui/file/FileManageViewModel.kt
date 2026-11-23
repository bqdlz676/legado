package io.legado.app.ui.file

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 落文 · 本机文件浏览主控。
 *
 * 目录扫描 / 删除走 IO；状态汇总到 [uiState]。兜底：目录不存在或不可读 → 错误文案；
 * 删除失败 → 文案提示并留在当前目录；搜索仅过滤当前层，不递归。
 */
class FileManageViewModel(application: Application) : BaseViewModel(application) {

    val rootDoc: File? = context.getExternalFilesDir(null)?.parentFile
    private val _uiState = MutableStateFlow(FileManageState())
    val uiState: StateFlow<FileManageState> = _uiState.asStateFlow()

    val lastDir: File? get() = _uiState.value.subDocs.lastOrNull() ?: rootDoc

    fun init() {
        if (_uiState.value.files.isEmpty()) upFiles(rootDoc)
    }

    fun upFiles(parentFile: File?) {
        if (parentFile == null) {
            _uiState.update { it.copy(error = "根目录不可用") }
            return
        }
        execute {
            withContext(Dispatchers.IO) {
                val children = parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name }),
                ).orEmpty()
                if (parentFile == rootDoc) children
                else listOf(parentFile) + children
            }
        }.onStart {
            _uiState.update { it.copy(loading = true, error = null) }
        }.onSuccess { files ->
            _uiState.update { it.copy(files = files, loading = false) }
        }.onError { error ->
            _uiState.update { it.copy(loading = false, error = error.localizedMessage) }
            context.toastOnUi(error.localizedMessage)
        }
    }

    fun navigate(dir: File) {
        _uiState.update { it.copy(subDocs = it.subDocs + dir) }
        upFiles(dir)
    }

    fun pop() {
        _uiState.update { it.copy(subDocs = it.subDocs.dropLast(1)) }
        upFiles(lastDir)
    }

    fun jumpTo(index: Int) {
        _uiState.update { it.copy(subDocs = it.subDocs.take(index + 1)) }
        upFiles(lastDir)
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun visibleFiles(): List<File> {
        val query = _uiState.value.query.trim()
        val files = _uiState.value.files
        if (query.isEmpty()) return files
        return files.filter { it == lastDir || it.name.contains(query, ignoreCase = true) }
    }

    fun requestDelete(file: File) = _uiState.update { it.copy(confirmDelete = file) }
    fun dismissConfirm() = _uiState.update { it.copy(confirmDelete = null) }

    fun executeDelete(file: File) {
        execute {
            withContext(Dispatchers.IO) {
                file.deleteRecursively()
            }
        }.onSuccess {
            upFiles(lastDir)
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
        _uiState.update { it.copy(confirmDelete = null) }
    }
}

data class FileManageState(
    val files: List<File> = emptyList(),
    val subDocs: List<File> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val confirmDelete: File? = null,
)
