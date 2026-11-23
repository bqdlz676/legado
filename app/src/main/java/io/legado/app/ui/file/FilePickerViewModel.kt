package io.legado.app.ui.file

import android.app.Application
import android.os.Bundle
import android.os.Environment
import io.legado.app.base.BaseViewModel
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.isSameOrDescendantOf
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 落文 · 文件 / 目录选择器主控。
 *
 * 支持目录选择与文件选择两种模式；扩展名白名单在选择文件时校验。
 * 兜底：根路径不可用 → 错误文案；非法文件夹名 → 校验拦截；选择文件为空 → 确认按钮禁用。
 */
class FilePickerViewModel(application: Application) : BaseViewModel(application) {

    var rootDoc: File? = Environment.getExternalStorageDirectory()
    var mode: Int = HandleFileContract.FILE
    var isShowHideDir: Boolean = false
    var allowExtensions: Array<String>? = null

    private val _uiState = MutableStateFlow(FilePickerState())
    val uiState: StateFlow<FilePickerState> = _uiState.asStateFlow()

    val isSelectDir: Boolean get() = mode == HandleFileContract.DIR
    val isSelectFile: Boolean get() = mode == HandleFileContract.FILE
    val lastDir: File? get() = _uiState.value.subDocs.lastOrNull() ?: rootDoc

    fun initData(arguments: Bundle?) {
        arguments?.let {
            mode = it.getInt("mode", HandleFileContract.FILE)
            isShowHideDir = it.getBoolean("isShowHideDir")
            it.getString("initPath")?.let { path -> rootDoc = File(path) }
            allowExtensions = it.getStringArray("allowExtensions")
        }
        upFiles(rootDoc)
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
            _uiState.update { it.copy(loading = true, selectFile = null, error = null) }
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

    fun select(file: File) {
        if (isSelectFile) {
            val allowed = allowExtensions.isNullOrEmpty() ||
                allowExtensions!!.contains(io.legado.app.utils.FileUtils.getExtension(file.path))
            if (allowed) _uiState.update { it.copy(selectFile = file) }
        }
    }

    fun createFolder(name: String) {
        execute {
            val dir = lastDir ?: throw NoStackTraceException("父文件夹不存在")
            val folder = File(dir, name)
            if (!folder.isSameOrDescendantOf(dir)) {
                throw NoStackTraceException("非法文件名")
            }
            folder.mkdir()
        }.onSuccess { upFiles(lastDir) }
            .onError { context.toastOnUi(it.localizedMessage) }
    }
}

data class FilePickerState(
    val files: List<File> = emptyList(),
    val subDocs: List<File> = emptyList(),
    val selectFile: File? = null,
    val loading: Boolean = false,
    val error: String? = null,
)
