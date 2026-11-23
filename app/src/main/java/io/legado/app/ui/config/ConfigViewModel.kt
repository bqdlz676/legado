package io.legado.app.ui.config

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.storage.LanBackupSession
import io.legado.app.help.storage.LanBackupTransfer
import io.legado.app.help.storage.Restore
import io.legado.app.utils.FileUtils
import io.legado.app.utils.QRCodeUtils
import io.legado.app.utils.getPrefString
import io.legado.app.utils.restart
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/**
 * 设置 / 备份分区的状态与动作层（新写，不继承旧 Fragment 编排）。
 *
 * 数据能力全部复用既有帮助类：[BookHelp] / [FileUtils] / [AppWebDav] / [Backup] /
 * [Restore] / [ImportOldData] / [LanBackupTransfer]；这里只做协程编排与异常收口，
 * 不持有任何 View / Binding / Adapter。
 */
class ConfigViewModel(application: Application) : BaseViewModel(application) {

    /** 推送 WebDAV 配置（地址 / 账号 / 口令 / 目录变更后调用）。 */
    fun upWebDavConfig() {
        execute { AppWebDav.upConfig() }
    }

    /** 清除书籍图片与缓存目录。 */
    fun clearCache() {
        execute {
            BookHelp.clearCache()
            FileUtils.delete(context.cacheDir.absolutePath)
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
        }
    }

    /** 清除 WebView 数据并重启应用。 */
    fun clearWebViewData() {
        execute {
            FileUtils.delete(context.getDir("webview", android.content.Context.MODE_PRIVATE))
            FileUtils.delete(context.getDir("hws_webview", android.content.Context.MODE_PRIVATE), true)
            context.toastOnUi(R.string.clear_webview_data_success)
            delay(3000)
            appCtx.restart()
        }
    }

    /** 收缩数据库（VACUUM）。 */
    fun shrinkDatabase() {
        execute {
            appDb.openHelper.writableDatabase.execSQL("VACUUM")
        }.onSuccess {
            context.toastOnUi(R.string.success)
        }
    }

    /** 本地 / 本地+WebDAV 备份；返回失败文案，成功返回 null。 */
    suspend fun backup(backupPath: String?, uploadWebDav: Boolean): String? =
        runCatching {
            withContext(Dispatchers.IO) {
                Backup.backupLocked(appCtx, backupPath, uploadWebDav)
            }
        }.getOrElse { error ->
            AppLog.put("备份出错\n${error.localizedMessage}", error)
            error.localizedMessage
        }

    /** 从本地 zip 恢复。 */
    suspend fun restoreFromUri(uri: Uri) {
        withContext(Dispatchers.IO) { Restore.restore(appCtx, uri) }
    }

    /** 导入旧版数据。 */
    fun importOldData(uri: Uri) {
        execute { ImportOldData.importUri(appCtx, uri) }
    }

    /** 拉取 WebDAV 备份名列表。 */
    suspend fun webDavBackupNames(): Result<List<String>> = runCatching {
        withContext(Dispatchers.IO) { AppWebDav.getBackupNames() }
    }

    /** 从 WebDAV 指定备份恢复。 */
    suspend fun restoreWebDav(name: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { AppWebDav.restoreWebDav(name) }
    }

    /** 手动推送到 WebDAV。 */
    suspend fun webDavBackup(fileName: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { AppWebDav.backUpWebDav(fileName) }
    }

    /** 局域网发送：打包 + 建会话 + 生成二维码。 */
    suspend fun lanPrepare(): Result<Pair<LanBackupSession, Bitmap>> = runCatching {
        val backupFile = withContext(Dispatchers.IO) { Backup.backupForLanTransferLocked(appCtx) }
        val session = LanBackupTransfer.prepare(backupFile, deviceName())
        val qrCode = QRCodeUtils.createQRCode(session.qrText)
            ?: throw IllegalStateException("生成二维码失败")
        session to qrCode
    }

    /** 局域网接收：拉取备份并恢复。 */
    suspend fun lanReceive(qrText: String): Result<Unit> = runCatching {
        val received = withContext(Dispatchers.IO) { LanBackupTransfer.receive(appCtx, qrText) }
        withContext(Dispatchers.IO) {
            Backup.backupBeforeLanRestoreLocked(appCtx)
            LanBackupTransfer.requireRestoreSpace(appCtx, received.uncompressedBytes)
        }
        kotlin.coroutines.coroutineContext.ensureActive()
        withContext(Dispatchers.IO) {
            Restore.restoreOrThrow(appCtx, received.file.toUri(), lanTransfer = true)
        }
        received.file.parentFile?.deleteRecursively()
    }

    private fun deviceName(): String {
        val configured = appCtx.getPrefString(PreferKey.webDavDeviceName)
        return configured?.takeIf { it.isNotBlank() }
            ?: "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
    }
}
