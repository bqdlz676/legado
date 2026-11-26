package io.legado.app.ui.config.b3

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString

/**
 * B3 设置页 · 分组装配。
 *
 * 分组口径：外观 / 阅读 / 备份与云同步 / 其它（任务书 §七三阶）。行模型只持有数据与动作，
 * 视觉收口到 [io.legado.app.ui.card.SettingRowCard]；偏好键全部复用既有 [PreferKey]，
 * 不新增同义分叉键。跨域入口（书源 / 封面 / 规则弹窗本体）归对应批次，本页只留行位。
 */

/** 主题模式档位：与 `theme_mode` 数组同序，值 0–3（P-13 四套主题口径）。 */
private val themeEntries = listOf("跟随系统", "亮色主题", "暗色主题", "墨水屏模式")
private val themeValues = listOf("0", "1", "2", "3")

/** 阅读字体三选项（R11）；注音小字维持无衬线，不随正文字体切换。 */
private val fontEntries = listOf("默认宋体", "外部目录字体", "应用目录字体")

/** 坚果云 WebDAV 注册页（R10）。 */
private const val JIANGUOYUN_URL = "https://www.jianguoyun.com/"

/** Google TTS 引擎 Play 详情页（R12）。 */
private const val GOOGLE_TTS_URL = "https://play.google.com/store/apps/details?id=com.google.android.tts"

/**
 * 装配当前可见分组列表。
 *
 * @param actions 侧效动作契约
 * @param webDavTestSummary 测试连接结果文案；null = 尚未测试
 * @param onTestWebDav 点击「测试连接」回调，结果状态由页面持有
 * @param openTarget 打开弹层目标
 */
@Composable
internal fun b3SettingGroups(
    actions: B3SettingActions,
    webDavTestSummary: String?,
    onTestWebDav: () -> Unit,
    openTarget: (B3SheetTarget) -> Unit,
): List<B3SettingGroup> {
    val context = LocalContext.current

    // ── 外观分组 ──
    val themeCurrent = rememberB3String(PreferKey.themeMode, "0") ?: "0"
    val themeLabel = themeEntries.getOrElse(themeValues.indexOf(themeCurrent)) { themeEntries.first() }
    val fontScaleRaw = rememberB3Int(PreferKey.fontScale, 0)
    // O-15：读取路径越界高值一并钳制到 8–16；非正回落默认 10，再走区间钳制。
    val fontScale = clampB3Int(fontScaleRaw.takeIf { it > 0 } ?: 10, 8, 16)

    // ── 阅读分组 ──
    // R11：字体档位落盘既有正文字体域字符串键（取值依据见返工报告 §R11）；初始值 / 外部改键均由桥重读。
    val fontStored = rememberB3String(PreferKey.fontFolder, fontEntries.first())
    val fontChoice = b3FontEntryFor(fontStored, fontEntries, fontEntries.first())

    // ── 备份与云同步分组 ──
    val webDavUrl = rememberB3String(PreferKey.webDavUrl, null)
    val webDavAccount = rememberB3String(PreferKey.webDavAccount, null)
    val webDavPassword = rememberB3String(PreferKey.webDavPassword, null)

    // 弹层标题 / 提示必须在 composable 作用域先取，不能落在 onClick 这类非 Composable lambda 内（编译期硬约束）。
    val themeModeTitle = stringResource(R.string.theme_mode)
    val fontScaleTitle = stringResource(R.string.font_scale)
    val fontScaleDefaultLabel = stringResource(R.string.restore_default)
    val webDavUrlTitle = stringResource(R.string.web_dav_url)
    val webDavUrlHint = stringResource(R.string.web_dav_url_s)
    val webDavAccountTitle = stringResource(R.string.web_dav_account)
    val webDavAccountHint = stringResource(R.string.web_dav_account_s)
    val webDavPwTitle = stringResource(R.string.web_dav_pw)
    val webDavPwHint = stringResource(R.string.web_dav_pw_s)
    val clearCacheTitle = stringResource(R.string.clear_cache)
    val clearWebviewTitle = stringResource(R.string.clear_webview_data)
    val shrinkDbTitle = stringResource(R.string.shrink_database)

    return listOf(
        B3SettingGroup(
            title = "外观",
            rows = listOf(
                B3ValueRow(
                    title = stringResource(R.string.theme_mode),
                    summary = themeLabel,
                    keywords = listOf("theme", "主题", "外观"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.ListPick(
                                title = themeModeTitle,
                                entries = themeEntries,
                                values = themeValues,
                                current = themeCurrent,
                                onPick = { value -> context.putPrefString(PreferKey.themeMode, value) },
                            ),
                        )
                    },
                ),
                B3ValueRow(
                    title = stringResource(R.string.font_scale),
                    summary = fontScale.toString(),
                    keywords = listOf("font", "字体", "字号"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.Number(
                                title = fontScaleTitle,
                                min = 8,
                                max = 16,
                                current = fontScale,
                                unit = null,
                                defaultLabel = fontScaleDefaultLabel,
                                onDefault = { context.putPrefInt(PreferKey.fontScale, 10) },
                                onConfirm = { value -> context.putPrefInt(PreferKey.fontScale, value) },
                            ),
                        )
                    },
                ),
            ),
        ),
        B3SettingGroup(
            title = "阅读",
            rows = listOf(
                B3ValueRow(
                    title = "阅读字体",
                    summary = fontChoice,
                    keywords = listOf("font", "字体", "左传"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.ListPick(
                                title = "阅读字体",
                                entries = fontEntries,
                                values = fontEntries,
                                current = fontChoice,
                                onPick = { value -> context.putPrefString(PreferKey.fontFolder, value) },
                            ),
                        )
                    },
                ),
                B3InfoRow(
                    title = "《左传》预览",
                    summary = "当前：$fontChoice —— 隐公元年，郑伯克段于鄢。注音小字维持无衬线不变。",
                    keywords = listOf("preview", "预览"),
                ),
                B3InfoRow(
                    title = "音效反馈",
                    summary = "与阅读底栏三态双向同步，默认全响；同源状态位随 D27 后端落地接入。",
                    keywords = listOf("sound", "音效", "声音", "InkSoundPool"),
                ),
                B3SwitchRow(
                    key = PreferKey.enableMangaEInk,
                    title = "墨水屏模式",
                    summary = "关动画 / 关粒子 / 关 3D / 黑白灰配色",
                    def = false,
                    keywords = listOf("eink", "墨水屏", "灰度"),
                ),
                B3SwitchRow(
                    key = PreferKey.noAnimScrollPage,
                    title = "关闭翻页动画",
                    summary = "墨水屏模式下关闭页面翻页动效",
                    def = false,
                    keywords = listOf("anim", "动画"),
                ),
                B3SwitchRow(
                    key = PreferKey.enableMangaGray,
                    title = "黑白灰配色",
                    summary = "剔除彩色，仅保留黑白灰阶",
                    def = false,
                    keywords = listOf("gray", "灰度", "黑白"),
                ),
            ),
        ),
        B3SettingGroup(
            title = "备份与云同步",
            rows = listOf(
                B3ValueRow(
                    title = stringResource(R.string.web_dav_url),
                    summary = webDavUrl?.takeIf { it.isNotBlank() } ?: stringResource(R.string.web_dav_url_s),
                    keywords = listOf("webdav", "url", "地址"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.Text(
                                key = PreferKey.webDavUrl,
                                title = webDavUrlTitle,
                                initial = webDavUrl,
                                hint = webDavUrlHint,
                                password = false,
                                onConfirm = { value ->
                                    context.putPrefString(PreferKey.webDavUrl, value)
                                    actions.saveWebDavConfig()
                                },
                            ),
                        )
                    },
                ),
                B3ValueRow(
                    title = stringResource(R.string.web_dav_account),
                    summary = webDavAccount?.takeIf { it.isNotBlank() } ?: stringResource(R.string.web_dav_account_s),
                    keywords = listOf("webdav", "account", "账号"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.Text(
                                key = PreferKey.webDavAccount,
                                title = webDavAccountTitle,
                                initial = webDavAccount,
                                hint = webDavAccountHint,
                                password = false,
                                onConfirm = { value ->
                                    context.putPrefString(PreferKey.webDavAccount, value)
                                    actions.saveWebDavConfig()
                                },
                            ),
                        )
                    },
                ),
                B3ValueRow(
                    title = stringResource(R.string.web_dav_pw),
                    summary = webDavPassword?.takeIf { it.isNotBlank() }?.let { "•".repeat(it.length) }
                        ?: stringResource(R.string.web_dav_pw_s),
                    keywords = listOf("webdav", "password", "密码"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.Text(
                                key = PreferKey.webDavPassword,
                                title = webDavPwTitle,
                                initial = webDavPassword,
                                hint = webDavPwHint,
                                password = true,
                                onConfirm = { value ->
                                    context.putPrefString(PreferKey.webDavPassword, value)
                                    actions.saveWebDavConfig()
                                },
                            ),
                        )
                    },
                ),
                B3ValueRow(
                    title = "测试连接",
                    summary = webDavTestSummary ?: "检查地址和密码后测试",
                    keywords = listOf("webdav", "test", "连接"),
                    onClick = onTestWebDav,
                ),
                B3ValueRow(
                    title = "推荐：坚果云 WebDAV",
                    summary = "每月 1GB 流量，够用了",
                    keywords = listOf("jianguoyun", "坚果云", "webdav"),
                    onClick = { actions.openUrl(JIANGUOYUN_URL) },
                ),
                B3ValueRow(
                    title = stringResource(R.string.backup),
                    summary = b3BackupSummary(b3BackupContents),
                    keywords = listOf("backup", "备份"),
                    onClick = actions.backupNow,
                ),
                B3InfoRow(
                    title = "从备份文件恢复",
                    // 冲突口径与纯函数 b3PickLaterTimestamp 同参：晚覆盖早、同戳保持本地、不做 diff 合并；
                    // 底层 Restore 合并接入该判定归备份批次（见返工报告 §O-10）。
                    summary = "文件选择契约随宿主批次接入；冲突按最后修改时间戳晚覆盖早，不做 diff 合并。",
                    keywords = listOf("restore", "恢复"),
                ),
            ),
        ),
        B3SettingGroup(
            title = "其它",
            rows = listOf(
                B3ValueRow(
                    title = "推荐安装 Google TTS 引擎以获得更好的朗读体验",
                    summary = "Google Play",
                    keywords = listOf("tts", "朗读", "引擎"),
                    onClick = { actions.openUrl(GOOGLE_TTS_URL) },
                ),
                B3ValueRow(
                    title = stringResource(R.string.clear_cache),
                    summary = stringResource(R.string.clear_cache_summary),
                    danger = true,
                    keywords = listOf("cache", "缓存"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.Confirm(
                                title = clearCacheTitle,
                                message = "清除已下载书籍与字体缓存？此操作不可撤销。",
                                confirmLabel = "清理",
                                onConfirm = actions.clearCache,
                            ),
                        )
                    },
                ),
                B3ValueRow(
                    title = stringResource(R.string.clear_webview_data),
                    summary = stringResource(R.string.clear_webview_data_summary),
                    danger = true,
                    keywords = listOf("webview", "浏览器"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.Confirm(
                                title = clearWebviewTitle,
                                message = "清除内置浏览器所有数据，应用将在 3 秒后重启？",
                                confirmLabel = "清除",
                                onConfirm = actions.clearWebViewData,
                            ),
                        )
                    },
                ),
                B3ValueRow(
                    title = stringResource(R.string.shrink_database),
                    summary = stringResource(R.string.shrink_database_summary),
                    danger = true,
                    keywords = listOf("database", "数据库", "压缩"),
                    onClick = {
                        openTarget(
                            B3SheetTarget.Confirm(
                                title = shrinkDbTitle,
                                message = "收缩数据库文件（VACUUM）？",
                                confirmLabel = "压缩",
                                onConfirm = actions.shrinkDatabase,
                            ),
                        )
                    },
                ),
            ),
        ),
    )
}
