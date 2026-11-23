package io.legado.app.ui.about

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.help.CrashHandler
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.config.settings.GroupHeader
import io.legado.app.ui.config.settings.SettingDivider
import io.legado.app.ui.config.settings.SettingRowShell
import io.legado.app.ui.config.settings.RowTitleBlock
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.FileDoc
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.find
import io.legado.app.utils.list
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendMail
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

/**
 * 关于页（新写 Compose 宿主，类名保留供 My 页 / 旧入口 startActivity）。
 *
 * 行功能逐条对照旧 [AboutFragment]：贡献者、更新日志、检查更新、Beta 更新、
 * 崩溃日志、保存日志、堆转储、隐私政策、开源协议、免责声明。
 */
class AboutActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent { AboutScreen(activity = this@AboutActivity) }
        })
    }

    private fun showMdFile(title: String, fileName: String) {
        val mdText = String(assets.open(fileName).readBytes())
        showDialogFragment(TextDialog(title, mdText, TextDialog.Mode.MD))
    }

    private fun saveLog() = scope.launch {
        val backupPath = AppConfig.backupPath ?: let {
            appCtx.toastOnUi("未设置备份目录"); return@launch
        }
        if (!AppConfig.recordLog) {
            appCtx.toastOnUi("未开启日志记录，请去其他设置里打开记录日志"); delay(3000)
        }
        withContext(Dispatchers.IO) {
            val doc = FileDoc.fromUri(Uri.parse(backupPath), true)
            copyLogs(doc)
            copyHeapDump(doc)
        }
        appCtx.toastOnUi("已保存至备份目录")
    }

    private fun createHeapDump() = scope.launch {
        val backupPath = AppConfig.backupPath ?: let {
            appCtx.toastOnUi("未设置备份目录"); return@launch
        }
        if (!AppConfig.recordHeapDump) {
            appCtx.toastOnUi("未开启堆转储记录，请去其他设置里打开记录堆转储"); delay(3000)
        }
        appCtx.toastOnUi("开始创建堆转储")
        System.gc()
        CrashHandler.doHeapDump(true)
        val copied = withContext(Dispatchers.IO) {
            val doc = FileDoc.fromUri(Uri.parse(backupPath), true)
            copyHeapDump(doc)
        }
        appCtx.toastOnUi(if (copied) "已保存至备份目录" else "未找到堆转储文件")
    }

    private fun copyLogs(doc: FileDoc) {
        val cacheDir = appCtx.externalCache
        val logFiles = File(cacheDir, "logs")
        val crashFiles = File(cacheDir, "crash")
        val logcatFile = File(cacheDir, "logcat.txt")
        dumpLogcat(logcatFile)
        val zipFile = File(cacheDir, "logs.zip")
        ZipUtils.zipFiles(arrayListOf(logFiles, crashFiles, logcatFile), zipFile)
        doc.find("logs.zip")?.delete()
        zipFile.inputStream().use { input ->
            doc.createFileIfNotExist("logs.zip").openOutputStream().getOrNull()?.use { input.copyTo(it) }
        }
        zipFile.delete()
    }

    private fun copyHeapDump(doc: FileDoc): Boolean {
        val heapFile = FileDoc.fromFile(File(appCtx.externalCache, "heapDump")).list()?.firstOrNull() ?: return false
        doc.find("heapDump")?.delete()
        val heapDumpDoc = doc.createFolderIfNotExist("heapDump")
        heapFile.openInputStream().getOrNull()?.use { input ->
            heapDumpDoc.createFileIfNotExist(heapFile.name).openOutputStream().getOrNull()?.use { input.copyTo(it) }
        }
        return true
    }

    private fun dumpLogcat(file: File) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            file.outputStream().use { process.inputStream.copyTo(it) }
        } catch (e: Exception) {
            AppLog.put("保存Logcat失败\n$e", e)
        }
    }

    internal fun onRowClick(key: String) {
        when (key) {
            "contributors" -> openUrl(getString(R.string.contributors_url))
            "update_log" -> showMdFile(getString(R.string.update_log), "updateLog.md")
            "check_update" -> checkAppUpdate()
            "check_beta_update" -> checkAppUpdate(beta = true)
            "mail" -> sendMail(getString(R.string.email))
            "license" -> showMdFile(getString(R.string.license), "LICENSE.md")
            "disclaimer" -> showMdFile(getString(R.string.disclaimer), "disclaimer.md")
            "privacyPolicy" -> showMdFile(getString(R.string.privacy_policy), "privacyPolicy.md")
            "gzGzh" -> sendToClip(getString(R.string.legado_gzh))
            "crashLog" -> showDialogFragment<CrashLogsDialog>()
            "saveLog" -> saveLog()
            "createHeapDump" -> createHeapDump()
        }
    }
}

private data class AboutRow(
    val key: String,
    val titleRes: Int,
    val summaryRes: Int? = null,
)

@Composable
private fun AboutScreen(activity: AboutActivity) {
    val context = LocalContext.current

    val mainRows = remember {
        listOf(
            AboutRow("contributors", R.string.contributors, R.string.contributors_summary),
            AboutRow("update_log", R.string.update_log),
            AboutRow("check_update", R.string.check_update),
            AboutRow("check_beta_update", R.string.check_beta_update),
        )
    }
    val otherRows = remember {
        listOf(
            AboutRow("crashLog", R.string.crash_log),
            AboutRow("saveLog", R.string.save_log),
            AboutRow("createHeapDump", R.string.create_heap_dump),
            AboutRow("privacyPolicy", R.string.privacy_policy),
            AboutRow("license", R.string.license),
            AboutRow("disclaimer", R.string.disclaimer),
        )
    }

    Column(Modifier.fillMaxSize().background(ComponentTokens.bgPage)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.foundation.text.BasicText(
                        text = context.getString(R.string.app_name),
                        style = androidx.compose.ui.text.TextStyle(
                            color = ComponentTokens.textPrimary,
                            fontSize = 24.sp24(),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        ),
                    )
                    androidx.compose.foundation.text.BasicText(
                        text = "${context.getString(R.string.version)} ${AppConst.appInfo.versionName}",
                        style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textSecondary, fontSize = 13.sp13()),
                    )
                    androidx.compose.foundation.text.BasicText(
                        text = context.getString(R.string.legado_gzh),
                        style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textAccent, fontSize = 13.sp13()),
                    )
                }
            }
            item { SettingDivider() }
            mainRows.forEach { row ->
                item(row.key) {
                    AboutRowItem(row) { activity.onRowClick(row.key) }
                }
            }
            item { GroupHeader(context.getString(R.string.other)) }
            otherRows.forEach { row ->
                item(row.key) {
                    AboutRowItem(row) { activity.onRowClick(row.key) }
                }
            }
            item {
                InkButton(
                    text = context.getString(R.string.share),
                    variant = InkButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    context.sendToClip(context.getString(R.string.app_share_description))
                }
            }
        }
    }
}

@Composable
private fun AboutRowItem(row: AboutRow, onClick: () -> Unit) {
    SettingRowShell(onClick = onClick, enabled = true) {
        RowTitleBlock(
            title = androidx.compose.ui.res.stringResource(row.titleRes),
            summary = row.summaryRes?.let { androidx.compose.ui.res.stringResource(it) },
        )
    }
}

private fun Int.sp24() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp13() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
