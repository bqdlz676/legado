package io.legado.app.ui.config.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.settings.ListRow
import io.legado.app.ui.config.settings.NumberPickerSheet
import io.legado.app.ui.config.settings.NumberTarget
import io.legado.app.ui.config.settings.SettingGroup
import io.legado.app.ui.config.settings.SettingList
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SwitchRow
import io.legado.app.ui.config.settings.TextEditSheet
import io.legado.app.ui.config.settings.ValueRow
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref

/**
 * 其它设置分区（对应旧 pref_config_other.xml + OtherConfigFragment）。
 *
 * 行项与旧 Preference 一一对应；开关 / 列表 / 数字 / 文本编辑全部走 Compose 自绘弹层，
 * 数据落盘仍写同一批偏好键。服务启停、日志级别等副作用在 onChange 里按旧口径回吐。
 */
@Composable
fun OtherSection(actions: SectionActions) {
    val context = LocalContext.current
    val vm = actions.vm

    var numberTarget by remember { mutableStateOf<NumberTarget?>(null) }

    // 文本编辑弹层目标（回调回写型；与 BackupSection 按偏好键回写的 TextTarget 同名会在包级 Redeclaration，故本分区用独立名）
    var textTarget by remember { mutableStateOf<TextEditTarget?>(null) }

    val languageEntries = context.resources.getStringArray(R.array.language).toList()
    val languageValues = context.resources.getStringArray(R.array.language_value).toList()
    val syncEntries = context.resources.getStringArray(R.array.book_metadata_sync_options).toList()
    val syncValues = context.resources.getStringArray(R.array.book_metadata_sync_values).toList()
    val homeEntries = context.resources.getStringArray(R.array.default_home_page).toList()
    val homeValues = context.resources.getStringArray(R.array.default_home_page_value).toList()

    val groups = listOf(
        SettingGroup(
            null,
            listOf(
                ListRow(
                    key = PreferKey.language,
                    title = stringRes(R.string.language),
                    entries = languageEntries,
                    values = languageValues,
                    def = "auto",
                ) { /* 语言切换旧口径为重启，交由宿主事件处理 */ },
                ListRow(
                    key = PreferKey.bookMetadataSync,
                    title = stringRes(R.string.book_metadata_sync),
                    summary = stringRes(R.string.book_metadata_sync_summary),
                    entries = syncEntries,
                    values = syncValues,
                    def = "never",
                ) { },
            ),
        ),
        SettingGroup(
            stringRes(R.string.main_activity),
            listOf(
                SwitchRow(PreferKey.autoRefresh, stringRes(R.string.pt_auto_refresh), stringRes(R.string.ps_auto_refresh), false),
                SwitchRow(PreferKey.onlyUpdateRead, stringRes(R.string.only_update_read), stringRes(R.string.ps_only_update_read), false),
                SwitchRow(PreferKey.defaultToRead, stringRes(R.string.pt_default_read), stringRes(R.string.ps_default_read), false),
                SwitchRow(PreferKey.showDiscovery, stringRes(R.string.show_discovery), def = true),
                SwitchRow(PreferKey.showDiscoveryFastScroller, stringRes(R.string.show_discovery_fast_scroller), def = false),
                SwitchRow(PreferKey.showRss, stringRes(R.string.show_rss), def = true),
                ListRow(
                    key = PreferKey.defaultHomePage,
                    title = stringRes(R.string.default_home_page),
                    entries = homeEntries,
                    values = homeValues,
                    def = "bookshelf",
                ) { },
            ),
        ),
        SettingGroup(
            stringRes(R.string.other_setting),
            listOf(
                ValueRow(stringRes(R.string.user_agent), summary = AppConfig.userAgent) {
                    textTarget = TextEditTarget(R.string.user_agent, AppConfig.userAgent) { v ->
                        if (v.isBlank()) context.removePref(PreferKey.userAgent) else context.putPrefString(PreferKey.userAgent, v)
                    }
                },
                ValueRow(stringRes(R.string.custom_hosts), stringRes(R.string.custom_hosts_summary)) {
                    textTarget = TextEditTarget(R.string.custom_hosts, AppConfig.customHosts) { v ->
                        context.putPrefString(PreferKey.customHosts, v)
                    }
                },
                SwitchRow(PreferKey.webServiceWakeLock, stringRes(R.string.web_service_wake_lock), stringRes(R.string.web_service_wake_lock_summary), false),
                ValueRow(stringRes(R.string.book_tree_uri_t), stringRes(R.string.book_tree_uri_s)) {
                    actions.pickFolder { uri -> if (uri != null) AppConfig.defaultBookTreeUri = uri }
                },
                ValueRow(stringRes(R.string.source_edit_text_max_line), AppConfig.sourceEditMaxLine.toString()) {
                    numberTarget = NumberTarget(R.string.source_edit_text_max_line, 10, Int.MAX_VALUE, AppConfig.sourceEditMaxLine) {
                        AppConfig.sourceEditMaxLine = it
                    }
                },
                ValueRow(stringRes(R.string.check_source_config), io.legado.app.model.CheckSource.summary) {
                    actions.showDialog(io.legado.app.ui.config.CheckSourceConfig::class.java)
                },
                ValueRow(stringRes(R.string.direct_link_upload_rule), stringRes(R.string.direct_link_upload_rule_summary)) {
                    actions.showDialog(io.legado.app.ui.config.DirectLinkUploadConfig::class.java)
                },
                SwitchRow(PreferKey.cronet, "Cronet", stringRes(R.string.pref_cronet_summary), false),
                SwitchRow(PreferKey.antiAlias, stringRes(R.string.anti_alias), stringRes(R.string.pref_anti_alias_summary), false),
                ValueRow(stringRes(R.string.bitmap_cache_size), AppConfig.bitmapCacheSize.toString()) {
                    numberTarget = NumberTarget(R.string.bitmap_cache_size, 1, 1024, AppConfig.bitmapCacheSize) {
                        AppConfig.bitmapCacheSize = it
                    }
                },
                ValueRow(stringRes(R.string.image_retain_number), AppConfig.imageRetainNum.toString()) {
                    numberTarget = NumberTarget(R.string.image_retain_number, 0, 999, AppConfig.imageRetainNum) {
                        AppConfig.imageRetainNum = it
                    }
                },
                ValueRow(stringRes(R.string.pre_download), AppConfig.preDownloadNum.toString()) {
                    numberTarget = NumberTarget(R.string.pre_download, 0, 9999, AppConfig.preDownloadNum) {
                        AppConfig.preDownloadNum = it
                    }
                },
                SwitchRow(PreferKey.replaceEnableDefault, stringRes(R.string.replace_enable_default_t), stringRes(R.string.replace_enable_default_s), true),
                SwitchRow("mediaButtonOnExit", stringRes(R.string.media_button_on_exit_title), stringRes(R.string.media_button_on_exit_summary), true),
                SwitchRow(PreferKey.readAloudByMediaButton, stringRes(R.string.read_aloud_by_media_button_title), stringRes(R.string.read_aloud_by_media_button_summary), false),
                SwitchRow(PreferKey.ignoreAudioFocus, stringRes(R.string.ignore_audio_focus_title), stringRes(R.string.ignore_audio_focus_summary), false),
                SwitchRow(PreferKey.autoClearExpired, stringRes(R.string.auto_clear_expired), stringRes(R.string.auto_clear_expired_summary), true),
                SwitchRow(PreferKey.showAddToShelfAlert, stringRes(R.string.show_add_to_shelf_alert_title), stringRes(R.string.show_add_to_shelf_alert_summary), true),
                SwitchRow("autoUpdateVariant", stringRes(R.string.auto_update), stringRes(R.string.auto_update_summary), true),
                SwitchRow(PreferKey.liveUpdateNotifications, stringRes(R.string.live_update_notifications), stringRes(R.string.live_update_notifications_summary), false),
                SwitchRow(PreferKey.showMangaUi, stringRes(R.string.show_manga_ui), def = true),
                ValueRow(stringRes(R.string.video_setting), stringRes(R.string.video_setting_summary)) {
                    actions.showVideoSettings()
                },
                ValueRow(stringRes(R.string.web_port_title), AppConfig.webPort.toString()) {
                    numberTarget = NumberTarget(R.string.web_port_title, 1024, 60000, AppConfig.webPort) { AppConfig.webPort = it }
                },
                ValueRow(stringRes(R.string.mcp_port_title), AppConfig.mcpPort.toString()) {
                    numberTarget = NumberTarget(R.string.mcp_port_title, 1024, 65530, AppConfig.mcpPort) { AppConfig.mcpPort = it }
                },
                SwitchRow(PreferKey.jsSourceApiTokenRequired, stringRes(R.string.js_source_api_token_required_title), stringRes(R.string.js_source_api_token_required_summary), true),
                ValueRow(stringRes(R.string.js_source_api_token_title), stringRes(R.string.js_source_api_token_summary)) {
                    textTarget = TextEditTarget(R.string.js_source_api_token_title, context.getPrefString(PreferKey.jsSourceApiToken)) { v ->
                        context.putPrefString(PreferKey.jsSourceApiToken, v)
                    }
                },
                ValueRow(stringRes(R.string.clear_cache), stringRes(R.string.clear_cache_summary), danger = true) { vm.clearCache() },
                ValueRow(stringRes(R.string.clear_webview_data), stringRes(R.string.clear_webview_data_summary), danger = true) { vm.clearWebViewData() },
                ValueRow(stringRes(R.string.shrink_database), stringRes(R.string.shrink_database_summary), danger = true) { vm.shrinkDatabase() },
                ValueRow(stringRes(R.string.threads_num_title), AppConfig.threadCount.toString()) {
                    numberTarget = NumberTarget(R.string.threads_num_title, 1, 999, AppConfig.threadCount) { AppConfig.threadCount = it }
                },
                SwitchRow(PreferKey.processText, stringRes(R.string.add_to_text_context_menu_t), stringRes(R.string.add_to_text_context_menu_s), true),
                SwitchRow(PreferKey.recordLog, stringRes(R.string.record_log), stringRes(R.string.record_debug_log), false),
                SwitchRow(PreferKey.recordHttpLog, stringRes(R.string.record_http_log), stringRes(R.string.record_http_log_summary), false),
                SwitchRow(PreferKey.recordHeapDump, stringRes(R.string.record_heap_dump_t), stringRes(R.string.record_heap_dump_s), false),
            ),
        ),
    )

    SettingList(groups)

    numberTarget?.let { target ->
        NumberPickerSheet(
            visible = true,
            title = stringRes(target.titleRes),
            min = target.min,
            max = target.max,
            current = target.current,
            onConfirm = target.apply,
            onDismiss = { numberTarget = null },
        )
    }
    textTarget?.let { target ->
        TextEditSheet(
            visible = true,
            title = stringRes(target.titleRes),
            initial = target.initial,
            hint = "",
            onConfirm = target.apply,
            onDismiss = { textTarget = null },
        )
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

/**
 * 文本编辑弹层待决目标（仅本分区使用；回调回写型）。
 *
 * 命名避开同包 BackupSection 内按偏好键回写的 `TextTarget`：
 * Kotlin 同包两个顶层声明即便都标 private，同名仍按 Redeclaration 冲突（可见性管访问、不管身份）。
 */
private data class TextEditTarget(val titleRes: Int, val initial: String?, val apply: (String) -> Unit)
