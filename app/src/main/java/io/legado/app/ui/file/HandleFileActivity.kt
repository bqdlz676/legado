package io.legado.app.ui.file

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.core.net.toUri
import io.legado.app.constant.AppLog
import io.legado.app.help.IntentData
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.checkWrite
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getJsonArray
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isSameOrDescendantOf
import io.legado.app.utils.launch
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.File

/**
 * 落文 · 文件分发透明宿主（Compose 自绘壳）。
 *
 * 职责与旧版一致：按 [HandleFileContract] 模式列出系统选择器 / 应用内选择器 /
 * 手动输入 / 上传等动作，结果回写 Activity Result 后结束。选择器弹窗仍走
 * [FilePickerDialog]，上传与落盘归 [HandleFileViewModel]。
 */
internal fun takePersistableUriPermissions(
    requestedFlags: Int,
    takePermission: (Int) -> Unit,
): Int {
    var persistedFlags = 0
    listOf(
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    ).forEach { flag ->
        if (requestedFlags and flag != 0) {
            try {
                takePermission(flag)
                persistedFlags = persistedFlags or flag
            } catch (_: SecurityException) {
            }
        }
    }
    return persistedFlags
}

class HandleFileActivity : FragmentActivity(), FilePickerDialog.CallBack {

    private val viewModel: HandleFileViewModel by viewModels()
    private var mode = 0
    private var showManualDir by mutableStateOf(false)
    private var showManualImg by mutableStateOf(false)

    private val selectDocTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                onResult(Intent().setData(uri))
            } ?: finish()
        }

    private val selectDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let {
            takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            onResult(Intent().setData(it))
        } ?: finish()
    }

    private val selectImage = registerForActivityResult(SelectImageContract()) {
        it.uri?.let { uri -> onResult(Intent().setData(uri)) } ?: finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getIntExtra("mode", 0)

        viewModel.error.value?.let {
            toastOnUi(it)
            finish()
            return
        }

        val allowExtensions = intent.getStringArrayExtra("allowExtensions")
        val selectList: ArrayList<SelectItem<Int>> = when (mode) {
            HandleFileContract.DIR_SYS -> getDirActions(true)
            HandleFileContract.DIR -> getDirActions()
            HandleFileContract.FILE -> getFileActions()
            HandleFileContract.EXPORT -> arrayListOf(
                SelectItem(getString(io.legado.app.R.string.upload_url), 111),
            ).apply { addAll(getDirActions()) }

            HandleFileContract.IMAGE -> getImageActions()
            else -> arrayListOf()
        }
        intent.getJsonArray<SelectItem<Int>>("otherActions")?.let { selectList.addAll(it) }

        val title = intent.getStringExtra("title") ?: when (mode) {
            HandleFileContract.EXPORT -> getString(io.legado.app.R.string.export)
            HandleFileContract.DIR -> getString(io.legado.app.R.string.select_folder)
            HandleFileContract.IMAGE -> getString(io.legado.app.R.string.select_image)
            else -> getString(io.legado.app.R.string.select_file)
        }

        setContent {
            val actions = remember { selectList }
            io.legado.app.ui.components.HandleFileSheet(
                title = title,
                actions = actions,
                onAction = { item -> dispatchAction(item, allowExtensions) },
                onCancel = { finish() },
                showManualDir = showManualDir,
                showManualImg = showManualImg,
                onDismissManualDir = { showManualDir = false },
                onDismissManualImg = { showManualImg = false },
                onConfirmDir = { path -> manualDirResult(path) },
                onConfirmImg = { src -> manualImgResult(src) },
            )
        }
    }

    private fun dispatchAction(item: SelectItem<Int>, allowExtensions: Array<String>?) {
        val value = item.value
        when (value) {
            HandleFileContract.DIR -> runCatching { selectDocTree.launch() }
                .onFailure {
                    AppLog.put(getString(io.legado.app.R.string.open_sys_dir_picker_error), it, true)
                    checkPermissions {
                        FilePickerDialog.show(supportFragmentManager, mode = HandleFileContract.DIR)
                    }
                }

            HandleFileContract.FILE -> runCatching {
                selectDoc.launch(typesOfExtensions(allowExtensions))
            }.onFailure {
                AppLog.put(getString(io.legado.app.R.string.open_sys_dir_picker_error), it, true)
                checkPermissions {
                    FilePickerDialog.show(
                        supportFragmentManager,
                        mode = HandleFileContract.FILE,
                        allowExtensions = allowExtensions,
                    )
                }
            }

            HandleFileContract.IMAGE -> selectImage.launch()

            10 -> checkPermissions {
                FilePickerDialog.show(supportFragmentManager, mode = HandleFileContract.DIR)
            }

            11 -> checkPermissions {
                FilePickerDialog.show(
                    supportFragmentManager,
                    mode = HandleFileContract.FILE,
                    allowExtensions = allowExtensions,
                )
            }

            111 -> getFileData()?.let { fileData ->
                viewModel.upload(fileData.first, fileData.second, fileData.third) { url ->
                    setResult(RESULT_OK, Intent().setData(url.toUri()))
                    finish()
                }
            }

            112 -> checkPermissions { showManualDir = true }
            113 -> checkPermissions { showManualImg = true }

            else -> {
                // otherActions 自定义路径项：直接回传 Uri。
                val path = item.title
                val uri = if (path.isContentScheme()) path.toUri() else Uri.fromFile(File(path))
                onResult(Intent().setData(uri))
            }
        }
    }

    private fun manualDirResult(inputPath: String) {
        if (inputPath.isBlank()) {
            toastOnUi(getString(io.legado.app.R.string.empty_directory_input))
            return
        }
        val file = File(inputPath)
        if (file.exists() && file.isDirectory && isExternalStorage(file) && file.checkWrite()) {
            onResult(Intent().setData(Uri.fromFile(file)))
        } else {
            toastOnUi(getString(io.legado.app.R.string.invalid_directory))
        }
    }

    private fun manualImgResult(inputPath: String) {
        if (inputPath.isBlank()) {
            toastOnUi(getString(io.legado.app.R.string.empty_img_src_input))
            return
        }
        if (inputPath.startsWith("http", ignoreCase = true)) {
            onResult(Intent().setData(inputPath.toUri()))
            return
        }
        val file = File(inputPath)
        if (file.exists() && file.isFile && isExternalStorage(file) && file.canRead()) {
            onResult(Intent().setData(Uri.fromFile(file)))
        } else {
            toastOnUi(getString(io.legado.app.R.string.invalid_file_path))
        }
    }

    private fun isExternalStorage(path: File): Boolean {
        if (path.isSameOrDescendantOf(appCtx.externalFiles.parentFile!!)) return false
        try {
            if (Environment.isExternalStorageEmulated(path)) return true
        } catch (_: IllegalArgumentException) {
        }
        try {
            if (Environment.isExternalStorageRemovable(path)) return true
        } catch (_: IllegalArgumentException) {
        }
        return false
    }

    private fun takePersistableUriPermission(uri: Uri, requestedFlags: Int) {
        if (!uri.isContentScheme()) return
        takePersistableUriPermissions(requestedFlags) { flag ->
            contentResolver.takePersistableUriPermission(uri, flag)
        }
    }

    private fun getFileData(): Triple<String, Any, String>? {
        val fileName = intent.getStringExtra("fileName")
        val file = intent.getStringExtra("fileKey")?.let { IntentData.get<Any>(it) }
        val contentType = intent.getStringExtra("contentType")
        if (fileName != null && file != null && contentType != null) {
            return Triple(fileName, file, contentType)
        }
        return null
    }

    private fun getDirActions(onlySys: Boolean = false): ArrayList<SelectItem<Int>> =
        if (onlySys) {
            arrayListOf(
                SelectItem(getString(io.legado.app.R.string.sys_folder_picker), HandleFileContract.DIR),
                SelectItem(getString(io.legado.app.R.string.manual_input), 112),
            )
        } else {
            arrayListOf(
                SelectItem(getString(io.legado.app.R.string.sys_folder_picker), HandleFileContract.DIR),
                SelectItem(getString(io.legado.app.R.string.app_folder_picker), 10),
                SelectItem(getString(io.legado.app.R.string.manual_input), 112),
            )
        }

    private fun getFileActions(): ArrayList<SelectItem<Int>> = arrayListOf(
        SelectItem(getString(io.legado.app.R.string.sys_file_picker), HandleFileContract.FILE),
        SelectItem(getString(io.legado.app.R.string.app_file_picker), 11),
    )

    private fun getImageActions(): ArrayList<SelectItem<Int>> = arrayListOf(
        SelectItem(getString(io.legado.app.R.string.sys_image_picker), HandleFileContract.IMAGE),
    ).apply {
        addAll(getFileActions())
        add(SelectItem(getString(io.legado.app.R.string.manual_input_img_src), 113))
    }

    private fun checkPermissions(success: (() -> Unit)? = null) {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(io.legado.app.R.string.tip_perm_request_storage)
            .onGranted { success?.invoke() }
            .onDenied { finish() }
            .onError { finish() }
            .request()
    }

    private fun typesOfExtensions(allowExtensions: Array<String>?): Array<String> {
        val types = hashSetOf<String>()
        if (allowExtensions.isNullOrEmpty()) {
            types.add("*/*")
        } else {
            allowExtensions.forEach { ext ->
                when (ext) {
                    "*" -> types.add("*/*")
                    "txt", "xml" -> types.add("text/*")
                    "js" -> {
                        types.add("application/javascript")
                        types.add("text/javascript")
                    }

                    else -> {
                        val mime = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                        types.add(mime)
                    }
                }
            }
        }
        return types.toTypedArray()
    }

    override fun onResult(data: Intent) {
        val uri = data.data ?: run {
            finish()
            return
        }
        if (mode == HandleFileContract.EXPORT) {
            getFileData()?.let { fileData ->
                viewModel.saveToLocal(uri, fileData.first, fileData.second) { savedUri ->
                    setResult(RESULT_OK, Intent().setData(savedUri))
                    finish()
                }
            }
        } else {
            data.putExtra("value", intent.getStringExtra("value"))
            setResult(RESULT_OK, data)
            finish()
        }
    }
}
