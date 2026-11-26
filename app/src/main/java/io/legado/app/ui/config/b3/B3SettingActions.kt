package io.legado.app.ui.config.b3

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.storage.Backup
import io.legado.app.utils.FileUtils
import io.legado.app.utils.restart
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/**
 * B3 设置页 · 侧效动作契约。
 *
 * 页面 Composable 只发动作（清缓存 / 清 WebView / 压缩库 / 备份 / 测试连接 / 外链跳转），
 * 不直接碰文件系统、数据库与 Intent 契约；底层能力复用既有帮助类（`BookHelp` /
 * `FileUtils` / `AppWebDav` / `Backup` / `appDb`），与旧 `ConfigViewModel` 同口径，
 * UI 壳不背底层调度（任务书 §3.1 处置）。
 *
 * 动作内协程统一走 [rememberCoroutineScope]；异常收口为 toast 文案，不抛到 UI 层、不留半屏。
 */
class B3SettingActions(
    /** 打开外部链接（坚果云注册页 / Google Play）；无浏览器时回落 toast，不崩。 */
    val openUrl: (String) -> Unit,
    /** 轻提示。 */
    val toast: (String) -> Unit,
    /** 清除书籍图片与缓存目录（确认层通过后调用）。 */
    val clearCache: () -> Unit,
    /** 清除 WebView 数据并重启应用（确认层通过后调用）。 */
    val clearWebViewData: () -> Unit,
    /** 收缩数据库 VACUUM（确认层通过后调用）。 */
    val shrinkDatabase: () -> Unit,
    /** 本地备份（不附加上传 WebDAV）；失败回吐文案。 */
    val backupNow: () -> Unit,
    /** 测试 WebDAV 连接：连上回 true 显绿点口径，连不上回 false 显失败文案（R9）。 */
    val testWebDav: (onResult: (Boolean) -> Unit) -> Unit,
    /** 落盘 WebDAV 地址 / 账号 / 密码后刷新授权配置。 */
    val saveWebDavConfig: () -> Unit,
)

/** 按当前 Context 与组合作用域装配默认动作实现；测试 / 宿主可覆写传入。 */
@Composable
fun rememberB3SettingActions(): B3SettingActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        B3SettingActions(
            openUrl = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure { context.toastOnUi(R.string.tip_cannot_jump_setting_page) }
            },
            toast = { message -> context.toastOnUi(message) },
            clearCache = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        BookHelp.clearCache()
                        FileUtils.delete(context.cacheDir.absolutePath)
                    }
                    context.toastOnUi(R.string.clear_cache_success)
                }
            },
            clearWebViewData = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        FileUtils.delete(context.getDir("webview", Context.MODE_PRIVATE).absolutePath)
                        FileUtils.delete(
                            context.getDir("hws_webview", Context.MODE_PRIVATE).absolutePath,
                            true,
                        )
                    }
                    context.toastOnUi(R.string.clear_webview_data_success)
                    delay(3000)
                    appCtx.restart()
                }
            },
            shrinkDatabase = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        appDb.openHelper.writableDatabase.execSQL("VACUUM")
                    }
                    context.toastOnUi(R.string.success)
                }
            },
            backupNow = {
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            Backup.backupLocked(appCtx, null, false)
                        }
                    }
                    if (result.isSuccess) context.toastOnUi(R.string.backup_success)
                    else context.toastOnUi(R.string.restore_fail)
                }
            },
            testWebDav = { onResult ->
                scope.launch {
                    val ok = runCatching {
                        withContext(Dispatchers.IO) { AppWebDav.getBackupNames() }
                        Unit
                    }.isSuccess
                    onResult(ok)
                }
            },
            saveWebDavConfig = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { AppWebDav.upConfig() } }
                }
            },
        )
    }
}
