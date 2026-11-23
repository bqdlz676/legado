package io.legado.app.ui.config

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.legado.app.R
import io.legado.app.ui.config.sections.BackupSection
import io.legado.app.ui.config.sections.CoverFontSection
import io.legado.app.ui.config.sections.CoverSection
import io.legado.app.ui.config.sections.OtherSection
import io.legado.app.ui.config.sections.ThemeSection
import io.legado.app.ui.config.sections.WelcomeSection
import io.legado.app.ui.config.settings.ListPickSheet
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SettingScaffold
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi

/**
 * 设置壳（新写 Compose 宿主，不再承载 Fragment / Binding / Preference 树）。
 *
 * 按 extra `configTag` 路由到对应分区 Composable；文件 / 图片 / 二维码选择契约在本 Activity
 * 注册，分区只通过 [SectionActions] 发动作。旧版的偏好搜索索引随 Preference 树一并移除。
 */
class ConfigActivity : AppCompatActivity() {

    private val viewModel by viewModels<ConfigViewModel>()

    /** 启动图标选择弹层状态（跨动作回调与 Compose 状态桥接）。 */
    private var iconPickerState by mutableStateOf<IconPickerState?>(null)

    private var folderResult: ((String?) -> Unit)? = null
    private var imageResult: ((Uri?) -> Unit)? = null
    private var fileResult: ((Uri?) -> Unit)? = null
    private var qrResult: ((String?) -> Unit)? = null

    private val folderContract = registerForActivityResult(HandleFileContract()) { result ->
        folderResult?.invoke(result.uri?.toString())
    }
    private val imageContract = registerForActivityResult(HandleFileContract()) { result ->
        imageResult?.invoke(result.uri)
    }
    private val fileContract = registerForActivityResult(HandleFileContract()) { result ->
        fileResult?.invoke(result.uri)
    }
    private val qrContract = registerForActivityResult(QrCodeResult()) { qr ->
        qrResult?.invoke(qr)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actions = SectionActions(
            vm = viewModel,
            pickFolder = { cb -> folderResult = cb; folderContract.launch { mode = HandleFileContract.DIR_SYS } },
            pickImage = { cb -> imageResult = cb; imageContract.launch { mode = HandleFileContract.IMAGE } },
            pickFile = { extensions, cb ->
                fileResult = cb
                fileContract.launch {
                    mode = HandleFileContract.FILE
                    allowExtensions = extensions ?: arrayOf("zip")
                }
            },
            openIntent = { startActivity(it) },
            openConfig = { tag ->
                startActivity(Intent(this, ConfigActivity::class.java).putExtra("configTag", tag))
            },
            showIconPicker = { entries, values, current, onPick ->
                iconPickerState = IconPickerState(entries, values, current, onPick)
            },
            showDialog = { clazz -> showConfigDialog(clazz) },
            showVideoSettings = {
                showDialogFragment(io.legado.app.ui.video.config.SettingsDialog(this))
            },
            toast = { toastOnUi(it) },
            scanQr = { cb -> qrResult = cb; qrContract.launch(null) },
        )

        setContent {
            val tag = intent.getStringExtra("configTag") ?: ConfigTag.OTHER_CONFIG

            // 设置页搜索词：骨架搜索条输入后经 LocalSettingSearchQuery 下发到各分区 SettingList 过滤；
            // 切换分区时清空，避免上一页的残留词把新页面列表滤空。
            var searchQuery by remember { mutableStateOf("") }
            LaunchedEffect(tag) { searchQuery = "" }

            val titleRes = when (tag) {
                ConfigTag.THEME_CONFIG -> R.string.theme_setting
                ConfigTag.BACKUP_CONFIG -> R.string.backup_restore
                ConfigTag.COVER_CONFIG -> R.string.cover_config
                ConfigTag.COVER_FONT_CONFIG -> R.string.cover_font_config
                ConfigTag.WELCOME_CONFIG -> R.string.welcome_style
                else -> R.string.other_setting
            }

            SettingScaffold(
                title = getString(titleRes),
                onBack = { finish() },
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
            ) {
                when (tag) {
                    ConfigTag.THEME_CONFIG -> ThemeSection(actions)
                    ConfigTag.BACKUP_CONFIG -> BackupSection(actions)
                    ConfigTag.COVER_CONFIG -> CoverSection(actions)
                    ConfigTag.COVER_FONT_CONFIG -> CoverFontSection(actions)
                    ConfigTag.WELCOME_CONFIG -> WelcomeSection(actions)
                    else -> OtherSection(actions)
                }
            }

            iconPickerState?.let { picker ->
                ListPickSheet(
                    visible = true,
                    title = getString(R.string.change_icon),
                    entries = picker.entries,
                    values = picker.values,
                    current = picker.current,
                    onPick = picker.onPick,
                    onDismiss = { iconPickerState = null },
                )
            }
        }
    }

    private data class IconPickerState(
        val entries: List<String>,
        val values: List<String>,
        val current: String,
        val onPick: (String) -> Unit,
    )

    @Suppress("DEPRECATION")
    private fun showConfigDialog(clazz: Class<*>) {
        val dialog = clazz.newInstance() as? DialogFragment ?: return
        dialog.show(supportFragmentManager, clazz.simpleName)
    }
}
