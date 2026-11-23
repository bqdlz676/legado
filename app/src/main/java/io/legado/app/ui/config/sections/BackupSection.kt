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
import io.legado.app.ui.config.settings.SettingGroup
import io.legado.app.ui.config.settings.SettingList
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SwitchRow
import io.legado.app.ui.config.settings.TextEditSheet
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

    var textTarget by remember { mutableStateOf<TextTarget?>(null) }
    var numberTarget by remember { mutableStateOf<NumberTarget?>(null) }
    var contentMulti by remember { mutableStateOf(false) }
    var ignoreMulti by remember { mutableStateOf(false) }
    var autoBackupSheet by remember { mutableStateOf(false) }
    var backupModeSheet by remember { mutableStateOf(false) }
    var restoreNames by remember { mutableStateOf<List<String>?>(null) }
    var lanSheet by remember { mutableStateOf<LanState>(LanState.Closed) }
    var confirmLocalPw by remember { mutableStateOf(false) }

    data class TextTarget(val key: String, val titleRes: Int, val initial: String?, val password: Boolean)
    data class NumberTarget(val titleRes: Int, val min: Int, val max: Int, val current: Int, val apply: (Int) -> Unit)
    sealed class LanState {
        object Closed : LanState()
        object Menu : LanState()
        data class Qr(val bitmap: Bitmap) : LanState()
    }

    val groups = listOf(
        SettingGroup(
            stringRes(R.string.web_dav_set),
            listOf(
                ValueRow(stringRes(R.string.web_dav_url), context.getPrefString(PreferKey.webDavUrl) ?: stringRes(R.string.web_dav_url_s)) {
                    textTarget = TextTarget(PreferKey.webDavUrl, R.string.web_dav_url, context.getPrefString(PreferKey.webDavUrl), false)
                },
                ValueRow(stringRes(R.string.web_dav_account), context.getPrefString(PreferKey.webDavAccount) ?: stringRes(R.string.web_dav_account_s)) {
                    textTarget = TextTarget(PreferKey.webDavAccount, R.string.web_dav_account, context.getPrefString(PreferKey.webDavAccount), false)
                },
                ValueRow(stringRes(R.string.web_dav_pw), context.getPrefString(PreferKey.webDavPassword)?.let { "*".repeat(it.length) } ?: stringRes(R.string.web_dav_pw_s)) {
                    textTarget = TextTarget(PreferKey.webDavPassword, R.string.web_dav_pw, context.getPrefString(PreferKey.webDavPassword), true)
                },
                ValueRow(stringRes(R.string.sub_dir), AppConfig.webDavDir) {
                    textTarget = TextTarget(PreferKey.webDavDir, R.string.sub_dir, AppConfig.webDavDir, false)
                },
                ValueRow(stringRes(R.string.webdav_device_name), AppConfig.webDavDeviceName) {
                    textTarget = TextTarget(PreferKey.webDavDeviceName, R.string.webdav_device_name, AppConfig.webDavDeviceName, false)
                },
                ValueRow(stringRes(R.string.webdav_backup_keep_count_t), stringRes(R.string.webdav_backup_keep_count_s)) {
                    numberTarget = NumberTarget(R.string.webdav_backup_keep_count_t, 0, 999, AppConfig.webDavBackupKeepCount) {
                        putPrefInt(PreferKey.webDavBackupKeepCount, it)
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
                ValueRow(stringRes(R.string.set_local_password), stringRes(R.string.set_local_password_summary)) { confirmLocalPw = true },
                ValueRow(stringRes(R.string.backup_path), stringRes(R.string.select_backup_path)) {
                    actions.pickFolder { uri -> if (uri != null) putPrefString(PreferKey.backupPath, uri) }
                },
                ValueRow(stringRes(R.string.backup_content), stringRes(R.string.backup_content_summary)) { contentMulti = true },
                ValueRow(stringRes(R.string.lan_backup_transfer), stringRes(R.string.lan_backup_transfer_summary)) { lanSheet = LanState.Menu },
                ValueRow(stringRes(R.string.backup), stringRes(R.string.backup_destination_summary)) { backupModeSheet = true },
                ValueRow(stringRes(R.string.restore), stringRes(R.string.restore_summary)) {
                    scope.launch {
                        vm.webDavBackupNames().onSuccess { names ->
                            restoreNames = names
                        }.onFailure { actions.toast(it.localizedMessage ?: "") }
                    }
                },
                ValueRow(stringRes(R.string.restore_ignore), stringRes(R.string.restore_ignore_summary)) { ignoreMulti = true },
                SwitchRow(PreferKey.onlyLatestBackup, stringRes(R.string.only_latest_backup_t), stringRes(R.string.only_latest_backup_s), true),
                ValueRow(stringRes(R.string.auto_backup_t)) { autoBackupSheet = true },
                SwitchRow(PreferKey.autoCheckNewBackup, stringRes(R.string.auto_check_new_backup_t), stringRes(R.string.auto_check_new_backup_s), true),
            ),
        ),
    )

    SettingList(groups)

    textTarget?.let { t ->
        TextEditSheet(true, stringRes(t.titleRes), t.initial, "", password = t.password, onConfirm = { v ->
            putPrefString(t.key, v)
            vm.upWebDavConfig()
        }, onDismiss = { textTarget = null })
    }

    numberTarget?.let { t ->
        NumberPickerSheet(true, stringRes(t.titleRes), t.min, t.max, t.current, onConfirm = t.apply, onDismiss = { numberTarget = null })
    }

    if (contentMulti) {
        val checked = BooleanArray(BackupConfig.contentKeys.size) { BackupConfig.contentIsEnabled(BackupConfig.contentKeys[it]) }
        MultiChoiceSheet(true, stringRes(R.string.backup_content), BackupConfig.contentTitles.toList(), checked) { result ->
            BackupConfig.contentKeys.forEachIndexed { i, key -> BackupConfig.ignoreConfig[key] = !result[i] }
            BackupConfig.saveIgnoreConfig()
            contentMulti = false
        }
    }

    if (ignoreMulti) {
        val checked = BooleanArray(BackupConfig.ignoreKeys.size) { BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[it]] ?: false }
        MultiChoiceSheet(true, stringRes(R.string.restore_ignore), BackupConfig.ignoreTitle.toList(), checked) { result ->
            BackupConfig.ignoreKeys.forEachIndexed { i, key -> BackupConfig.ignoreConfig[key] = result[i] }
            BackupConfig.saveIgnoreConfig()
            ignoreMulti = false
        }
    }

    if (autoBackupSheet) {
        AutoBackupSheet(onDismiss = { autoBackupSheet = false })
    }

    if (backupModeSheet) {
        InkBottomSheetHost(title = stringRes(R.string.backup), onDismiss = { backupModeSheet = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

    restoreNames?.let { names ->
        ListPickSheet(true, stringRes(R.string.select_restore_file), names, names, "", onPick = { name ->
            scope.launch {
                vm.restoreWebDav(name).onFailure { actions.toast(it.localizedMessage ?: "") }
            }
        }, onDismiss = { restoreNames = null })
    }

    if (confirmLocalPw) {
        TextEditSheet(true, stringRes(R.string.set_local_password), LocalConfig.password, "password", onConfirm = { v ->
            LocalConfig.password = v.ifBlank { null }
        }, onDismiss = { confirmLocalPw = false })
    }

    when (val lan = lanSheet) {
        LanState.Menu -> LanMenuSheet(
            onSend = {
                lanSheet = LanState.Closed
                scope.launch {
                    vm.lanPrepare().onSuccess { (_, bitmap) -> lanSheet = LanState.Qr(bitmap) }
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
            onDismiss = { lanSheet = LanState.Closed },
        )
        is LanState.Qr -> InkBottomSheetHost(title = stringRes(R.string.lan_backup_send), onDismiss = { lanSheet = LanState.Closed }) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Image(bitmap = lan.bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(200.dp))
                Spacer(Modifier.height(12.dp))
            }
        }
        LanState.Closed -> Unit
    }
}

@Composable
private fun LanMenuSheet(onSend: () -> Unit, onReceive: () -> Unit, onDismiss: () -> Unit) {
    InkBottomSheetHost(title = stringRes(R.string.lan_backup_transfer), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            io.legado.app.ui.components.InkButton(stringRes(R.string.lan_backup_send), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY, onClick = onSend)
            io.legado.app.ui.components.InkButton(stringRes(R.string.lan_backup_receive), onClick = onReceive)
        }
    }
}

@Composable
private fun AutoBackupSheet(onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(AppConfig.autoBackup) }
    var webDav by remember { mutableStateOf(AppConfig.autoBackupWebDav) }
    var days by remember { mutableStateOf(AppConfig.autoBackupIntervalDays) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            io.legado.app.ui.config.settings.RowTitleBlock(stringRes(R.string.auto_backup_t), null)
            io.legado.app.ui.config.settings.InkSwitch(checked = enabled, enabled = true) { enabled = it }
        }
        Row(Modifier.fillMaxWidth()) {
            io.legado.app.ui.components.InkButton(
                if (webDav) stringRes(R.string.backup_local_webdav) else stringRes(R.string.backup_local_only),
                variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY,
                onClick = { webDav = !webDav },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            io.legado.app.ui.config.settings.RowTitleBlock(stringRes(R.string.auto_backup_interval_days), null)
            io.legado.app.ui.components.InkButton(days.toString(), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY) {
                days = days.coerceIn(1, 365)
            }
        }
        io.legado.app.ui.components.InkButton(stringRes(R.string.ok)) {
            putPrefBoolean(PreferKey.autoBackup, enabled)
            putPrefBoolean(PreferKey.autoBackupWebDav, webDav)
            putPrefInt(PreferKey.autoBackupIntervalDays, days)
            onDismiss()
        }
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
