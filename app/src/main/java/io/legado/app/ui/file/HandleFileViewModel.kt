package io.legado.app.ui.file

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.help.DirectLinkUpload
import io.legado.app.utils.GSON
import io.legado.app.utils.FileUtils
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.writeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 落文 · 文件分发宿主数据主控。
 *
 * 上传 / 落盘逻辑保留；错误状态改 StateFlow 外露，由 Compose 层提示并结束页面。
 */
class HandleFileViewModel(application: Application) : BaseViewModel(application) {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun upload(
        fileName: String,
        file: Any,
        contentType: String,
        success: (url: String) -> Unit,
    ) {
        execute { DirectLinkUpload.upLoad(fileName, file, contentType) }
            .onSuccess { success(it) }
            .onError { error ->
                AppLog.put("上传文件失败\n${error.localizedMessage}", error)
                error.printOnDebug()
                _error.update { error.localizedMessage }
            }
    }

    fun saveToLocal(uri: Uri, fileName: String, data: Any, success: (uri: Uri) -> Unit) {
        execute {
            val bytes = when (data) {
                is File -> data.readBytes()
                is ByteArray -> data
                is String -> data.toByteArray()
                else -> GSON.toJson(data).toByteArray()
            }
            withContext(Dispatchers.IO) {
                if (uri.isContentScheme()) {
                    val doc = DocumentFile.fromTreeUri(context, uri)!!
                    doc.findFile(fileName)?.delete()
                    val newDoc = doc.createFile("", fileName)
                    newDoc!!.writeBytes(context, bytes)
                    newDoc.uri
                } else {
                    val file = File(uri.path ?: uri.toString())
                    val newFile = FileUtils.createFileIfNotExist(file, fileName)
                    newFile.writeBytes(bytes)
                    Uri.fromFile(newFile)
                }
            }
        }.onError { error ->
            error.printOnDebug()
            _error.update { error.localizedMessage }
        }.onSuccess { success(it) }
    }
}
