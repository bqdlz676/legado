package io.legado.app.ui.config.sections

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.storage.BackupConfig
import io.legado.app.ui.config.settings.InkBottomSheetHost
import io.legado.app.ui.config.settings.ListPickSheet
import io.legado.app.ui.config.settings.MultiChoiceSheet
import io.legado.app.ui.config.settings.NumberPickerSheet
import io.legado.app.ui.config.settings.NumberTarget
import io.legado.app.ui.config.settings.SettingGroup
import io.legado.app.ui.config.settings.SettingList
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SheetExitHost
import io.legado.app.ui.config.settings.SwitchRow
import io.legado.app.ui.config.settings.TextEditSheet
import io.legado.app.ui.config.settings.rememberSheetExitController
import io.legado.app.ui.config.settings.ValueRow
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.launch

/**
 * 备份 / 恢复分区（对应旧 pref_config_backup.xml + BackupConfigFragment）。
 *
 * 备份打包、WebDAV 同步、局域网传输全部复用既有帮助类（[io.legado.app.help.storage.Backup] /
 * [io.legado.app.help.AppWebDav] / [io.legado.app.help.storage.LanBackupTransfer]）；
 * 本层只负责 Compose 编排、多选清单与结果回吐。
 */
@Composable
fun BackupSection(actions: SectionActions) {
    val context = LocalContext.current
    val vm = actions.vm
    val scope = rememberCoroutineScope()

    var textTarget by remember { mutableStateOf<PrefTextTarget?>(null) }
    var numberTarget by remember { mutableStateOf<NumberTarget?>(null) }
    var contentMulti by remember { mutableStateOf(false) }
    var ignoreMulti by remember { mutableStateOf(false) }
    var autoBackupSheet by remember { mutableStateOf(false) }
    var backupModeSheet by remember { mutableStateOf(false) }
    var restoreNames by remember { mutableStateOf<List<String>?>(null) }
    var lanSheet by remember { mutableStateOf<LanState>(LanState.Closed) }
    var confirmLocalPw by remember { mutableStateOf(false) }
    // B29：各弹层槽位各持一份两段式离场控制器；离场段拒收重复打开。
    val textExit = rememberSheetExitController()
    val numberExit = rememberSheetExitController()
    val contentExit = rememberSheetExitController()
    val ignoreExit = rememberSheetExitController()
    val modeExit = rememberSheetExitController()
    val restoreExit = rememberSheetExitController()
    val pwExit = rememberSheetExitController()
    val lanExit = rememberSheetExitController()
    val qrExit = rememberSheetExitController()

    val groups = listOf(
        SettingGroup(
            stringRes(R.string.web_dav_set),
            listOf(
                ValueRow(stringRes(R.string.web_dav_url), context.getPrefString(PreferKey.webDavUrl) ?: stringRes(R.string.web_dav_url_s)) {
                    if (textExit.canOpen) {
                        textTarget = PrefTextTarget(PreferKey.webDavUrl, R.string.web_dav_url, context.getPrefString(PreferKey.webDavUrl), false)
                        textExit.open()
                    }
                },
                ValueRow(stringRes(R.string.web_dav_account), context.getPrefString(PreferKey.webDavAccount) ?: stringRes(R.string.web_dav_account_s)) {
                    if (textExit.canOpen) {
                        textTarget = PrefTextTarget(PreferKey.webDavAccount, R.string.web_dav_account, context.getPrefString(PreferKey.webDavAccount), false)
                        textExit.open()
                    }
                },
                ValueRow(stringRes(R.string.web_dav_pw), context.getPrefString(PreferKey.webDavPassword)?.let { "*".repeat(it.length) } ?: stringRes(R.string.web_dav_pw_s)) {
                    if (textExit.canOpen) {
                        textTarget = PrefTextTarget(PreferKey.webDavPassword, R.string.web_dav_pw, context.getPrefString(PreferKey.webDavPassword), true)
                        textExit.open()
                    }
                },
                ValueRow(stringRes(R.string.sub_dir), AppConfig.webDavDir) {
                    if (textExit.canOpen) {
                        textTarget = PrefTextTarget(PreferKey.webDavDir, R.string.sub_dir, AppConfig.webDavDir, false)
                        textExit.open()
                    }
                },
                ValueRow(stringRes(R.string.webdav_device_name), AppConfig.webDavDeviceName) {
                    if (textExit.canOpen) {
                        textTarget = PrefTextTarget(PreferKey.webDavDeviceName, R.string.webdav_device_name, AppConfig.webDavDeviceName, false)
                        textExit.open()
                    }
                },
                ValueRow(stringRes(R.string.webdav_backup_keep_count_t), stringRes(R.string.webdav_backup_keep_count_s)) {
                    if (numberExit.canOpen) {
                        numberTarget = NumberTarget(R.string.webdav_backup_keep_count_t, 0, 999, AppConfig.webDavBackupKeepCount) {
                            context.putPrefInt(PreferKey.webDavBackupKeepCount, it)
                        }
                        numberExit.open()
                    }
                },
                SwitchRow(PreferKey.webDavBookAutoUpload, stringRes(R.string.webdav_book_auto_upload_t), stringRes(R.string.webdav_book_auto_upload_s), false),
                SwitchRow(PreferKey.webDavBookAutoRestore, stringRes(R.string.webdav_book_auto_restore_t), stringRes(R.string.webdav_book_auto_restore_s), false),
                SwitchRow(PreferKey.syncBookProgress, stringRes(R.string.sync_book_progress_t), stringRes(R.string.sync_book_progress_s), true),
                SwitchRow(PreferKey.syncBookProgressPlus, stringRes(R.string.sync_book_progress_plus_t), stringRes(R.string.sync_book_progress_plus_s), false),
            ),
        ),
        SettingGroup(
            stringRes(R.string.backup_restore),
            listOf(
                ValueRow(stringRes(R.string.set_local_password), stringRes(R.string.set_local_password_summary)) {
                    if (pwExit.canOpen) {
                        confirmLocalPw = true
                        pwExit.open()
                    }
                },
                ValueRow(stringRes(R.string.backup_path), stringRes(R.string.select_backup_path)) {
                    actions.pickFolder { uri -> if (uri != null) context.putPrefString(PreferKey.backupPath, uri) }
                },
                ValueRow(stringRes(R.string.backup_content), stringRes(R.string.backup_content_summary)) {
                    if (contentExit.canOpen) {
                        contentMulti = true
                        contentExit.open()
                    }
                },
                ValueRow(stringRes(R.string.lan_backup_transfer), stringRes(R.string.lan_backup_transfer_summary)) {
                    if (lanExit.canOpen) {
                        lanSheet = LanState.Menu
                        lanExit.open()
                    }
                },
                ValueRow(stringRes(R.string.backup), stringRes(R.string.backup_destination_summary)) {
                    if (modeExit.canOpen) {
                        backupModeSheet = true
                        modeExit.open()
                    }
                },
                ValueRow(stringRes(R.string.restore), stringRes(R.string.restore_summary)) {
                    scope.launch {
                        vm.webDavBackupNames().onSuccess { names ->
                            if (restoreExit.canOpen) {
                                restoreNames = names
                                restoreExit.open()
                            }
                        }.onFailure { actions.toast(it.localizedMessage ?: "") }
                    }
                },
                ValueRow(stringRes(R.string.restore_ignore), stringRes(R.string.restore_ignore_summary)) {
                    if (ignoreExit.canOpen) {
                        ignoreMulti = true
                        ignoreExit.open()
                    }
                },
                SwitchRow(PreferKey.onlyLatestBackup, stringRes(R.string.only_latest_backup_t), stringRes(R.string.only_latest_backup_s), true),
                ValueRow(stringRes(R.string.auto_backup_t)) { autoBackupSheet = true },
                SwitchRow(PreferKey.autoCheckNewBackup, stringRes(R.string.auto_check_new_backup_t), stringRes(R.string.auto_check_new_backup_s), true),
            ),
        ),
    )

    SettingList(groups)

    textTarget?.let { t ->
        SheetExitHost(textExit, onFinished = { textTarget = null }) {
            TextEditSheet(textExit.visible, stringRes(t.titleRes), t.initial, "", password = t.password, onConfirm = { v ->
                context.putPrefString(t.key, v)
                vm.upWebDavConfig()
            }, onDismiss = { textExit.beginExit() })
        }
    }

    numberTarget?.let { t ->
        SheetExitHost(numberExit, onFinished = { numberTarget = null }) {
            NumberPickerSheet(numberExit.visible, stringRes(t.titleRes), t.min, t.max, t.current, onConfirm = t.apply, onDismiss = { numberExit.beginExit() })
        }
    }

    if (contentMulti) {
        val checked = BooleanArray(BackupConfig.contentKeys.size) { BackupConfig.contentIsEnabled(BackupConfig.contentKeys[it]) }
        SheetExitHost(contentExit, onFinished = { contentMulti = false }) {
            MultiChoiceSheet(
                contentExit.visible,
                stringRes(R.string.backup_content),
                BackupConfig.contentTitles.toList(),
                checked,
                onConfirm = { result ->
                    BackupConfig.contentKeys.forEachIndexed { i, key -> BackupConfig.ignoreConfig[key] = !result[i] }
                    BackupConfig.saveIgnoreConfig()
                },
                onDismiss = { contentExit.beginExit() },
            )
        }
    }

    if (ignoreMulti) {
        val checked = BooleanArray(BackupConfig.ignoreKeys.size) { BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[it]] ?: false }
        SheetExitHost(ignoreExit, onFinished = { ignoreMulti = false }) {
            MultiChoiceSheet(
                ignoreExit.visible,
                stringRes(R.string.restore_ignore),
                BackupConfig.ignoreTitle.toList(),
                checked,
                onConfirm = { result ->
                    BackupConfig.ignoreKeys.forEachIndexed { i, key -> BackupConfig.ignoreConfig[key] = result[i] }
                    BackupConfig.saveIgnoreConfig()
                },
                onDismiss = { ignoreExit.beginExit() },
            )
        }
    }

    if (autoBackupSheet) {
        AutoBackupSheet(onDismiss = { autoBackupSheet = false })
    }

    if (backupModeSheet) {
        SheetExitHost(modeExit, onFinished = { backupModeSheet = false }) {
            InkBottomSheetHost(visible = modeExit.visible, title = stringRes(R.string.backup), onDismiss = { modeExit.beginExit() }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 动作按钮为同屏触发备份任务后直摘（旧口径）；遮罩 / 拖拽关闭走 modeExit 两段式离场。
                    io.legado.app.ui.components.InkButton(stringRes(R.string.backup_local_only), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY) {
                        backupModeSheet = false
                        scope.launch {
                            val err = vm.backup(AppConfig.backupPath, false)
                            actions.toast(if (err == null) context.getString(R.string.backup_success) else context.getString(R.string.backup_fail, err))
                        }
                    }
                    io.legado.app.ui.components.InkButton(stringRes(R.string.backup_local_webdav)) {
                        backupModeSheet = false
                        scope.launch {
                            val err = vm.backup(AppConfig.backupPath, true)
                            actions.toast(if (err == null) context.getString(R.string.backup_success) else context.getString(R.string.backup_fail, err))
                        }
                    }
                }
            }
        }
    }

    restoreNames?.let { names ->
        SheetExitHost(restoreExit, onFinished = { restoreNames = null }) {
            ListPickSheet(restoreExit.visible, stringRes(R.string.select_restore_file), names, names, "", onPick = { name ->
                scope.launch {
                    vm.restoreWebDav(name).onFailure { actions.toast(it.localizedMessage ?: "") }
                }
            }, onDismiss = { restoreExit.beginExit() })
        }
    }

    if (confirmLocalPw) {
        SheetExitHost(pwExit, onFinished = { confirmLocalPw = false }) {
            TextEditSheet(pwExit.visible, stringRes(R.string.set_local_password), LocalConfig.password, "password", onConfirm = { v ->
                LocalConfig.password = v.ifBlank { null }
            }, onDismiss = { pwExit.beginExit() })
        }
    }

    when (val lan = lanSheet) {
        LanState.Menu -> SheetExitHost(lanExit, onFinished = { lanSheet = LanState.Closed }) {
            LanMenuSheet(
                visible = lanExit.visible,
                onSend = {
                    lanSheet = LanState.Closed
                    scope.launch {
                        vm.lanPrepare().onSuccess { bitmap -> lanSheet = LanState.Qr(bitmap) }
                            .onFailure { actions.toast(it.localizedMessage ?: "") }
                    }
                },
                onReceive = {
                    lanSheet = LanState.Closed
                    actions.scanQr { qr ->
                        if (qr != null) scope.launch {
                            vm.lanReceive(qr).onFailure { actions.toast(it.localizedMessage ?: "") }
                        }
                    }
                },
                onDismiss = { lanExit.beginExit() },
            )
        }
        is LanState.Qr -> {
            LaunchedEffect(lanSheet) { qrExit.open() }
            SheetExitHost(qrExit, onFinished = { lanSheet = LanState.Closed }) {
                InkBottomSheetHost(visible = qrExit.visible, title = stringRes(R.string.lan_backup_send), onDismiss = { qrExit.beginExit() }) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Image(bitmap = lan.bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(200.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        LanState.Closed -> Unit
    }
}

@Composable
private fun LanMenuSheet(visible: Boolean, onSend: () -> Unit, onReceive: () -> Unit, onDismiss: () -> Unit) {
    InkBottomSheetHost(visible = visible, title = stringRes(R.string.lan_backup_transfer), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            io.legado.app.ui.components.InkButton(stringRes(R.string.lan_backup_send), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY, onClick = onSend)
            io.legado.app.ui.components.InkButton(stringRes(R.string.lan_backup_receive), onClick = onReceive)
        }
    }
}

@Composable
private fun AutoBackupSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AppConfig.autoBackup) }
    var webDav by remember { mutableStateOf(AppConfig.autoBackupWebDav) }
    var days by remember { mutableStateOf(AppConfig.autoBackupIntervalDays) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            io.legado.app.ui.config.settings.RowTitleBlock(
                stringRes(R.string.auto_backup_t),
                null,
                modifier = Modifier.weight(1f),
            )
            io.legado.app.ui.config.settings.InkSwitch(
                checked = enabled,
                enabled = true,
                onCheckedChange = { enabled = it },
            )
        }
        Row(Modifier.fillMaxWidth()) {
            io.legado.app.ui.components.InkButton(
                if (webDav) stringRes(R.string.backup_local_webdav) else stringRes(R.string.backup_local_only),
                variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY,
                onClick = { webDav = !webDav },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            io.legado.app.ui.config.settings.RowTitleBlock(
                stringRes(R.string.auto_backup_interval_days),
                null,
                modifier = Modifier.weight(1f),
            )
            io.legado.app.ui.components.InkButton(days.toString(), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY) {
                days = days.coerceIn(1, 365)
            }
        }
        io.legado.app.ui.components.InkButton(stringRes(R.string.ok)) {
            context.putPrefBoolean(PreferKey.autoBackup, enabled)
            context.putPrefBoolean(PreferKey.autoBackupWebDav, webDav)
            context.putPrefInt(PreferKey.autoBackupIntervalDays, days)
            onDismiss()
        }
    }
}

/**
 * 文本编辑弹层待决目标（仅本分区使用；按偏好键回写 + 密码掩码型）。
 *
 * 命名避开同包 OtherSection 的回调型 `TextEditTarget`：Kotlin 同包两个顶层声明
 * 即便都标 private，同名仍按 Redeclaration 冲突（可见性管访问、不管身份）。
 */
private data class PrefTextTarget(
    val key: String,
    val titleRes: Int,
    val initial: String?,
    val password: Boolean,
)

/** 局域网备份弹层状态机：关闭 / 菜单 / 发送二维码。 */
private sealed interface LanState {
    object Closed : LanState
    object Menu : LanState
    data class Qr(val bitmap: Bitmap) : LanState
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
