package io.legado.app.ui.config

// F-73 冻结：本模块无具体规格，暂停扩写与新派单；须先出 ≥2 份候选规格→独立选型→再重做（审计 表四#9，C1 设置页，字段级口径待补）

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
import io.legado.app.ui.book.read.NoteDialogExitGate
import io.legado.app.ui.config.settings.ListPickSheet
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SettingScaffold
import io.legado.app.ui.config.settings.SheetExitController
import io.legado.app.ui.config.settings.SheetExitHost
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi

/**
 * 设置壳（新写 Compose 宿主，不再承载 Fragment / Binding / Preference 树）。
 *
 * 按 extra `configTag` 路由到对应分区 Composable；文件 / 图片 / 二维码选择契约在本 Activity
 * 注册，分区只通过 [SectionActions] 发动作。旧版的偏好搜索索引随 Preference 树一并移除。
 *
 * 〔B3 收口留痕（R20 / A3 先接后删）〕新设置面 = [io.legado.app.ui.config.b3.B3SettingScreen]，
 * 挂主壳第三槽；本 Activity 属旧半成品宿主，待新设置面接线验收通过后按三口径扫描删除，
 * 本单不提前物理删除，避免留下设置空窗。旧直达入口仅余 `ui/main/my/` 旧 Fragment（已与新壳脱钩）。
 */
class ConfigActivity : AppCompatActivity() {

    private val viewModel by viewModels<ConfigViewModel>()

    /** 启动图标选择弹层状态（跨动作回调与 Compose 状态桥接）。 */
    private var iconPickerState by mutableStateOf<IconPickerState?>(null)

    /** B29：图标选择弹层两段式离场控制器（Activity 字段，跨动作回调桥接，口径同 [iconPickerState]）。 */
    private val iconExit = SheetExitController(NoteDialogExitGate(), mutableStateOf(false))

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
                // B29：离场段拒收重复打开；放行后登记在场段。
                if (iconExit.canOpen) {
                    iconPickerState = IconPickerState(entries, values, current, onPick)
                    iconExit.open()
                }
            },
            showDialog = { clazz -> showConfigDialog(clazz) },
            showMdAsset = { titleRes, assetName ->
                // 合规文档落点：读取 assets 内 MD 后走既有 TextDialog（MD 模式）展示，与旧关于页同一渲染通道。
                val md = String(assets.open(assetName).readBytes())
                showDialogFragment(TextDialog(getString(titleRes), md, TextDialog.Mode.MD))
            },
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
                SheetExitHost(iconExit, onFinished = { iconPickerState = null }) {
                    ListPickSheet(
                        visible = iconExit.visible,
                        title = getString(R.string.change_icon),
                        entries = picker.entries,
                        values = picker.values,
                        current = picker.current,
                        onPick = picker.onPick,
                        onDismiss = { iconExit.beginExit() },
                    )
                }
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
